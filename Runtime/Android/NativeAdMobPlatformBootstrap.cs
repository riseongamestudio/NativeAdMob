using UnityEngine;

namespace RiseOn.NativeAdMob.Android {
    internal static class NativeAdMobPlatformBootstrap {
        // This assembly only compiles into Android builds, so it is the one
        // platform present; install before any scene code constructs an ad.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Install() {
            if (Application.platform != RuntimePlatform.Android) return;

            NativeAdMobPlatformRegistry.Install(new NativeAdMobPlatform());
        }
    }
}
