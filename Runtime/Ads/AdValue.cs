namespace RiseOn.NativeAdMob {
    /// <summary>
    /// One paid impression, mirroring AdMob's AdValue with the routing
    /// fields every logger wants next to it.
    /// </summary>
    public readonly struct AdValue {
        /// <summary>Mediation adapter class that filled the impression.</summary>
        public readonly string AdSource;
        public readonly string AdUnitId;
        /// <summary>In currency units, not micros.</summary>
        public readonly double Value;
        public readonly string CurrencyCode;
        public readonly AdValuePrecision Precision;

        internal AdValue(
            string adSource
          , string adUnitId
          , double value
          , string currencyCode
          , AdValuePrecision precision) {
            AdSource     = adSource;
            AdUnitId     = adUnitId;
            Value        = value;
            CurrencyCode = currencyCode;
            Precision    = precision;
        }
    }
}
