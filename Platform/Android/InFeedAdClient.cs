using System;
using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class InFeedAdClient : IInFeedAdClient {
        private const string JAVA_CLASS_NAME          = "com.riseon.nativeadmob.InFeedAd";
        private const string JAVA_SET_LISTENER_METHOD = "SetListener";
        private const string JAVA_CONFIGURE_METHOD    = "Configure";
        private const string JAVA_SHOW_METHOD         = "Show";
        private const string JAVA_HIDE_METHOD         = "Hide";
        private const string JAVA_SET_POSITION_METHOD = "SetPosition";
        private const string JAVA_RELEASE_METHOD      = "Release";

        private AndroidJavaObject javaObject;
        // Anchors the proxy while the Java side holds it.
        private InFeedAdListenerProxy listener;

        internal InFeedAdClient(
            InFeedAd.Settings settings
          , IInFeedAdCallbacks callbacks) {
            javaObject = new AndroidJavaObject(
                JAVA_CLASS_NAME
              , AndroidApplication.currentActivity
              , settings.AdUnitId
              , settings.SlotCount
              , settings.CacheSize
              , settings.BackgroundAlpha);
            listener = new InFeedAdListenerProxy(callbacks);
            javaObject.Call(
                JAVA_SET_LISTENER_METHOD
              , new object[] { listener });
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            javaObject?.Call(
                JAVA_CONFIGURE_METHOD
              , AndroidApplication.currentActivity
              , slotIndex
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y);
        }

        public void ShowSlot(int slotIndex) {
            javaObject?.Call(
                JAVA_SHOW_METHOD
              , AndroidApplication.currentActivity
              , slotIndex);
        }

        public void HideSlot(int slotIndex) {
            javaObject?.Call(JAVA_HIDE_METHOD, slotIndex);
        }

        public void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            javaObject?.Call(
                JAVA_SET_POSITION_METHOD
              , slotIndex
              , positionPx.x
              , positionPx.y);
        }

        public void Release() {
            var releasedObject = javaObject;
            javaObject = null;
            listener   = null;
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
