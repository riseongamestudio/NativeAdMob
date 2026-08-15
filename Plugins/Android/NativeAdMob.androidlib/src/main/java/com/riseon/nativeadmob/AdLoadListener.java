package com.riseon.nativeadmob;

// Ten package + ten method phai khop Android proxy ben Unity.
public interface AdLoadListener {
    void OnStateChanged(boolean isReady, boolean isLoading);
    void OnShowNotReady();
    void OnLoadingCompleted(int errorCode, String errorMessage);
    void OnLoadingStarted();
    void OnAdPaid(
        String adSource
      , String adUnitId
      , double value
      , String currencyCode);
    void OnDisplayed();
    void OnPresentationFailed(int errorCode, String errorMessage);
}
