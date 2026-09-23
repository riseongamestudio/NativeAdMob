namespace RiseOn.NativeAdMob {
    public readonly struct AdInfo {
        public readonly string Source;
        public readonly string UnitId;
        public readonly string Format;
        public readonly double Value;
        public readonly string Currency;

        public AdInfo(
            string source
          , string unitId
          , string format
          , double value
          , string currency) {
            Source   = source;
            UnitId   = unitId;
            Format   = format;
            Value    = value;
            Currency = currency;
        }
    }
}