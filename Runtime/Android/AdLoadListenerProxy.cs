using UnityEngine;

namespace RiseOn.NativeAdMob {
    // Ten interface + ten method phai khop phia Java. Callbacks arrive on
    // JNI threads; the core wrapper marshals and gates them.
    internal sealed class AdLoadListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.AdLoadListener";

        private readonly IOverlayCallbacks callbacks;

        internal AdLoadListenerProxy(IOverlayCallbacks callbacks)
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
