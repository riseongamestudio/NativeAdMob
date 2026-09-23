using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Shared spine of one native ad wrapper: the release gate, the native-to-Unity marshalling, and the unit-level events every format raises.<br/>
    /// Each format keeps its state machine here in the core and talks to its platform through an internal client created by the one platform assembly present in the build.
    /// </summary>
    public abstract class BaseAd {
        private const int LOAD_SUCCESS_CODE = 0;

        private protected readonly object nativeAdStateLock = new();
        private protected bool releasedManaged;

        /// <summary>Supply-side events of the ad unit.</summary>
        // Declared in the order an ad lives them: it loads, it goes up,
        // it earns, it goes away. Every list about an ad follows this - the
        // events here, the subscriptions, the handlers - so the order is
        // learned once.
        public event Action<LoadedAdInfo> OnAdLoaded;
        public event Action<AdError> OnAdLoadFailed;
        public event Action<AdInfo> OnAdPaid;

        /// <summary>
        /// Where the game puts this placement.<br/>
        /// No SDK can know it - one native ad unit serves an in-feed cell, a collapsible strip or an end card depending only on who asked - so each placement declares it, and every paid impression this unit raises carries it.
        /// </summary>
        public string Format { get; }

        private protected BaseAd(string adUnitId, string format) {
            if (string.IsNullOrWhiteSpace(adUnitId)) {
                throw new ArgumentException(
                    "A non-empty ad unit ID is required."
                  , nameof(adUnitId));
            }
            Format = format;
        }


        // Native calls back on its own thread and this pack leaves it
        // there, the way the AdMob SDK does: a listener that needs Unity's
        // thread says so itself. Marshalling here would push every caller a
        // frame late whether they needed it or not.
        private protected void DispatchFromNative(Action callback) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;
            }
            InvokeSafely(callback);
        }

        // The native side reports one completion carrying a code. The two
        // outcomes part company here, so no caller ever has to test a code
        // again to find out which of them it got.
        private protected void RaiseLoadingCompleted(
            int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize) {
            if (errorCode is LOAD_SUCCESS_CODE) {
                var loaded = OnAdLoaded;
                if (loaded == null) return;

                LoadedAdInfo info = new(cachedCount, cacheSize);
                InvokeSafely(() => loaded(info));
                return;
            }

            var handler = OnAdLoadFailed;
            if (handler == null) return;

            AdError error = new(errorCode, errorMessage);
            InvokeSafely(() => handler(error));
        }

        private protected void RaiseAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode) {
            var handler = OnAdPaid;
            if (handler == null) return;

            // Native reports the money and the network; only this side knows
            // the placement. The record is therefore assembled here and
            // nowhere else, and it leaves the pack already complete.
            var impression = new AdInfo(
                source
              , adUnitId
              , Format
              , value
              , currencyCode);
            InvokeSafely(() => handler(impression));
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
