using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class OverlayAdClient : IOverlayAdClient {
        private const string JAVA_CLASS_NAME               = "com.riseon.nativeadmob.OverlayAd";
        private const string JAVA_SET_LISTENER_METHOD      = "SetListener";
        private const string JAVA_CONFIGURE_METHOD         = "Configure";
        private const string JAVA_SET_COUNTDOWN_SEC_METHOD = "SetCountdownSec";
        private const string JAVA_LOAD_AD_METHOD           = "Load";
        private const string JAVA_SHOW_AD_METHOD           = "Show";
        private const string JAVA_HIDE_AD_METHOD           = "Hide";
        private const string JAVA_RELEASE_METHOD           = "Release";

        private readonly IOverlayAdCallbacks callbacks;
        private AndroidJavaObject javaObject;
        // Anchor the proxies while the Java side holds them.
        private NativeAdLoadListenerProxy listener;
        private NativeAdCompletedListenerProxy activeShowCompleted;

        internal OverlayAdClient(
            OverlayAdSettings settings
          , IOverlayAdCallbacks callbacks) {
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
            listener = new NativeAdLoadListenerProxy(callbacks);
            javaObject.Call(
                JAVA_SET_LISTENER_METHOD
              , new object[] { listener });
        }

        public void SetCountdownSec(int countdownSec) {
            javaObject?.Call(JAVA_SET_COUNTDOWN_SEC_METHOD, countdownSec);
        }

        public void Load() {
            javaObject?.Call(
                JAVA_LOAD_AD_METHOD
              , AndroidApplication.currentActivity);
        }

        public void Show(int showId) {
            var completedListener = new NativeAdCompletedListenerProxy(
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

        public void Hide() {
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
