using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal sealed class AdCompletedListenerProxy : AndroidJavaProxy {
        private const string JAVA_LISTENER_CLASS_NAME = "com.riseon.nativeadmob.AdCompletedListener";

        private readonly Action<string, bool> onAdCompleted;

        internal AdCompletedListenerProxy(Action<string, bool> onAdCompleted)
            : base(JAVA_LISTENER_CLASS_NAME) {
            this.onAdCompleted = onAdCompleted;
        }

        public void OnAdCompleted(string errorMessage, bool adConsumed) {
            onAdCompleted?.Invoke(errorMessage, adConsumed);
        }
    }
}
