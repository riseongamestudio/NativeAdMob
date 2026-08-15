using System;
using UnityEngine;

namespace RiseOn.NativeAdMob.iOS {
    internal sealed class NativeInFeedAdMobClient : INativeInFeedAdMobClient, INativeAdMobSharedHandlers {
        private readonly INativeInFeedAdMobCallbacks callbacks;
        private IntPtr handle;
        private int instanceId;

        internal NativeInFeedAdMobClient(
            NativeInFeedAdMob.Settings settings
          , INativeInFeedAdMobCallbacks callbacks) {
            this.callbacks = callbacks;
            instanceId = NativeAdMobBridge.Register(this);
            handle = NativeAdMobBridge.RONativeInFeedAdMob_Create(
                settings.AdUnitId
              , settings.SlotCount
              , settings.CacheSize
              , settings.BackgroundAlpha
              , instanceId);
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeInFeedAdMob_SetListener(
                handle
              , NativeAdMobBridge.OnLoadingStartedCallback
              , NativeAdMobBridge.OnLoadingCompletedCallback
              , NativeAdMobBridge.OnAdPaidCallback
              , NativeAdMobBridge.OnSlotDisplayedCallback
              , NativeAdMobBridge.OnSlotShowNotReadyCallback
              , NativeAdMobBridge.OnSlotPresentationFailedCallback);
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeInFeedAdMob_Configure(
                handle
              , slotIndex
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y);
        }

        public void ShowSlot(int slotIndex) {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeInFeedAdMob_Show(handle, slotIndex);
        }

        public void HideSlot(int slotIndex) {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeInFeedAdMob_Hide(handle, slotIndex);
        }

        public void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeInFeedAdMob_SetPosition(
                handle
              , slotIndex
              , positionPx.x
              , positionPx.y);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                NativeAdMobBridge.RONativeInFeedAdMob_Release(releasedHandle);
            }
            NativeAdMobBridge.Unregister(instanceId);
        }

        void INativeAdMobSharedHandlers.HandleLoadingStarted()
            => callbacks.OnLoadingStarted();

        void INativeAdMobSharedHandlers.HandleLoadingCompleted(
            int errorCode, string errorMessage)
            => callbacks.OnLoadingCompleted(errorCode, errorMessage);

        void INativeAdMobSharedHandlers.HandleAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            => callbacks.OnAdPaid(new AdValue(
                source
              , adUnitId
              , value
              , currencyCode
              , (AdValuePrecision)precision));

        internal void HandleSlotDisplayed(int slotIndex)
            => callbacks.OnSlotDisplayed(slotIndex);

        internal void HandleSlotShowNotReady(int slotIndex)
            => callbacks.OnSlotShowNotReady(slotIndex);

        internal void HandleSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => callbacks.OnSlotPresentationFailed(
                slotIndex
              , errorCode
              , errorMessage);
    }
}
