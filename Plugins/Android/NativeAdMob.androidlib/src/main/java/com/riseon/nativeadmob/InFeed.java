package com.riseon.nativeadmob;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;

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

public final class InFeed extends Ad {
    private static final String TAG = "NativeAdMobInFeed";
    private static final int LOAD_SUCCESS_CODE = 0;
    private static final int MAX_RETRY_EXPONENT = 5;
    private static final long RETRY_BASE_DELAY_MS = 1_000L;
    private static final long NO_RETRY_SCHEDULED_MS = -1L;
    private static final int RETRY_IMMEDIATE_LAYOUT_ATTEMPTS = 2;
    private static final int MAX_UNPROVEN_LAYOUT_FAILURES = 20;
    private static final long UNPROVEN_LAYOUT_RETRY_DELAY_MS = 300_000L;
    private static final int IN_FEED_AD_BUFFER_MAX = 2;
    // Rotation follows appearances rather than a clock: a slot that keeps being
    // shown and hidden earns a fresh ad every time it comes back, and the dwell
    // interval only covers a slot that never goes away.
    private static final long DWELL_REFRESH_INTERVAL_MS = 30_000L;
    private static final long MIN_DWELL_MS = 1_000L;
    private static final long MIN_SWAP_INTERVAL_MS = 3_000L;
    private static final long MAX_CACHED_AD_AGE_MS = 3_600_000L;
    private static final long NO_VISIBLE_TIMER = -1L;
    private static final long SLOT_WATCHDOG_INTERVAL_MS = 1_000L;
    private static final long FOREGROUND_RECHECK_DELAY_MS = 1_000L;
    private static final long ACTIVE_SLOT_VISIBILITY_TIMEOUT_MS = 3_000L;
    // Last-resort net only. Walking down the layout candidates costs up to
    // MAX_FINAL_LAYOUT_OBSERVATION_MS each, so a creative that needs many
    // attempts is legitimately slow; the presentation bounds its own waits and
    // ends as a dismissal, which is the path that normally clears a slot.
    private static final long MATERIALIZING_SLOT_TIMEOUT_MS = 20_000L;

    private static final class InFeedStyle {
        final int xPx;
        final int yPx;
        final int widthPx;
        final int heightPx;
        final float backgroundAlpha;

        InFeedStyle(
                int xPx
              , int yPx
              , int widthPx
              , int heightPx
              , float backgroundAlpha) {
            this.xPx = xPx;
            this.yPx = yPx;
            this.widthPx = Math.max(1, widthPx);
            this.heightPx = Math.max(1, heightPx);
            this.backgroundAlpha = backgroundAlpha;
        }

        InFeedStyle WithPosition(int newXPx, int newYPx) {
            return new InFeedStyle(
                    newXPx
                  , newYPx
                  , widthPx
                  , heightPx
                  , backgroundAlpha);
        }
    }

    private static final class CachedAd {
        final com.google.android.gms.ads.nativead.NativeAd ad;
        final long loadedAtMs;

        CachedAd(
                com.google.android.gms.ads.nativead.NativeAd ad
              , long loadedAtMs) {
            this.ad = ad;
            this.loadedAtMs = loadedAtMs;
        }
    }

    private static final class DisplaySlot {
        final com.google.android.gms.ads.nativead.NativeAd ad;
        final InFeedPresentation presentation;
        final long loadedAtMs;
        long accumulatedVisibleMs;
        long visibleStartedAtMs = NO_VISIBLE_TIMER;
        long visibleWaitStartedAtMs = NO_VISIBLE_TIMER;
        long displayedAtMs = NO_VISIBLE_TIMER;
        boolean ready;
        boolean actuallyVisible;

        DisplaySlot(
                com.google.android.gms.ads.nativead.NativeAd ad
              , InFeedPresentation presentation
              , long loadedAtMs) {
            this.ad = ad;
            this.presentation = presentation;
            this.loadedAtMs = loadedAtMs;
        }
    }

    private final Runnable refreshRunnable = this::HandleRefresh;
    private final Runnable retryRunnable = this::HandleRetry;
    private final Runnable hiddenExpiryRunnable = this::HandleHiddenExpiry;
    private final Runnable slotWatchdogRunnable = this::HandleSlotWatchdog;
    private final Runnable foregroundRecheckRunnable =
            this::HandleForegroundRecheck;
    private final Runnable hiddenSwapRunnable = this::HandleHiddenSwap;
    // Posting from inside a frame callback lands the runnable after that
    // frame's traversal and draw, which is the earliest point where the hide
    // is actually on screen.
    private final Choreographer.FrameCallback hiddenSwapFrameCallback =
            frameTimeNanos -> main.post(hiddenSwapRunnable);

