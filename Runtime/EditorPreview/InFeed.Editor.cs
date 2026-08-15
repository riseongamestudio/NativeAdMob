#if UNITY_EDITOR
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public sealed partial class InFeed {
        private const int EDITOR_PREVIEW_SUCCESS_CODE = 0;

        private EditorPreviewConfig editorPreviewConfig;
        private EditorPreview editorPreview;
        private EditorPreview editorSwappedPreview;
        private EditorPreview editorReleasePending;

        partial void EditorConfigure(
            Vector2Int positionPx
          , Vector2Int sizePx
          , float backgroundAlpha) {
            editorSwappedPreview = editorPreview;
            editorPreview        = null;
            editorPreviewConfig  = EditorPreviewConfig.CreateInFeed(
                adUnitId
              , positionPx
              , sizePx
              , backgroundAlpha);
        }

        partial void EditorReleaseSwappedPreview() {
            EditorPreview previous;
            lock (nativeAdStateLock) {
                previous             = editorSwappedPreview;
                editorSwappedPreview = null;
            }
            if (previous) previous.Release();
        }

        partial void EditorShow() {
            if (editorPreview != null) {
                editorPreview.SetVisible(true);
                InvokeSafely(editorOnDisplayed);
                return;
            }

            var loadingStarted   = editorOnLoadingStarted;
            var loadingCompleted = editorOnLoadingCompleted;
            var displayed        = editorOnDisplayed;
            var config           = editorPreviewConfig?.Snapshot();

            InvokeSafely(loadingStarted);
            if (config == null) return;

            editorPreview = EditorPreview.Show(
                config
              , () => {
                    lock (nativeAdStateLock) {
                        editorPreview = null;
                    }
                });
            InvokeSafely(
                () => loadingCompleted?.Invoke(
                    EDITOR_PREVIEW_SUCCESS_CODE
                  , string.Empty));
            InvokeSafely(displayed);
        }

        partial void EditorHide() {
            if (editorPreview) editorPreview.SetVisible(false);
        }

        partial void EditorSetPosition(Vector2Int positionPx) {
            editorPreviewConfig?.SetPosition(positionPx);
            if (editorPreview) editorPreview.SetPosition(positionPx);
        }

        partial void EditorTakeReleased() {
            editorReleasePending = editorPreview;
            editorPreview        = null;
            editorPreviewConfig  = null;
        }

        partial void EditorFinishRelease() {
            var preview = editorReleasePending;
            editorReleasePending = null;
            if (preview) preview.Release();
        }
    }
}
#endif
