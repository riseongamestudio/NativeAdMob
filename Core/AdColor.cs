using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// One colour, one int, across three languages. The wire carries packed
    /// ARGB because that is what both native sides build a colour from -
    /// Color.argb over there, the shift-and-mask in ROInFeedAdPresentation
    /// here - and because a float per channel would cost four parameters
    /// where the ABI check watches one.
    /// </summary>
    internal static class AdColor {
        private const int CHANNEL_MAX = 255;

        internal static int Pack(Color colour) {
            return Channel(colour.a) << 24
                 | Channel(colour.r) << 16
                 | Channel(colour.g) << 8
                 | Channel(colour.b);
        }

        internal static Color Unpack(int packed) {
            return new Color(
                (packed >> 16 & 0xFF) / (float)CHANNEL_MAX
              , (packed >> 8  & 0xFF) / (float)CHANNEL_MAX
              , (packed       & 0xFF) / (float)CHANNEL_MAX
              , (packed >> 24 & 0xFF) / (float)CHANNEL_MAX);
        }

        private static int Channel(float value) {
            return Mathf.Clamp(Mathf.RoundToInt(value * CHANNEL_MAX), 0, CHANNEL_MAX);
        }
    }
}
