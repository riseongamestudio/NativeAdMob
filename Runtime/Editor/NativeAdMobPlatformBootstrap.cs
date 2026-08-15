using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    internal static class NativeAdMobPlatformBootstrap {
        // This assembly only exists in the Editor, where the preview stands
        // in for both device platforms.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Install() {
            if (!Application.isEditor) return;

            NativeAdMobPlatformRegistry.Install(new NativeAdMobPlatform());
        }
    }
}
