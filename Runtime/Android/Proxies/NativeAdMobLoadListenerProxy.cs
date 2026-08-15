using UnityEngine;

namespace RiseOn.NativeAdMob.Android {
    // The interface and method names must match the Java side exactly:
    // AndroidJavaProxy dispatches by name. Callbacks arrive on JNI threads;
    // the core wrapper marshals and gates them.
    internal sealed class NativeAdMobLoadListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.NativeAdMobLoadListener";

        private readonly INativeOverlayAdMobCallbacks callbacks;

        internal NativeAdMobLoadListenerProxy(INativeOverlayAdMobCallbacks callbacks)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.callbacks = callbacks;
        }

        public void OnStateChanged(bool isReady, bool isLoading)
            => callbacks.OnStateChanged(isReady, isLoading);

        public void OnShowNotReady() => callbacks.OnShowNotReady();

        public void OnLoadingCompleted(int errorCode, string errorMessage)
            => callbacks.OnLoadingCompleted(errorCode, errorMessage);

        public void OnLoadingStarted() => callbacks.OnLoadingStarted();

        public void OnAdPaid(
            string adSource
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            => callbacks.OnAdPaid(new AdValue(
                adSource
              , adUnitId
              , value
              , currencyCode
              , (AdValuePrecision)precision));

        public void OnDisplayed() => callbacks.OnDisplayed();

        public void OnPresentationFailed(int errorCode, string errorMessage)
            => callbacks.OnPresentationFailed(errorCode, errorMessage);
    }
}
