#if UNITY_ANDROID
using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob {
    public sealed partial class InFeed {
        private const string JAVA_CONFIGURE_METHOD    = "Configure";
        private const string JAVA_SHOW_METHOD         = "Show";
        private const string JAVA_HIDE_METHOD         = "Hide";
        private const string JAVA_SET_POSITION_METHOD = "SetPosition";
        private const string JAVA_RELEASE_METHOD      = "Release";

        private AndroidJavaObject androidReleasePending;

        partial void AndroidConfigure(
            Vector2Int positionPx
          , Vector2Int sizePx
          , float backgroundAlpha) {
            androidNativeAd?.Call(
                JAVA_CONFIGURE_METHOD
              , AndroidApplication.currentActivity
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y
              , backgroundAlpha);
        }

        partial void AndroidShow() {
            androidNativeAd?.Call(
                JAVA_SHOW_METHOD
              , AndroidApplication.currentActivity);
        }

        partial void AndroidHide() {
            androidNativeAd?.Call(JAVA_HIDE_METHOD);
        }

        partial void AndroidSetPosition(Vector2Int positionPx) {
            androidNativeAd?.Call(
                JAVA_SET_POSITION_METHOD
              , positionPx.x
              , positionPx.y);
        }

        partial void AndroidTakeReleased() {
            androidReleasePending = androidNativeAd;
            androidNativeAd       = null;
        }

        partial void AndroidFinishRelease() {
            if (!supportsAndroid) return;

            var javaObject = androidReleasePending;
            androidReleasePending = null;
            try {
                javaObject?.Call(JAVA_RELEASE_METHOD);
            } catch (Exception exception) {
                Debug.LogException(exception);
            } finally {
                javaObject?.Dispose();
            }
        }
    }
}
#endif
