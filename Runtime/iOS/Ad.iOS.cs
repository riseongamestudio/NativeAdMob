#if UNITY_IOS
using System;

namespace RiseOn.NativeAdMob {
    public abstract partial class Ad {
        protected IntPtr iosNativeAd;
        protected int iosInstanceId;
        private Action<int, string> iosOnLoadingCompleted;
        private Action iosOnLoadingStarted;
        private Action<string, string, double, string> iosOnAdPaid;
        private Action iosOnDisplayed;
        private Action<int, string> iosOnPresentationFailed;

        /// <summary>
        /// Đăng ký bộ trampoline static với native; mỗi subclass gọi đúng
        /// hàm SetListener của lớp mình.
        /// </summary>
        private protected virtual void IOSRegisterListenerNative() {}

        partial void IOSSetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed) {
            lock (nativeAdStateLock) {
                if (releasedManaged || iosNativeAd == IntPtr.Zero) return;

                ++loadListenerGeneration;
                iosOnLoadingCompleted   = onLoadingCompleted;
                iosOnLoadingStarted     = onLoadingStarted;
                iosOnAdPaid             = onAdPaid;
                iosOnDisplayed          = onDisplayed;
                iosOnPresentationFailed = onPresentationFailed;
                IOSRegisterListenerNative();
            }
        }

        partial void IOSInvalidateLoadListener() {
            iosOnLoadingCompleted   = null;
            iosOnLoadingStarted     = null;
            iosOnAdPaid             = null;
            iosOnDisplayed          = null;
            iosOnPresentationFailed = null;
        }

        // Native gọi về trên main thread của iOS; marshal qua
        // MobileAdsEventExecutor đúng như các proxy Android, đọc action hiện
        // hành dưới lock tại thời điểm chạy.
        private void DispatchIOSListenerCallback(Func<Action> resolveCallback) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                Action callback;
                lock (nativeAdStateLock) {
                    if (releasedManaged) return;
                    callback = resolveCallback();
                }
                InvokeSafely(callback);
            });
        }

        internal void IOSHandleLoadingStarted()
            => DispatchIOSListenerCallback(() => iosOnLoadingStarted);

        internal void IOSHandleLoadingCompleted(int errorCode, string errorMessage)
            => DispatchIOSListenerCallback(() => {
                var callback = iosOnLoadingCompleted;
                return callback == null
                    ? null
                    : () => callback(errorCode, errorMessage);
            });

        internal void IOSHandleAdPaid(
            string source, string adUnitId, double value, string currencyCode)
            => DispatchIOSListenerCallback(() => {
                var callback = iosOnAdPaid;
                return callback == null
                    ? null
                    : () => callback(source, adUnitId, value, currencyCode);
            });

        internal void IOSHandleDisplayed()
            => DispatchIOSListenerCallback(() => iosOnDisplayed);

        internal void IOSHandlePresentationFailed(int errorCode, string errorMessage)
            => DispatchIOSListenerCallback(() => {
                var callback = iosOnPresentationFailed;
                return callback == null
                    ? null
                    : () => callback(errorCode, errorMessage);
            });

        internal void IOSHandleStateChanged(bool isReady, bool isLoading)
            => HandleNativeStateChanged(isReady, isLoading);

        internal void IOSHandleShowNotReady() => HandleShowNotReady();
    }
}
#endif
