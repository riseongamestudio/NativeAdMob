using UnityEngine;

namespace RiseOn.NativeAdMob {
    // The interface and method names must match the Java side exactly:
    // AndroidJavaProxy dispatches by name. Callbacks arrive on JNI threads;
    // the core wrapper marshals and gates them.
    internal sealed class InFeedListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.InFeedListener";

        private readonly IInFeedCallbacks callbacks;

        internal InFeedListenerProxy(IInFeedCallbacks callbacks)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.callbacks = callbacks;
        }

        public void OnLoadingStarted() => callbacks.OnLoadingStarted();

        public void OnLoadingCompleted(int errorCode, string errorMessage)
            => callbacks.OnLoadingCompleted(errorCode, errorMessage);

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

        public void OnSlotDisplayed(int slotIndex)
            => callbacks.OnSlotDisplayed(slotIndex);

        public void OnSlotShowNotReady(int slotIndex)
            => callbacks.OnSlotShowNotReady(slotIndex);

        public void OnSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => callbacks.OnSlotPresentationFailed(
                slotIndex
              , errorCode
              , errorMessage);
    }
}
