namespace RiseOn.NativeAdMob.iOS {
    internal sealed class NativeCoverPlatform : INativeCoverPlatform {
        // iOS has no Activity, so the pause Android gets for free is asked
        // for by hand - and the full-screen cover is the one that asks. See
        // RONativeCover.mm for who owns it when an ad opens on top.
        public void ShowFullScreen(int argb) {
            NativeCoverBridge.RONativeCover_ShowFull(argb);
        }

        public void HideFullScreen() {
            NativeCoverBridge.RONativeCover_HideFull();
        }

        public void ShowHalfScreen(int argb, float heightRatio) {
            NativeCoverBridge.RONativeCover_ShowHalf(argb, heightRatio);
        }

        public void HideHalfScreen() {
            NativeCoverBridge.RONativeCover_HideHalf();
        }
    }
}
