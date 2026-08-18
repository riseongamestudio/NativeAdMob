using RiseOn.Analytics;

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

            private AdFormat format;

            /// <summary>
            /// Where the game puts this placement. Unset means NATIVE_COLLAPSIBLE.
            /// </summary>
            public AdFormat Format {
                // A struct cannot initialise a field, so the default lives in
                // the reading of it rather than in an assignment.
                readonly get => format is AdFormat.UNKNOWN
                        ? AdFormat.NATIVE_COLLAPSIBLE
                        : format;
                set => format = value;
            }
        }

        public HalfScreenAd(in Settings settings)
            : base(
                settings.AdUnitId
              , settings.Format
              , coversFullScreen: false
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha) {}
    }
}
