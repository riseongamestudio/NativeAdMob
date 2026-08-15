#if UNITY_ANDROID
using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob {
    public sealed partial class FullScreen {
        private const string JAVA_LOAD_AD_METHOD = "LoadAd";
        private const string JAVA_SHOW_AD_METHOD = "ShowAd";
        private const string JAVA_RELEASE_METHOD = "Release";

        private AdCompletedListenerProxy androidCompletedListener;

        partial void AndroidLoadAd() {
            Debug.Log("LoadAd()");

            var unityActivity = AndroidApplication.currentActivity;

            var parameters = new object[] { unityActivity };
            lock (nativeAdStateLock) {
                if (!releasedManaged) {
                    androidNativeAd?.Call(JAVA_LOAD_AD_METHOD, parameters);
                }
            }
        }

        partial void AndroidShowAd(ShowCompletedHandler onAdCompleted) {
            AdCompletedListenerProxy completedListener;
            int                      generation;
            string                   rejectedError = null;
            lock (nativeAdStateLock) {
                if (releasedManaged || androidNativeAd == null) {
                    completedListener = null;
                    generation        = INVALID_GENERATION;
                    rejectedError     = AD_RELEASED_ERROR;
                } else if (showPendingOrActive) {
                    completedListener = null;
                    generation        = INVALID_GENERATION;
                    rejectedError     = AD_ALREADY_SHOWING_ERROR;
                } else {
                    showPendingOrActive      = true;
                    currentShowCompleted     = onAdCompleted;
                    generation               = ++showGeneration;
                    completedListener        = new(
                        (error, adConsumed) =>
                            CompleteShowFromNativeThread(
                                generation
                              , error
                              , adConsumed));
                    androidCompletedListener = completedListener;
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

            try {
                var unityActivity = AndroidApplication.currentActivity;

                var parameters = new object[] { unityActivity, completedListener };
                lock (nativeAdStateLock) {
                    if (!releasedManaged
                     && showPendingOrActive
                     && generation == showGeneration) {
                        androidNativeAd?.Call(JAVA_SHOW_AD_METHOD, parameters);
                    }
                }
            } catch (Exception exception) {
                Debug.LogException(exception);
                CompleteShowOnUnityThread(generation, exception.Message);
            }
        }

        partial void AndroidIsAdReady(ref bool isReady) {
            lock (nativeAdStateLock) {
                isReady =
                    !releasedManaged
                 && !showPendingOrActive
                 && androidNativeAd != null
                 && cachedAdReady;
            }
        }

        partial void AndroidIsAdLoading(ref bool isLoading) {
            lock (nativeAdStateLock) {
                isLoading =
                    !releasedManaged
                 && androidNativeAd != null
                 && cachedAdLoading;
            }
        }

        partial void AndroidClearCompletedListener() {
            androidCompletedListener = null;
        }

        partial void AndroidRelease() {
            AndroidJavaObject    javaObject;
            ShowCompletedHandler completed;
            Exception            releaseException = null;
            lock (nativeAdStateLock) {
                if (releasedManaged && androidNativeAd == null) return;

                releasedManaged = true;
                javaObject      = androidNativeAd;
                androidNativeAd = null;

                completed            = showPendingOrActive ? currentShowCompleted : null;
                showPendingOrActive  = false;
                currentShowCompleted = null;
                cachedAdReady        = false;
                cachedAdLoading      = false;
                ++showGeneration;

                InvalidateLoadListener();
                androidCompletedListener = null;
                try {
                    javaObject?.Call(JAVA_RELEASE_METHOD);
                } catch (Exception exception) {
                    releaseException = exception;
                } finally {
                    javaObject?.Dispose();
                }
            }

            if (releaseException != null) Debug.LogException(releaseException);

            InvokeCompletionSafely(
                completed
              , AD_RELEASED_ERROR
              , false);
        }
    }
}
#endif
