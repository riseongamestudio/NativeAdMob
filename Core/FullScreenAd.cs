using RiseOn.Analytics;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// An overlay ad that covers the whole screen - app open, interstitial,
    /// end card. The bottom-slice sibling is HalfScreenAd.
    /// </summary>
    public sealed class FullScreenAd : OverlayAd {
        public struct Settings {
            public string AdUnitId;
            public int    CountdownSec;
            public bool   XRandomSide;
            public bool   NumberOppositeSide;
            public float  BackgroundAlpha;
            /// <summary>
            /// When true the close button commits the ad's click on its way
            /// out: the tap both follows the ad and dismisses it.
            /// </summary>
            public bool   FakeCloseAutoDismiss;

            private AdFormat format;

            /// <summary>
            /// Where the game puts this placement. Unset means NATIVE.
            /// </summary>
            public AdFormat Format {
                // A struct cannot initialise a field, so the default lives in
                // the reading of it rather than in an assignment.
                readonly get => format is AdFormat.UNKNOWN ? AdFormat.NATIVE : format;
                set => format = value;
            }
        }

        public FullScreenAd(in Settings settings)
            : base(
                settings.AdUnitId
              , settings.Format
              , coversFullScreen: true
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , heightRatio: 1
              , settings.BackgroundAlpha
              , settings.FakeCloseAutoDismiss) {}
    }
}
