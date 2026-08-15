using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class AndroidPlatform : INativeAdMobPlatform {
        public IInFeedClient CreateInFeed(
            NativeInFeedAdMob.Settings settings
          , IInFeedCallbacks callbacks) {
            return new AndroidInFeedClient(settings, callbacks);
        }

        public IOverlayClient CreateOverlay(
            OverlaySettings settings
          , IOverlayCallbacks callbacks) {
            return new AndroidOverlayClient(settings, callbacks);
        }
    }

    internal static class AndroidPlatformBootstrap {
        // This assembly only compiles into Android builds, so it is the one
        // platform present; install before any scene code constructs an ad.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Install() {
            if (Application.platform != RuntimePlatform.Android) return;

            NativeAdMobPlatform.Install(new AndroidPlatform());
        }
    }
}
