namespace RiseOn.NativeAdMob {
    /// <param name="errorMessage">
    /// Empty when the native layer reports a normal completion; otherwise the
    /// reason the show was rejected or the presentation failed.
    /// </param>
    /// <param name="adConsumed">
    /// True when the cached ad was consumed, including presentation failures
    /// that happen after the ad leaves the ready state.
    /// </param>
    public delegate void ShowCompletedHandler(
        string errorMessage
      , bool adConsumed);
}
