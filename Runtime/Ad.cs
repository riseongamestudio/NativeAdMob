using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Shared spine of one native ad wrapper: platform detection, the release
    /// gate, listener generations, and the unit-level events every format
    /// raises. Each platform owns its handle and its state inside its partial
    /// file; the partial hooks compile away, call sites included, on
    /// platforms that do not implement them. Listeners attach internally at
    /// construction - the public surface is these events.
    /// </summary>
    public abstract partial class Ad {
        protected int loadListenerGeneration;
        protected bool releasedManaged;

        protected readonly bool supportsAndroid;
        protected readonly bool supportsIOS;
        protected readonly bool supportsEditorPreview;
        protected readonly object nativeAdStateLock = new();

        /// <summary>Supply-side events of the ad unit.</summary>
        public event Action OnLoadingStarted;
        public event Action<int, string> OnLoadingCompleted;
        public event Action<string, string, double, string> OnAdPaid;

        protected Ad(string adUnitId) {
            RequireAdUnitId(adUnitId);

            supportsAndroid = Application.platform == RuntimePlatform.Android;
            supportsIOS =
                Application.platform == RuntimePlatform.IPhonePlayer;
            supportsEditorPreview = Application.isEditor;
        }

        protected static string RequireAdUnitId(string adUnitId) {
            if (!string.IsNullOrWhiteSpace(adUnitId)) return adUnitId;

            throw new ArgumentException(
                "A non-empty ad unit ID is required."
              , nameof(adUnitId));
        }

        partial void AndroidCall(string methodName, object[] parameters);
        partial void AndroidInvalidateLoadListener();

        protected void CallAndroid(
            string methodName
          , params object[] parameters) {
            if (supportsAndroid) {
                AndroidCall(methodName, parameters);
            } else if (!supportsEditorPreview) {
                Debug.Log($"{methodName}() only supports Android for now ...");
            }
        }

        protected void InvalidateLoadListener() {
            ++loadListenerGeneration;
            AndroidInvalidateLoadListener();
        }

        protected void RaiseLoadingStarted() => InvokeSafely(OnLoadingStarted);

        protected void RaiseLoadingCompleted(int errorCode, string errorMessage) {
            var handler = OnLoadingCompleted;
            if (handler == null) return;
            InvokeSafely(() => handler(errorCode, errorMessage));
        }

        protected void RaiseAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode) {
            var handler = OnAdPaid;
            if (handler == null) return;
            InvokeSafely(() => handler(source, adUnitId, value, currencyCode));
        }

        protected virtual void HandleNativeStateChanged(
            bool isReady
          , bool isLoading) {}

        protected virtual void HandleShowNotReady() {}

        protected static void InvokeSafely(Action callback) {
            try {
                callback?.Invoke();
            } catch (Exception exception) {
                Debug.LogException(exception);
            }
        }
    }
}
