namespace RiseOn.NativeAdMob {
    internal interface IOverlayAdClient {
        void SetCountdownSec(int countdownSec);
        void Load();
        void Show(int showId);
        void Hide();
        void Release();
    }
}
