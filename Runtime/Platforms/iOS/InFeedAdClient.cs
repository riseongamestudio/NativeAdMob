using System;
using UnityEngine;

namespace RiseOn.NativeAdMob.iOS {
    internal sealed class InFeedAdClient : IInFeedAdClient, INativeAdSharedHandlers {
        private readonly IInFeedAdCallbacks callbacks;
        private IntPtr handle;
        private int instanceId;

        internal InFeedAdClient(
            InFeedAd.Settings settings
          , IInFeedAdCallbacks callbacks) {
            this.callbacks = callbacks;
            instanceId = NativeAdBridge.Register(this);
            handle = NativeAdBridge.ROInFeedAd_Create(
                settings.AdUnitId
              , settings.SlotCount
              , settings.CacheSize
              , AdColor.Pack(settings.BackgroundColor)
              , instanceId);
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROInFeedAd_SetListener(
                handle
              , NativeAdBridge.OnLoadingStartedCallback
              , NativeAdBridge.OnLoadingCompletedCallback
              , NativeAdBridge.OnAdPaidCallback
              , NativeAdBridge.OnSlotDisplayedCallback
              , NativeAdBridge.OnSlotShowNotReadyCallback
              , NativeAdBridge.OnSlotPresentationFailedCallback);
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx
          , int roundCornerPx) {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROInFeedAd_Configure(
                handle
              , slotIndex
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y
              , roundCornerPx);
        }

        public void ShowSlot(int slotIndex) {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROInFeedAd_Show(handle, slotIndex);
        }

        public void HideSlot(int slotIndex) {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROInFeedAd_Hide(handle, slotIndex);
        }

        public void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROInFeedAd_SetPosition(
                handle
              , slotIndex
              , positionPx.x
              , positionPx.y);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                NativeAdBridge.ROInFeedAd_Release(releasedHandle);
            }
            NativeAdBridge.Unregister(instanceId);
        }

        // Nothing downstream listens for a load beginning, and neither
        // AdMob nor MAX reports one. The trampoline still arrives.
        void INativeAdSharedHandlers.HandleLoadingStarted() {}

        void INativeAdSharedHandlers.HandleLoadingCompleted(
            int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize)
            => callbacks.OnLoadingCompleted(
                errorCode
              , errorMessage
              , cachedCount
              , cacheSize);

        void INativeAdSharedHandlers.HandleAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            // Precision is reported by the SDK but nothing downstream asks
            // for it, so it stops here rather than riding along unused.
            => callbacks.OnAdPaid(source, adUnitId, value, currencyCode);

        internal void HandleSlotDisplayed(int slotIndex)
            => callbacks.OnSlotDisplayed(slotIndex);

        internal void HandleSlotShowNotReady(int slotIndex)
            => callbacks.OnSlotShowNotReady(slotIndex);

        internal void HandleSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => callbacks.OnSlotDisplayFailed(
                slotIndex
              , errorCode
              , errorMessage);
    }
}
