using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// The shared engine of the two overlay formats - an ad presented over the game in its own window.<br/>
    /// FullScreenAd covers the whole screen; HalfScreenAd covers a bottom slice.<br/>
    /// The constructor is assembly-locked: those two are its only faces.
    /// </summary>
    public abstract class OverlayAd : BaseAd, IOverlayAdCallbacks {
        private const int INVALID_GENERATION = 0;
        private const string AD_RELEASED_ERROR        = "Ad released";
        private const string AD_ALREADY_SHOWING_ERROR = "Ad already showing";
        // A show that never reached the screen. The native side owns its own
        // codes; this one is raised on this side of the boundary.
        private const int SHOW_REJECTED_CODE = -1;

        /// <summary>Presentation-side events of this placement.</summary>
        public event Action OnAdDisplayed;
        public event Action<AdError> OnAdDisplayFailed;
        public event Action OnAdHidden;

        private IOverlayAdClient client;
        private bool showPendingOrActive;
        private bool cachedAdReady;
        private bool cachedAdLoading;
        private int  showGeneration;

        private protected OverlayAd(
            string adUnitId
          , string format
          , bool fullScreen
          , float heightRatio
          , int cacheSize
          , Color backgroundColor
          , in CloseSettings controls)
            : base(adUnitId, format) {
            var platform = AdPlatformRegistry.Installed;
            if (platform == null) {
                Debug.Log(
                    $"{GetType().Name} is not supported on "
                    + Application.platform);
                return;
            }
            client = platform.CreateOverlay(
                new OverlayAdSettings {
                    AdUnitId         = adUnitId
                  , FullScreen = fullScreen
                  , HeightRatio      = heightRatio
                  , CacheSize        = cacheSize
                  , BackgroundColor  = AdColor.Pack(backgroundColor)
                  , Close         = controls
                }
              , this);
        }

        /// <summary>
        /// Replaces the whole control strip: the ad shown next carries these.<br/>
        /// Every knob moves together, so a placement never ends up with one rule from an old plan and one from a new one.
        /// </summary>
        public void SetClose(in CloseSettings controls) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                client?.SetClose(controls);
            }
        }

        public void Load() {
            lock (nativeAdStateLock) {
                if (releasedManaged || client == null) return;

                client.Load();
            }
        }

        public void Show() {
            int    generation = INVALID_GENERATION;
            string rejectedError = null;
            lock (nativeAdStateLock) {
                if (releasedManaged || client == null) {
                    rejectedError = AD_RELEASED_ERROR;
                } else if (showPendingOrActive) {
                    rejectedError = AD_ALREADY_SHOWING_ERROR;
                } else {
                    showPendingOrActive = true;
                    generation          = ++showGeneration;
                }
            }

            // A show that is turned away never reaches the screen, which is
            // the same news as one that breaks on the way there.
            if (rejectedError != null) {
                RaiseDisplayFailed(SHOW_REJECTED_CODE, rejectedError);
                return;
            }

            lock (nativeAdStateLock) {
                if (!releasedManaged
                 && showPendingOrActive
                 && generation == showGeneration
                 && client != null) {
                    // The show id travels with the call and is echoed back, so
                    // a completion can only resolve the show that registered it.
                    client.Show(generation);
                }
            }
        }

        public void Hide() {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                client?.Hide();
            }
        }

        public bool IsReady() {
            lock (nativeAdStateLock) {
                return
                    !releasedManaged
                 && !showPendingOrActive
                 && client != null
                 && cachedAdReady;
            }
        }

        public bool IsLoading() {
            lock (nativeAdStateLock) {
                return
                    !releasedManaged
                 && client != null
                 && cachedAdLoading;
            }
        }

        public void Release() {
            IOverlayAdClient releasedClient;
            bool             showWasPending;
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                releasedManaged = true;
                releasedClient  = client;
                client          = null;

                showWasPending      = showPendingOrActive;
                showPendingOrActive = false;
                cachedAdReady       = false;
                cachedAdLoading     = false;
                ++showGeneration;
            }

            releasedClient?.Release();
            // A show still in flight can no longer land.
            if (showWasPending) {
                RaiseDisplayFailed(SHOW_REJECTED_CODE, AD_RELEASED_ERROR);
            }
        }

        void IOverlayAdCallbacks.OnLoadingCompleted(
            int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize)
            => DispatchFromNative(
                () => RaiseLoadingCompleted(
                    errorCode
                  , errorMessage
                  , cachedCount
                  , cacheSize));

        void IOverlayAdCallbacks.OnAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode)
            => DispatchFromNative(
                () => RaiseAdPaid(source, adUnitId, value, currencyCode));

        // Synchronous under the lock: IsReady right after a load completes
        // must already see the new state.
        void IOverlayAdCallbacks.OnStateChanged(bool isReady, bool isLoading) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                cachedAdReady   = isReady;
                cachedAdLoading = isLoading;
            }
        }

        void IOverlayAdCallbacks.OnShowNotReady() {}

        void IOverlayAdCallbacks.OnDisplayed() {
            DispatchFromNative(() => {
                InvokeSafely(OnAdDisplayed);
                // The replacement starts the moment this ad reaches the
                // screen, so the next placement finds one waiting instead
                // of a load that only began when this ad closed.
                Load();
            });
        }

        void IOverlayAdCallbacks.OnDisplayFailed(int errorCode, string errorMessage) {
            DispatchFromNative(() => RaiseDisplayFailed(errorCode, errorMessage));
        }

        void IOverlayAdCallbacks.OnShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed
          , int cachedCount) {
            if (!TryTakeShow(showId, cachedCount)) return;

            // Listeners must fully return before the consumed ad starts its
            // automatic replacement load.
            if (string.IsNullOrEmpty(errorMessage)) {
                InvokeSafely(OnAdHidden);
            } else {
                RaiseDisplayFailed(SHOW_REJECTED_CODE, errorMessage);
            }
            if (adConsumed) Load();
        }

        // Both halves of readiness settle here, together, under one lock.
        //
        // They are owned by different sides: whether a show is running is
        // this side's business, and what the cache holds is native's. The
        // count therefore travels WITH the completion - a listener asking
        // IsReady the moment it is told the ad is gone gets the cache as it
        // is now, not as the last separate state notification left it. Those
        // notifications still arrive and still agree; they are no longer the
        // only way the truth gets here, which is what used to make a chained
        // show read its answer from before the previous ad had ended.
        private bool TryTakeShow(int generation, int cachedCount) {
            lock (nativeAdStateLock) {
                if (!showPendingOrActive || generation != showGeneration) {
                    return false;
                }

                showPendingOrActive = false;
                cachedAdReady       = cachedCount > 0;
            }

            return true;
        }

        private void RaiseDisplayFailed(int errorCode, string errorMessage) {
            var handler = OnAdDisplayFailed;
            if (handler == null) return;

            AdError error = new(errorCode, errorMessage);
            InvokeSafely(() => handler(error));
        }
    }
}
