using System.Runtime.InteropServices;

namespace RiseOn.NativeAdMob.iOS {
    /// <summary>
    /// The C entry points of RONativeOverlay.mm. The two covers are kept
    /// apart the whole way down, because both can be up at once. Colour
    /// crosses as packed ARGB, the same shape the Android side takes.
    /// </summary>
    internal static class NativeOverlayBridge {
        [DllImport("__Internal")]
        internal static extern void RONativeOverlay_ShowFull(int color);

        [DllImport("__Internal")]
        internal static extern void RONativeOverlay_HideFull();

        [DllImport("__Internal")]
        internal static extern void RONativeOverlay_SetFullColor(int color);

        [DllImport("__Internal")]
        internal static extern void RONativeOverlay_ShowHalf(
            int color
          , float heightRatio);

        [DllImport("__Internal")]
        internal static extern void RONativeOverlay_HideHalf();

        [DllImport("__Internal")]
        internal static extern void RONativeOverlay_SetHalfColor(int color);
    }
}
