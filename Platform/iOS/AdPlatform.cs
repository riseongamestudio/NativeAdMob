using UnityEngine;

namespace RiseOn.NativeAdMob.iOS {
    internal sealed class AdPlatform : IAdPlatform, INativeOverlayPlatform {
        public IInFeedAdClient CreateInFeed(
            InFeedAd.Settings settings
          , IInFeedAdCallbacks callbacks) {
            return new InFeedAdClient(settings, callbacks);
        }

        public IOverlayAdClient CreateOverlay(
            OverlayAdSettings settings
          , IOverlayAdCallbacks callbacks) {
            return new OverlayAdClient(settings, callbacks);
        }

        // The covers have no per-instance native handle - one cover of
        // each size serves the whole app - so the platform answers for them
        // directly instead of minting a client.
        public void ShowFullScreen(int argb) => cover.ShowFullScreen(argb);

        public void HideFullScreen() => cover.HideFullScreen();

        public void SetFullScreenColor(int argb) => cover.SetFullScreenColor(argb);

        public void ShowHalfScreen(int argb, float heightRatio)
            => cover.ShowHalfScreen(argb, heightRatio);

        public void HideHalfScreen() => cover.HideHalfScreen();

        public void SetHalfScreenColor(int argb) => cover.SetHalfScreenColor(argb);

        private readonly NativeOverlayPlatform cover = new();
    }
}
