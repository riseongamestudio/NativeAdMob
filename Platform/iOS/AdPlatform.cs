using UnityEngine;

namespace RiseOn.NativeAdMob.iOS {
    internal sealed class AdPlatform : IAdPlatform {
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
    }
}
