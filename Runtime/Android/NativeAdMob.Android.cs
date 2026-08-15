#if UNITY_ANDROID
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public abstract partial class NativeAdMob {
        private protected AndroidJavaObject androidNativeAd;
        private AndroidJavaProxy androidListener;

        partial void AndroidCall(string methodName, object[] parameters) {
            lock (nativeAdStateLock) {
                if (!releasedManaged) {
                    androidNativeAd?.Call(methodName, parameters);
                }
            }
        }

        partial void AndroidInvalidateLoadListener() {
            androidListener = null;
        }

        // Each format attaches the one listener of its instance's life right
        // after creating its Java object; the generation still guards late
        // callbacks after Release.
        private protected int AndroidNextListenerGeneration() {
            return ++loadListenerGeneration;
        }

        private protected void AndroidAttachListener(AndroidJavaProxy listener) {
            androidListener = listener;
            androidNativeAd.Call("SetListener", new object[] { listener });
        }

        private protected void DispatchListenerCallback(
            AndroidJavaProxy source
          , int generation
          , Action callback) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                lock (nativeAdStateLock) {
                    if (releasedManaged
                     || generation != loadListenerGeneration
                     || !ReferenceEquals(androidListener, source))
                        return;
                }

                InvokeSafely(callback);
            });
        }
    }
}
#endif
