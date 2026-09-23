using UnityEngine;

namespace RiseOn.NativeAdMob.Android {
    // The interface and method names must match the Java side exactly:
    // AndroidJavaProxy dispatches by name. Callbacks arrive on JNI threads;
    // the core wrapper marshals and gates them.
    internal sealed class NativeAdLoadListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.NativeAdLoadListener";

        private readonly IOverlayAdCallbacks callbacks;

        internal NativeAdLoadListenerProxy(IOverlayAdCallbacks callbacks)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.callbacks = callbacks;
        }

        public void OnStateChanged(bool isReady, bool isLoading)
            => callbacks.OnStateChanged(isReady, isLoading);

        public void OnShowNotReady() => callbacks.OnShowNotReady();

        public void OnLoadingCompleted(
            int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize)
            => callbacks.OnLoadingCompleted(
                errorCode
              , errorMessage
              , cachedCount
              , cacheSize);

        // Nothing downstream listens for a load beginning, and neither
        // AdMob nor MAX reports one. The native contract still calls it.
        public void OnLoadingStarted() {}

        public void OnAdPaid(
            string adSource
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            // Precision is reported by the SDK but nothing downstream asks
            // for it, so it stops here rather than riding along unused.
            => callbacks.OnAdPaid(adSource, adUnitId, value, currencyCode);

        public void OnDisplayed() => callbacks.OnDisplayed();

        public void OnPresentationFailed(int errorCode, string errorMessage)
            => callbacks.OnDisplayFailed(errorCode, errorMessage);
    }
}
