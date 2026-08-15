using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class EditorPlatform : INativeAdMobPlatform {
        public IInFeedClient CreateInFeed(
            NativeInFeedAdMob.Settings settings
          , IInFeedCallbacks callbacks) {
            return new EditorInFeedClient(settings, callbacks);
        }

        public IOverlayClient CreateOverlay(
            OverlaySettings settings
          , IOverlayCallbacks callbacks) {
            return new EditorOverlayClient(settings, callbacks);
        }
    }

    internal static class EditorPlatformBootstrap {
        // This assembly only exists in the Editor, where the preview stands
        // in for both device platforms.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Install() {
            if (!Application.isEditor) return;

            NativeAdMobPlatform.Install(new EditorPlatform());
        }
    }
}
