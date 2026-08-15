using UnityEngine;

namespace RiseOn.NativeAdMob {
    // The wrapper serializes every call under its own lock and the Editor is
    // single-threaded, so this client needs no locking of its own.
    internal sealed class EditorInFeedClient : IInFeedClient {
        private const int EDITOR_PREVIEW_SUCCESS_CODE = 0;

        private readonly IInFeedCallbacks callbacks;
        private readonly string adUnitId;
        private readonly float backgroundAlpha;
        private readonly EditorPreviewConfig[] configs;
        private readonly EditorPreview[] previews;

        internal EditorInFeedClient(
            NativeInFeedAdMob.Settings settings
          , IInFeedCallbacks callbacks) {
            this.callbacks  = callbacks;
            adUnitId        = settings.AdUnitId;
            backgroundAlpha = settings.BackgroundAlpha;
            configs         = new EditorPreviewConfig[settings.SlotCount];
            previews        = new EditorPreview[settings.SlotCount];
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            var previous = previews[slotIndex];
            previews[slotIndex] = null;
            if (previous) previous.Release();

            configs[slotIndex] = EditorPreviewConfig.CreateInFeed(
                adUnitId
              , positionPx
              , sizePx
              , backgroundAlpha);
        }

        public void ShowSlot(int slotIndex) {
            var preview = previews[slotIndex];
            if (preview != null) {
                preview.SetVisible(true);
                callbacks.OnSlotDisplayed(slotIndex);
                return;
            }

            var config = configs[slotIndex]?.Snapshot();

            callbacks.OnLoadingStarted();
            if (config == null) return;

            previews[slotIndex] = EditorPreview.Show(
                config
              , () => previews[slotIndex] = null);
            callbacks.OnLoadingCompleted(
                EDITOR_PREVIEW_SUCCESS_CODE
              , string.Empty);
            callbacks.OnSlotDisplayed(slotIndex);
        }

        public void HideSlot(int slotIndex) {
            var preview = previews[slotIndex];
            if (preview) preview.SetVisible(false);
        }

        public void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            configs[slotIndex]?.SetPosition(positionPx);
            var preview = previews[slotIndex];
            if (preview) preview.SetPosition(positionPx);
        }

        public void Release() {
            for (var i = 0; i < previews.Length; ++i) {
                var preview = previews[i];
                previews[i] = null;
                configs[i]  = null;
                if (preview) preview.Release();
            }
        }
    }
}
