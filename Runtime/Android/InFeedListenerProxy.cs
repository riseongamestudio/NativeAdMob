#if UNITY_ANDROID
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class InFeedListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.InFeedListener";

        private readonly int                                     generation;
        private readonly Action<int, string>                     onLoadingCompleted;
        private readonly Action                                  onLoadingStarted;
        private readonly Action<string, string, double, string>  onAdPaid;
        private readonly Action<int>                             onSlotDisplayed;
        private readonly Action<int>                             onSlotShowNotReady;
        private readonly Action<int, int, string>                onSlotPresentationFailed;
        private readonly Action<AndroidJavaProxy, int, Action>   dispatch;

        internal InFeedListenerProxy(
            int generation
          , Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action<int> onSlotDisplayed
          , Action<int> onSlotShowNotReady
          , Action<int, int, string> onSlotPresentationFailed
          , Action<AndroidJavaProxy, int, Action> dispatch)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.generation               = generation;
            this.onLoadingCompleted       = onLoadingCompleted;
            this.onLoadingStarted         = onLoadingStarted;
            this.onAdPaid                 = onAdPaid;
            this.onSlotDisplayed          = onSlotDisplayed;
            this.onSlotShowNotReady       = onSlotShowNotReady;
            this.onSlotPresentationFailed = onSlotPresentationFailed;
            this.dispatch                 = dispatch;
        }

        public void OnLoadingStarted() {
            dispatch?.Invoke(
                this
              , generation
              , () => onLoadingStarted?.Invoke());
        }

        public void OnLoadingCompleted(int errorCode, string errorMessage) {
            dispatch?.Invoke(
                this
              , generation
              , () => onLoadingCompleted?.Invoke(errorCode, errorMessage));
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

        public void OnSlotDisplayed(int slotIndex) {
            dispatch?.Invoke(
                this
              , generation
              , () => onSlotDisplayed?.Invoke(slotIndex));
        }

        public void OnSlotShowNotReady(int slotIndex) {
            dispatch?.Invoke(
                this
              , generation
              , () => onSlotShowNotReady?.Invoke(slotIndex));
        }

        public void OnSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage) {
            dispatch?.Invoke(
                this
              , generation
              , () => onSlotPresentationFailed?.Invoke(
                    slotIndex
                  , errorCode
                  , errorMessage));
        }
    }
}
#endif
