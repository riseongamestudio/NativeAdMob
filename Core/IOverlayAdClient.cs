namespace RiseOn.NativeAdMob {
    internal interface IOverlayAdClient {
        void SetClose(in CloseSettings controls);
        void Load();
        void Show(int showId);
        void Hide();
        void Release();
    }
}
