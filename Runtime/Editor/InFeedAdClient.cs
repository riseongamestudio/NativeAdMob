using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The editor stand-in mirrors the native unit's behaviour, not just its
    // look: one shared cache per unit topped up behind a simulated load
    // latency, slots that consume from it, dwell rotation of a slot that
    // stays on screen, rotation during the hidden stretch, and the same
    // not-ready signal. The rotation constants match the Android controller.
    internal sealed class InFeedAdClient : IInFeedAdClient {
        private const float LOAD_DELAY_SECONDS         = 1.0f;
        private const float MATERIALIZE_DELAY_SECONDS  = 0.15f;
        private const float DWELL_REFRESH_INTERVAL_SEC = 30f;
        private const float MIN_DWELL_SECONDS          = 1f;
        private const float MIN_SWAP_INTERVAL_SECONDS  = 3f;
        private const int   MAX_CACHE_SIZE             = 5;
        private const int   SUCCESS_CODE               = 0;

        private readonly IInFeedAdCallbacks callbacks;
        private readonly string adUnitId;
        private readonly float backgroundAlpha;
        private readonly int cacheSize;
        private readonly Slot[] slots;
        private int cachedAds;
        private bool isAdLoading;
        private bool released;

        internal InFeedAdClient(
            InFeedAd.Settings settings
          , IInFeedAdCallbacks callbacks) {
            this.callbacks  = callbacks;
            adUnitId        = settings.AdUnitId;
            backgroundAlpha = settings.BackgroundAlpha;
            cacheSize = Mathf.Min(
                MAX_CACHE_SIZE
              , settings.CacheSize < 1
                    ? settings.SlotCount + 1
                    : settings.CacheSize);
            slots = new Slot[settings.SlotCount];
            for (var i = 0; i < slots.Length; ++i) slots[i] = new Slot(this, i);
            StartLoad();
        }

        public void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            slots[slotIndex].Configure(positionPx, sizePx);
        }

        public void ShowSlot(int slotIndex) => slots[slotIndex].Show();

        public void HideSlot(int slotIndex) => slots[slotIndex].Hide();

        public void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            slots[slotIndex].SetPosition(positionPx);
        }

        public void Release() {
            released = true;
            foreach (var slot in slots) slot.Release();
        }

        // Keep the cache topped up regardless of what is on screen, exactly
        // like the native supply; the slots decide when a warm ad is spent.
        private void StartLoad() {
            if (released || isAdLoading || cachedAds >= cacheSize) return;

            isAdLoading = true;
            callbacks.OnLoadingStarted();
            EditorAdScheduler.Instance.Schedule(LOAD_DELAY_SECONDS, () => {
                if (released) return;

                isAdLoading = false;
                ++cachedAds;
                callbacks.OnLoadingCompleted(SUCCESS_CODE, string.Empty);
                OfferCacheToSlots();
                StartLoad();
            });
        }

        private void OfferCacheToSlots() {
            foreach (var slot in slots) {
                if (cachedAds == 0) return;
                if (slot.WantsCachedAd) slot.PresentCachedAd();
            }
        }

        // Pops for one slot and immediately starts replacing what it took.
        private bool TryTakeCachedAd() {
            if (cachedAds == 0) {
                StartLoad();
                return false;
            }
            --cachedAds;
            StartLoad();
            return true;
        }

        private sealed class Slot {
            private readonly InFeedAdClient owner;
            private readonly int index;
            private EditorAdConfig config;
            private Vector2Int sizePx;
            private EditorAd view;
            private bool materializing;
            private bool visibleRequested;
            private double visibleStartedAt = -1;
            private double accumulatedVisible;
            private double lastSwapAt = -1;
            private object dwellToken;

            internal Slot(InFeedAdClient owner, int index) {
                this.owner = owner;
                this.index = index;
            }

            // A freshly loaded ad goes to a configured slot with nothing on
            // screen and nothing being built; rotation pulls on its own.
            internal bool WantsCachedAd
                => config != null && view == null && !materializing;

            internal void Configure(Vector2Int positionPx, Vector2Int newSizePx) {
                visibleRequested = false;
                CancelDwell();
                PauseVisibleTimer();
                if (view != null && newSizePx != sizePx) {
                    // A different rect has to lay out again, like the native
                    // rect change destroying its entries.
                    view.Release();
                    view = null;
                }
                sizePx = newSizePx;
                config = EditorAdConfig.CreateInFeed(
                    owner.adUnitId
                  , positionPx
                  , newSizePx
                  , owner.backgroundAlpha);
                if (view != null) view.SetVisible(false);
                if (WantsCachedAd) {
                    if (owner.cachedAds > 0) PresentCachedAd();
                    else owner.StartLoad();
                }
            }

            internal void Show() {
                if (owner.released || config == null) return;

                visibleRequested = true;
                if (view != null) {
                    view.SetVisible(true);
                    ResumeVisibleTimer();
                    owner.callbacks.OnSlotDisplayed(index);
                    ScheduleDwell();
                    return;
                }

                owner.callbacks.OnSlotShowNotReady(index);
                if (materializing) return;
                if (owner.cachedAds > 0) PresentCachedAd();
                else owner.StartLoad();
            }

            internal void Hide() {
                if (owner.released) return;

                visibleRequested = false;
                CancelDwell();
                PauseVisibleTimer();
                if (view != null) view.SetVisible(false);
                // Rotate during the hidden stretch, the way the native slot
                // swaps behind a committed hide frame.
                EditorAdScheduler.Instance.Schedule(0f, () => {
                    if (owner.released || visibleRequested) return;
                    TrySwap();
                });
            }

            internal void SetPosition(Vector2Int positionPx) {
                config?.SetPosition(positionPx);
                if (view != null) view.SetPosition(positionPx);
            }

            internal void Release() {
                CancelDwell();
                if (view != null) {
                    view.Release();
                    view = null;
                }
                config = null;
            }

            internal void PresentCachedAd() {
                if (owner.released || config == null || materializing) return;
                if (!owner.TryTakeCachedAd()) return;

                materializing = true;
                EditorAdScheduler.Instance.Schedule(
                    MATERIALIZE_DELAY_SECONDS
                  , () => {
                        materializing = false;
                        if (owner.released || config == null) return;

                        var previous = view;
                        view = EditorAd.Show(
                            config.Snapshot()
                          , () => view = null);
                        view.SetVisible(visibleRequested);
                        if (previous != null) previous.Release();
                        lastSwapAt = EditorAdScheduler.Now;
                        if (visibleRequested) {
                            RestartVisibleAccumulation();
                            owner.callbacks.OnSlotDisplayed(index);
                            ScheduleDwell();
                        }
                    });
            }

            // Rotate to a warm ad once the one on screen has had its turn,
            // spaced so a burst of show and hide cannot drain the cache.
            private void TrySwap() {
                if (owner.released
                 || view == null
                 || materializing
                 || owner.cachedAds == 0)
                    return;
                if (CurrentVisibleDuration() < MIN_DWELL_SECONDS) return;
                if (lastSwapAt >= 0
                 && EditorAdScheduler.Now - lastSwapAt
                        < MIN_SWAP_INTERVAL_SECONDS)
                    return;

                PresentCachedAd();
            }

            private void ScheduleDwell() {
                CancelDwell();
                if (!visibleRequested || view == null) return;

                var remaining = Mathf.Max(
                    0f
                  , DWELL_REFRESH_INTERVAL_SEC
                        - (float) CurrentVisibleDuration());
                dwellToken = EditorAdScheduler.Instance.Schedule(
                    remaining
                  , HandleDwell);
            }

            private void HandleDwell() {
                dwellToken = null;
                if (owner.released || !visibleRequested || view == null) return;

                TrySwap();
                // Restart the window whether or not a swap happened, so the
                // trigger cannot re-arm every tick.
                RestartVisibleAccumulation();
                ScheduleDwell();
            }

            private void CancelDwell() {
                if (dwellToken == null) return;
                EditorAdScheduler.Instance.Cancel(dwellToken);
                dwellToken = null;
            }

            private void ResumeVisibleTimer() {
                if (visibleStartedAt < 0) {
                    visibleStartedAt = EditorAdScheduler.Now;
                }
            }

            private void PauseVisibleTimer() {
                if (visibleStartedAt < 0) return;
                accumulatedVisible +=
                    EditorAdScheduler.Now - visibleStartedAt;
                visibleStartedAt = -1;
            }

            private void RestartVisibleAccumulation() {
                accumulatedVisible = 0;
                visibleStartedAt = visibleRequested
                    ? EditorAdScheduler.Now
                    : -1;
            }

            private double CurrentVisibleDuration() {
                var duration = accumulatedVisible;
                if (visibleStartedAt >= 0) {
                    duration += EditorAdScheduler.Now - visibleStartedAt;
                }
                return duration;
            }
        }
    }
}
