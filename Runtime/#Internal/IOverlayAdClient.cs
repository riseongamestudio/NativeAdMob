namespace RiseOn.NativeAdMob {
    internal interface IOverlayAdClient {
        void SetCountdownSec(int countdownSec);
        void LoadAd();
        void ShowAd(int showId);
        void HideAd();
        void Release();
    }
}
