using System;
using RiseOn.Analytics;
using UnityEngine;
namespace RiseOn.NativeAdMob {
    /// <summary>
    /// The shared engine of the two overlay formats - an ad presented over
    /// the game in its own window. FullScreenAd covers the whole
    /// screen; HalfScreenAd covers a bottom slice. The constructor
    /// is assembly-locked: those two are its only faces.
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
          , AdFormat format
          , bool coversFullScreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha)
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
                    AdUnitId           = adUnitId
                  , CoversFullScreen   = coversFullScreen
                  , CountdownSec       = countdownSec
                  , XRandomSide        = xRandomSide
                  , NumberOppositeSide = numberOppositeSide
                  , HeightRatio        = heightRatio
                  , BackgroundAlpha    = backgroundAlpha
                }
              , this);
        }

        public void SetCountdownSec(int countdownSec) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                client?.SetCountdownSec(countdownSec);
            }
        }

        public void Load() {
            lock (nativeAdStateLock) {
                if (releasedManaged || client == null) return;

                Debug.Log("Load()");
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

            Debug.Log("Show()");
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

        void IOverlayAdCallbacks.OnLoadingCompleted(int errorCode, string errorMessage)
            => DispatchFromNative(
                () => RaiseLoadingCompleted(errorCode, errorMessage));

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

        void IOverlayAdCallbacks.OnDisplayFailed(int errorCode, string errorMessage)
            => DispatchFromNative(() => RaiseDisplayFailed(errorCode, errorMessage));

        void IOverlayAdCallbacks.OnShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                if (!TryTakeShow(showId)) return;

                // Listeners must fully return before the consumed ad starts
                // its automatic replacement load.
                if (string.IsNullOrEmpty(errorMessage)) {
                    InvokeSafely(OnAdHidden);
                } else {
                    RaiseDisplayFailed(SHOW_REJECTED_CODE, errorMessage);
                }
                if (adConsumed) Load();
            });
        }

        // Readiness is never guessed here. The native side publishes it -
        // empty cache the moment a show takes the ad, full again the moment
        // a replacement lands - and that notification is applied straight
        // away while this completion waits a frame in Unity's queue. Wiping
        // the flag here would therefore overwrite the truth with a stale
        // assumption and strand a perfectly good cached ad.
        private bool TryTakeShow(int generation) {
            lock (nativeAdStateLock) {
                if (!showPendingOrActive || generation != showGeneration) {
                    return false;
                }

                showPendingOrActive = false;
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
