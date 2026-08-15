using System;

namespace RiseOn.NativeAdMob.iOS {
    internal sealed class NativeOverlayAdMobClient : INativeOverlayAdMobClient, INativeAdMobSharedHandlers {
        private readonly INativeOverlayAdMobCallbacks callbacks;
        private IntPtr handle;
        private int instanceId;

        internal NativeOverlayAdMobClient(
            NativeOverlayAdMobSettings settings
          , INativeOverlayAdMobCallbacks callbacks) {
            this.callbacks = callbacks;
            instanceId = NativeAdMobBridge.Register(this);
            handle = NativeAdMobBridge.RONativeOverlayAdMob_Create(
                settings.AdUnitId
              , instanceId);
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeOverlayAdMob_Configure(
                handle
              , settings.CoversFullScreen
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha);
            NativeAdMobBridge.RONativeOverlayAdMob_SetListener(
                handle
              , NativeAdMobBridge.OnLoadingStartedCallback
              , NativeAdMobBridge.OnLoadingCompletedCallback
              , NativeAdMobBridge.OnAdPaidCallback
              , NativeAdMobBridge.OnDisplayedCallback
              , NativeAdMobBridge.OnPresentationFailedCallback
              , NativeAdMobBridge.OnStateChangedCallback
              , NativeAdMobBridge.OnShowNotReadyCallback);
        }

        public void SetCountdownSec(int countdownSec) {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeOverlayAdMob_SetCountdownSec(
                handle
              , countdownSec);
        }

        public void LoadAd() {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeOverlayAdMob_LoadAd(handle);
        }

        public void ShowAd(int showId) {
            if (handle == IntPtr.Zero) {
                callbacks.OnShowCompleted(showId, "Ad released", false);
                return;
            }

            NativeAdMobBridge.RONativeOverlayAdMob_ShowAd(
                handle
              , showId
              , NativeAdMobBridge.OnShowCompletedCallback);
        }

        public void HideAd() {
            if (handle == IntPtr.Zero) return;

            NativeAdMobBridge.RONativeOverlayAdMob_HideAd(handle);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                NativeAdMobBridge.RONativeOverlayAdMob_Release(releasedHandle);
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

        internal void HandleStateChanged(bool isReady, bool isLoading)
            => callbacks.OnStateChanged(isReady, isLoading);

        internal void HandleShowNotReady() => callbacks.OnShowNotReady();

        internal void HandleDisplayed() => callbacks.OnDisplayed();

        internal void HandlePresentationFailed(int errorCode, string errorMessage)
            => callbacks.OnPresentationFailed(errorCode, errorMessage);

        internal void HandleShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed)
            => callbacks.OnShowCompleted(showId, errorMessage, adConsumed);
    }
}
