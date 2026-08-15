#if UNITY_IOS
using System;

namespace RiseOn.NativeAdMob {
    public abstract partial class Ad {
        protected IntPtr iosNativeAd;
        protected int iosInstanceId;

        // Native calls back on iOS's main thread; hop to Unity's update loop
        // and re-check the release gate there, the same marshalling the
        // Android proxies get from their dispatch.
        private protected void DispatchFromNative(Action callback) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                lock (nativeAdStateLock) {
                    if (releasedManaged) return;
                }
                InvokeSafely(callback);
            });
        }

        internal void IOSHandleLoadingStarted()
            => DispatchFromNative(RaiseLoadingStarted);

        internal void IOSHandleLoadingCompleted(int errorCode, string errorMessage)
            => DispatchFromNative(
                () => RaiseLoadingCompleted(errorCode, errorMessage));

        internal void IOSHandleAdPaid(
            string source, string adUnitId, double value, string currencyCode)
            => DispatchFromNative(
                () => RaiseAdPaid(source, adUnitId, value, currencyCode));

        internal void IOSHandleStateChanged(bool isReady, bool isLoading)
            => HandleNativeStateChanged(isReady, isLoading);

        internal void IOSHandleShowNotReady() => HandleShowNotReady();
    }
}
#endif
