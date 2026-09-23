namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Where the countdown sits.<br/>
    /// Two of these are relative to the close button, so the pair can be placed with one decision instead of two.<br/>
    /// The ordinals cross into the native layers.
    /// </summary>
    public enum TimerSide {
        Left            = 0
      , Right           = 1
      , Random          = 2
      , OppositeOfClose = 3
      , SameAsClose     = 4
    }
}
