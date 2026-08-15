using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class IOSInFeedClient : IInFeedClient, IIOSSharedHandlers {
        private readonly IInFeedCallbacks callbacks;
        private IntPtr handle;
        private int instanceId;

        internal IOSInFeedClient(
            NativeInFeedAdMob.Settings settings
          , IInFeedCallbacks callbacks) {
            this.callbacks = callbacks;
            instanceId = IOSBridge.Register(this);
            handle = IOSBridge.RONativeAdMobInFeed_Create(
                settings.AdUnitId
              , settings.SlotCount
              , settings.CacheSize
              , settings.BackgroundAlpha
              , instanceId);
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_SetListener(
                handle
              , IOSBridge.OnLoadingStartedCallback
              , IOSBridge.OnLoadingCompletedCallback
              , IOSBridge.OnAdPaidCallback
              , IOSBridge.OnSlotDisplayedCallback
              , IOSBridge.OnSlotShowNotReadyCallback
              , IOSBridge.OnSlotPresentationFailedCallback);
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Configure(
                handle
              , slotIndex
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y);
        }

        public void ShowSlot(int slotIndex) {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Show(handle, slotIndex);
        }

        public void HideSlot(int slotIndex) {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Hide(handle, slotIndex);
        }

        public void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_SetPosition(
                handle
              , slotIndex
              , positionPx.x
              , positionPx.y);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                IOSBridge.RONativeAdMobInFeed_Release(releasedHandle);
            }
            IOSBridge.Unregister(instanceId);
        }

        void IIOSSharedHandlers.HandleLoadingStarted()
            => callbacks.OnLoadingStarted();

        void IIOSSharedHandlers.HandleLoadingCompleted(
            int errorCode, string errorMessage)
            => callbacks.OnLoadingCompleted(errorCode, errorMessage);

        void IIOSSharedHandlers.HandleAdPaid(
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
