using UnityEngine;

namespace RiseOn.NativeAdMob {
    // The SPI between the shared state machines and one platform's transport.
    // Implementations live in the platform assemblies - exactly one of which
    // exists in any given build - and the game never sees any of this
    // (internal, opened to the platform assemblies via InternalsVisibleTo).
    internal interface INativeAdMobPlatform {
        IInFeedClient CreateInFeed(
            NativeInFeedAdMob.Settings settings
          , IInFeedCallbacks callbacks);

        IOverlayClient CreateOverlay(
            OverlaySettings settings
          , IOverlayCallbacks callbacks);
    }

    internal static class NativeAdMobPlatform {
        internal static INativeAdMobPlatform Installed { get; private set; }

        // Called by the platform assembly's RuntimeInitializeOnLoadMethod
        // bootstrap, before any scene code can construct an ad.
        internal static void Install(INativeAdMobPlatform platform) {
            Installed = platform;
        }
    }

    internal struct OverlaySettings {
        public string AdUnitId;
        public bool CoversFullScreen;
        public int CountdownSec;
        public bool XRandomSide;
        public bool NumberOppositeSide;
        public float HeightRatio;
        public float BackgroundAlpha;
    }

    internal interface IInFeedClient {
        void ConfigureSlot(
            int slotIndex, Vector2Int positionPx, Vector2Int sizePx);
        void ShowSlot(int slotIndex);
        void HideSlot(int slotIndex);
        void SetSlotPosition(int slotIndex, Vector2Int positionPx);
        void Release();
    }

    // Callbacks may arrive on any thread; the wrapper marshals and gates.
    internal interface IInFeedCallbacks {
        void OnLoadingStarted();
        void OnLoadingCompleted(int errorCode, string errorMessage);
        void OnAdPaid(AdValue adValue);
        void OnSlotDisplayed(int slotIndex);
        void OnSlotShowNotReady(int slotIndex);
        void OnSlotPresentationFailed(
            int slotIndex, int errorCode, string errorMessage);
    }

    internal interface IOverlayClient {
        void SetCountdownSec(int countdownSec);
        void LoadAd();
        void ShowAd(int showId);
        void HideAd();
        void Release();
    }

    internal interface IOverlayCallbacks {
        void OnLoadingStarted();
        void OnLoadingCompleted(int errorCode, string errorMessage);
        void OnAdPaid(AdValue adValue);
        void OnStateChanged(bool isReady, bool isLoading);
        void OnShowNotReady();
        void OnDisplayed();
        void OnPresentationFailed(int errorCode, string errorMessage);
        void OnShowCompleted(int showId, string errorMessage, bool adConsumed);
    }
}
