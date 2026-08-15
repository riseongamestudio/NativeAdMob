namespace RiseOn.NativeAdMob {
    // Callbacks may arrive on any thread; the wrapper marshals and gates.
    internal interface IInFeedAdCallbacks {
        void OnLoadingStarted();
        void OnLoadingCompleted(int errorCode, string errorMessage);
        void OnAdPaid(AdValue adValue);
        void OnSlotDisplayed(int slotIndex);
        void OnSlotShowNotReady(int slotIndex);
        void OnSlotPresentationFailed(
            int slotIndex, int errorCode, string errorMessage);
    }
}
