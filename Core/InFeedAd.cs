using System;
using System.Runtime.CompilerServices;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// One InFeedAd is one ad unit id for the life of the app: it
    /// owns the shared supply (cache, loads, retry) natively and exposes a
    /// fixed array of display slots. Index it to get an Item - a stateless
    /// handle over one slot - and call Show/Hide/SetPosition there;
    /// everything unit-wide (settings, events, disposal) lives here.
    /// </summary>
    public sealed class InFeedAd : BaseAd, IInFeedAdCallbacks {
        private const int MAX_SLOT_COUNT = 8;
        private const string FORMAT = "NATIVE_IN_FEED";

        [Serializable]
        public struct Settings {
            public string AdUnitId;
            public int SlotCount;
            /// <summary>Raw ads kept warm per unit; 0 means SlotCount + 1.</summary>
            public int CacheSize;
            /// <summary>
            /// The cell's own backdrop, alpha included. Transparent is a real
            /// answer here: a feed cell that wants no backdrop asks for it.
            /// </summary>
            public Color BackgroundColor;
            /// <summary>
            /// Radius of the cell's rounded corners, in pixels; 0 keeps them
            /// square. Every asset keeps clear of the curve - inset by just
            /// enough that its own corner touches the arc - except the
            /// background picture of the scrim layout, which fills the cell
            /// and is clipped by the curve instead. This is where the unit
            /// starts; Initialize carries a radius of its own for a caller
            /// that only knows it once the cell is measured.
            /// </summary>
            public int RoundCornerPx;
        }

        /// <summary>
        /// A stateless view over one slot: just (owner, index), so copies are
        /// harmless and every bit of state stays inside the owning unit.
        /// </summary>
        public readonly struct Item {
            private readonly InFeedAd owner;
            private readonly int index;

            internal Item(InFeedAd owner, int index) {
                this.owner = owner;
                this.index = index;
            }

            public void Show(Action onDisplayed = null)
                => owner.ShowSlot(index, onDisplayed);

            public void Hide() => owner.HideSlot(index);

            public void SetPosition(Vector2Int positionPx)
                => owner.SetSlotPosition(index, positionPx);
        }

        /// <summary>(slotIndex, error)</summary>
        public event Action<int, AdError> OnSlotDisplayFailed;

        private readonly Item[] items;
        private bool            initialized;
        private int             roundCornerPx;
        private readonly Action[] pendingOnDisplayed;
        private IInFeedAdClient client;

        public int SlotCount => items.Length;

        // An indexer's metadata name defaults to "Item", which would collide
        // with the nested Item type; only the metadata name changes here.
        [IndexerName("Slots")]
        public ref readonly Item this[int index] => ref items[index];

        /// <summary>
        /// Sizes every slot of this feed. Required before a slot can be
        /// positioned or shown - a slot with no size has nowhere to draw -
        /// and calling it again resizes them all.
        /// </summary>
        public void Initialize(Vector2Int sizePx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                ConfigureEverySlot(sizePx, roundCornerPx);
            }
        }

        /// <summary>
        /// The same, with the cell's corner radius. The radius travels with
        /// the size because a caller measuring a cell on screen only learns
        /// both at that moment, and the unit it draws from may have been
        /// built long before - warmed at start-up, say.
        /// </summary>
        public void Initialize(Vector2Int sizePx, int roundCornerPx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                ConfigureEverySlot(sizePx, Mathf.Max(0, roundCornerPx));
            }
        }

        private void ConfigureEverySlot(Vector2Int sizePx, int cornerPx) {
            roundCornerPx = cornerPx;
            for (var i = 0; i < items.Length; ++i) {
                pendingOnDisplayed[i] = null;
                client?.ConfigureSlot(i, default, sizePx, cornerPx);
            }
            initialized = true;
        }

        public InFeedAd(in Settings settings) : base(settings.AdUnitId, FORMAT) {
            if (settings.SlotCount < 1 || settings.SlotCount > MAX_SLOT_COUNT) {
                throw new ArgumentOutOfRangeException(
                    nameof(settings)
                  , settings.SlotCount
                  , $"SlotCount must be within [1, {MAX_SLOT_COUNT}].");
            }

            items = new Item[settings.SlotCount];
            for (var i = 0; i < items.Length; ++i) items[i] = new Item(this, i);
            pendingOnDisplayed = new Action[items.Length];
            roundCornerPx = Mathf.Max(0, settings.RoundCornerPx);

            var platform = AdPlatformRegistry.Installed;
            if (platform == null) {
                Debug.Log(
                    $"{nameof(InFeedAd)} is not supported on "
                    + Application.platform);
                return;
            }
            client = platform.CreateInFeed(settings, this);
        }

        private void ShowSlot(int slotIndex, Action onDisplayed) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;
                if (WarnIfNotInitialized(nameof(Item.Show))) return;

                pendingOnDisplayed[slotIndex] = onDisplayed;
                client?.ShowSlot(slotIndex);
            }
        }

        private void HideSlot(int slotIndex) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;
                if (!initialized) return;

                pendingOnDisplayed[slotIndex] = null;
                client?.HideSlot(slotIndex);
            }
        }

        private void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;
                if (WarnIfNotInitialized(nameof(Item.SetPosition))) return;

                client?.SetSlotPosition(slotIndex, positionPx);
            }
        }

        void IInFeedAdCallbacks.OnLoadingCompleted(
            int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize)
            => DispatchFromNative(
                () => RaiseLoadingCompleted(
                    errorCode
                  , errorMessage
                  , cachedCount
                  , cacheSize));

        void IInFeedAdCallbacks.OnAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode)
            => DispatchFromNative(
                () => RaiseAdPaid(source, adUnitId, value, currencyCode));

        void IInFeedAdCallbacks.OnSlotDisplayed(int slotIndex)
            => DispatchFromNative(() => HandleSlotDisplayed(slotIndex));

        void IInFeedAdCallbacks.OnSlotShowNotReady(int slotIndex)
            => DispatchFromNative(
                () => Debug.LogWarning(
                    $"{nameof(InFeedAd)} slot {slotIndex}: "
                    + "Show Called While Not Ready"));

        void IInFeedAdCallbacks.OnSlotDisplayFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => DispatchFromNative(() => {
                var handler = OnSlotDisplayFailed;
                if (handler == null) return;

                AdError error = new(errorCode, errorMessage);
                InvokeSafely(() => handler(slotIndex, error));
            });

        private bool WarnIfNotInitialized(string operation) {
            if (initialized) return false;

            Debug.LogError(
                $"{nameof(InFeedAd)}.{operation} called before "
                + $"{nameof(Initialize)}; the feed has no size yet.");
            return true;
        }

        private void HandleSlotDisplayed(int slotIndex) {
            Action onDisplayed;
            lock (nativeAdStateLock) {
                if (releasedManaged
                 || slotIndex < 0
                 || slotIndex >= pendingOnDisplayed.Length)
                    return;

                onDisplayed = pendingOnDisplayed[slotIndex];
                pendingOnDisplayed[slotIndex] = null;
            }
            InvokeSafely(onDisplayed);
        }

        /// <summary>
        /// Only for retiring the unit itself (an ad unit id swap); slots need
        /// no per-use cleanup.
        /// </summary>
        public void Dispose() {
            IInFeedAdClient releasedClient;
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                releasedManaged = true;
                releasedClient = client;
                client = null;
                Array.Clear(pendingOnDisplayed, 0, pendingOnDisplayed.Length);
            }

            releasedClient?.Release();
        }
    }
}
