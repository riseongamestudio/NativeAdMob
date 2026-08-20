using UnityEngine;

namespace RiseOn.NativeAdMob.Android {
    // The interface and method names must match the Java side exactly:
    // AndroidJavaProxy dispatches by name. Callbacks arrive on JNI threads;
    // the core wrapper marshals and gates them.
    internal sealed class InFeedAdListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.InFeedAdListener";

        private readonly IInFeedAdCallbacks callbacks;

        internal InFeedAdListenerProxy(IInFeedAdCallbacks callbacks)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.callbacks = callbacks;
        }

        // Nothing downstream listens for a load beginning, and neither
        // AdMob nor MAX reports one. The native contract still calls it.
        public void OnLoadingStarted() {}

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

        public void OnAdPaid(
            string adSource
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            // Precision is reported by the SDK but nothing downstream asks
            // for it, so it stops here rather than riding along unused.
            => callbacks.OnAdPaid(adSource, adUnitId, value, currencyCode);

        public void OnSlotDisplayed(int slotIndex)
            => callbacks.OnSlotDisplayed(slotIndex);

        public void OnSlotShowNotReady(int slotIndex)
            => callbacks.OnSlotShowNotReady(slotIndex);

        public void OnSlotDisplayFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => callbacks.OnSlotDisplayFailed(
                slotIndex
              , errorCode
              , errorMessage);
    }
}
