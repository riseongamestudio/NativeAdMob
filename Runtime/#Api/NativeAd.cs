using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Shared spine of one native ad wrapper: the release gate, the
    /// native-to-Unity marshalling, and the unit-level events every format
    /// raises. Each format keeps its state machine here in the core and
    /// talks to its platform through an internal client created by the one
    /// platform assembly present in the build.
    /// </summary>
    public abstract class NativeAd {
        private protected readonly object nativeAdStateLock = new();
        private protected bool releasedManaged;

        /// <summary>Supply-side events of the ad unit.</summary>
        public event Action OnLoadingStarted;
        public event Action<int, string> OnLoadingCompleted;
        public event Action<AdValue> OnAdPaid;

        private protected NativeAd(string adUnitId) {
            if (string.IsNullOrWhiteSpace(adUnitId)) {
                throw new ArgumentException(
                    "A non-empty ad unit ID is required."
                  , nameof(adUnitId));
            }
        }

        // Native calls back on its own thread; hop to Unity's update loop
        // and re-check the release gate there.
        private protected void DispatchFromNative(Action callback) {
            GoogleMobileAds.Common.MobileAdsEventExecutor.ExecuteInUpdate(() => {
                lock (nativeAdStateLock) {
                    if (releasedManaged) return;
                }
                InvokeSafely(callback);
            });
        }

        private protected void RaiseLoadingStarted()
            => InvokeSafely(OnLoadingStarted);

        private protected void RaiseLoadingCompleted(
            int errorCode
          , string errorMessage) {
            var handler = OnLoadingCompleted;
            if (handler == null) return;
            InvokeSafely(() => handler(errorCode, errorMessage));
        }

        private protected void RaiseAdPaid(AdValue adValue) {
            var handler = OnAdPaid;
            if (handler == null) return;
            InvokeSafely(() => handler(adValue));
        }

        private protected static void InvokeSafely(Action callback) {
            try {
                callback?.Invoke();
            } catch (Exception exception) {
                Debug.LogException(exception);
            }
        }
    }
}
