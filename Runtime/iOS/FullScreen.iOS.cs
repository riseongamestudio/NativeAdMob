#if UNITY_IOS
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public sealed partial class FullScreen {
        private IntPtr iosReleasePendingHandle;
        private int iosReleasePendingInstanceId;

        partial void IOSCreate() {
            iosInstanceId = IOSBridge.Register(this);
            iosNativeAd = IOSBridge.RONativeAdMobFullScreen_Create(
                adUnitId
              , iosInstanceId);
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobFullScreen_SetListener(
                iosNativeAd
              , IOSBridge.OnLoadingStartedCallback
              , IOSBridge.OnLoadingCompletedCallback
              , IOSBridge.OnAdPaidCallback
              , IOSBridge.OnDisplayedCallback
              , IOSBridge.OnPresentationFailedCallback
              , IOSBridge.OnStateChangedCallback
              , IOSBridge.OnShowNotReadyCallback);
        }

        internal void IOSHandleDisplayed() => DispatchFromNative(RaiseDisplayed);

        internal void IOSHandlePresentationFailed(
            int errorCode
          , string errorMessage)
            => DispatchFromNative(
                () => RaisePresentationFailed(errorCode, errorMessage));

        internal void IOSHandleShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed) {
            CompleteShowFromNativeThread(showId, errorMessage, adConsumed);
        }

        partial void IOSConfigure(
            bool fullscreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha) {
            lock (nativeAdStateLock) {
                if (!releasedManaged && iosNativeAd != IntPtr.Zero) {
                    IOSBridge.RONativeAdMobFullScreen_Configure(
                        iosNativeAd
                      , fullscreen
                      , countdownSec
                      , xRandomSide
                      , numberOppositeSide
                      , heightRatio
                      , backgroundAlpha);
                }
            }
        }

        partial void IOSSetCountdownSec(int countdownSec) {
            lock (nativeAdStateLock) {
                if (!releasedManaged && iosNativeAd != IntPtr.Zero) {
                    IOSBridge.RONativeAdMobFullScreen_SetCountdownSec(
                        iosNativeAd
                      , countdownSec);
                }
            }
        }

        partial void IOSLoadAd() {
            Debug.Log("LoadAd()");
            lock (nativeAdStateLock) {
                if (!releasedManaged && iosNativeAd != IntPtr.Zero) {
                    IOSBridge.RONativeAdMobFullScreen_LoadAd(iosNativeAd);
                }
            }
        }

        partial void IOSShowAd(ShowCompletedHandler onAdCompleted) {
            int    generation;
            string rejectedError = null;
            lock (nativeAdStateLock) {
                if (releasedManaged || iosNativeAd == IntPtr.Zero) {
                    generation    = INVALID_GENERATION;
                    rejectedError = AD_RELEASED_ERROR;
                } else if (showPendingOrActive) {
                    generation    = INVALID_GENERATION;
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
                 && iosNativeAd != IntPtr.Zero) {
                    // showId đi cùng lời gọi và được native vọng lại, nên
                    // completion chỉ khớp đúng lượt show đã đăng ký nó.
                    IOSBridge.RONativeAdMobFullScreen_ShowAd(
                        iosNativeAd
                      , generation
                      , IOSBridge.OnShowCompletedCallback);
                }
            }
        }

        partial void IOSHideAd() {
            lock (nativeAdStateLock) {
                if (!releasedManaged && iosNativeAd != IntPtr.Zero) {
                    IOSBridge.RONativeAdMobFullScreen_HideAd(iosNativeAd);
                }
            }
        }

        partial void IOSIsAdReady(ref bool isReady) {
            lock (nativeAdStateLock) {
                isReady =
                    !releasedManaged
                 && !showPendingOrActive
                 && iosNativeAd != IntPtr.Zero
                 && cachedAdReady;
            }
        }

        partial void IOSIsAdLoading(ref bool isLoading) {
            lock (nativeAdStateLock) {
                isLoading =
                    !releasedManaged
                 && iosNativeAd != IntPtr.Zero
                 && cachedAdLoading;
            }
        }

        partial void IOSRelease() {
            IntPtr               iosHandle;
            int                  releasedIOSInstanceId;
            ShowCompletedHandler completed;
            lock (nativeAdStateLock) {
                if (releasedManaged && iosNativeAd == IntPtr.Zero) return;

                releasedManaged       = true;
                iosHandle             = iosNativeAd;
                releasedIOSInstanceId = iosInstanceId;
                iosNativeAd           = IntPtr.Zero;

                completed            = showPendingOrActive ? currentShowCompleted : null;
                showPendingOrActive  = false;
                currentShowCompleted = null;
                cachedAdReady        = false;
                cachedAdLoading      = false;
                ++showGeneration;

                InvalidateLoadListener();
            }

            if (iosHandle != IntPtr.Zero) {
                IOSBridge.RONativeAdMobFullScreen_Release(iosHandle);
                IOSBridge.Unregister(releasedIOSInstanceId);
            }
            InvokeCompletionSafely(
                completed
              , AD_RELEASED_ERROR
              , false);
        }
    }
}
#endif
