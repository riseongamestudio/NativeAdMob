namespace RiseOn.NativeAdMob {
    internal interface IOverlayAdCallbacks {
        void OnLoadingCompleted(int errorCode, string errorMessage);
        void OnAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode);
        void OnStateChanged(bool isReady, bool isLoading);
        void OnShowNotReady();
        void OnDisplayed();
        void OnDisplayFailed(int errorCode, string errorMessage);
        void OnShowCompleted(int showId, string errorMessage, bool adConsumed);
    }
}
