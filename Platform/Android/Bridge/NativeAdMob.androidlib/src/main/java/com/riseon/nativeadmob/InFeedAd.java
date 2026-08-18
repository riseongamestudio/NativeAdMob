package com.riseon.nativeadmob;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAd;

// One InFeedAd is one ad unit id for the life of the app. It owns the shared
// supply - the cache of raw loaded ads, the load requests, the no-fill
// backoff - and a fixed array of display slots that consume from it.
// Everything rect-shaped (layout, dwell, rotation, watchdogs) lives in
// InFeedAdSlot.
public final class InFeedAd extends BaseAd {
    static final String TAG = "InFeedAd";
    private static final int LOAD_SUCCESS_CODE = 0;
    private static final int MAX_RETRY_EXPONENT = 5;
    private static final long RETRY_BASE_DELAY_MS = 1_000L;
    static final long NO_RETRY_SCHEDULED_MS = -1L;
    static final long MAX_CACHED_AD_AGE_MS = 3_600_000L;
    private static final int MAX_SLOT_COUNT = 8;
    private static final int MAX_CACHE_SIZE = 5;

    static final class CachedAd {
        final NativeAd ad;
        final long loadedAtMs;

        CachedAd(
                NativeAd ad
              , long loadedAtMs) {
            this.ad = ad;
            this.loadedAtMs = loadedAtMs;
        }
    }

    private final Runnable retryRunnable = this::HandleRetry;
    private final Runnable cacheExpiryRunnable = this::HandleCacheExpiry;

    private final String adUnitId;
    private final int cacheSize;
    private final float backgroundAlpha;
    private final InFeedAdSlot[] slots;
    private Activity activity;
    private final ArrayDeque<CachedAd> cachedAds = new ArrayDeque<>();
    // OwnsAd runs inside the SDK's paid-event callback, off whatever thread the
    // SDK chose, while every collection here is mutated on main. A membership
    // test against a concurrent set stays correct without walking a structure
    // that may be changing underneath it.
    private final Set<NativeAd> ownedAds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile InFeedAdListener listener;
    private boolean isAdLoading;
    private boolean retryScheduled;
    private int noFillStreak;

