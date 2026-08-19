using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// An overlay ad that covers the whole screen - app open, interstitial,
    /// end card. The bottom-slice sibling is HalfScreenAd.
    /// </summary>
    public sealed class FullScreenAd : OverlayAd {
        private const string DEFAULT_FORMAT = "NATIVE_FULL_SCREEN";
        
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

            [SerializeField] private string format;

            /// <summary>
            /// Where the game puts this placement. Unset means NATIVE.
            /// </summary>
            public string Format {
                // A struct cannot initialise a field, so the default lives in
                // the reading of it rather than in an assignment.
                readonly get => string.IsNullOrEmpty(format) ? DEFAULT_FORMAT : format;
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
