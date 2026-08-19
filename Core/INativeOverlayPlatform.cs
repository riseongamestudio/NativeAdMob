namespace RiseOn.NativeAdMob {
    // The SPI between the covers and one platform's transport. Two families,
    // kept apart all the way down: a full-screen cover and a half-screen one
    // can be up at the same time, so neither may touch the other's state.
    internal interface INativeOverlayPlatform {
        void ShowFullScreen(int argb);

        void HideFullScreen();

        void SetFullScreenColor(int argb);

        void ShowHalfScreen(int argb, float heightRatio);

        void HideHalfScreen();

        void SetHalfScreenColor(int argb);
    }
}