    private final String adUnitId;
    private InFeedStyle style;
    private Activity activity;
    private DisplaySlot activeSlot;
    private DisplaySlot materializingSlot;
    private final ArrayDeque<CachedAd> cachedAds = new ArrayDeque<>();
    // OwnsAd runs inside the SDK's paid-event callback, off whatever thread the
    // SDK chose, while every collection here is mutated on main. A membership
    // test against a concurrent set stays correct without walking a structure
    // that may be changing underneath it.
    private final Set<com.google.android.gms.ads.nativead.NativeAd> ownedAds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private long lastSwapAtMs = NO_VISIBLE_TIMER;
    private boolean configured;
    private boolean visibleRequested;
    private boolean isAdLoading;
    private boolean retryScheduled;
    private int noFillStreak;
    private int layoutFailStreak;
    private boolean layoutProven;

    public InFeed(
            Activity currentActivity
          , String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "InFeed requires a non-empty adUnitId");
        }

        this.adUnitId = adUnitId;
        this.activity = currentActivity;
        main.post(() -> {
            if (released) return;
            if (!IsActivityUsable(activity)) {
                Log.e(
                        TAG
                      , "InFeed created without a usable Activity; "
                                + "cache loading will start when Configure or "
                                + "Show receives one");
                return;
            }
            StartLoad();
        });
    }

    public void Configure(
            Activity currentActivity
          , int xPx
          , int yPx
          , int widthPx
          , int heightPx
          , float backgroundAlpha) {
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

            InFeedStyle newStyle = new InFeedStyle(
                    xPx
                  , yPx
                  , widthPx
                  , heightPx
                  , ResolveBackgroundAlpha(backgroundAlpha));
            boolean activityChanged = activity != null
                    && activity != currentActivity;
            boolean styleChanged = configured
                    && !HasSameStyle(style, newStyle);

            visibleRequested = false;
            main.removeCallbacks(refreshRunnable);
            PauseVisibleTimer(activeSlot);
            if (activeSlot != null) {
                activeSlot.presentation.SetVisible(false);
            }
            if (materializingSlot != null) {
                materializingSlot.presentation.SetVisible(false);
            }

            if (styleChanged) {
                // A different rect has to prove itself again.
                layoutFailStreak = 0;
                layoutProven = false;
            }
            if (activityChanged || styleChanged) {
                DestroySlot(materializingSlot);
                materializingSlot = null;
                DestroySlot(activeSlot);
                activeSlot = null;
            }

            activity = currentActivity;
            style = newStyle;
            configured = true;
            RemoveExpiredCachedAd();
            RemoveExpiredMaterializingSlot();
            RemoveExpiredActiveSlot();
            if (ShouldMaterializeCachedAd()) {
                PresentCachedAd();
            } else {
                StartLoad();
            }
            ScheduleHiddenExpiry();
        });
    }

    public void Show(Activity currentActivity) {
        if (released) return;
        main.post(() -> ShowInternal(currentActivity));
    }

    public void Hide() {
        if (released) return;
        main.post(this::HideInternal);
    }

    public void SetPosition(int xPx, int yPx) {
        if (released) return;
        main.post(() -> {
            if (released || !configured || style == null) {
                Log.e(
                        TAG
                      , "SetPosition ignored before Configure or after Release");
                return;
            }

            style = style.WithPosition(xPx, yPx);
            SetSlotPosition(materializingSlot, xPx, yPx);
            SetSlotPosition(activeSlot, xPx, yPx);
        });
    }

    public void Release() {
        if (released) return;
        released = true;
        InvalidateLoadGeneration();
        main.removeCallbacksAndMessages(null);
        main.post(() -> {
            // On main because Choreographer is per-thread; the callback was
            // posted from HideInternal on this same thread.
            Choreographer.getInstance()
                    .removeFrameCallback(hiddenSwapFrameCallback);
            isAdLoading = false;
            retryScheduled = false;
            visibleRequested = false;
            PauseVisibleTimer(activeSlot);
            DestroySlot(materializingSlot);
            materializingSlot = null;
            DestroySlot(activeSlot);
            activeSlot = null;
            DestroyCachedAd();
            activity = null;
            style = null;
            ClearLoadListener();
        });
    }

    private void ShowInternal(Activity currentActivity) {
        ShowInternalCore(currentActivity);
        ScheduleSlotWatchdog();
    }

    private String DescribeSlot(DisplaySlot slot) {
        if (slot == null) return "none";

        return "{ready=" + slot.ready
                + ",visible=" + slot.actuallyVisible
                + ",displayed=" + (slot.displayedAtMs != NO_VISIBLE_TIMER)
                + "}";
    }

    private void ShowInternalCore(Activity currentActivity) {
        if (released || !configured) {
            Log.e(TAG, "Show ignored before Configure or after Release");
            return;
        }
        if (!IsActivityUsable(currentActivity)) {
            Log.e(TAG, "Show ignored because Activity is not usable");
            return;
        }

        Log.i(
                TAG
              , "Show: active=" + DescribeSlot(activeSlot)
                        + " materializing=" + DescribeSlot(materializingSlot)
                        + " cached=" + cachedAds.size());
        visibleRequested = true;
        main.removeCallbacks(hiddenExpiryRunnable);

        if (activity != null && activity != currentActivity) {
            PauseVisibleTimer(activeSlot);
            DestroySlot(materializingSlot);
            materializingSlot = null;
            DestroySlot(activeSlot);
            activeSlot = null;
        }
        activity = currentActivity;

        RemoveExpiredCachedAd();
        RemoveExpiredMaterializingSlot();
        RemoveExpiredActiveSlot();
        if (activeSlot != null) {
            activeSlot.presentation.RequestDisplayNotification();
        }
        if (materializingSlot != null) {
            materializingSlot.presentation.RequestDisplayNotification();
        }
        if (!IsReadyForShow()) NotifyShowNotReady();
        if (materializingSlot != null) {
            if (materializingSlot.ready) {
                // Already laid out, so open straight onto it. Showing the
                // outgoing ad first is what made one appear and get replaced a
                // moment later.
                materializingSlot.presentation.SetVisible(true);
            } else {
                ShowActiveSlot();
                if (activeSlot == null) {
                    materializingSlot.presentation.SetVisible(true);
                }
            }
            return;
        }
        if (activeSlot != null) {
            if (ShowActiveSlot()) ScheduleRefresh();
            return;
        }
        if (!cachedAds.isEmpty()) {
            PresentCachedAd();
            return;
        }

        StartLoad();
    }

    private boolean IsReadyForShow() {
        return activeSlot != null
                || materializingSlot != null && materializingSlot.ready;
    }

    private void HideInternal() {
        if (released) return;

        Log.i(
                TAG
              , "Hide: active=" + DescribeSlot(activeSlot)
                        + " materializing=" + DescribeSlot(materializingSlot)
                        + " cached=" + cachedAds.size());
        visibleRequested = false;
        main.removeCallbacks(refreshRunnable);
        PauseVisibleTimer(activeSlot);
        if (activeSlot != null) {
            activeSlot.presentation.SetVisible(false);
        }
        if (materializingSlot != null) {
            materializingSlot.presentation.SetVisible(false);
        }
        // Rotate during the hidden stretch rather than on the way back in: the
        // replacement gets that whole stretch to load and lay itself out, and
        // the next appearance opens on it instead of on the ad being replaced.
        // But not in this message: laying the replacement out occupies the
        // main thread for long enough that the hide ordered above would sit
        // undrawn behind it, still on screen. Let a frame commit the hide
        // first, then rotate.
        Choreographer choreographer = Choreographer.getInstance();
        choreographer.removeFrameCallback(hiddenSwapFrameCallback);
        choreographer.postFrameCallback(hiddenSwapFrameCallback);
        if (!ShouldRetry()) CancelRetry();
        ClearVisibilityWait(activeSlot);
        ClearVisibilityWait(materializingSlot);
        CancelSlotWatchdog();
        ScheduleHiddenExpiry();
    }

    private boolean StartLoad() {
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
                            if (activeSlot == null
                                    && ShouldMaterializeCachedAd()) {
                                PresentCachedAd();
                            }
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

    private void HandleLoadedAd(
            int generation
          , String requestedAdUnitId
          , com.google.android.gms.ads.nativead.NativeAd ad) {
        if (!IsCurrentLoad(generation)) {
            DestroyAd(ad);
            return;
        }

        noFillStreak = 0;
        ownedAds.add(ad);
        cachedAds.addLast(
                new CachedAd(ad, SystemClock.elapsedRealtime()));
        ScheduleHiddenExpiry();
        BindPaidEvent(
                ad
              , requestedAdUnitId
              , () -> OwnsAd(ad));
    }

    private void PresentCachedAd() {
        if (released
                || !configured
                || materializingSlot != null
                || !ShouldMaterializeCachedAd()
                || !IsActivityUsable(activity)) {
            return;
        }
        if (!IsActivityVisible(activity)) {
            // The geometry observation loop only advances on draw passes, so
            // presenting into a window that is not drawing parks the slot and
            // blocks every later load. Keep the ad cached and wait instead.
            ScheduleForegroundRecheck();
            return;
        }

        RemoveExpiredCachedAd();
        CachedAd next = cachedAds.pollFirst();
        if (next == null) {
            StartLoad();
            return;
        }

        final com.google.android.gms.ads.nativead.NativeAd ad = next.ad;
        final long loadedAtMs = next.loadedAtMs;
        StartLoad();

        final DisplaySlot[] holder = new DisplaySlot[1];
        InFeedPresentation presentation =
                new InFeedPresentation(
                        activity
                      , ad
                      , style.xPx
                      , style.yPx
                      , style.widthPx
                      , style.heightPx
                      , style.backgroundAlpha
                      , new AdPresentation.Listener() {
                            @Override
                            public void OnReady() {
                                HandlePresentationReady(holder[0]);
                            }

                            @Override
                            public void OnDisplayed() {
                                HandlePresentationDisplayed(holder[0]);
                            }

                            @Override
                            public void OnDismissed() {
                                HandlePresentationDismissed(holder[0]);
                            }

                            @Override
                            public void OnActualVisibilityChanged(
                                    boolean isActuallyVisible) {
                                HandleActualVisibilityChanged(
                                        holder[0]
                                      , isActuallyVisible);
                            }
                        });
        DisplaySlot slot = new DisplaySlot(ad, presentation, loadedAtMs);
        holder[0] = slot;
        materializingSlot = slot;

        presentation.SetVisible(
                visibleRequested && activeSlot == null);
        if (!presentation.Show()) {
            String failureMessage = presentation.GetFailureMessage();
            materializingSlot = null;
            DestroySlot(slot);
            ShowActiveSlot();
            long retryDelayMs = ScheduleLayoutRetry();
            NotifyPresentationFailed(
                    INTERNAL_PRESENTATION_ERROR
                  , BuildFailureMessage(
                        failureMessage == null
                                ? "In-feed presentation failed before display"
                                : failureMessage
                      , retryDelayMs));
        }
        ScheduleSlotWatchdog();
    }

    private void HandlePresentationReady(DisplaySlot slot) {
        if (released || slot == null || materializingSlot != slot) return;

        slot.ready = true;
        if (visibleRequested) slot.presentation.SetVisible(true);
    }

    private void HandlePresentationDisplayed(DisplaySlot slot) {
        if (released
                || slot == null
                || materializingSlot != slot && activeSlot != slot) {
            return;
        }
        if (!visibleRequested) {
            slot.presentation.SetVisible(false);
            return;
        }

        if (materializingSlot == slot) {
            DisplaySlot previous = activeSlot;
            activeSlot = slot;
            materializingSlot = null;
            layoutFailStreak = 0;
            layoutProven = true;
            lastSwapAtMs = SystemClock.elapsedRealtime();
            DestroySlot(previous);
        }
        if (slot.displayedAtMs == NO_VISIBLE_TIMER) {
            slot.displayedAtMs = SystemClock.elapsedRealtime();
        }

        if (slot.actuallyVisible) {
            ResumeVisibleTimer(slot);
        } else {
            PauseVisibleTimer(slot);
        }
        ScheduleSlotWatchdog();
        NotifyDisplayed();
        ScheduleRefresh();
    }

    private void HandleActualVisibilityChanged(
            DisplaySlot slot
          , boolean isActuallyVisible) {
        if (slot == null) return;

        slot.actuallyVisible = isActuallyVisible;
        MarkVisibilityWait(slot);
        if (slot != activeSlot) return;

        if (visibleRequested && isActuallyVisible) {
            ResumeVisibleTimer(slot);
            ScheduleRefresh();
        } else {
            main.removeCallbacks(refreshRunnable);
            PauseVisibleTimer(slot);
        }
        ScheduleSlotWatchdog();
    }

    private void HandlePresentationDismissed(DisplaySlot slot) {
        if (slot == null) return;

        String failureMessage = slot.presentation.GetFailureMessage();
        if (materializingSlot == slot) materializingSlot = null;
        if (activeSlot == slot) {
            PauseVisibleTimer(slot);
            activeSlot = null;
        }
        DestroyAd(slot.ad);

        if (released) return;
        ShowActiveSlot();
        long retryDelayMs = ScheduleLayoutRetry();
        ScheduleSlotWatchdog();
        if (failureMessage != null) {
            NotifyPresentationFailed(
                    INTERNAL_PRESENTATION_ERROR
                  , BuildFailureMessage(failureMessage, retryDelayMs));
        }
    }

    private void HandleRefresh() {
        if (released || !IsActivityUsable(activity)) return;
        if (!visibleRequested
                || activeSlot == null
                || !activeSlot.actuallyVisible) {
            PauseVisibleTimer(activeSlot);
            return;
        }

        ResumeVisibleTimer(activeSlot);
        long remainingMs = DWELL_REFRESH_INTERVAL_MS
                - CurrentVisibleDurationMs(activeSlot);
        if (remainingMs > 0L) {
            main.postDelayed(refreshRunnable, remainingMs);
            return;
        }

        TrySwapActiveSlot();
        // Start the next dwell window whether or not a swap happened. Without
        // this the accumulated time stays past the interval, ScheduleRefresh
        // keeps computing a zero delay, and the trigger re-arms every frame -
        // which turns MIN_SWAP_INTERVAL_MS into the rotation period.
        RestartVisibleAccumulation(activeSlot);
        ScheduleRefresh();
    }

    private void RestartVisibleAccumulation(DisplaySlot slot) {
        if (slot == null) return;

        slot.accumulatedVisibleMs = 0L;
        slot.visibleStartedAtMs = visibleRequested && slot.actuallyVisible
                ? SystemClock.elapsedRealtime()
                : NO_VISIBLE_TIMER;
    }

    // Rotate to a warm ad once the one on screen has had its turn: it has to
    // have been displayed and stayed up long enough to be worth an impression,
    // and swaps are spaced so a burst of show and hide cannot drain the buffer.
    private void TrySwapActiveSlot() {
        if (released
                || materializingSlot != null
                || activeSlot == null
                || cachedAds.isEmpty()
                || !IsActiveSlotConsumed()) {
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        if (lastSwapAtMs != NO_VISIBLE_TIMER
                && nowMs - lastSwapAtMs < MIN_SWAP_INTERVAL_MS) {
            return;
        }

        PresentCachedAd();
    }

    private boolean IsActiveSlotConsumed() {
        return activeSlot != null
                && activeSlot.displayedAtMs != NO_VISIBLE_TIMER
                && CurrentVisibleDurationMs(activeSlot) >= MIN_DWELL_MS;
    }

    private void HandleHiddenSwap() {
        if (released || visibleRequested) return;
        TrySwapActiveSlot();
    }

    private void HandleRetry() {
        retryScheduled = false;
        if (released || !ShouldRetry()) return;
        if (ShouldMaterializeCachedAd()) {
            PresentCachedAd();
            return;
        }

        boolean started = StartLoad();
        if (!started && ShouldRetry() && !retryScheduled) {
            // Nothing failed here, the state simply was not ready, so re-arm at
            // the current delay rather than counting another failure.
            PostRetry(
                    BackoffDelayMs(
                            Math.max(
                                    1
                                  , Math.max(noFillStreak, layoutFailStreak))
                          , 0));
        }
    }

    private void HandleHiddenExpiry() {
        if (released || visibleRequested) return;

        RemoveExpiredCachedAd();
        RemoveExpiredMaterializingSlot();
        RemoveExpiredActiveSlot();
        if (ShouldMaterializeCachedAd()) {
            PresentCachedAd();
        } else {
            StartLoad();
        }
        ScheduleHiddenExpiry();
    }

    private void ScheduleRefresh() {
        main.removeCallbacks(refreshRunnable);
        if (released
                || !visibleRequested
                || activeSlot == null
                || !activeSlot.actuallyVisible
                || !IsActivityUsable(activity)) {
            return;
        }

        long remainingMs = Math.max(
                0L
              , DWELL_REFRESH_INTERVAL_MS
                        - CurrentVisibleDurationMs(activeSlot));
        main.postDelayed(refreshRunnable, remainingMs);
    }

    // No fill is a property of inventory: waiting is the only thing that can
    // help, so back off.
    private long ScheduleNoFillRetry() {
        CancelRetry();
        if (released || !ShouldRetry()) return NO_RETRY_SCHEDULED_MS;

        ++noFillStreak;
        return PostRetry(BackoffDelayMs(noFillStreak, 0));
    }

    // A layout failure is a property of this creative, not of inventory. Waiting
    // cannot make it fit, so the first attempts fetch a replacement straight
    // away and only a long run of failures is treated as the rect itself being
    // unusable - and never once some creative has already rendered in it.
    private long ScheduleLayoutRetry() {
        CancelRetry();
        if (released || !ShouldRetry()) return NO_RETRY_SCHEDULED_MS;

        ++layoutFailStreak;
        if (!layoutProven
                && layoutFailStreak >= MAX_UNPROVEN_LAYOUT_FAILURES) {
            Log.e(
                    TAG
                  , layoutFailStreak
                            + " creatives in a row failed to lay out in "
                            + DescribeRequestedRect()
                            + " and none has ever rendered there; falling back"
                            + " to a slow poll");
            return PostRetry(UNPROVEN_LAYOUT_RETRY_DELAY_MS);
        }
        return PostRetry(
                BackoffDelayMs(
                        layoutFailStreak
                      , RETRY_IMMEDIATE_LAYOUT_ATTEMPTS));
    }

    private long PostRetry(long delayMs) {
        CancelRetry();
        if (released || !ShouldRetry()) return NO_RETRY_SCHEDULED_MS;

        retryScheduled = true;
        main.postDelayed(retryRunnable, Math.max(0L, delayMs));
        return delayMs;
    }

    private static long BackoffDelayMs(int streak, int immediateAttempts) {
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

    // Only armed while something is actually waiting to be seen. A
    // materializing slot with no pending Show is just a warm ad and must not
    // be dropped, or every prefetch made while the feed is hidden is thrown
    // away and re-requested.
    private void ScheduleSlotWatchdog() {
        main.removeCallbacks(slotWatchdogRunnable);
        if (released || !visibleRequested) return;
        if (!IsActivityVisible(activity)) {
            ClearVisibilityWait(activeSlot);
            ClearVisibilityWait(materializingSlot);
            ScheduleForegroundRecheck();
            return;
        }

        MarkVisibilityWait(activeSlot);
        MarkVisibilityWait(materializingSlot);
        if (materializingSlot == null && activeSlot == null) return;

        main.postDelayed(slotWatchdogRunnable, SLOT_WATCHDOG_INTERVAL_MS);
    }

    private void CancelSlotWatchdog() {
        main.removeCallbacks(slotWatchdogRunnable);
    }

    // A backgrounded Activity is still "usable", but its windows do not draw.
    // Everything that depends on drawing - presenting a slot, expecting a slot
    // to become visible - has to wait for the foreground instead of being
    // treated as a failure.
    private static boolean IsActivityVisible(Activity currentActivity) {
        return IsActivityUsable(currentActivity)
                && currentActivity.hasWindowFocus();
    }

    private void ScheduleForegroundRecheck() {
        main.removeCallbacks(foregroundRecheckRunnable);
        if (released
                || IsActivityVisible(activity)
                || !IsActivityUsable(activity)) {
            return;
        }
        if (!visibleRequested && !ShouldMaterializeCachedAd()) return;

        main.postDelayed(
                foregroundRecheckRunnable
              , FOREGROUND_RECHECK_DELAY_MS);
    }

    private void HandleForegroundRecheck() {
        if (released) return;
        if (!IsActivityVisible(activity)) {
            ScheduleForegroundRecheck();
            return;
        }

        if (ShouldMaterializeCachedAd()) PresentCachedAd();
        ShowActiveSlot();
        ScheduleSlotWatchdog();
        ScheduleRefresh();
    }

    // Without this both slots can park forever. A materializing slot blocks
    // every swap until it is promoted or dismissed, and an active slot that
    // never reports actual visibility never accumulates the dwell that both
    // rotation triggers need, so it is never replaced. Either state would
    // otherwise only clear after MAX_CACHED_AD_AGE_MS or an app restart.
    private void HandleSlotWatchdog() {
        if (released || !IsActivityUsable(activity)) return;
        if (!IsActivityVisible(activity)) {
            ScheduleSlotWatchdog();
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();
        if (materializingSlot != null) {
            if (materializingSlot.visibleWaitStartedAtMs == NO_VISIBLE_TIMER
                    || nowMs - materializingSlot.visibleWaitStartedAtMs
                            < MATERIALIZING_SLOT_TIMEOUT_MS) {
                ScheduleSlotWatchdog();
                return;
            }

            Log.e(
                    TAG
                  , "In-feed materializing slot was never displayed within "
                            + MATERIALIZING_SLOT_TIMEOUT_MS
                            + "ms; dropping it and reloading");
            DisplaySlot stuck = materializingSlot;
            materializingSlot = null;
            DestroySlot(stuck);
            ShowActiveSlot();
            RequestReplacementAd();
            ScheduleSlotWatchdog();
            return;
        }

        if (visibleRequested
                && activeSlot != null
                && !activeSlot.actuallyVisible
                && activeSlot.visibleWaitStartedAtMs != NO_VISIBLE_TIMER
                && nowMs - activeSlot.visibleWaitStartedAtMs
                        >= ACTIVE_SLOT_VISIBILITY_TIMEOUT_MS) {
            Log.e(
                    TAG
                  , "In-feed active slot never became visible within "
                            + ACTIVE_SLOT_VISIBILITY_TIMEOUT_MS
                            + "ms; dropping it and reloading");
            DisplaySlot stuck = activeSlot;
            PauseVisibleTimer(stuck);
            activeSlot = null;
            DestroySlot(stuck);
            RequestReplacementAd();
        }

        ScheduleSlotWatchdog();
    }

    private void CommitAdClick() {
        if (activeSlot != null) activeSlot.presentation.CommitAdClick();
        if (materializingSlot != null) {
            materializingSlot.presentation.CommitAdClick();
        }
    }

    private void RequestReplacementAd() {
        if (ShouldMaterializeCachedAd()) {
            PresentCachedAd();
            return;
        }
        // Go through the retry backoff instead of loading straight away so a
        // slot that keeps failing to display cannot churn ad requests.
        ScheduleLayoutRetry();
    }

    private void MarkVisibilityWait(DisplaySlot slot) {
        if (slot == null) return;
        if (!visibleRequested || slot.actuallyVisible) {
            slot.visibleWaitStartedAtMs = NO_VISIBLE_TIMER;
            return;
        }
        if (slot.visibleWaitStartedAtMs == NO_VISIBLE_TIMER) {
            slot.visibleWaitStartedAtMs = SystemClock.elapsedRealtime();
        }
    }

    private static void ClearVisibilityWait(DisplaySlot slot) {
        if (slot == null) return;

        slot.visibleWaitStartedAtMs = NO_VISIBLE_TIMER;
    }

    private void ScheduleHiddenExpiry() {
        main.removeCallbacks(hiddenExpiryRunnable);
        if (released || visibleRequested) return;

        long nextExpiryAtMs = Long.MAX_VALUE;
        for (CachedAd cached : cachedAds) {
            nextExpiryAtMs = Math.min(
                    nextExpiryAtMs
                  , cached.loadedAtMs + MAX_CACHED_AD_AGE_MS);
        }
        if (materializingSlot != null) {
            nextExpiryAtMs = Math.min(
                    nextExpiryAtMs
                  , materializingSlot.loadedAtMs + MAX_CACHED_AD_AGE_MS);
        }
        if (activeSlot != null) {
            nextExpiryAtMs = Math.min(
                    nextExpiryAtMs
                  , activeSlot.loadedAtMs + MAX_CACHED_AD_AGE_MS);
        }
        if (nextExpiryAtMs == Long.MAX_VALUE) return;

        main.postDelayed(
                hiddenExpiryRunnable
              , Math.max(0L, nextExpiryAtMs - SystemClock.elapsedRealtime()));
    }

    private boolean ShouldMaterializeCachedAd() {
        return configured && !cachedAds.isEmpty();
    }

    // Keep the buffer topped up regardless of what is on screen. Loading is no
    // longer throttled by having an ad displayed; the swap triggers decide when
    // a warm ad is spent, this only decides when to fetch another.
    private boolean ShouldStartLoad() {
        return cachedAds.size() < IN_FEED_AD_BUFFER_MAX;
    }

    // Two different things get retried here. Reaching for the next warm ad does
    // not care whether a request is in flight - PresentCachedAd starts one as it
    // pops, so isAdLoading is set precisely when a presentation is about to be
    // built, and gating on it left a failed presentation with no retry at all.
    // Fetching into the buffer is the part that has to wait for the request.
    private boolean ShouldRetry() {
        if (released) return false;
        if (ShouldMaterializeCachedAd()) return true;

        return !isAdLoading && ShouldStartLoad();
    }

    private boolean ShowActiveSlot() {
        if (!visibleRequested || activeSlot == null) return false;
        if (!activeSlot.presentation.SetVisible(true)) return false;

        if (activeSlot.actuallyVisible) ResumeVisibleTimer(activeSlot);
        return true;
    }

    private void ResumeVisibleTimer(DisplaySlot slot) {
        if (slot == null
                || slot.visibleStartedAtMs != NO_VISIBLE_TIMER
                || !visibleRequested
                || !slot.actuallyVisible
                || !IsActivityUsable(activity)) {
            return;
        }
        slot.visibleStartedAtMs = SystemClock.elapsedRealtime();
    }

    private void PauseVisibleTimer(DisplaySlot slot) {
        if (slot == null || slot.visibleStartedAtMs == NO_VISIBLE_TIMER) return;

        slot.accumulatedVisibleMs += Math.max(
                0L
              , SystemClock.elapsedRealtime() - slot.visibleStartedAtMs);
        slot.visibleStartedAtMs = NO_VISIBLE_TIMER;
    }

    private long CurrentVisibleDurationMs(DisplaySlot slot) {
        if (slot == null) return 0L;

        long durationMs = slot.accumulatedVisibleMs;
        if (slot.visibleStartedAtMs != NO_VISIBLE_TIMER) {
            durationMs += Math.max(
                    0L
                  , SystemClock.elapsedRealtime() - slot.visibleStartedAtMs);
        }
        return durationMs;
    }

    private void RemoveExpiredCachedAd() {
        long nowMs = SystemClock.elapsedRealtime();
        for (Iterator<CachedAd> it = cachedAds.iterator(); it.hasNext(); ) {
            CachedAd cached = it.next();
            if (nowMs - cached.loadedAtMs < MAX_CACHED_AD_AGE_MS) continue;

            DestroyAd(cached.ad);
            it.remove();
        }
    }

    private void RemoveExpiredMaterializingSlot() {
        if (!IsExpired(materializingSlot)) return;

        DestroySlot(materializingSlot);
        materializingSlot = null;
    }

    private void RemoveExpiredActiveSlot() {
        if (!IsExpired(activeSlot)) return;

        PauseVisibleTimer(activeSlot);
        DestroySlot(activeSlot);
        activeSlot = null;
    }

    private static boolean IsExpired(DisplaySlot slot) {
        return slot != null
                && SystemClock.elapsedRealtime() - slot.loadedAtMs
                        >= MAX_CACHED_AD_AGE_MS;
    }

    private void DestroyCachedAd() {
        for (CachedAd cached : cachedAds) DestroyAd(cached.ad);
        cachedAds.clear();
    }

    private void DestroySlot(DisplaySlot slot) {
        if (slot == null) return;
        slot.presentation.Release();
        DestroyAd(slot.ad);
    }

    private void DestroyAd(
            com.google.android.gms.ads.nativead.NativeAd ad) {
        if (ad == null) return;

        ownedAds.remove(ad);
        ad.destroy();
    }

    private boolean IsCurrentLoad(int generation) {
        return IsCurrentLoadGeneration(generation);
    }

    private boolean OwnsAd(
            com.google.android.gms.ads.nativead.NativeAd ad) {
        return ownedAds.contains(ad);
    }

    private static void SetSlotPosition(
            DisplaySlot slot
          , int xPx
          , int yPx) {
        if (slot == null) return;
        slot.presentation.SetPosition(xPx, yPx);
    }

    private static float ResolveBackgroundAlpha(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            Log.w(TAG, "backgroundAlpha is not finite; using 1");
            return 1f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean HasSameStyle(
            InFeedStyle first
          , InFeedStyle second) {
        return first != null
                && second != null
                && first.xPx == second.xPx
                && first.yPx == second.yPx
                && first.widthPx == second.widthPx
                && first.heightPx == second.heightPx
                && Float.compare(
                        first.backgroundAlpha
                      , second.backgroundAlpha) == 0;
    }

    private String BuildFailureMessage(
            String reason
          , long retryDelayMs) {
        String retryDescription = retryDelayMs == NO_RETRY_SCHEDULED_MS
                ? "not scheduled"
                : retryDelayMs + "ms";
        return String.valueOf(reason)
                + " | adUnitId=" + String.valueOf(adUnitId)
                + " | rect=" + DescribeRequestedRect()
                + " | noFill=" + noFillStreak
                + " | layoutFail=" + layoutFailStreak
                + " | retry=" + retryDescription;
    }

    private String DescribeRequestedRect() {
        if (style == null) return "unavailable";
        if (!IsActivityUsable(activity)) {
            return "["
                    + style.xPx + "," + style.yPx + ","
                    + style.widthPx + "," + style.heightPx + "]px";
        }

        float density =
                activity.getResources().getDisplayMetrics().density;
        return "["
                + style.xPx + "," + style.yPx + ","
                + style.widthPx + "," + style.heightPx + "]px = ["
                + Math.round(style.widthPx / density) + ","
                + Math.round(style.heightPx / density) + "]dp"
                + " (density=" + density + ")";
    }

}
