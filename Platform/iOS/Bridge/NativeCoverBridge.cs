using System.Runtime.InteropServices;

namespace RiseOn.NativeAdMob.iOS {
    /// <summary>
    /// The C entry points of RONativeCover.mm. The two covers are kept
    /// apart the whole way down, because both can be up at once. Colour
    /// crosses as packed ARGB, the same shape the Android side takes.
    /// </summary>
    internal static class NativeCoverBridge {
        [DllImport("__Internal")]
        internal static extern void RONativeCover_ShowFull(int color);

        [DllImport("__Internal")]
        internal static extern void RONativeCover_HideFull();

        [DllImport("__Internal")]
        internal static extern void RONativeCover_ShowHalf(
            int color
          , float heightRatio);

        [DllImport("__Internal")]
        internal static extern void RONativeCover_HideHalf();
    }
}
