using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class OverlayAdClient : IOverlayAdClient {
        private const string JAVA_CLASS_NAME               = "com.riseon.nativeadmob.OverlayAd";
        private const string JAVA_SET_LISTENER_METHOD      = "SetListener";
        private const string JAVA_CONFIGURE_METHOD         = "Configure";
        private const string JAVA_SET_CLOSE_METHOD         = "SetClose";
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
              , settings.FullScreen
              , settings.HeightRatio
              , settings.BackgroundColor
              , settings.CacheSize
              , settings.Close.Cooldown
              , (int)settings.Close.CloseSide
              , (int)settings.Close.TimerSide
              , settings.Close.RedirectOnClose);
            listener = new NativeAdLoadListenerProxy(callbacks);
            javaObject.Call(
                JAVA_SET_LISTENER_METHOD
              , new object[] { listener });
        }

        public void SetClose(in CloseSettings controls) {
            javaObject?.Call(
                JAVA_SET_CLOSE_METHOD
              , controls.Cooldown
              , (int)controls.CloseSide
              , (int)controls.TimerSide
              , controls.RedirectOnClose);
        }

        public void Load() {
            javaObject?.Call(
                JAVA_LOAD_AD_METHOD
              , AndroidApplication.currentActivity);
        }

        public void Show(int showId) {
            var completedListener = new NativeAdCompletedListenerProxy(
                (errorMessage, adConsumed, cachedCount) =>
                    callbacks.OnShowCompleted(
                        showId
                      , errorMessage
                      , adConsumed
                      , cachedCount));
            activeShowCompleted = completedListener;
            try {
                javaObject?.Call(
                    JAVA_SHOW_AD_METHOD
                  , AndroidApplication.currentActivity
                  , completedListener);
            } catch (Exception exception) {
                Debug.LogException(exception);
                // The call never reached Java, so there is no cache news to
                // report. An empty cache is the safe thing to claim: it can
                // only skip an ad, and the next state notification corrects
                // it - claiming a warm one would offer an ad nobody has.
                callbacks.OnShowCompleted(showId, exception.Message, false, 0);
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
