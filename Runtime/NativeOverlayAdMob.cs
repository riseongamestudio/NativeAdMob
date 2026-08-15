using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <param name="errorMessage">
    /// Empty when the native layer reports a normal completion; otherwise the
    /// reason the show was rejected or the presentation failed.
    /// </param>
    /// <param name="adConsumed">
    /// True when the cached ad was consumed, including presentation failures
    /// that happen after the ad leaves the ready state.
    /// </param>
    public delegate void ShowCompletedHandler(
        string errorMessage
      , bool adConsumed);

    public abstract partial class NativeOverlayAdMob : NativeAdMob {
        private const int INVALID_GENERATION = 0;

        private const string AD_RELEASED_ERROR        = "NativeAdMob released";
        private const string AD_ALREADY_SHOWING_ERROR = "NativeAdMob already showing";
        private const string AD_NOT_READY_ERROR       = "NativeAdMob not ready";
        private const string AD_NOT_CONFIGURED_ERROR  = "NativeAdMob not configured";

        private const string JAVA_CLASS_NAME               = "com.riseon.nativeadmob.FullScreen";
        private const string JAVA_CONFIGURE_METHOD         = "Configure";
        private const string JAVA_SET_COUNTDOWN_SEC_METHOD = "SetCountdownSec";
        private const string JAVA_HIDE_AD_METHOD           = "HideAd";

        private readonly string adUnitId;
        private ShowCompletedHandler currentShowCompleted;
        private bool showPendingOrActive;
        private bool cachedAdReady;
        private bool cachedAdLoading;
        private int  showGeneration;

        /// <summary>Presentation-side events of this placement.</summary>
        public event Action OnDisplayed;
        public event Action<int, string> OnPresentationFailed;

        private protected NativeOverlayAdMob(
            string adUnitId
          , bool coversFullScreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha)
            : base(adUnitId) {
            this.adUnitId = adUnitId;
            if (supportsAndroid) {
                AndroidCreate();
            } else if (supportsIOS) {
                IOSCreate();
            }
            Configure(
                coversFullScreen
              , countdownSec
              , xRandomSide
              , numberOppositeSide
              , heightRatio
              , backgroundAlpha);
        }

        partial void AndroidCreate();
        partial void IOSCreate();
        partial void EditorSetPreviewConfig(
            bool fullscreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha);
        partial void EditorSetPreviewCountdownSec(int countdownSec);
        partial void IOSConfigure(
            bool fullscreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha);
        partial void IOSSetCountdownSec(int countdownSec);
        partial void AndroidLoadAd();
        partial void IOSLoadAd();
        partial void EditorLoadAd();
        partial void AndroidShowAd(ShowCompletedHandler onAdCompleted);
        partial void IOSShowAd(ShowCompletedHandler onAdCompleted);
        partial void EditorShowAd(ShowCompletedHandler onAdCompleted);
        partial void IOSHideAd();
        partial void EditorHideAd();
        partial void AndroidIsAdReady(ref bool isReady);
        partial void IOSIsAdReady(ref bool isReady);
        partial void EditorIsAdReady(ref bool isReady);
        partial void AndroidIsAdLoading(ref bool isLoading);
        partial void IOSIsAdLoading(ref bool isLoading);
        partial void EditorIsAdLoading(ref bool isLoading);
        partial void AndroidRelease();
        partial void IOSRelease();
        partial void EditorRelease();
        partial void AndroidClearCompletedListener();

        private void Configure(
            bool fullscreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha) {
            EditorSetPreviewConfig(
                fullscreen
              , countdownSec
              , xRandomSide
              , numberOppositeSide
              , heightRatio
              , backgroundAlpha);
            if (supportsIOS) {
                IOSConfigure(
                    fullscreen
                  , countdownSec
                  , xRandomSide
                  , numberOppositeSide
                  , heightRatio
                  , backgroundAlpha);
                return;
            }
            CallAndroid(
                JAVA_CONFIGURE_METHOD
              , fullscreen
              , countdownSec
              , xRandomSide
              , numberOppositeSide
              , heightRatio
              , backgroundAlpha);
        }

        public void SetCountdownSec(int countdownSec) {
            EditorSetPreviewCountdownSec(countdownSec);
            if (supportsIOS) {
                IOSSetCountdownSec(countdownSec);
                return;
            }
            CallAndroid(JAVA_SET_COUNTDOWN_SEC_METHOD, countdownSec);
        }

        public void LoadAd() {
            if (supportsAndroid) {
                AndroidLoadAd();
            } else if (supportsIOS) {
                IOSLoadAd();
            } else if (supportsEditorPreview) {
                EditorLoadAd();
            } else {
                Debug.Log("LoadAd() is not supported on this platform");
            }
        }

        public void ShowAd(ShowCompletedHandler onAdCompleted) {
            if (supportsAndroid) {
                AndroidShowAd(onAdCompleted);
            } else if (supportsIOS) {
                IOSShowAd(onAdCompleted);
            } else if (supportsEditorPreview) {
                EditorShowAd(onAdCompleted);
            } else {
                Debug.Log("ShowAd() is not supported on this platform");
                InvokeCompletionSafely(
                    onAdCompleted
                  , string.Empty
                  , false);
            }
        }

        public void HideAd() {
            if (supportsAndroid) {
                CallAndroid(JAVA_HIDE_AD_METHOD);
            } else if (supportsIOS) {
                IOSHideAd();
            } else if (supportsEditorPreview) {
                EditorHideAd();
            } else {
                Debug.Log("HideAd() only supports Android for now ...");
            }
        }

        public bool IsAdReady() {
            if (supportsAndroid) {
                var isReady = false;
                AndroidIsAdReady(ref isReady);
                return isReady;
            }
            if (supportsIOS) {
                var isReady = false;
                IOSIsAdReady(ref isReady);
                return isReady;
            }
            if (supportsEditorPreview) {
                var isReady = false;
                EditorIsAdReady(ref isReady);
                return isReady;
            }

            Debug.Log("IsAdReady() only supports Android for now ...");
            return false;
        }

        public bool IsAdLoading() {
            if (supportsAndroid) {
                var isLoading = false;
                AndroidIsAdLoading(ref isLoading);
                return isLoading;
            }
            if (supportsIOS) {
                var isLoading = false;
                IOSIsAdLoading(ref isLoading);
                return isLoading;
            }
            if (supportsEditorPreview) {
                var isLoading = false;
                EditorIsAdLoading(ref isLoading);
                return isLoading;
            }

            Debug.Log("IsAdLoading() only supports Android for now ...");
            return false;
        }

        public void Release() {
            if (supportsAndroid) {
                AndroidRelease();
            } else if (supportsIOS) {
                IOSRelease();
            } else if (supportsEditorPreview) {
                EditorRelease();
            } else {
                Debug.Log("Release() only supports Android for now ...");
            }
        }

        private protected override void HandleNativeStateChanged(
            bool isReady
          , bool isLoading) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                cachedAdReady   = isReady;
                cachedAdLoading = isLoading;
            }
        }

        private void CompleteShowFromNativeThread(
            int generation
          , string errorMessage
          , bool adConsumed) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                if (!TryTakeShowCompletion(
                        generation
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

        private void CompleteShowOnUnityThread(int generation, string errorMessage) {
            if (TryTakeShowCompletion(
                    generation
                  , false
                  , out var completed)) {
                InvokeCompletionSafely(
                    completed
                  , errorMessage
                  , false);
            }
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
                AndroidClearCompletedListener();
            }

            return true;
        }

        private void RaiseDisplayed() => InvokeSafely(OnDisplayed);

        private void RaisePresentationFailed(int errorCode, string errorMessage) {
            var handler = OnPresentationFailed;
            if (handler == null) return;
            InvokeSafely(() => handler(errorCode, errorMessage));
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
