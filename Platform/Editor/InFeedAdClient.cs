using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The wrapper serializes every call under its own lock and the Editor is
    // single-threaded, so this client needs no locking of its own.
    internal sealed class InFeedAdClient : IInFeedAdClient {
        private const int EDITOR_AD_SUCCESS_CODE = 0;

        private readonly IInFeedAdCallbacks callbacks;
        private readonly string adUnitId;
        private readonly float backgroundAlpha;
        private readonly EditorAdConfig[] configs;
        private readonly EditorAd[] previews;

        internal InFeedAdClient(
            InFeedAd.Settings settings
          , IInFeedAdCallbacks callbacks) {
            this.callbacks  = callbacks;
            adUnitId        = settings.AdUnitId;
            backgroundAlpha = settings.BackgroundAlpha;
            configs         = new EditorAdConfig[settings.SlotCount];
            previews        = new EditorAd[settings.SlotCount];
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            var previous = previews[slotIndex];
            previews[slotIndex] = null;
            if (previous) previous.Release();

            configs[slotIndex] = EditorAdConfig.CreateInFeed(
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

            if (config == null) return;

            previews[slotIndex] = EditorAd.Show(
                config
              , () => previews[slotIndex] = null);
            callbacks.OnLoadingCompleted(
                EDITOR_AD_SUCCESS_CODE
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
