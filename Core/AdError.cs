namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Why something failed. It is reported only on the failure events, so a
    /// caller never has to test a code to find out whether anything went
    /// wrong at all.
    /// </summary>
    public readonly struct AdError {
        public readonly int    Code;
        public readonly string Message;

        internal AdError(int code, string message) {
            Code    = code;
            Message = message ?? string.Empty;
        }

        public override string ToString() => "[" + Code + "] " + Message;
    }
}
