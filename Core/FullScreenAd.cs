namespace RiseOn.NativeAdMob {
    /// <summary>
    /// An overlay ad that covers the whole screen - app open, interstitial,
    /// end card. The bottom-slice sibling is HalfScreenAd.
    /// </summary>
    public sealed class FullScreenAd : OverlayAd {
        public struct Settings {
            public string AdUnitId;
            public int CountdownSec;
            public bool XRandomSide;
            public bool NumberOppositeSide;
            public float BackgroundAlpha;
        }

        public FullScreenAd(in Settings settings)
            : base(
                settings.AdUnitId
              , coversFullScreen: true
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , heightRatio: 1
              , settings.BackgroundAlpha) {}
    }
}
