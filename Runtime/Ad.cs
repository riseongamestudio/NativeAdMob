using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Shared spine of one native ad wrapper: platform detection, the release
    /// gate, and listener generations. Each platform owns its handle and its
    /// state inside its partial file (Ad.Android.cs / Ad.iOS.cs /
    /// Ad.Editor.cs); the partial hooks below compile away, call sites
    /// included, on platforms that do not implement them.
    /// </summary>
    public abstract partial class Ad {
        protected int loadListenerGeneration;
        protected bool releasedManaged;

        protected readonly bool supportsAndroid;
        protected readonly bool supportsIOS;
        protected readonly bool supportsEditorPreview;
        protected readonly object nativeAdStateLock = new();

        protected Ad(
            string javaClassName
          , string adUnitId
          , bool passCurrentActivity = false) {
            var validatedAdUnitId = RequireAdUnitId(adUnitId);

            supportsAndroid = Application.platform == RuntimePlatform.Android;
            supportsIOS =
                Application.platform == RuntimePlatform.IPhonePlayer;
            supportsEditorPreview = Application.isEditor;
            if (supportsAndroid) {
                AndroidCreate(
                    javaClassName
                  , validatedAdUnitId
                  , passCurrentActivity);
            }
        }

        protected static string RequireAdUnitId(string adUnitId) {
            if (!string.IsNullOrWhiteSpace(adUnitId)) return adUnitId;

            throw new ArgumentException(
                "A non-empty ad unit ID is required."
              , nameof(adUnitId));
        }

        partial void AndroidCreate(
            string javaClassName
          , string adUnitId
          , bool passCurrentActivity);

        partial void AndroidCall(string methodName, object[] parameters);

        partial void AndroidSetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed);

        partial void IOSSetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed);

        partial void EditorSetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed);

        partial void AndroidInvalidateLoadListener();
        partial void IOSInvalidateLoadListener();
        partial void EditorInvalidateLoadListener();

        protected void CallAndroid(
            string methodName
          , params object[] parameters) {
            if (supportsAndroid) {
                AndroidCall(methodName, parameters);
            } else if (!supportsEditorPreview) {
                Debug.Log($"{methodName}() only supports Android for now ...");
            }
        }

        public void SetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed = null
          , Action<int, string> onPresentationFailed = null) {
            if (supportsAndroid) {
                AndroidSetListener(
                    onLoadingCompleted
                  , onLoadingStarted
                  , onAdPaid
                  , onDisplayed
                  , onPresentationFailed);
            } else if (supportsIOS) {
                IOSSetListener(
                    onLoadingCompleted
                  , onLoadingStarted
                  , onAdPaid
                  , onDisplayed
                  , onPresentationFailed);
            } else if (supportsEditorPreview) {
                EditorSetListener(
                    onLoadingCompleted
                  , onLoadingStarted
                  , onAdPaid
                  , onDisplayed
                  , onPresentationFailed);
            } else {
                Debug.Log("SetListener() is not supported on this platform");
            }
        }

        protected void InvalidateLoadListener() {
            ++loadListenerGeneration;
            AndroidInvalidateLoadListener();
            IOSInvalidateLoadListener();
            EditorInvalidateLoadListener();
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
