using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    internal static class AdPlatformBootstrap {
        // This assembly only exists in the Editor, where the preview stands
        // in for both device platforms.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Install() {
            if (!Application.isEditor) return;

            AdPlatformRegistry.Install(new AdPlatform());
        }
    }
}
