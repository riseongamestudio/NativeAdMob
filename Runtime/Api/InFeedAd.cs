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
    public sealed class InFeedAd : NativeAd, IInFeedAdCallbacks {
        private const int MAX_SLOT_COUNT = 8;

        public struct Settings {
            public string AdUnitId;
            public int SlotCount;
            /// <summary>Raw ads kept warm per unit; 0 means SlotCount + 1.</summary>
            public int CacheSize;
            public float BackgroundAlpha;
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

            public void Configure(Vector2Int positionPx, Vector2Int sizePx)
                => owner.ConfigureSlot(index, positionPx, sizePx);

            public void Show(Action onDisplayed = null)
                => owner.ShowSlot(index, onDisplayed);

            public void Hide() => owner.HideSlot(index);

            public void SetPosition(Vector2Int positionPx)
                => owner.SetSlotPosition(index, positionPx);
        }

        /// <summary>(slotIndex, errorCode, errorMessage)</summary>
        public event Action<int, int, string> OnSlotPresentationFailed;

        private readonly Item[] items;
        private readonly Action[] pendingOnDisplayed;
        private IInFeedAdClient client;

        public int SlotCount => items.Length;

        // An indexer's metadata name defaults to "Item", which would collide
        // with the nested Item type; only the metadata name changes here.
        [IndexerName("Slots")]
        public ref readonly Item this[int index] => ref items[index];

        public InFeedAd(in Settings settings)
            : base(settings.AdUnitId) {
            if (settings.SlotCount < 1 || settings.SlotCount > MAX_SLOT_COUNT) {
                throw new ArgumentOutOfRangeException(
                    nameof(settings)
                  , settings.SlotCount
                  , $"SlotCount must be within [1, {MAX_SLOT_COUNT}].");
            }

            items = new Item[settings.SlotCount];
            for (var i = 0; i < items.Length; ++i) items[i] = new Item(this, i);
            pendingOnDisplayed = new Action[items.Length];

            var platform = AdPlatformRegistry.Installed;
            if (platform == null) {
                Debug.Log(
                    $"{nameof(InFeedAd)} is not supported on "
                    + Application.platform);
                return;
            }
            client = platform.CreateInFeed(settings, this);
        }

        private void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                pendingOnDisplayed[slotIndex] = null;
                client?.ConfigureSlot(slotIndex, positionPx, sizePx);
            }
        }

        private void ShowSlot(int slotIndex, Action onDisplayed) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                pendingOnDisplayed[slotIndex] = onDisplayed;
                client?.ShowSlot(slotIndex);
            }
        }

        private void HideSlot(int slotIndex) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                pendingOnDisplayed[slotIndex] = null;
                client?.HideSlot(slotIndex);
            }
        }

        private void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                client?.SetSlotPosition(slotIndex, positionPx);
            }
        }

        void IInFeedAdCallbacks.OnLoadingStarted()
            => DispatchFromNative(RaiseLoadingStarted);

        void IInFeedAdCallbacks.OnLoadingCompleted(int errorCode, string errorMessage)
            => DispatchFromNative(
                () => RaiseLoadingCompleted(errorCode, errorMessage));

        void IInFeedAdCallbacks.OnAdPaid(AdValue adValue)
            => DispatchFromNative(() => RaiseAdPaid(adValue));

        void IInFeedAdCallbacks.OnSlotDisplayed(int slotIndex)
            => DispatchFromNative(() => HandleSlotDisplayed(slotIndex));

        void IInFeedAdCallbacks.OnSlotShowNotReady(int slotIndex)
            => DispatchFromNative(
                () => Debug.LogWarning(
                    $"{nameof(InFeedAd)} slot {slotIndex}: "
                    + "Show Called While Not Ready"));

        void IInFeedAdCallbacks.OnSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage)
            => DispatchFromNative(() => {
                var handler = OnSlotPresentationFailed;
                if (handler == null) return;
                InvokeSafely(() => handler(slotIndex, errorCode, errorMessage));
            });

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
