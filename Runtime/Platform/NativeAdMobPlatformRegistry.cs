namespace RiseOn.NativeAdMob {
    internal static class NativeAdMobPlatformRegistry {
        internal static INativeAdMobPlatform Installed { get; private set; }

        // Called by the platform assembly's RuntimeInitializeOnLoadMethod
        // bootstrap, before any scene code can construct an ad.
        internal static void Install(INativeAdMobPlatform platform) {
            Installed = platform;
        }
    }
}
