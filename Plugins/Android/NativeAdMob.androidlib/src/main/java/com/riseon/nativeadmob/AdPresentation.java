package com.riseon.nativeadmob;

// Adapter chung de controller base khong phai biet renderer la Dialog hay View.
public interface AdPresentation {

    interface Listener {
        default void OnReady() {}
        void OnDisplayed();
        void OnDismissed();

        // Only reports whether the rendered ad view is actually visible to the user.
        // It must never gate loading, retrying, or presentation creation.
        default void OnActualVisibilityChanged(boolean isActuallyVisible) {}
    }

    boolean Show();
    boolean IsShowing();
    default void OnAdClicked() {}

    // Dong binh thuong: renderer phai phat OnDismissed.
    void Dismiss();

    // Dong im lang khi Release/controller cleanup.
    void Release();
}
