#if UNITY_IOS
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public sealed partial class InFeed {
        private IntPtr iosReleasePendingHandle;
        private int iosReleasePendingInstanceId;

        partial void IOSCreate(string adUnitId) {
            iosInstanceId = IOSBridge.Register(this);
            iosNativeAd = IOSBridge.RONativeAdMobInFeed_Create(
                adUnitId
              , iosInstanceId);
        }

        private protected override void IOSRegisterListenerNative() {
            IOSBridge.RONativeAdMobInFeed_SetListener(
                iosNativeAd
              , IOSBridge.OnLoadingStartedCallback
              , IOSBridge.OnLoadingCompletedCallback
              , IOSBridge.OnAdPaidCallback
              , IOSBridge.OnDisplayedCallback
              , IOSBridge.OnPresentationFailedCallback
              , IOSBridge.OnStateChangedCallback
              , IOSBridge.OnShowNotReadyCallback);
        }

        partial void IOSConfigure(
            Vector2Int positionPx
          , Vector2Int sizePx
          , float backgroundAlpha) {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Configure(
                iosNativeAd
              , positionPx.x
              , positionPx.y
              , sizePx.x
              , sizePx.y
              , backgroundAlpha);
        }

        partial void IOSShow() {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Show(iosNativeAd);
        }

        partial void IOSHide() {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_Hide(iosNativeAd);
        }

        partial void IOSSetPosition(Vector2Int positionPx) {
            if (iosNativeAd == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobInFeed_SetPosition(
                iosNativeAd
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
