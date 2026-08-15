#if UNITY_EDITOR
using System;

namespace RiseOn.NativeAdMob {
    public abstract partial class Ad {
        protected Action<int, string> editorOnLoadingCompleted;
        protected Action editorOnLoadingStarted;
        protected Action<string, string, double, string> editorOnAdPaid;
        protected Action editorOnDisplayed;
        protected Action<int, string> editorOnPresentationFailed;

        partial void EditorSetListener(
            Action<int, string> onLoadingCompleted
          , Action onLoadingStarted
          , Action<string, string, double, string> onAdPaid
          , Action onDisplayed
          , Action<int, string> onPresentationFailed) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                ++loadListenerGeneration;
                editorOnLoadingCompleted   = onLoadingCompleted;
                editorOnLoadingStarted     = onLoadingStarted;
                editorOnAdPaid             = onAdPaid;
                editorOnDisplayed          = onDisplayed;
                editorOnPresentationFailed = onPresentationFailed;
            }
        }

        partial void EditorInvalidateLoadListener() {
            editorOnLoadingCompleted   = null;
            editorOnLoadingStarted     = null;
            editorOnAdPaid             = null;
            editorOnDisplayed          = null;
            editorOnPresentationFailed = null;
        }
    }
}
#endif
