namespace RiseOn.NativeAdMob {
    /// <summary>
    /// An overlay ad that covers a bottom slice of the screen - the
    /// collapsible placement. HeightRatio is the covered fraction.
    /// </summary>
    public sealed class HalfScreenAd : OverlayAd {
        public struct Settings {
            public string AdUnitId;
            public int CountdownSec;
            public bool XRandomSide;
            public bool NumberOppositeSide;
            public float HeightRatio;
            public float BackgroundAlpha;
        }

        public HalfScreenAd(in Settings settings)
            : base(
                settings.AdUnitId
              , coversFullScreen: false
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha) {}
    }
}
