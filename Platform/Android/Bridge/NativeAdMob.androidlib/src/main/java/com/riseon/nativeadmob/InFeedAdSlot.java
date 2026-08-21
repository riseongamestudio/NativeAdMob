package com.riseon.nativeadmob;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import com.google.android.gms.ads.nativead.NativeAd;

// One display slot of an InFeedAd unit: a rect on screen, the presentation
// pair being shown and warmed for it, and every trigger that decides when
// the rect earns a fresh ad. It consumes raw ads from its owner's shared
// cache and never talks to the network itself.
final class InFeedAdSlot {
    private static final String TAG = InFeedAd.TAG;
    private static final int RETRY_IMMEDIATE_LAYOUT_ATTEMPTS = 2;
    private static final int MAX_UNPROVEN_LAYOUT_FAILURES = 20;
    private static final long UNPROVEN_LAYOUT_RETRY_DELAY_MS = 300_000L;
    // Rotation follows appearances rather than a clock: a slot that keeps being
    // shown and hidden earns a fresh ad every time it comes back, and the dwell
    // interval only covers a slot that never goes away.
    private static final long DWELL_REFRESH_INTERVAL_MS = 30_000L;
    // How long an ad has to have been on screen before a hide is allowed to
    // rotate it away. A second is a glance; the ad is gone before it has been
    // read, and the one that replaces it burns an impression on a slot the
    // player is leaving.
    private static final long MIN_DWELL_MS = 4_000L;
    private static final long MIN_SWAP_INTERVAL_MS = 3_000L;
    private static final long NO_VISIBLE_TIMER = -1L;
    private static final long WATCHDOG_INTERVAL_MS = 1_000L;
    private static final long FOREGROUND_RECHECK_DELAY_MS = 1_000L;
    private static final long ACTIVE_ENTRY_VISIBILITY_TIMEOUT_MS = 3_000L;
    // Last-resort net only. Walking down the layout candidates costs up to
    // MAX_FINAL_LAYOUT_OBSERVATION_MS each, so a creative that needs many
    // attempts is legitimately slow; the presentation bounds its own waits and
    // ends as a dismissal, which is the path that normally clears a slot.
    private static final long MATERIALIZING_ENTRY_TIMEOUT_MS = 20_000L;

    private static final class SlotRect {
        final int xPx;
        final int yPx;
        final int widthPx;
        final int heightPx;

        SlotRect(int xPx, int yPx, int widthPx, int heightPx) {
            this.xPx = xPx;
            this.yPx = yPx;
            this.widthPx = Math.max(1, widthPx);
            this.heightPx = Math.max(1, heightPx);
        }

        SlotRect WithPosition(int newXPx, int newYPx) {
            return new SlotRect(newXPx, newYPx, widthPx, heightPx);
        }
    }

    private static final class DisplayEntry {
        final NativeAd ad;
        final InFeedAdPresentation presentation;
        final long loadedAtMs;
        long accumulatedVisibleMs;
        long visibleStartedAtMs = NO_VISIBLE_TIMER;
        long visibleWaitStartedAtMs = NO_VISIBLE_TIMER;
        long displayedAtMs = NO_VISIBLE_TIMER;
        boolean ready;
        boolean actuallyVisible;

        DisplayEntry(
                NativeAd ad
              , InFeedAdPresentation presentation
              , long loadedAtMs) {
            this.ad = ad;
            this.presentation = presentation;
            this.loadedAtMs = loadedAtMs;
        }
    }

    private final InFeedAd owner;
    private final int index;

    private final Runnable refreshRunnable = this::HandleRefresh;
    private final Runnable layoutRetryRunnable = this::HandleLayoutRetry;
    private final Runnable entryExpiryRunnable = this::HandleEntryExpiry;
    private final Runnable watchdogRunnable = this::HandleWatchdog;
    private final Runnable foregroundRecheckRunnable =
            this::HandleForegroundRecheck;
    private final Runnable hiddenSwapRunnable = this::HandleHiddenSwap;
    // Posting from inside a frame callback lands the runnable after that
    // frame's traversal and draw, which is the earliest point where the hide
    // is actually on screen.
    private final Choreographer.FrameCallback hiddenSwapFrameCallback =
            frameTimeNanos -> PostHiddenSwap();
    private SlotRect rect;
    private DisplayEntry activeEntry;
    private DisplayEntry materializingEntry;
    private long lastSwapAtMs = NO_VISIBLE_TIMER;
    private boolean configured;
    private boolean visibleRequested;
    private int layoutFailStreak;
    private boolean layoutProven;

