using System;

namespace RiseOn.NativeAdMob {
    internal sealed class IOSOverlayClient : IOverlayClient, IIOSSharedHandlers {
        private readonly IOverlayCallbacks callbacks;
        private IntPtr handle;
        private int instanceId;

        internal IOSOverlayClient(
            OverlaySettings settings
          , IOverlayCallbacks callbacks) {
            this.callbacks = callbacks;
            instanceId = IOSBridge.Register(this);
            handle = IOSBridge.RONativeAdMobOverlay_Create(
                settings.AdUnitId
              , instanceId);
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobOverlay_Configure(
                handle
              , settings.CoversFullScreen
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha);
            IOSBridge.RONativeAdMobOverlay_SetListener(
                handle
              , IOSBridge.OnLoadingStartedCallback
              , IOSBridge.OnLoadingCompletedCallback
              , IOSBridge.OnAdPaidCallback
              , IOSBridge.OnDisplayedCallback
              , IOSBridge.OnPresentationFailedCallback
              , IOSBridge.OnStateChangedCallback
              , IOSBridge.OnShowNotReadyCallback);
        }

        public void SetCountdownSec(int countdownSec) {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobOverlay_SetCountdownSec(
                handle
              , countdownSec);
        }

        public void LoadAd() {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobOverlay_LoadAd(handle);
        }

        public void ShowAd(int showId) {
            if (handle == IntPtr.Zero) {
                callbacks.OnShowCompleted(showId, "Ad released", false);
                return;
            }

            IOSBridge.RONativeAdMobOverlay_ShowAd(
                handle
              , showId
              , IOSBridge.OnShowCompletedCallback);
        }

        public void HideAd() {
            if (handle == IntPtr.Zero) return;

            IOSBridge.RONativeAdMobOverlay_HideAd(handle);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                IOSBridge.RONativeAdMobOverlay_Release(releasedHandle);
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
