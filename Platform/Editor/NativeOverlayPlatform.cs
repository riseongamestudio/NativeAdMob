using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The preview draws ads; it does not draw covers.
    //
    // Saying so once is more honest than painting a plausible rectangle. A
    // cover exists to hide a seam between two native surfaces, and the
    // preview has no such seam; the full-screen one also stops the game, and
    // there is no Activity and no UnityPause in play here to stop it with. A
    // rectangle that looked right would still be testing nothing.
    internal sealed class NativeOverlayPlatform : INativeOverlayPlatform {
        private static bool unsupportedLogged;

        public void ShowFullScreen(int argb) => LogUnsupported();

        public void HideFullScreen() => LogUnsupported();

        public void SetFullScreenColor(int argb) => LogUnsupported();

        public void ShowHalfScreen(int argb, float heightRatio)
            => LogUnsupported();

        public void HideHalfScreen() => LogUnsupported();

        public void SetHalfScreenColor(int argb) => LogUnsupported();

        // Said once for the session. A cover is shown and hidden around every
        // ad, so a line per call would bury the preview's own logging.
        private static void LogUnsupported() {
            if (unsupportedLogged) return;

            unsupportedLogged = true;
            Debug.LogWarning(
                "Native covers are not drawn in the Editor preview; Show, "
                + "Hide and SetColor are a no-op here. Build to a device to "
                + "see them.");
        }
    }
}
