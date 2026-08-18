using System;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// The close button and the countdown that gates it - the control strip
    /// every overlay ad carries. One struct, shared by every format, and
    /// changeable after the ad is built.
    /// </summary>
    [Serializable]
    public struct CloseSettings {
        /// <summary>
        /// Seconds the ad holds the close button back. Zero means there is
        /// no timer at all, not a timer that expires at once.
        /// </summary>
        public int Cooldown;

        public CloseSide CloseSide;

        public TimerSide TimerSide;

        /// <summary>
        /// When true the close button commits the ad's click on its way out:
        /// the tap both follows the ad and dismisses it.
        /// </summary>
        public bool RedirectOnClose;
    }
}
