using System.Runtime.InteropServices;

namespace RiseOn.NativeAdMob.iOS {
    /// <summary>
    /// The C entry points of ROCover.mm. The two covers are kept
    /// apart the whole way down, because both can be up at once. Colour
    /// crosses as packed ARGB, the same shape the Android side takes.
    /// </summary>
    internal static class CoverBridge {
        [DllImport("__Internal")]
        internal static extern void ROFullScreenCover_Show(int color);

        [DllImport("__Internal")]
        internal static extern void ROFullScreenCover_Hide();

        [DllImport("__Internal")]
        internal static extern void ROHalfScreenCover_Show(
            int color
          , float heightRatio);

        [DllImport("__Internal")]
        internal static extern void ROHalfScreenCover_Hide();
    }
}
