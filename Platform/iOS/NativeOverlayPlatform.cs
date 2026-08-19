namespace RiseOn.NativeAdMob.iOS {
    internal sealed class NativeOverlayPlatform : INativeOverlayPlatform {
        // iOS has no Activity, so the pause Android gets for free is asked
        // for by hand - and the full-screen cover is the one that asks. See
        // RONativeOverlay.mm for who owns it when an ad opens on top.
        public void ShowFullScreen(int argb) {
            NativeOverlayBridge.RONativeOverlay_ShowFull(argb);
        }

        public void HideFullScreen() {
            NativeOverlayBridge.RONativeOverlay_HideFull();
        }

        public void SetFullScreenColor(int argb) {
            NativeOverlayBridge.RONativeOverlay_SetFullColor(argb);
        }

        public void ShowHalfScreen(int argb, float heightRatio) {
            NativeOverlayBridge.RONativeOverlay_ShowHalf(argb, heightRatio);
        }

        public void HideHalfScreen() {
            NativeOverlayBridge.RONativeOverlay_HideHalf();
        }

        public void SetHalfScreenColor(int argb) {
            NativeOverlayBridge.RONativeOverlay_SetHalfColor(argb);
        }
    }
}
