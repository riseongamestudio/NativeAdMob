using System;
using UnityEngine;
namespace RiseOn.NativeAdMob {
    /// <summary>
    /// The shared engine of the two overlay formats - an ad presented over
    /// the game in its own window. FullScreenAd covers the whole
    /// screen; HalfScreenAd covers a bottom slice. The constructor
    /// is assembly-locked: those two are its only faces.
    /// </summary>
    public abstract class OverlayAd : NativeAd, IOverlayAdCallbacks {
        private const int INVALID_GENERATION = 0;
        private const string AD_RELEASED_ERROR        = "Ad released";
        private const string AD_ALREADY_SHOWING_ERROR = "Ad already showing";

        /// <summary>Presentation-side events of this placement.</summary>
        public event Action OnDisplayed;
        public event Action<int, string> OnPresentationFailed;

        private IOverlayAdClient client;
        private ShowCompletedHandler currentShowCompleted;
        private bool showPendingOrActive;
        private bool cachedAdReady;
        private bool cachedAdLoading;
        private int  showGeneration;

        private protected OverlayAd(
            string adUnitId
          , bool coversFullScreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha)
            : base(adUnitId) {
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

        public void LoadAd() {
            lock (nativeAdStateLock) {
                if (releasedManaged || client == null) return;

                Debug.Log("LoadAd()");
                client.LoadAd();
            }
        }

        public void ShowAd(ShowCompletedHandler onAdCompleted) {
            int    generation = INVALID_GENERATION;
            string rejectedError = null;
            lock (nativeAdStateLock) {
                if (releasedManaged || client == null) {
                    rejectedError = AD_RELEASED_ERROR;
                } else if (showPendingOrActive) {
                    rejectedError = AD_ALREADY_SHOWING_ERROR;
                } else {
                    showPendingOrActive  = true;
                    currentShowCompleted = onAdCompleted;
                    generation           = ++showGeneration;
                }
            }

            if (rejectedError != null) {
                InvokeCompletionSafely(
                    onAdCompleted
                  , rejectedError
                  , false);
                return;
            }

            Debug.Log("ShowAd()");
            lock (nativeAdStateLock) {
                if (!releasedManaged
                 && showPendingOrActive
                 && generation == showGeneration
                 && client != null) {
                    // The show id travels with the call and is echoed back, so
                    // a completion can only resolve the show that registered it.
                    client.ShowAd(generation);
                }
            }
        }

        public void HideAd() {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                client?.HideAd();
            }
        }

        public bool IsAdReady() {
            lock (nativeAdStateLock) {
                return
                    !releasedManaged
                 && !showPendingOrActive
                 && client != null
                 && cachedAdReady;
            }
        }

        public bool IsAdLoading() {
            lock (nativeAdStateLock) {
                return
                    !releasedManaged
                 && client != null
                 && cachedAdLoading;
            }
        }

        public void Release() {
            IOverlayAdClient       releasedClient;
            ShowCompletedHandler completed;
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                releasedManaged = true;
                releasedClient  = client;
                client          = null;

                completed            = showPendingOrActive
                        ? currentShowCompleted
                        : null;
                showPendingOrActive  = false;
                currentShowCompleted = null;
                cachedAdReady        = false;
                cachedAdLoading      = false;
                ++showGeneration;
            }

            releasedClient?.Release();
            InvokeCompletionSafely(
                completed
              , AD_RELEASED_ERROR
              , false);
        }

        void IOverlayAdCallbacks.OnLoadingStarted()
            => DispatchFromNative(RaiseLoadingStarted);

        void IOverlayAdCallbacks.OnLoadingCompleted(int errorCode, string errorMessage)
            => DispatchFromNative(
                () => RaiseLoadingCompleted(errorCode, errorMessage));

        void IOverlayAdCallbacks.OnAdPaid(AdValue adValue)
            => DispatchFromNative(() => RaiseAdPaid(adValue));

        // Synchronous under the lock: IsAdReady right after a load completes
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
            DispatchFromNative(() => InvokeSafely(OnDisplayed));
        }

        void IOverlayAdCallbacks.OnPresentationFailed(int errorCode, string errorMessage)
            => DispatchFromNative(() => {
                var handler = OnPresentationFailed;
                if (handler == null) return;
                InvokeSafely(() => handler(errorCode, errorMessage));
            });

        void IOverlayAdCallbacks.OnShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                if (!TryTakeShowCompletion(
                        showId
                      , adConsumed
                      , out var completed))
                    return;

                // The game callback must fully return before the consumed ad
                // starts its automatic replacement load.
                InvokeCompletionSafely(
                    completed
                  , errorMessage
                  , adConsumed);
                if (adConsumed) LoadAd();
            });
        }

        private bool TryTakeShowCompletion(
            int generation
          , bool adConsumed
          , out ShowCompletedHandler completed) {
            lock (nativeAdStateLock) {
                if (!showPendingOrActive || generation != showGeneration) {
                    completed = null;
                    return false;
                }

                if (adConsumed) {
                    cachedAdReady   = false;
                    cachedAdLoading = false;
                }
                showPendingOrActive  = false;
                completed            = currentShowCompleted;
                currentShowCompleted = null;
            }

            return true;
        }

        private static void InvokeCompletionSafely(
            ShowCompletedHandler completion
          , string errorMessage
          , bool adConsumed) {
            try {
                completion?.Invoke(errorMessage, adConsumed);
            } catch (Exception exception) {
                Debug.LogException(exception);
            }
        }
    }
}
