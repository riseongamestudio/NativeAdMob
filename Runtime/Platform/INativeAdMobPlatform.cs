namespace RiseOn.NativeAdMob {
    // The SPI between the shared state machines and one platform's transport.
    // Implementations live in the platform assemblies - exactly one of which
    // exists in any given build - and the game never sees any of this
    // (internal, opened to the platform assemblies via InternalsVisibleTo).
    internal interface INativeAdMobPlatform {
        INativeInFeedAdMobClient CreateInFeed(
            NativeInFeedAdMob.Settings settings
          , INativeInFeedAdMobCallbacks callbacks);

        INativeOverlayAdMobClient CreateOverlay(
            NativeOverlayAdMobSettings settings
          , INativeOverlayAdMobCallbacks callbacks);
    }
}
