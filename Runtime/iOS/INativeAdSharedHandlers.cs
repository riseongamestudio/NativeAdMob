namespace RiseOn.NativeAdMob.iOS {
    // The loading and paid callbacks are shared by both client kinds; the
    // trampolines reach them through this without caring which one answered.
    internal interface INativeAdSharedHandlers {
        void HandleLoadingStarted();
        void HandleLoadingCompleted(int errorCode, string errorMessage);
        void HandleAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision);
    }
}
