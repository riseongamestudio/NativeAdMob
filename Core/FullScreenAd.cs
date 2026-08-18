using System;
using RiseOn.Analytics;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// An overlay ad that covers the whole screen - app open, interstitial,
    /// end card. The bottom-slice sibling is HalfScreenAd.
    /// </summary>
    public sealed class FullScreenAd : OverlayAd {
        [Serializable]
        public struct Settings {
            public string AdUnitId;

            /// <summary>
            /// How many ads this placement keeps warm at once. 0 means 1 -
            /// always hold a spare. Raise it where one show is followed
            /// straight by another, so the second is already in hand.
            /// </summary>
            public int CacheSize;

            public float BackgroundAlpha;

            [SerializeField] private AdFormat format;

            /// <summary>
            /// Where the game puts this placement. Unset means NATIVE.
            /// </summary>
            public AdFormat Format {
                // A struct cannot initialise a field, so the default lives in
                // the reading of it rather than in an assignment.
                readonly get => format is AdFormat.UNKNOWN ? AdFormat.NATIVE : format;
                set => format = value;
            }

            /// <summary>
            /// The close button and the countdown that gates it. Changeable
            /// afterwards through SetClose.
            /// </summary>
            public CloseSettings Close;
        }

        public FullScreenAd(in Settings settings)
            : base(
                settings.AdUnitId
              , settings.Format
              , coversFullScreen: true
              , heightRatio: 1
              , settings.CacheSize
              , settings.BackgroundAlpha
              , settings.Close) {}
    }
}
