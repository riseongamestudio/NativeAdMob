using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    internal sealed class NativeAdMobPlatform : INativeAdMobPlatform {
        public INativeInFeedAdMobClient CreateInFeed(
            NativeInFeedAdMob.Settings settings
          , INativeInFeedAdMobCallbacks callbacks) {
            return new NativeInFeedAdMobClient(settings, callbacks);
        }

        public INativeOverlayAdMobClient CreateOverlay(
            NativeOverlayAdMobSettings settings
          , INativeOverlayAdMobCallbacks callbacks) {
            return new NativeOverlayAdMobClient(settings, callbacks);
        }
    }
}
