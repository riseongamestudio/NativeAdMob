namespace RiseOn.NativeAdMob {
    // The SPI between the covers and one platform's transport. Two families,
    // kept apart all the way down: a full-screen cover and a half-screen one
    // can be up at the same time, so neither may touch the other's state.
    internal interface ICoverPlatform {
        void ShowFullScreen(int argb);

        void HideFullScreen();

        void ShowHalfScreen(int argb, float heightRatio);

        void HideHalfScreen();
    }
}
