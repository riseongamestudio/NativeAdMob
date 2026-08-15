using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class IOSPlatform : INativeAdMobPlatform {
        public IInFeedClient CreateInFeed(
            NativeInFeedAdMob.Settings settings
          , IInFeedCallbacks callbacks) {
            return new IOSInFeedClient(settings, callbacks);
        }

        public IOverlayClient CreateOverlay(
            OverlaySettings settings
          , IOverlayCallbacks callbacks) {
            return new IOSOverlayClient(settings, callbacks);
        }
    }

    internal static class IOSPlatformBootstrap {
        // This assembly only compiles into iOS builds, so it is the one
        // platform present; install before any scene code constructs an ad.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Install() {
            if (Application.platform != RuntimePlatform.IPhonePlayer) return;

            NativeAdMobPlatform.Install(new IOSPlatform());
        }
    }
}
