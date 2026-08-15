using UnityEngine;

namespace RiseOn.NativeAdMob {
    internal interface IInFeedAdClient {
        void ConfigureSlot(
            int slotIndex, Vector2Int positionPx, Vector2Int sizePx);
        void ShowSlot(int slotIndex);
        void HideSlot(int slotIndex);
        void SetSlotPosition(int slotIndex, Vector2Int positionPx);
        void Release();
    }
}
