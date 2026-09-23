namespace RiseOn.NativeAdMob {
    internal static class AdPlatformRegistry {
        internal static IAdPlatform Installed { get; private set; }

        // Called by the platform assembly's RuntimeInitializeOnLoadMethod
        // bootstrap, before any scene code can construct an ad.
        internal static void Install(IAdPlatform platform) {
            Installed = platform;
        }
    }
}
