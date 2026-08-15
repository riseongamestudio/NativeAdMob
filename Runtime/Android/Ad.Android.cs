#if UNITY_ANDROID
using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob {
    public abstract partial class Ad {
        private const string JAVA_SET_LISTENER_METHOD = "SetListener";

        protected AndroidJavaObject androidNativeAd;
        private AdLoadListenerProxy androidLoadListener;

        partial void AndroidCreate(
            string javaClassName
          , string adUnitId
          , bool passCurrentActivity) {
            androidNativeAd = passCurrentActivity
                ? new AndroidJavaObject(
                    javaClassName
                  , AndroidApplication.currentActivity
                  , adUnitId)
                : new AndroidJavaObject(
                    javaClassName
                  , adUnitId);
        }

        partial void AndroidCall(string methodName, object[] parameters) {
            lock (nativeAdStateLock) {
                if (!releasedManaged) {
                    androidNativeAd?.Call(methodName, parameters);
                }
            }
        }

        partial void AndroidSetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed) {
            lock (nativeAdStateLock) {
                if (releasedManaged || androidNativeAd == null) return;

                var generation = ++loadListenerGeneration;
                androidLoadListener = new(
                    generation
                  , onLoadingCompleted
                  , onLoadingStarted
                  , onAdPaid
                  , onDisplayed
                  , onPresentationFailed
                  , HandleNativeStateChanged
                  , HandleShowNotReady
                  , DispatchLoadListenerCallback);

                var parameters = new object[] { androidLoadListener };
                androidNativeAd.Call(JAVA_SET_LISTENER_METHOD, parameters);
            }
        }

        partial void AndroidInvalidateLoadListener() {
            androidLoadListener = null;
        }

        private void DispatchLoadListenerCallback(
            AdLoadListenerProxy source
          , int generation
          , Action callback) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                lock (nativeAdStateLock) {
                    if (releasedManaged
                     || generation != loadListenerGeneration
                     || !ReferenceEquals(androidLoadListener, source))
                        return;
                }

                InvokeSafely(callback);
            });
        }
    }
}
#endif
