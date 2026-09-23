namespace RiseOn.NativeAdMob.Editor {
    // Where every preview canvas sits. One number is set by hand; the rest
    // follow from it, so the stack cannot fall out of order when that one
    // number moves.
    //
    // On a device none of this exists: each layer is its own native window
    // or Activity, stacked by the platform. In the Editor everything is a
    // Screen Space Overlay canvas, and sorting order is the only stacking
    // there is - so the order has to reproduce the device's stack, including
    // the third-party SDKs' stub ads that open over the full-screen cover.
    internal static class EditorSortingOrder {
        // The very top: nothing a game or an SDK draws in the Editor may land
        // over a full-screen native ad. It needs only to clear the cover, and
        // the ceiling clears everything.
        internal const int FULL_SCREEN_AD = short.MaxValue;

        // The lowest order any third-party full-screen stub draws at in the
        // Editor, minus one, so every one of those stubs opens over the cover
        // the way its Activity opens over the cover's Activity on a device:
        //
        //   AppLovin MAX       MaxSdk/Prefabs/Interstitial.prefab (also its
        //                      app open) and Rewarded.prefab       999
        //   Google Mobile Ads  PlaceholderAds/{Interstitials,Rewarded,
        //                      AppOpen,AdInspector}/{768x1024,1024x768}   0
        //
        // min(999, 0) - 1 = -1. Set by hand: both numbers are baked into
        // package prefabs and exposed through nothing the pack can read.
        // Check them again whenever either SDK is upgraded.
        //
        // The game's own canvases have to stay below this whole stack.
        internal const int FULL_SCREEN_COVER = -1;

        // Everything else is attached to the game's surface on a device, so
        // it sits under the full-screen cover's Activity.
        internal const int HALF_SCREEN_AD = FULL_SCREEN_COVER - 1;

        // Raised before its ad on a device, so it stays under it.
        internal const int HALF_SCREEN_COVER = HALF_SCREEN_AD - 1;

        internal const int IN_FEED_AD = HALF_SCREEN_COVER - 1;
    }
}
