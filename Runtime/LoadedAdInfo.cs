namespace RiseOn.NativeAdMob {
    /// <summary>
    /// What a successful load has to say for itself.<br/>
    /// It arrives with <c>OnAdLoaded</c> so a caller can log or measure the fill without asking the ad anything afterwards - by then the numbers have already moved on, because a load that leaves room in the cache starts the next one immediately.
    /// </summary>
    public readonly struct LoadedAdInfo {
        /// <summary>Warm ads held after this load landed.</summary>
        public int CachedCount { get; }

        /// <summary>How many the placement keeps warm when it is full.
        /// </summary>
        public int CacheSize { get; }

        internal LoadedAdInfo(int cachedCount, int cacheSize) {
            CachedCount = cachedCount;
            CacheSize = cacheSize;
        }
    }
}
