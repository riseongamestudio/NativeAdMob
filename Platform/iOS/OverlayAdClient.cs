using System;

namespace RiseOn.NativeAdMob.iOS {
    internal sealed class OverlayAdClient : IOverlayAdClient, INativeAdSharedHandlers {
        private readonly IOverlayAdCallbacks callbacks;
        private IntPtr handle;
        private int instanceId;

        internal OverlayAdClient(
            OverlayAdSettings settings
          , IOverlayAdCallbacks callbacks) {
            this.callbacks = callbacks;
            instanceId = NativeAdBridge.Register(this);
            handle = NativeAdBridge.ROOverlayAd_Create(
                settings.AdUnitId
              , instanceId);
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_Configure(
                handle
              , settings.CoversFullScreen
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha);
            NativeAdBridge.ROOverlayAd_SetListener(
                handle
              , NativeAdBridge.OnLoadingStartedCallback
              , NativeAdBridge.OnLoadingCompletedCallback
              , NativeAdBridge.OnAdPaidCallback
              , NativeAdBridge.OnDisplayedCallback
              , NativeAdBridge.OnPresentationFailedCallback
              , NativeAdBridge.OnStateChangedCallback
              , NativeAdBridge.OnShowNotReadyCallback);
        }

        public void SetCountdownSec(int countdownSec) {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_SetCountdownSec(
                handle
              , countdownSec);
        }

        public void LoadAd() {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_LoadAd(handle);
        }

        public void ShowAd(int showId) {
            if (handle == IntPtr.Zero) {
                callbacks.OnShowCompleted(showId, "Ad released", false);
                return;
            }

            NativeAdBridge.ROOverlayAd_ShowAd(
                handle
              , showId
              , NativeAdBridge.OnShowCompletedCallback);
        }

        public void HideAd() {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_HideAd(handle);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                NativeAdBridge.ROOverlayAd_Release(releasedHandle);
            }
            NativeAdBridge.Unregister(instanceId);
        }

        void INativeAdSharedHandlers.HandleLoadingStarted()
            => callbacks.OnLoadingStarted();

        void INativeAdSharedHandlers.HandleLoadingCompleted(
            int errorCode, string errorMessage)
            => callbacks.OnLoadingCompleted(errorCode, errorMessage);

        void INativeAdSharedHandlers.HandleAdPaid(
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
