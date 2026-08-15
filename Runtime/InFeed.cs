using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// One InFeed is one ad unit id for the life of the app: it owns the
    /// shared supply (cache, loads, retry) natively and exposes a fixed array
    /// of display slots. Index it to get an Item - a stateless handle over
    /// one slot - and call Show/Hide/SetPosition there; everything unit-wide
    /// (settings, events, disposal) lives here.
    /// </summary>
    public sealed partial class InFeed : Ad {
        private const string JAVA_CLASS_NAME = "com.riseon.nativeadmob.InFeed";
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
        /// harmless and every bit of state stays inside the owning InFeed.
        /// </summary>
        public readonly struct Item {
            private readonly InFeed owner;
            private readonly int index;

            internal Item(InFeed owner, int index) {
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

        private readonly string adUnitId;
        private readonly Item[] items;
        private readonly Action[] pendingOnDisplayed;

        public int SlotCount => items.Length;

        public ref readonly Item this[int index] => ref items[index];

        public InFeed(in Settings settings)
            : base(settings.AdUnitId) {
            if (settings.SlotCount < 1 || settings.SlotCount > MAX_SLOT_COUNT) {
                throw new ArgumentOutOfRangeException(
                    nameof(settings)
                  , settings.SlotCount
                  , $"SlotCount must be within [1, {MAX_SLOT_COUNT}].");
            }

            adUnitId = settings.AdUnitId;
            items = new Item[settings.SlotCount];
            for (var i = 0; i < items.Length; ++i) items[i] = new Item(this, i);
            pendingOnDisplayed = new Action[items.Length];

            if (supportsAndroid) {
                AndroidCreate(settings);
            } else if (supportsIOS) {
                IOSCreate(settings);
            } else if (supportsEditorPreview) {
                EditorCreate(settings);
            } else {
                Debug.Log($"{nameof(InFeed)} is not supported on this platform");
            }
        }

        partial void AndroidCreate(Settings settings);
        partial void IOSCreate(Settings settings);
        partial void EditorCreate(Settings settings);
        partial void AndroidConfigureSlot(
            int slotIndex, Vector2Int positionPx, Vector2Int sizePx);
        partial void IOSConfigureSlot(
            int slotIndex, Vector2Int positionPx, Vector2Int sizePx);
        partial void EditorConfigureSlot(
            int slotIndex, Vector2Int positionPx, Vector2Int sizePx);
        partial void EditorReleaseSwappedPreview();
        partial void AndroidShowSlot(int slotIndex);
        partial void IOSShowSlot(int slotIndex);
        partial void EditorShowSlot(int slotIndex);
        partial void AndroidHideSlot(int slotIndex);
        partial void IOSHideSlot(int slotIndex);
        partial void EditorHideSlot(int slotIndex);
        partial void AndroidSetSlotPosition(int slotIndex, Vector2Int positionPx);
        partial void IOSSetSlotPosition(int slotIndex, Vector2Int positionPx);
        partial void EditorSetSlotPosition(int slotIndex, Vector2Int positionPx);
        partial void AndroidTakeReleased();
        partial void IOSTakeReleased();
        partial void EditorTakeReleased();
        partial void AndroidFinishRelease();
        partial void IOSFinishRelease();
        partial void EditorFinishRelease();

        private void ConfigureSlot(
            int slotIndex
          , Vector2Int positionPx
          , Vector2Int sizePx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                pendingOnDisplayed[slotIndex] = null;
                if (supportsAndroid) {
                    AndroidConfigureSlot(slotIndex, positionPx, sizePx);
                } else if (supportsIOS) {
                    IOSConfigureSlot(slotIndex, positionPx, sizePx);
                } else if (supportsEditorPreview) {
                    EditorConfigureSlot(slotIndex, positionPx, sizePx);
                }
            }
            EditorReleaseSwappedPreview();
        }

        private void ShowSlot(int slotIndex, Action onDisplayed) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                pendingOnDisplayed[slotIndex] = onDisplayed;
                if (supportsAndroid) {
                    AndroidShowSlot(slotIndex);
                } else if (supportsIOS) {
                    IOSShowSlot(slotIndex);
                } else if (supportsEditorPreview) {
                    EditorShowSlot(slotIndex);
                }
            }
        }

        private void HideSlot(int slotIndex) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                pendingOnDisplayed[slotIndex] = null;
                if (supportsAndroid) {
                    AndroidHideSlot(slotIndex);
                } else if (supportsIOS) {
                    IOSHideSlot(slotIndex);
                } else if (supportsEditorPreview) {
                    EditorHideSlot(slotIndex);
                }
            }
        }

        private void SetSlotPosition(int slotIndex, Vector2Int positionPx) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                if (supportsAndroid) {
                    AndroidSetSlotPosition(slotIndex, positionPx);
                } else if (supportsIOS) {
                    IOSSetSlotPosition(slotIndex, positionPx);
                } else if (supportsEditorPreview) {
                    EditorSetSlotPosition(slotIndex, positionPx);
                }
            }
        }

        // Called on the Unity thread by the platform listeners.
        internal void HandleSlotDisplayed(int slotIndex) {
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

        internal void HandleSlotShowNotReady(int slotIndex) {
            Debug.LogWarning(
                $"{nameof(InFeed)} slot {slotIndex}: Show Called While Not Ready");
        }

        internal void HandleSlotPresentationFailed(
            int slotIndex
          , int errorCode
          , string errorMessage) {
            var handler = OnSlotPresentationFailed;
            if (handler == null) return;
            InvokeSafely(() => handler(slotIndex, errorCode, errorMessage));
        }

        /// <summary>
        /// Only for retiring the unit itself (an ad unit id swap); slots need
        /// no per-use cleanup.
        /// </summary>
        public void Dispose() {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                releasedManaged = true;
                AndroidTakeReleased();
                IOSTakeReleased();
                EditorTakeReleased();
                Array.Clear(pendingOnDisplayed, 0, pendingOnDisplayed.Length);
                InvalidateLoadListener();
            }

            AndroidFinishRelease();
            IOSFinishRelease();
            EditorFinishRelease();
        }
    }
}
