package com.riseon.nativeadmob;

// The package and method names must match the C# proxy exactly:
// AndroidJavaProxy dispatches by name. The loading and paid events describe
// the shared supply of one ad unit; the slot-indexed events describe one
// display slot of it.
public interface InFeedAdListener {
    void OnLoadingStarted();
    void OnLoadingCompleted(
            int errorCode
          , String errorMessage
          , int cachedCount
          , int cacheSize);
    void OnAdPaid(
        String adSource
      , String adUnitId
      , double value
      , String currencyCode
      , int precision);
    void OnSlotDisplayed(int slotIndex);
    void OnSlotShowNotReady(int slotIndex);
    void OnSlotPresentationFailed(
        int slotIndex
      , int errorCode
      , String errorMessage);
}
