#if UNITY_ANDROID
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class AdLoadListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.AdLoadListener";

        private readonly int                                                  generation;
        private readonly Action<int, string>                                  onLoadingCompleted;
        private readonly Action                                               onLoadingStarted;
        private readonly Action<string, string, double, string>               onAdPaid;
        private readonly Action                                               onDisplayed;
        private readonly Action<int, string>                                  onPresentationFailed;
        private readonly Action<bool, bool>                                   onStateChanged;
        private readonly Action                                               onShowNotReady;
        private readonly Action<AdLoadListenerProxy, int, Action>        dispatch;

        internal AdLoadListenerProxy(
            int generation
          , Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed
          , Action<bool, bool> onStateChanged
          , Action onShowNotReady
          , Action<AdLoadListenerProxy, int, Action> dispatch)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.generation         = generation;
            this.onLoadingCompleted = onLoadingCompleted;
            this.onLoadingStarted   = onLoadingStarted;
            this.onAdPaid           = onAdPaid;
            this.onDisplayed        = onDisplayed;
            this.onPresentationFailed = onPresentationFailed;
            this.onStateChanged     = onStateChanged;
            this.onShowNotReady     = onShowNotReady;
            this.dispatch           = dispatch;
        }

        public void OnStateChanged(bool isReady, bool isLoading) {
            dispatch?.Invoke(
                this
              , generation
              , () => onStateChanged?.Invoke(isReady, isLoading));
        }

        public void OnShowNotReady() {
            dispatch?.Invoke(
                this
              , generation
              , () => onShowNotReady?.Invoke());
        }

        public void OnLoadingCompleted(int errorCode, string errorMessage) {
            dispatch?.Invoke(
                this
              , generation
              , () => onLoadingCompleted?.Invoke(errorCode, errorMessage));
        }

        public void OnLoadingStarted() {
            dispatch?.Invoke(
                this
              , generation
              , () => onLoadingStarted?.Invoke());
        }

        public void OnAdPaid(
            string adSource
          , string adUnitId
          , double value
          , string currencyCode) {
            dispatch?.Invoke(
                this
              , generation
              , () => onAdPaid?.Invoke(adSource, adUnitId, value, currencyCode));
        }

        public void OnDisplayed() {
            dispatch?.Invoke(
                this
              , generation
              , () => onDisplayed?.Invoke());
        }

        public void OnPresentationFailed(int errorCode, string errorMessage) {
            dispatch?.Invoke(
                this
              , generation
              , () => onPresentationFailed?.Invoke(errorCode, errorMessage));
        }
    }
}
#endif
