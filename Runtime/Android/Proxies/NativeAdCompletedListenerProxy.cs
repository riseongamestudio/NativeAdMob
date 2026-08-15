using System;
using UnityEngine;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class NativeAdCompletedListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.NativeAdCompletedListener";

        private readonly Action<string, bool> onAdCompleted;

        internal NativeAdCompletedListenerProxy(Action<string, bool> onAdCompleted)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.onAdCompleted = onAdCompleted;
        }

        public void OnAdCompleted(string errorMessage, bool adConsumed) {
            onAdCompleted?.Invoke(errorMessage, adConsumed);
        }
    }
}
