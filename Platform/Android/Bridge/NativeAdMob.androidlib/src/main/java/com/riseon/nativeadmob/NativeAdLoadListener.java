package com.riseon.nativeadmob;

// The package and method names must match the C# proxy exactly:
// AndroidJavaProxy dispatches by name.
public interface NativeAdLoadListener {
    void OnStateChanged(boolean isReady, boolean isLoading);
    void OnShowNotReady();
    void OnLoadingCompleted(int errorCode, String errorMessage);
    void OnLoadingStarted();
    void OnAdPaid(
        String adSource
      , String adUnitId
      , double value
      , String currencyCode
      , int precision);
    void OnDisplayed();
    void OnPresentationFailed(int errorCode, String errorMessage);
}