    public InFeedAd(
            Activity currentActivity
          , String adUnitId
          , int slotCount
          , int cacheSize
          , float backgroundAlpha) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "InFeedAd requires a non-empty adUnitId");
        }
        if (slotCount < 1 || slotCount > MAX_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "slotCount must be within [1, " + MAX_SLOT_COUNT + "]");
        }

        this.adUnitId = adUnitId;
        this.cacheSize = Math.min(
                MAX_CACHE_SIZE
              , cacheSize < 1 ? slotCount + 1 : cacheSize);
        this.backgroundAlpha = ResolveBackgroundAlpha(backgroundAlpha);
        this.activity = currentActivity;
        InFeedAdSlot[] createdSlots = new InFeedAdSlot[slotCount];
        for (int i = 0; i < slotCount; ++i) {
            createdSlots[i] = new InFeedAdSlot(this, i);
        }
        this.slots = createdSlots;
        main.post(() -> {
            if (released) return;
            if (!IsActivityUsable(activity)) {
                Log.e(
                        TAG
                      , "InFeedAd created without a usable Activity; "
                                + "cache loading will start when Configure or "
                                + "Show receives one");
                return;
            }
            StartLoad();
        });
    }

    public void SetListener(InFeedAdListener newListener) {
        if (released) return;
        listener = newListener;
    }

    public void Configure(
            Activity currentActivity
          , int slotIndex
          , int xPx
          , int yPx
          , int widthPx
          , int heightPx) {
        main.post(() -> {
            if (released) {
                Log.e(
                        TAG
                      , "Configure ignored after Release; create a new instance");
                return;
            }
            if (!IsActivityUsable(currentActivity)) {
                Log.e(TAG, "Configure requires a usable Activity");
                return;
            }
            InFeedAdSlot slot = SlotAt(slotIndex, "Configure");
            if (slot == null) return;

            AdoptActivity(currentActivity);
            slot.Configure(xPx, yPx, widthPx, heightPx);
        });
    }

    public void Show(Activity currentActivity, int slotIndex) {
        if (released) return;
        main.post(() -> {
            if (released) return;
            if (!IsActivityUsable(currentActivity)) {
                Log.e(TAG, "Show ignored because Activity is not usable");
                return;
            }
            InFeedAdSlot slot = SlotAt(slotIndex, "Show");
            if (slot == null) return;

            AdoptActivity(currentActivity);
            slot.Show();
        });
    }

    public void Hide(int slotIndex) {
        if (released) return;
        main.post(() -> {
            if (released) return;
            InFeedAdSlot slot = SlotAt(slotIndex, "Hide");
            if (slot != null) slot.Hide();
        });
    }

    public void SetPosition(int slotIndex, int xPx, int yPx) {
        if (released) return;
        main.post(() -> {
            if (released) return;
            InFeedAdSlot slot = SlotAt(slotIndex, "SetPosition");
            if (slot != null) slot.SetPosition(xPx, yPx);
        });
    }

    public void Release() {
        if (released) return;
        released = true;
        InvalidateLoadGeneration();
        main.removeCallbacksAndMessages(null);
        main.post(() -> {
            isAdLoading = false;
            retryScheduled = false;
            for (InFeedAdSlot slot : slots) slot.ReleaseOnMain();
            DestroyCachedAds();
            activity = null;
            listener = null;
        });
    }

    private InFeedAdSlot SlotAt(int slotIndex, String operation) {
        if (slotIndex >= 0 && slotIndex < slots.length) {
            return slots[slotIndex];
        }
        Log.e(
                TAG
              , operation + " ignored: slot " + slotIndex
                        + " is outside [0, " + (slots.length - 1) + "]");
        return null;
    }

    // Every call hands over the current Unity activity; a change means the old
    // windows are gone, so every slot starts over on the new one.
    private void AdoptActivity(Activity currentActivity) {
        if (activity != null && activity != currentActivity) {
            for (InFeedAdSlot slot : slots) slot.HandleActivityChanged();
        }
        activity = currentActivity;
    }

    boolean StartLoad() {
        if (released
                || isAdLoading
                || retryScheduled
                || !ShouldStartLoad()
                || !IsActivityUsable(activity)) {
            return false;
        }

        isAdLoading = true;
        final int generation = NextLoadGeneration();
        final String requestedAdUnitId = adUnitId;
        NotifyLoadingStarted();

        try {
            NativeAdOptions nativeAdOptions =
                    CreateNativeAdOptions(true);

            AdLoader adLoader =
                    new AdLoader.Builder(activity, requestedAdUnitId)
                    .forNativeAd(ad -> HandleLoadedAd(
                            generation
                          , requestedAdUnitId
                          , ad))
                    .withNativeAdOptions(nativeAdOptions)
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            if (!IsCurrentLoad(generation)) return;

                            isAdLoading = false;
                            long retryDelayMs = ScheduleNoFillRetry();
                            NotifyLoadingCompleted(
                                    error.getCode()
                                  , BuildFailureMessage(
                                        error.getMessage()
                                      , retryDelayMs));
                        }

                        @Override
                        public void onAdClicked() {
                            // Not gated on the load generation: the ad that was
                            // clicked may well have come from an earlier load.
                            CommitAdClick();
                        }

                        @Override
                        public void onAdLoaded() {
                            if (!IsCurrentLoad(generation)) return;

                            isAdLoading = false;
                            NotifyLoadingCompleted(LOAD_SUCCESS_CODE, "");
                            OfferCacheToSlots();
                            StartLoad();
                        }
                    })
                    .build();

            adLoader.loadAd(new AdRequest.Builder().build());
            return true;
        } catch (RuntimeException exception) {
            if (!IsCurrentLoad(generation)) return false;

            isAdLoading = false;
            Log.e(TAG, "Native in-feed load failed before callback", exception);
            long retryDelayMs = ScheduleNoFillRetry();
            NotifyLoadingCompleted(
                    INTERNAL_LOAD_ERROR
                  , BuildFailureMessage(
                        "Failed to load ad: "
                                + String.valueOf(exception.getMessage())
                      , retryDelayMs));
            return false;
        }
    }

    // From a slot that has nothing to materialize and found the cache empty:
    // start a load, or if one is already in flight or backed off, leave the
    // existing schedule in charge.
    void RequestLoad() {
        if (released) return;
        if (StartLoad()) return;
        if (!isAdLoading && !retryScheduled && ShouldStartLoad()) {
            PostRetry(BackoffDelayMs(Math.max(1, noFillStreak), 0));
        }
    }

    private void HandleLoadedAd(
            int generation
          , String requestedAdUnitId
          , NativeAd ad) {
        if (!IsCurrentLoad(generation)) {
            DestroyAd(ad);
            return;
        }

        noFillStreak = 0;
        ownedAds.add(ad);
        cachedAds.addLast(
                new CachedAd(ad, SystemClock.elapsedRealtime()));
        ScheduleCacheExpiry();
        BindPaidEvent(
                ad
              , requestedAdUnitId
              , () -> OwnsAd(ad));
    }

    private void OfferCacheToSlots() {
        for (InFeedAdSlot slot : slots) {
            if (cachedAds.isEmpty()) return;
            if (slot.WantsCachedAd()) slot.PresentCachedAd();
        }
    }

    boolean HasCachedAd() {
        RemoveExpiredCachedAds();
        return !cachedAds.isEmpty();
    }

    int CachedCount() {
        return cachedAds.size();
    }

    // Pops for one slot and immediately starts replacing what it took.
    CachedAd TakeCachedAd() {
        RemoveExpiredCachedAds();
        CachedAd next = cachedAds.pollFirst();
        StartLoad();
        return next;
    }

    private void HandleRetry() {
        retryScheduled = false;
        if (released) return;

        OfferCacheToSlots();
        if (StartLoad()) return;
        if (!isAdLoading && ShouldStartLoad() && !retryScheduled) {
            // Nothing failed here, the state simply was not ready, so re-arm at
            // the current delay rather than counting another failure.
            PostRetry(BackoffDelayMs(Math.max(1, noFillStreak), 0));
        }
    }

    // Keep the cache topped up regardless of what is on screen. The slots
    // decide when a warm ad is spent; this only decides when to fetch another.
    private boolean ShouldStartLoad() {
        return cachedAds.size() < cacheSize;
    }

    // No fill is a property of inventory: waiting is the only thing that can
    // help, so back off.
    private long ScheduleNoFillRetry() {
        CancelRetry();
        if (released) return NO_RETRY_SCHEDULED_MS;

        ++noFillStreak;
        return PostRetry(BackoffDelayMs(noFillStreak, 0));
    }

    private long PostRetry(long delayMs) {
        CancelRetry();
        if (released) return NO_RETRY_SCHEDULED_MS;

        retryScheduled = true;
        main.postDelayed(retryRunnable, Math.max(0L, delayMs));
        return delayMs;
    }

    static long BackoffDelayMs(int streak, int immediateAttempts) {
        if (streak <= immediateAttempts) return 0L;

        int exponent = Math.min(
                MAX_RETRY_EXPONENT
              , streak - immediateAttempts);
        return RETRY_BASE_DELAY_MS * (1L << exponent);
    }

    private void CancelRetry() {
        main.removeCallbacks(retryRunnable);
        retryScheduled = false;
    }

    private void HandleCacheExpiry() {
        if (released) return;

        RemoveExpiredCachedAds();
        OfferCacheToSlots();
        StartLoad();
        ScheduleCacheExpiry();
    }

    private void ScheduleCacheExpiry() {
        main.removeCallbacks(cacheExpiryRunnable);
        if (released) return;

        long nextExpiryAtMs = Long.MAX_VALUE;
        for (CachedAd cached : cachedAds) {
            nextExpiryAtMs = Math.min(
                    nextExpiryAtMs
                  , cached.loadedAtMs + MAX_CACHED_AD_AGE_MS);
        }
        if (nextExpiryAtMs == Long.MAX_VALUE) return;

        main.postDelayed(
                cacheExpiryRunnable
              , Math.max(0L, nextExpiryAtMs - SystemClock.elapsedRealtime()));
    }

    private void RemoveExpiredCachedAds() {
        long nowMs = SystemClock.elapsedRealtime();
        for (Iterator<CachedAd> it = cachedAds.iterator(); it.hasNext(); ) {
            CachedAd cached = it.next();
            if (nowMs - cached.loadedAtMs < MAX_CACHED_AD_AGE_MS) continue;

            DestroyAd(cached.ad);
            it.remove();
        }
    }

    private void DestroyCachedAds() {
        for (CachedAd cached : cachedAds) DestroyAd(cached.ad);
        cachedAds.clear();
    }

    void DestroyAd(
            NativeAd ad) {
        if (ad == null) return;

        ownedAds.remove(ad);
        ad.destroy();
    }

    private boolean IsCurrentLoad(int generation) {
        return IsCurrentLoadGeneration(generation);
    }

    private boolean OwnsAd(
            NativeAd ad) {
        return ownedAds.contains(ad);
    }

    private void CommitAdClick() {
        for (InFeedAdSlot slot : slots) slot.CommitAdClick();
    }

    Activity CurrentActivity() {
        return activity;
    }

    float BackgroundAlpha() {
        return backgroundAlpha;
    }

    String AdUnitId() {
        return adUnitId;
    }

    int NoFillStreak() {
        return noFillStreak;
    }

    private static float ResolveBackgroundAlpha(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            Log.w(TAG, "backgroundAlpha is not finite; using 1");
            return 1f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private String BuildFailureMessage(
            String reason
          , long retryDelayMs) {
        String retryDescription = retryDelayMs == NO_RETRY_SCHEDULED_MS
                ? "not scheduled"
                : retryDelayMs + "ms";
        return String.valueOf(reason)
                + " | adUnitId=" + String.valueOf(adUnitId)
                + " | cached=" + cachedAds.size()
                + " | noFill=" + noFillStreak
                + " | retry=" + retryDescription;
    }

    private void NotifyLoadingStarted() {
        InFeedAdListener current = listener;
        if (current == null) return;
        try {
            current.OnLoadingStarted();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnLoadingStarted callback failed", exception);
        }
    }

    private void NotifyLoadingCompleted(
            int errorCode
          , String errorMessage) {
        InFeedAdListener current = listener;
        if (current == null) return;
        try {
            current.OnLoadingCompleted(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnLoadingCompleted callback failed", exception);
        }
    }

    @Override
    protected void NotifyAdPaid(
            String source
          , String paidAdUnitId
          , double value
          , String currencyCode
          , int precision) {
        InFeedAdListener current = listener;
        if (current == null) return;
        try {
            current.OnAdPaid(
                    source
                  , paidAdUnitId
                  , value
                  , currencyCode
                  , precision);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnAdPaid callback failed", exception);
        }
    }

    void NotifySlotDisplayed(int slotIndex) {
        InFeedAdListener current = listener;
        if (current == null) return;
        try {
            current.OnSlotDisplayed(slotIndex);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnSlotDisplayed callback failed", exception);
        }
    }

    void NotifySlotShowNotReady(int slotIndex) {
        InFeedAdListener current = listener;
        if (current == null) return;
        try {
            current.OnSlotShowNotReady(slotIndex);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnSlotShowNotReady callback failed", exception);
        }
    }

    void NotifySlotPresentationFailed(
            int slotIndex
          , int errorCode
          , String errorMessage) {
        InFeedAdListener current = listener;
        if (current == null) return;
        try {
            current.OnSlotPresentationFailed(
                    slotIndex
                  , errorCode
                  , errorMessage);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnSlotPresentationFailed callback failed", exception);
        }
    }
}
