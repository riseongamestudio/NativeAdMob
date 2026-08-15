#if UNITY_EDITOR
using System.Collections.Generic;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public sealed partial class InFeed {
        private const int EDITOR_PREVIEW_SUCCESS_CODE = 0;

        private EditorPreviewConfig[] editorConfigs;
        private EditorPreview[] editorPreviews;
        private readonly List<EditorPreview> editorSwappedPreviews = new();
        private readonly List<EditorPreview> editorReleasePending = new();
        private float editorBackgroundAlpha;

        partial void EditorCreate(Settings settings) {
            editorConfigs = new EditorPreviewConfig[settings.SlotCount];
            editorPreviews = new EditorPreview[settings.SlotCount];
            editorBackgroundAlpha = settings.BackgroundAlpha;
        }

        partial void EditorConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            if (editorPreviews[slotIndex] != null) {
                editorSwappedPreviews.Add(editorPreviews[slotIndex]);
                editorPreviews[slotIndex] = null;
            }
            editorConfigs[slotIndex] = EditorPreviewConfig.CreateInFeed(
                adUnitId
              , positionPx
              , sizePx
              , editorBackgroundAlpha);
        }

        partial void EditorReleaseSwappedPreview() {
            EditorPreview[] previous = null;
            lock (nativeAdStateLock) {
                if (editorSwappedPreviews.Count > 0) {
                    previous = editorSwappedPreviews.ToArray();
                    editorSwappedPreviews.Clear();
                }
            }
            if (previous == null) return;
            foreach (var preview in previous) {
                if (preview) preview.Release();
            }
        }

        partial void EditorShowSlot(int slotIndex) {
            var preview = editorPreviews[slotIndex];
            if (preview != null) {
                preview.SetVisible(true);
                HandleSlotDisplayed(slotIndex);
                return;
            }

            var config = editorConfigs[slotIndex]?.Snapshot();

            RaiseLoadingStarted();
            if (config == null) return;

            editorPreviews[slotIndex] = EditorPreview.Show(
                config
              , () => {
                    lock (nativeAdStateLock) {
                        editorPreviews[slotIndex] = null;
                    }
                });
            RaiseLoadingCompleted(EDITOR_PREVIEW_SUCCESS_CODE, string.Empty);
            HandleSlotDisplayed(slotIndex);
        }

        partial void EditorHideSlot(int slotIndex) {
            var preview = editorPreviews[slotIndex];
            if (preview) preview.SetVisible(false);
        }

        partial void EditorSetSlotPosition(int slotIndex, Vector2Int positionPx) {
            editorConfigs[slotIndex]?.SetPosition(positionPx);
            var preview = editorPreviews[slotIndex];
            if (preview) preview.SetPosition(positionPx);
        }

        partial void EditorTakeReleased() {
            if (editorPreviews == null) return;

            for (var i = 0; i < editorPreviews.Length; ++i) {
                if (editorPreviews[i] != null) {
                    editorReleasePending.Add(editorPreviews[i]);
                    editorPreviews[i] = null;
                }
                editorConfigs[i] = null;
            }
            editorReleasePending.AddRange(editorSwappedPreviews);
            editorSwappedPreviews.Clear();
        }

        partial void EditorFinishRelease() {
            if (editorReleasePending.Count == 0) return;

            var previews = editorReleasePending.ToArray();
            editorReleasePending.Clear();
            foreach (var preview in previews) {
                if (preview) preview.Release();
            }
        }
    }
}
#endif
