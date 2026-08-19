using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// A flat colour over a bottom slice of the screen. The strip above it is
    /// not merely transparent but absent, so touches there reach the game as
    /// if no cover existed - and the game keeps running behind it on both
    /// platforms. Nothing here pauses anything, which is the whole difference
    /// from <see cref="FullScreenCover"/>.
    ///
    /// <para>Static because a game has one of these. The full-screen cover is
    /// a separate type, not a sibling instance: both can be up at once and
    /// neither disturbs the other.</para>
    /// </summary>
    public static class HalfScreenCover {
        private const float DEFAULT_HEIGHT_RATIO = .5f;

        /// <summary>Black, at half the screen - neither has to be named.
        /// </summary>
        public static void Show() => Show(Color.black, DEFAULT_HEIGHT_RATIO);

        /// <summary>Half the screen, the height nobody has to name.</summary>
        public static void Show(Color color) => Show(color, DEFAULT_HEIGHT_RATIO);

        /// <summary>Black, at the height named.</summary>
        public static void Show(float heightRatio) => Show(Color.black, heightRatio);

        /// <summary>
        /// <paramref name="heightRatio"/> is the share of the screen the
        /// cover claims, measured from the bottom edge. At or below 0, or at
        /// or above 1, means the whole screen - still with no pause, so this
        /// is not a way to reach <see cref="FullScreenCover"/>.
        /// </summary>
        public static void Show(Color color, float heightRatio) {
            var platform = CoverTransport.Platform;
            if (platform == null) return;

            platform.ShowHalfScreen(AdColor.Pack(color), heightRatio);
        }

        public static void Hide() {
            var platform = CoverTransport.Platform;
            if (platform == null) return;

            platform.HideHalfScreen();
        }
    }
}
