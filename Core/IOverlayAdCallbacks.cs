namespace RiseOn.NativeAdMob {
    internal interface IOverlayAdCallbacks {
        void OnLoadingCompleted(
            int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize);
        void OnAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode);
        void OnStateChanged(bool isReady, bool isLoading);
        void OnShowNotReady();
        void OnDisplayed();
        void OnDisplayFailed(int errorCode, string errorMessage);
        // cachedCount is the cache as it stands AFTER this completion. It
        // rides along instead of being read back later because the separate
        // state notification is its own trip over the bridge, and that trip
        // has been measured arriving a frame and a half after this one.
        void OnShowCompleted(
            int showId
          , string errorMessage
          , bool adConsumed
          , int cachedCount);
    }
}
