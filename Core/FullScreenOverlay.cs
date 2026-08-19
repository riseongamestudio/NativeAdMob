using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// A flat colour over the whole screen, drawn by the platform above
    /// everything Unity draws. Used to hold a still frame across a moment
    /// that would otherwise flash - a scene swap, one ad closing while the
    /// next opens.
    ///
    /// <para>The game is PAUSED behind it on both platforms. Android gets
    /// that from the cover being an Activity; iOS gets it because this cover
    /// asks for it. A full-screen ad opened on top raises its own request
    /// anyway - the two are OR'd, so the second ask changes nothing while
    /// this cover is up, and the game still stops if the cover comes down
    /// first.</para>
    ///
    /// <para>Static because a game has one of these. The half-screen cover is
    /// a separate type, not a sibling instance: both can be up at once and
    /// neither disturbs the other.</para>
    /// </summary>
    public static class FullScreenOverlay {
        private static Color currentColor = Color.black;

        /// <summary>Repeats the last cover shown, black on the first call.
        /// </summary>
        public static void Show() => Show(currentColor);

        public static void Show(Color color) {
            currentColor = color;

            var platform = OverlayTransport.Platform;
            if (platform == null) return;

            platform.ShowFullScreen(AdColor.Pack(color));
        }

        public static void Hide() {
            var platform = OverlayTransport.Platform;
            if (platform == null) return;

            platform.HideFullScreen();
        }

        public static void SetColor(Color color) {
            currentColor = color;

            var platform = OverlayTransport.Platform;
            if (platform == null) return;

            platform.SetFullScreenColor(AdColor.Pack(color));
        }
    }
}
