#if UNITY_IOS
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public sealed partial class NativeInFeedAdMob {
        private IntPtr iosReleasePendingHandle;
        private int iosReleasePendingInstanceId;

        partial void IOSCreate(Settings settings) {
            iosInstanceId = IOSBridge.Register(this);
            iosNativeAd = IOSBridge.RONativeAdMobInFeed_Create(
                adUnitId
              , settings.SlotCount
              , settings.CacheSize
              , settings.BackgroundAlpha
              , iosInstanceId);
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_SetListener(
                iosNativeAd
              , IOSBridge.OnLoadingStartedCallback
              , IOSBridge.OnLoadingCompletedCallback
              , IOSBridge.OnAdPaidCallback
              , IOSBridge.OnSlotDisplayedCallback
              , IOSBridge.OnSlotShowNotReadyCallback
              , IOSBridge.OnSlotPresentationFailedCallback);
        }

        internal void IOSHandleSlotDisplayed(int slotIndex)
            => DispatchFromNative(() => HandleSlotDisplayed(slotIndex));

        internal void IOSHandleSlotShowNotReady(int slotIndex)
            => DispatchFromNative(() => HandleSlotShowNotReady(slotIndex));

        internal void IOSHandleSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => DispatchFromNative(
                () => HandleSlotPresentationFailed(
                    slotIndex
                  , errorCode
                  , errorMessage));

        partial void IOSConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Configure(
                iosNativeAd
              , slotIndex
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y);
        }

        partial void IOSShowSlot(int slotIndex) {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Show(iosNativeAd, slotIndex);
        }

        partial void IOSHideSlot(int slotIndex) {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Hide(iosNativeAd, slotIndex);
        }

        partial void IOSSetSlotPosition(int slotIndex, Vector2Int positionPx) {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_SetPosition(
                iosNativeAd
              , slotIndex
              , positionPx.x
              , positionPx.y);
        }

        partial void IOSTakeReleased() {
            iosReleasePendingHandle     = iosNativeAd;
            iosReleasePendingInstanceId = iosInstanceId;
            iosNativeAd                 = IntPtr.Zero;
        }

        partial void IOSFinishRelease() {
            if (!supportsIOS || iosReleasePendingHandle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Release(iosReleasePendingHandle);
            IOSBridge.Unregister(iosReleasePendingInstanceId);
            iosReleasePendingHandle = IntPtr.Zero;
        }
    }
}
#endif
