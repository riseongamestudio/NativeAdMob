package com.riseon.nativeadmob;

// Shared adapter so the controllers never need to know whether the
// renderer is a Dialog or a plain View.
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

    // The ordinary close path: the renderer must emit OnDismissed.
    void Dismiss();

    // The silent close used by Release and controller cleanup.
    void Release();
}
