namespace RiseOn.NativeAdMob.iOS {
    internal sealed class CoverPlatform : ICoverPlatform {
        // iOS has no Activity, so the pause Android gets for free is asked
        // for by hand - and the full-screen cover is the one that asks. See
        // ROCover.mm for who owns it when an ad opens on top.
        public void ShowFullScreen(int argb) {
            CoverBridge.ROFullScreenCover_Show(argb);
        }

        public void HideFullScreen() {
            CoverBridge.ROFullScreenCover_Hide();
        }

        public void ShowHalfScreen(int argb, float heightRatio) {
            CoverBridge.ROHalfScreenCover_Show(argb, heightRatio);
        }

        public void HideHalfScreen() {
            CoverBridge.ROHalfScreenCover_Hide();
        }
    }
}
