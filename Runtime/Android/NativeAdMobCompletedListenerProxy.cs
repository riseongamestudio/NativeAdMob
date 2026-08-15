using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class NativeAdMobCompletedListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.NativeAdMobCompletedListener";

        private readonly Action<string, bool> onAdCompleted;

        internal NativeAdMobCompletedListenerProxy(Action<string, bool> onAdCompleted)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.onAdCompleted = onAdCompleted;
        }

        public void OnAdCompleted(string errorMessage, bool adConsumed) {
            onAdCompleted?.Invoke(errorMessage, adConsumed);
        }
    }
}
