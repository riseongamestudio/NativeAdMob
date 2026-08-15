namespace RiseOn.NativeAdMob {
    internal interface INativeOverlayAdMobClient {
        void SetCountdownSec(int countdownSec);
        void LoadAd();
        void ShowAd(int showId);
        void HideAd();
        void Release();
    }
}
