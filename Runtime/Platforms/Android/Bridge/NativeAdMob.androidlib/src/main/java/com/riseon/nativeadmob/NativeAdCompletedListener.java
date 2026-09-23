package com.riseon.nativeadmob;

// The package and method names must match the C# proxy exactly:
// AndroidJavaProxy dispatches by name.
public interface NativeAdCompletedListener {
    // cachedCount is what the cache holds once this completion is done with
    // it, carried along so the listener never has to ask afterwards - the
    // answer to that question travels by a slower road.
    void OnAdCompleted(
            String errorMessage
          , boolean adConsumed
          , int cachedCount);
}