    InFeedAdSlot(InFeedAd owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    private void PostHiddenSwap() {
        owner.main.post(hiddenSwapRunnable);
    }

    // ---- operations; the owner already hopped to main and vetted the
    // activity before calling any of these ----

    void Configure(int xPx, int yPx, int widthPx, int heightPx) {
        SlotRect newRect = new SlotRect(xPx, yPx, widthPx, heightPx);
        boolean rectChanged = configured && !HasSameRect(rect, newRect);

        visibleRequested = false;
        owner.main.removeCallbacks(refreshRunnable);
        PauseVisibleTimer(activeEntry);
        if (activeEntry != null) {
            activeEntry.presentation.SetVisible(false);
        }
        if (materializingEntry != null) {
            materializingEntry.presentation.SetVisible(false);
        }

        if (rectChanged) {
            // A different rect has to prove itself again.
            layoutFailStreak = 0;
            layoutProven = false;
            DestroyEntry(materializingEntry);
            materializingEntry = null;
            DestroyEntry(activeEntry);
            activeEntry = null;
        }

        rect = newRect;
        configured = true;
        RemoveExpiredMaterializingEntry();
        RemoveExpiredActiveEntry();
        if (owner.HasCachedAd()) {
            PresentCachedAd();
        } else {
            owner.RequestLoad();
        }
        ScheduleEntryExpiry();
    }

    void Show() {
        ShowCore();
        // The entry on screen ages like any other. Re-armed after every
        // show, because a slot that stays open - a popup left up while the
        // phone sits in a pocket - has no other moment that would catch it.
        ScheduleEntryExpiry();
        ScheduleWatchdog();
    }

    private void ShowCore() {
        if (owner.released || !configured) {
            Log.e(TAG, "Show ignored before Configure or after Release");
            return;
        }

        visibleRequested = true;

        RemoveExpiredMaterializingEntry();
        RemoveExpiredActiveEntry();
        if (activeEntry != null) {
            activeEntry.presentation.RequestDisplayNotification();
        }
        if (materializingEntry != null) {
            materializingEntry.presentation.RequestDisplayNotification();
        }
        if (!IsReadyForShow()) owner.NotifySlotShowNotReady(index);
        if (materializingEntry != null) {
            if (materializingEntry.ready) {
                // Already laid out, so open straight onto it. Showing the
                // outgoing ad first is what made one appear and get replaced a
                // moment later.
                materializingEntry.presentation.SetVisible(true);
            } else {
                ShowActiveEntry();
                if (activeEntry == null) {
                    materializingEntry.presentation.SetVisible(true);
                }
            }
            return;
        }
        if (activeEntry != null) {
            if (ShowActiveEntry()) ScheduleRefresh();
            return;
        }
        if (owner.HasCachedAd()) {
            PresentCachedAd();
            return;
        }

        owner.RequestLoad();
    }

    private boolean IsReadyForShow() {
        return activeEntry != null
                || materializingEntry != null && materializingEntry.ready;
    }

    void Hide() {
        if (owner.released) return;

        visibleRequested = false;
        owner.main.removeCallbacks(refreshRunnable);
        PauseVisibleTimer(activeEntry);
        if (activeEntry != null) {
            activeEntry.presentation.SetVisible(false);
        }
        if (materializingEntry != null) {
            materializingEntry.presentation.SetVisible(false);
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
        ClearVisibilityWait(activeEntry);
        ClearVisibilityWait(materializingEntry);
        CancelWatchdog();
        ScheduleEntryExpiry();
    }

    void SetPosition(int xPx, int yPx) {
        if (owner.released || !configured || rect == null) {
            Log.e(
                    TAG
                  , "SetPosition ignored before Configure or after Release");
            return;
        }

        rect = rect.WithPosition(xPx, yPx);
        SetEntryPosition(materializingEntry, xPx, yPx);
        SetEntryPosition(activeEntry, xPx, yPx);
    }

    // The activity the ad windows hung off is gone; everything materialized
    // is dead weight, only the owner's raw cache survives the move.
    void HandleActivityChanged() {
        PauseVisibleTimer(activeEntry);
        DestroyEntry(materializingEntry);
        materializingEntry = null;
        DestroyEntry(activeEntry);
        activeEntry = null;
    }

    void ReleaseOnMain() {
        // On main because Choreographer is per-thread; the callback was
        // posted from Hide on this same thread.
        Choreographer.getInstance()
                .removeFrameCallback(hiddenSwapFrameCallback);
        visibleRequested = false;
        PauseVisibleTimer(activeEntry);
        DestroyEntry(materializingEntry);
        materializingEntry = null;
        DestroyEntry(activeEntry);
        activeEntry = null;
        rect = null;
    }

    void CommitAdClick() {
        if (activeEntry != null) activeEntry.presentation.CommitAdClick();
        if (materializingEntry != null) {
            materializingEntry.presentation.CommitAdClick();
        }
    }

    // A freshly loaded ad goes to the first configured slot with nothing on
    // screen and nothing being built; rotation of a filled slot pulls from
    // the cache on its own schedule instead.
    boolean WantsCachedAd() {
        return configured && activeEntry == null && materializingEntry == null;
    }

    void PresentCachedAd() {
        if (owner.released
                || !configured
                || materializingEntry != null
                || !owner.HasCachedAd()
                || !BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return;
        }
        if (!IsActivityVisible(owner.CurrentActivity())) {
            // The geometry observation loop only advances on draw passes, so
            // presenting into a window that is not drawing parks the slot and
            // blocks every later load. Keep the ad cached and wait instead.
            ScheduleForegroundRecheck();
            return;
        }

        InFeedAd.CachedAd next = owner.TakeCachedAd();
        if (next == null) {
            owner.RequestLoad();
            return;
        }

        final NativeAd ad = next.ad;
        final long loadedAtMs = next.loadedAtMs;

        final DisplayEntry[] holder = new DisplayEntry[1];
        InFeedAdPresentation presentation =
                new InFeedAdPresentation(
                        owner.CurrentActivity()
                      , ad
                      , rect.xPx
                      , rect.yPx
                      , rect.widthPx
                      , rect.heightPx
                      , owner.BackgroundColor()
                      , new NativeAdPresentation.Listener() {
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
        DisplayEntry entry = new DisplayEntry(ad, presentation, loadedAtMs);
        holder[0] = entry;
        materializingEntry = entry;
        // Armed where the entry is BORN, not where some caller remembered.
        // Eight call sites reach this method and three of them re-armed
        // afterwards, which held only while a timer happened to be pending
        // already; once an expiry emptied the slot the schedule went quiet,
        // and the next ad to arrive inherited no deadline at all. If the
        // presentation below fails and this entry is destroyed, the pending
        // timer is a harmless early wakeup that re-arms on what it finds.
        ScheduleEntryExpiry();

        presentation.SetVisible(
                visibleRequested && activeEntry == null);
        if (!presentation.Show()) {
            String failureMessage = presentation.GetFailureMessage();
            materializingEntry = null;
            DestroyEntry(entry);
            ShowActiveEntry();
            long retryDelayMs = ScheduleLayoutRetry();
            owner.NotifySlotPresentationFailed(
                    index
                  , BaseAd.INTERNAL_PRESENTATION_ERROR
                  , BuildFailureMessage(
                        failureMessage == null
                                ? "In-feed presentation failed before display"
                                : failureMessage
                      , retryDelayMs));
        }
        ScheduleWatchdog();
    }

    private void HandlePresentationReady(DisplayEntry entry) {
        if (owner.released || entry == null || materializingEntry != entry) {
            return;
        }

        entry.ready = true;
        if (visibleRequested) entry.presentation.SetVisible(true);
    }

    private void HandlePresentationDisplayed(DisplayEntry entry) {
        if (owner.released
                || entry == null
                || materializingEntry != entry && activeEntry != entry) {
            return;
        }
        if (!visibleRequested) {
            entry.presentation.SetVisible(false);
            return;
        }

        if (materializingEntry == entry) {
            DisplayEntry previous = activeEntry;
            activeEntry = entry;
            materializingEntry = null;
            layoutFailStreak = 0;
            layoutProven = true;
            lastSwapAtMs = SystemClock.elapsedRealtime();
            DestroyEntry(previous);
        }
        if (entry.displayedAtMs == NO_VISIBLE_TIMER) {
            entry.displayedAtMs = SystemClock.elapsedRealtime();
        }

        if (entry.actuallyVisible) {
            ResumeVisibleTimer(entry);
        } else {
            PauseVisibleTimer(entry);
        }
        ScheduleWatchdog();
        owner.NotifySlotDisplayed(index);
        ScheduleRefresh();
    }

    private void HandleActualVisibilityChanged(
            DisplayEntry entry
          , boolean isActuallyVisible) {
        if (entry == null) return;

        entry.actuallyVisible = isActuallyVisible;
        MarkVisibilityWait(entry);
        if (entry != activeEntry) return;

        if (visibleRequested && isActuallyVisible) {
            ResumeVisibleTimer(entry);
            ScheduleRefresh();
        } else {
            owner.main.removeCallbacks(refreshRunnable);
            PauseVisibleTimer(entry);
        }
        ScheduleWatchdog();
    }

    private void HandlePresentationDismissed(DisplayEntry entry) {
        if (entry == null) return;

        String failureMessage = entry.presentation.GetFailureMessage();
        if (materializingEntry == entry) materializingEntry = null;
        if (activeEntry == entry) {
            PauseVisibleTimer(entry);
            activeEntry = null;
        }
        owner.DestroyAd(entry.ad);

        if (owner.released) return;
        ShowActiveEntry();
        long retryDelayMs = ScheduleLayoutRetry();
        ScheduleWatchdog();
        if (failureMessage != null) {
            owner.NotifySlotPresentationFailed(
                    index
                  , BaseAd.INTERNAL_PRESENTATION_ERROR
                  , BuildFailureMessage(failureMessage, retryDelayMs));
        }
    }

    private void HandleRefresh() {
        if (owner.released
                || !BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return;
        }
        if (!visibleRequested
                || activeEntry == null
                || !activeEntry.actuallyVisible) {
            PauseVisibleTimer(activeEntry);
            return;
        }

        ResumeVisibleTimer(activeEntry);
        long remainingMs = DWELL_REFRESH_INTERVAL_MS
                - CurrentVisibleDurationMs(activeEntry);
        if (remainingMs > 0L) {
            owner.main.postDelayed(refreshRunnable, remainingMs);
            return;
        }

        TrySwapActiveEntry();
        // Start the next dwell window whether or not a swap happened. Without
        // this the accumulated time stays past the interval, ScheduleRefresh
        // keeps computing a zero delay, and the trigger re-arms every frame -
        // which turns MIN_SWAP_INTERVAL_MS into the rotation period.
        RestartVisibleAccumulation(activeEntry);
        ScheduleRefresh();
    }

    private void RestartVisibleAccumulation(DisplayEntry entry) {
        if (entry == null) return;

        entry.accumulatedVisibleMs = 0L;
        entry.visibleStartedAtMs = visibleRequested && entry.actuallyVisible
                ? SystemClock.elapsedRealtime()
                : NO_VISIBLE_TIMER;
    }

    // Rotate to a warm ad once the one on screen has had its turn: it has to
    // have been displayed and stayed up long enough to be worth an impression,
    // and swaps are spaced so a burst of show and hide cannot drain the cache.
    private void TrySwapActiveEntry() {
        if (owner.released
                || materializingEntry != null
                || activeEntry == null
                || !owner.HasCachedAd()
                || !IsActiveEntryConsumed()) {
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        if (lastSwapAtMs != NO_VISIBLE_TIMER
                && nowMs - lastSwapAtMs < MIN_SWAP_INTERVAL_MS) {
            return;
        }

        PresentCachedAd();
    }

    private boolean IsActiveEntryConsumed() {
        return activeEntry != null
                && activeEntry.displayedAtMs != NO_VISIBLE_TIMER
                && CurrentVisibleDurationMs(activeEntry) >= MIN_DWELL_MS;
    }

    private void HandleHiddenSwap() {
        if (owner.released || visibleRequested) return;
        TrySwapActiveEntry();
    }

    // A layout failure is a property of this creative, not of inventory. Waiting
    // cannot make it fit, so the first attempts fetch a replacement straight
    // away and only a long run of failures is treated as the rect itself being
    // unusable - and never once some creative has already rendered in it.
    private long ScheduleLayoutRetry() {
        CancelLayoutRetry();
        if (owner.released || !configured) return InFeedAd.NO_RETRY_SCHEDULED_MS;

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
            return PostLayoutRetry(UNPROVEN_LAYOUT_RETRY_DELAY_MS);
        }
        return PostLayoutRetry(
                InFeedAd.BackoffDelayMs(
                        layoutFailStreak
                      , RETRY_IMMEDIATE_LAYOUT_ATTEMPTS));
    }

    private long PostLayoutRetry(long delayMs) {
        CancelLayoutRetry();
        if (owner.released || !configured) return InFeedAd.NO_RETRY_SCHEDULED_MS;

        owner.main.postDelayed(layoutRetryRunnable, Math.max(0L, delayMs));
        return delayMs;
    }

    private void CancelLayoutRetry() {
        owner.main.removeCallbacks(layoutRetryRunnable);
    }

    private void HandleLayoutRetry() {
        if (owner.released || !configured) return;
        if (owner.HasCachedAd()) {
            PresentCachedAd();
            return;
        }

        owner.RequestLoad();
    }

    // An expired creative may not stay on screen, so this runs whether the
    // slot is shown or hidden. The replacement it presents deliberately
    // skips MIN_DWELL_MS and MIN_SWAP_INTERVAL_MS - the old entry is already
    // destroyed by then, so there is nothing left for a dwell rule to
    // protect. Do not "restore" that check here.
    //
    // With nothing warm behind it the slot goes blank until a load lands -
    // the same outcome a hidden slot already had, and the only honest one:
    // the impression count belongs to the SDK, so the pack cannot keep the
    // ad up and stop counting it.
    private void HandleEntryExpiry() {
        if (owner.released) return;

        RemoveExpiredMaterializingEntry();
        RemoveExpiredActiveEntry();
        if (owner.HasCachedAd()) {
            PresentCachedAd();
        } else {
            owner.RequestLoad();
        }
        ScheduleEntryExpiry();
    }

    private void ScheduleEntryExpiry() {
        owner.main.removeCallbacks(entryExpiryRunnable);
        if (owner.released) return;

        long nextExpiryAtMs = Long.MAX_VALUE;
        if (materializingEntry != null) {
            nextExpiryAtMs = Math.min(
                    nextExpiryAtMs
                  , materializingEntry.loadedAtMs + InFeedAd.MAX_CACHED_AD_AGE_MS);
        }
        if (activeEntry != null) {
            nextExpiryAtMs = Math.min(
                    nextExpiryAtMs
                  , activeEntry.loadedAtMs + InFeedAd.MAX_CACHED_AD_AGE_MS);
        }
        if (nextExpiryAtMs == Long.MAX_VALUE) return;

        owner.main.postDelayed(
                entryExpiryRunnable
              , Math.max(0L, nextExpiryAtMs - SystemClock.elapsedRealtime()));
    }

    // Only armed while something is actually waiting to be seen. A
    // materializing entry with no pending Show is just a warm ad and must not
    // be dropped, or every prefetch made while the feed is hidden is thrown
    // away and re-requested.
    private void ScheduleWatchdog() {
        owner.main.removeCallbacks(watchdogRunnable);
        if (owner.released || !visibleRequested) return;
        if (!IsActivityVisible(owner.CurrentActivity())) {
            ClearVisibilityWait(activeEntry);
            ClearVisibilityWait(materializingEntry);
            ScheduleForegroundRecheck();
            return;
        }

        MarkVisibilityWait(activeEntry);
        MarkVisibilityWait(materializingEntry);
        if (materializingEntry == null && activeEntry == null) return;

        owner.main.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
    }

    private void CancelWatchdog() {
        owner.main.removeCallbacks(watchdogRunnable);
    }

    // A backgrounded Activity is still "usable", but its windows do not draw.
    // Everything that depends on drawing - presenting an entry, expecting an
    // entry to become visible - has to wait for the foreground instead of
    // being treated as a failure.
    private static boolean IsActivityVisible(Activity currentActivity) {
        return BaseAd.IsActivityUsable(currentActivity)
                && currentActivity.hasWindowFocus();
    }

    private void ScheduleForegroundRecheck() {
        owner.main.removeCallbacks(foregroundRecheckRunnable);
        if (owner.released
                || IsActivityVisible(owner.CurrentActivity())
                || !BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return;
        }
        if (!visibleRequested && !(configured && owner.HasCachedAd())) return;

        owner.main.postDelayed(
                foregroundRecheckRunnable
              , FOREGROUND_RECHECK_DELAY_MS);
    }

    private void HandleForegroundRecheck() {
        if (owner.released) return;
        if (!IsActivityVisible(owner.CurrentActivity())) {
            ScheduleForegroundRecheck();
            return;
        }

        // Age is measured on elapsed real time, which counts the hours the
        // phone spent asleep; the expiry timer is posted on uptime, which
        // does not. So the timer alone cannot be trusted across a long
        // sleep, and coming back to the foreground is where the entry gets
        // read for age instead of waited on. The overlay's cache learned
        // this first and sweeps on the way into every Show.
        RemoveExpiredMaterializingEntry();
        RemoveExpiredActiveEntry();

        // Coming back to the foreground is a resume, not a rotation. Present
        // only what could not be presented while the window was dark - an
        // empty slot - and leave whatever survived the background on screen.
        // PresentCachedAd is also the swap, so calling it here for a slot that
        // already has an ad turns every unfocus and focus into a rotation,
        // with none of the dwell the two rotation triggers apply.
        if (WantsCachedAd() && owner.HasCachedAd()) PresentCachedAd();
        ShowActiveEntry();
        ScheduleWatchdog();
        ScheduleRefresh();
    }

    // Without this both entries can park forever. A materializing entry blocks
    // every swap until it is promoted or dismissed, and an active entry that
    // never reports actual visibility never accumulates the dwell that both
    // rotation triggers need, so it is never replaced. Either state would
    // otherwise only clear after MAX_CACHED_AD_AGE_MS or an app restart.
    private void HandleWatchdog() {
        if (owner.released
                || !BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return;
        }
        if (!IsActivityVisible(owner.CurrentActivity())) {
            ScheduleWatchdog();
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();
        if (materializingEntry != null) {
            if (materializingEntry.visibleWaitStartedAtMs == NO_VISIBLE_TIMER
                    || nowMs - materializingEntry.visibleWaitStartedAtMs
                            < MATERIALIZING_ENTRY_TIMEOUT_MS) {
                ScheduleWatchdog();
                return;
            }

            Log.e(
                    TAG
                  , "In-feed materializing slot was never displayed within "
                            + MATERIALIZING_ENTRY_TIMEOUT_MS
                            + "ms; dropping it and reloading");
            DisplayEntry stuck = materializingEntry;
            materializingEntry = null;
            DestroyEntry(stuck);
            ShowActiveEntry();
            RequestReplacementAd();
            ScheduleWatchdog();
            return;
        }

        if (visibleRequested
                && activeEntry != null
                && !activeEntry.actuallyVisible
                && activeEntry.visibleWaitStartedAtMs != NO_VISIBLE_TIMER
                && nowMs - activeEntry.visibleWaitStartedAtMs
                        >= ACTIVE_ENTRY_VISIBILITY_TIMEOUT_MS) {
            Log.e(
                    TAG
                  , "In-feed active slot never became visible within "
                            + ACTIVE_ENTRY_VISIBILITY_TIMEOUT_MS
                            + "ms; dropping it and reloading");
            DisplayEntry stuck = activeEntry;
            PauseVisibleTimer(stuck);
            activeEntry = null;
            DestroyEntry(stuck);
            RequestReplacementAd();
        }

        ScheduleWatchdog();
    }

    private void RequestReplacementAd() {
        if (owner.HasCachedAd()) {
            PresentCachedAd();
            return;
        }
        // Go through the retry backoff instead of loading straight away so a
        // slot that keeps failing to display cannot churn ad requests.
        ScheduleLayoutRetry();
    }

    private void MarkVisibilityWait(DisplayEntry entry) {
        if (entry == null) return;
        if (!visibleRequested || entry.actuallyVisible) {
            entry.visibleWaitStartedAtMs = NO_VISIBLE_TIMER;
            return;
        }
        if (entry.visibleWaitStartedAtMs == NO_VISIBLE_TIMER) {
            entry.visibleWaitStartedAtMs = SystemClock.elapsedRealtime();
        }
    }

    private static void ClearVisibilityWait(DisplayEntry entry) {
        if (entry == null) return;

        entry.visibleWaitStartedAtMs = NO_VISIBLE_TIMER;
    }

    private void ScheduleRefresh() {
        owner.main.removeCallbacks(refreshRunnable);
        if (owner.released
                || !visibleRequested
                || activeEntry == null
                || !activeEntry.actuallyVisible
                || !BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return;
        }

        long remainingMs = Math.max(
                0L
              , DWELL_REFRESH_INTERVAL_MS
                        - CurrentVisibleDurationMs(activeEntry));
        owner.main.postDelayed(refreshRunnable, remainingMs);
    }

    private boolean ShowActiveEntry() {
        if (!visibleRequested || activeEntry == null) return false;
        if (!activeEntry.presentation.SetVisible(true)) return false;

        if (activeEntry.actuallyVisible) ResumeVisibleTimer(activeEntry);
        return true;
    }

    private void ResumeVisibleTimer(DisplayEntry entry) {
        if (entry == null
                || entry.visibleStartedAtMs != NO_VISIBLE_TIMER
                || !visibleRequested
                || !entry.actuallyVisible
                || !BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return;
        }
        entry.visibleStartedAtMs = SystemClock.elapsedRealtime();
    }

    private void PauseVisibleTimer(DisplayEntry entry) {
        if (entry == null
                || entry.visibleStartedAtMs == NO_VISIBLE_TIMER) {
            return;
        }

        entry.accumulatedVisibleMs += Math.max(
                0L
              , SystemClock.elapsedRealtime() - entry.visibleStartedAtMs);
        entry.visibleStartedAtMs = NO_VISIBLE_TIMER;
    }

    private long CurrentVisibleDurationMs(DisplayEntry entry) {
        if (entry == null) return 0L;

        long durationMs = entry.accumulatedVisibleMs;
        if (entry.visibleStartedAtMs != NO_VISIBLE_TIMER) {
            durationMs += Math.max(
                    0L
                  , SystemClock.elapsedRealtime() - entry.visibleStartedAtMs);
        }
        return durationMs;
    }

    private void RemoveExpiredMaterializingEntry() {
        if (!IsExpired(materializingEntry)) return;

        DestroyEntry(materializingEntry);
        materializingEntry = null;
    }

    private void RemoveExpiredActiveEntry() {
        if (!IsExpired(activeEntry)) return;

        PauseVisibleTimer(activeEntry);
        DestroyEntry(activeEntry);
        activeEntry = null;
    }

    private static boolean IsExpired(DisplayEntry entry) {
        return entry != null
                && SystemClock.elapsedRealtime() - entry.loadedAtMs
                        >= InFeedAd.MAX_CACHED_AD_AGE_MS;
    }

    private void DestroyEntry(DisplayEntry entry) {
        if (entry == null) return;
        entry.presentation.Release();
        owner.DestroyAd(entry.ad);
    }

    private static void SetEntryPosition(
            DisplayEntry entry
          , int xPx
          , int yPx) {
        if (entry == null) return;
        entry.presentation.SetPosition(xPx, yPx);
    }

    private static boolean HasSameRect(SlotRect first, SlotRect second) {
        return first != null
                && second != null
                && first.xPx == second.xPx
                && first.yPx == second.yPx
                && first.widthPx == second.widthPx
                && first.heightPx == second.heightPx;
    }

    private static String Describe(DisplayEntry entry) {
        if (entry == null) return "none";

        return "{ready=" + entry.ready
                + ",visible=" + entry.actuallyVisible
                + ",displayed=" + (entry.displayedAtMs != NO_VISIBLE_TIMER)
                + "}";
    }

    private String BuildFailureMessage(
            String reason
          , long retryDelayMs) {
        String retryDescription = retryDelayMs == InFeedAd.NO_RETRY_SCHEDULED_MS
                ? "not scheduled"
                : retryDelayMs + "ms";
        return String.valueOf(reason)
                + " | adUnitId=" + String.valueOf(owner.AdUnitId())
                + " | slot=" + index
                + " | rect=" + DescribeRequestedRect()
                + " | noFill=" + owner.NoFillStreak()
                + " | layoutFail=" + layoutFailStreak
                + " | retry=" + retryDescription;
    }

    private String DescribeRequestedRect() {
        if (rect == null) return "unavailable";
        if (!BaseAd.IsActivityUsable(owner.CurrentActivity())) {
            return "["
                    + rect.xPx + "," + rect.yPx + ","
                    + rect.widthPx + "," + rect.heightPx + "]px";
        }

        float density = owner.CurrentActivity()
                .getResources().getDisplayMetrics().density;
        return "["
                + rect.xPx + "," + rect.yPx + ","
                + rect.widthPx + "," + rect.heightPx + "]px = ["
                + Math.round(rect.widthPx / density) + ","
                + Math.round(rect.heightPx / density) + "]dp"
                + " (density=" + density + ")";
    }
}
