package com.riseon.nativeadmob;

// The package and method names must match the C# proxy exactly:
// AndroidJavaProxy dispatches by name.
public interface NativeAdMobCompletedListener {
    void OnAdCompleted(String errorMessage, boolean adConsumed);
}
