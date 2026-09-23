namespace RiseOn.NativeAdMob {
    // Callbacks may arrive on any thread; the wrapper marshals and gates.
    internal interface IInFeedAdCallbacks {
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
        void OnSlotDisplayed(int slotIndex);
        void OnSlotShowNotReady(int slotIndex);
        void OnSlotDisplayFailed(
            int slotIndex, int errorCode, string errorMessage);
    }
}
