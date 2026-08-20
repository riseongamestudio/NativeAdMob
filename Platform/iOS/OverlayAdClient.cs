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
              , settings.HeightRatio
              , settings.BackgroundColor
              , settings.CacheSize
              , settings.Close.Cooldown
              , (int)settings.Close.CloseSide
              , (int)settings.Close.TimerSide
              , settings.Close.RedirectOnClose);
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

        public void SetClose(in CloseSettings controls) {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_SetClose(
                handle
              , controls.Cooldown
              , (int)controls.CloseSide
              , (int)controls.TimerSide
              , controls.RedirectOnClose);
        }

        public void Load() {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_Load(handle);
        }

        public void Show(int showId) {
            if (handle == IntPtr.Zero) {
                // Released: the cache went with it.
                callbacks.OnShowCompleted(showId, "Ad released", false, 0);
                return;
            }

            NativeAdBridge.ROOverlayAd_Show(
                handle
              , showId
              , NativeAdBridge.OnShowCompletedCallback);
        }

        public void Hide() {
            if (handle == IntPtr.Zero) return;

            NativeAdBridge.ROOverlayAd_Hide(handle);
        }

        public void Release() {
            var releasedHandle = handle;
            handle = IntPtr.Zero;
            if (releasedHandle != IntPtr.Zero) {
                NativeAdBridge.ROOverlayAd_Release(releasedHandle);
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

        internal void HandleStateChanged(bool isReady, bool isLoading)
            => callbacks.OnStateChanged(isReady, isLoading);

        internal void HandleShowNotReady() => callbacks.OnShowNotReady();

        internal void HandleDisplayed() => callbacks.OnDisplayed();

        internal void HandlePresentationFailed(int errorCode, string errorMessage)
            => callbacks.OnDisplayFailed(errorCode, errorMessage);

        internal void HandleShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed
          , int cachedCount)
            => callbacks.OnShowCompleted(
                showId, errorMessage, adConsumed, cachedCount);
    }
}
