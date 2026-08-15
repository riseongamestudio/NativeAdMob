using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob {
    internal sealed class AndroidOverlayClient : IOverlayClient {
        private const string JAVA_CLASS_NAME               = "com.riseon.nativeadmob.Overlay";
        private const string JAVA_SET_LISTENER_METHOD      = "SetListener";
        private const string JAVA_CONFIGURE_METHOD         = "Configure";
        private const string JAVA_SET_COUNTDOWN_SEC_METHOD = "SetCountdownSec";
        private const string JAVA_LOAD_AD_METHOD           = "LoadAd";
        private const string JAVA_SHOW_AD_METHOD           = "ShowAd";
        private const string JAVA_HIDE_AD_METHOD           = "HideAd";
        private const string JAVA_RELEASE_METHOD           = "Release";

        private readonly IOverlayCallbacks callbacks;
        private AndroidJavaObject javaObject;
        // Anchor the proxies while the Java side holds them.
        private AdLoadListenerProxy listener;
        private AdCompletedListenerProxy activeShowCompleted;

        internal AndroidOverlayClient(
            OverlaySettings settings
          , IOverlayCallbacks callbacks) {
            this.callbacks = callbacks;
            javaObject = new AndroidJavaObject(
                JAVA_CLASS_NAME
              , settings.AdUnitId);
            javaObject.Call(
                JAVA_CONFIGURE_METHOD
              , settings.CoversFullScreen
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha);
            listener = new AdLoadListenerProxy(callbacks);
            javaObject.Call(
                JAVA_SET_LISTENER_METHOD
              , new object[] { listener });
        }

        public void SetCountdownSec(int countdownSec) {
            javaObject?.Call(JAVA_SET_COUNTDOWN_SEC_METHOD, countdownSec);
        }

        public void LoadAd() {
            javaObject?.Call(
                JAVA_LOAD_AD_METHOD
              , AndroidApplication.currentActivity);
        }

        public void ShowAd(int showId) {
            var completedListener = new AdCompletedListenerProxy(
                (errorMessage, adConsumed) =>
                    callbacks.OnShowCompleted(
                        showId
                      , errorMessage
                      , adConsumed));
            activeShowCompleted = completedListener;
            try {
                javaObject?.Call(
                    JAVA_SHOW_AD_METHOD
                  , AndroidApplication.currentActivity
                  , completedListener);
            } catch (Exception exception) {
                Debug.LogException(exception);
                callbacks.OnShowCompleted(showId, exception.Message, false);
            }
        }

        public void HideAd() {
            javaObject?.Call(JAVA_HIDE_AD_METHOD);
        }

        public void Release() {
            var releasedObject = javaObject;
            javaObject          = null;
            listener            = null;
            activeShowCompleted = null;
            try {
                releasedObject?.Call(JAVA_RELEASE_METHOD);
            } catch (Exception exception) {
                Debug.LogException(exception);
            } finally {
                releasedObject?.Dispose();
            }
        }
    }
}
