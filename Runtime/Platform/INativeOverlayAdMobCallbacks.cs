namespace RiseOn.NativeAdMob {
    internal interface INativeOverlayAdMobCallbacks {
        void OnLoadingStarted();
        void OnLoadingCompleted(int errorCode, string errorMessage);
        void OnAdPaid(AdValue adValue);
        void OnStateChanged(bool isReady, bool isLoading);
        void OnShowNotReady();
        void OnDisplayed();
        void OnPresentationFailed(int errorCode, string errorMessage);
        void OnShowCompleted(int showId, string errorMessage, bool adConsumed);
    }
}
