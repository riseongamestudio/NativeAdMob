package com.riseon.nativeadmob;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAd;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OverlayAd extends BaseAd {

    // The ceiling the in-feed unit already keeps: past a handful, warm ads
    // expire unseen and the impressions are simply burnt.
    private static final int MAX_CACHE_SIZE      = 5;
    // developers.google.com/admob/android/native/start, Request ads:
    // "Since ads expire after an hour, you should clear this cache and
    // reload with new ads every hour." The in-feed unit keeps the same
    // number, from the same sentence.
    private static final long MAX_CACHED_AD_AGE_MS = 3_600_000L;

    private static final int SIDE_LEFT           = 0;
    private static final int SIDE_RIGHT          = 1;
    private static final int TIMER_SIDE_OPPOSITE = 3;
    private static final int TIMER_SIDE_SAME     = 4;

    static final class OverlayAdStyle {
        final boolean fullscreen;
        final int cooldown;
        // Ordinals shared with the C# enums: Left 0, Right 1, Random 2, and
        // for the timer OppositeOfClose 3, SameAsClose 4.
        final int closeSide;
        final int timerSide;
        final float heightRatio;
        final float backgroundAlpha;
        // The close button commits the ad's click on its way out.
        final boolean fakeCloseAutoDismiss;

        OverlayAdStyle(
                boolean fullscreen
              , int cooldown
              , int closeSide
              , int timerSide
              , float heightRatio
              , float backgroundAlpha
              , boolean fakeCloseAutoDismiss) {
            this.fullscreen = fullscreen;
            this.cooldown = Math.max(0, cooldown);
            this.closeSide = closeSide;
            this.timerSide = timerSide;
            this.heightRatio = heightRatio;
            this.backgroundAlpha = backgroundAlpha;
            this.fakeCloseAutoDismiss = fakeCloseAutoDismiss;
        }

        OverlayAdStyle WithClose(
                int newCooldown
              , int newCloseSide
              , int newTimerSide
              , boolean newRedirectOnClose) {
            return new OverlayAdStyle(
                    fullscreen
                  , newCooldown
                  , newCloseSide
                  , newTimerSide
                  , heightRatio
                  , backgroundAlpha
                  , newRedirectOnClose);
        }

        // Random is answered once per presentation; the relative timer modes
        // then read that answer rather than rolling again.
        boolean ResolveCloseOnLeft() {
            if (closeSide == SIDE_LEFT) return true;
            if (closeSide == SIDE_RIGHT) return false;
            return Math.random() < 0.5d;
        }

        boolean ResolveTimerOnLeft(boolean closeOnLeft) {
            switch (timerSide) {
                case SIDE_LEFT:               return true;
                case SIDE_RIGHT:              return false;
                case TIMER_SIDE_OPPOSITE:     return !closeOnLeft;
                case TIMER_SIDE_SAME:         return closeOnLeft;
                default:                      return Math.random() < 0.5d;
            }
        }

        boolean PausesGame() {
            return fullscreen;
        }
    }

    private final String adUnitId;
    private volatile OverlayAdStyle configuredStyle;
    private volatile int cacheSize = 1;

    // The warm ads, oldest first. The head is the one the next Show takes,
    // and the only one worth a prepared face. Oldest first also means the
    // head is always the first to go stale.
    private final ArrayDeque<CachedAd> cachedAds = new ArrayDeque<>();
    private final Runnable cacheExpiryRunnable = this::HandleCacheExpiry;
    // The stage a timer-driven reload builds on. Every Load and Show hands
    // one in; the sweep that fires an hour later has none of its own.
    private Activity cacheActivity;
    // The deque lives on the main thread; readiness is asked for from
    // Unity's. One volatile count is the crossing point.
    private volatile int cachedCount;
    // The paid event fires on whatever thread the SDK picked, while the
    // deque is a main-thread structure. Membership answers "is this still
    // ours" without walking one that may be changing underneath.
    private final Set<NativeAd> ownedAds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile NativeAd activeNativeAd;
    private volatile boolean configured;
    private volatile boolean isAdLoading;
    private OverlayAdPresentation presentation;
    private OverlayAdPresentation preparedPresentation;
    private Activity preparedActivity;
    private NativeAd preparedNativeAd;
    private OverlayAdStyle preparedStyle;
    private OverlayAdContentView preparedContentView;
    private Activity preparedContentActivity;
    private NativeAd preparedContentAd;
    private boolean preparedContentCloseOnLeft;
    private OverlayAdActivity.CloseRelay preparedContentCloseRelay;
    private String preparedContentMediaSignature;
    private String preparedPresentationMediaSignature;

    // The face of the creative's assets at one moment. A presentation built
    // at load time is only valid while this stays the same: assets that
    // finish arriving later change the layout the ad needs, and a stale
    // face must be rebuilt rather than shown.
    // An ad and the moment it arrived - the only two things needed to know
    // whether it may still be shown.
    private static final class CachedAd {
        final NativeAd ad;
        final long loadedAtMs;

        CachedAd(
                NativeAd ad
              , long loadedAtMs) {
            this.ad = ad;
            this.loadedAtMs = loadedAtMs;
        }
    }

    private static String MediaSignature(
            NativeAd nativeAd) {
        com.google.android.gms.ads.MediaContent mediaContent =
                nativeAd.getMediaContent();
        boolean hasVideo = mediaContent != null
                && mediaContent.hasVideoContent();
        boolean hasMainImage = mediaContent != null
                && mediaContent.getMainImage() != null;
        float aspectRatio = mediaContent != null
                ? mediaContent.getAspectRatio()
                : 0f;
        boolean hasAnyImage = false;
        java.util.List<NativeAd.Image>
                images = nativeAd.getImages();
        if (images != null) {
            for (NativeAd.Image image
                    : images) {
                if (image != null && image.getDrawable() != null) {
                    hasAnyImage = true;
                    break;
                }
            }
        }
        return hasVideo + "|" + hasMainImage + "|" + hasAnyImage
                + "|" + aspectRatio;
    }
    private NativeAdCompletedListener activeShowCompleted;
    private String activeActivitySessionId;

    // The prepared full-screen face: the content view built at load time,
    // waiting for the Activity that will host it.
    static final class PreparedFullScreenContent {
        final OverlayAdContentView view;
        final boolean closeOnLeft;
        final OverlayAdActivity.CloseRelay closeRelay;

        PreparedFullScreenContent(
                OverlayAdContentView view
              , boolean closeOnLeft
              , OverlayAdActivity.CloseRelay closeRelay) {
            this.view = view;
            this.closeOnLeft = closeOnLeft;
            this.closeRelay = closeRelay;
        }
    }

    public OverlayAd(String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "OverlayAd requires a non-empty adUnitId");
        }
        this.adUnitId = adUnitId;
    }

    // The full-screen listener surface stays flat (no slot index), so this
    // format keeps the NativeAdLoadListener shape and owns its delivery.
    private volatile NativeAdLoadListener loadListener;

    public void SetListener(NativeAdLoadListener listener) {
        if (released) return;
        loadListener = listener;
        NotifyCurrentState();
    }

    private void ClearLoadListener() {
        loadListener = null;
    }

    private void NotifyLoadingStarted() {
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnLoadingStarted();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnLoadingStarted callback failed", exception);
        }
    }

    private void NotifyStateChanged(
            boolean isReady
          , boolean isLoading) {
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnStateChanged(isReady, isLoading);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnStateChanged callback failed", exception);
        }
    }

    private void NotifyShowNotReady() {
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnShowNotReady();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnShowNotReady callback failed", exception);
        }
    }

    private void NotifyLoadingCompleted(
            int errorCode
          , String errorMessage) {
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnLoadingCompleted(errorCode, errorMessage);
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
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnAdPaid(
                    source
                  , paidAdUnitId
                  , value
                  , currencyCode
                  , precision);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnAdPaid callback failed", exception);
        }
    }

    private void NotifyDisplayed() {
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnDisplayed();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnDisplayed callback failed", exception);
        }
    }

    private void NotifyPresentationFailed(
            int errorCode
          , String errorMessage) {
        NativeAdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnPresentationFailed(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnPresentationFailed callback failed", exception);
        }
    }

    public synchronized void Configure(
            boolean fullscreen
          , float heightRatio
          , float backgroundAlpha
          , int newCacheSize
          , int cooldown
          , int closeSide
          , int timerSide
          , boolean redirectOnClose) {
        if (released) {
            Log.e(
                    TAG
                  , "Configure ignored after Release; create a new instance");
            return;
        }
        if (configured) {
            Log.e(
                    TAG
                  , "Configure may only be called once per Ad instance");
            return;
        }

        // How many ads stay warm at once. One - always hold a spare -
        // is the placement that never asked; a chained placement raises it
        // so the follow-up is already in hand when the first ad closes.
        cacheSize = Math.max(1, Math.min(MAX_CACHE_SIZE, newCacheSize));
        configuredStyle = new OverlayAdStyle(
                fullscreen
              , cooldown
              , closeSide
              , timerSide
              , heightRatio
              , backgroundAlpha
              , redirectOnClose);
        configured = true;
    }

    public void SetClose(
            int cooldown
          , int closeSide
          , int timerSide
          , boolean redirectOnClose) {
        OverlayAdStyle currentStyle = configuredStyle;
        if (!configured || released || currentStyle == null) {
            Log.e(TAG, "SetClose ignored before Configure or after Release");
            return;
        }
        OverlayAdStyle updatedStyle = currentStyle.WithClose(
                cooldown
              , closeSide
              , timerSide
              , redirectOnClose);
        configuredStyle = updatedStyle;
        RequestPreparedFaceRebuild(updatedStyle);
    }

    public void Load(final Activity activity) {
        if (!configured || released) {
            Log.e(TAG, "Load ignored before Configure or after Release");
            return;
        }
        if (!IsActivityUsable(activity)) {
            Log.e(TAG, "Load ignored because Activity is not usable");
            return;
        }

        // Load chi snapshot phan style can cho request (hien tai la mute
        // policy). Presentation style se duoc snapshot muon hon tai Show.
        final String requestAdUnitId = adUnitId;
        final OverlayAdStyle loadStyle = configuredStyle;

        RunOnMainThread(
                () -> StartLoad(activity, requestAdUnitId, loadStyle));
    }

    // The one gate every load passes. A warm ad is never thrown away to
    // fetch another - that is what made the replacement fetched during a
    // show die at dismissal and the player wait for a fresh load anyway -
    // so a free seat in the cache is the only reason to load, whoever is
    // on screen. Says whether a request actually left.
    private boolean StartLoad(
            final Activity activity
          , final String requestAdUnitId
          , final OverlayAdStyle loadStyle) {
        if (released
                || !configured
                || isAdLoading
                || loadStyle == null
                || cachedAds.size() >= cacheSize
                || !IsActivityUsable(activity)) {
            return false;
        }

        cacheActivity = activity;
        DoLoadAd(activity, requestAdUnitId, loadStyle);
        return true;
    }

    private void DoLoadAd(
            final Activity activity
          , final String requestAdUnitId
          , final OverlayAdStyle loadStyle) {
        isAdLoading = true;
        final int generation = NextLoadGeneration();
        NotifyCurrentState();
        NotifyLoadingStarted();

        try {
            NativeAdOptions nativeAdOptions =
                    CreateNativeAdOptions(!loadStyle.PausesGame());

            AdLoader adLoader =
                    new AdLoader.Builder(activity, requestAdUnitId)
                    .forNativeAd(ad -> {
                        if (!IsCurrentLoadGeneration(generation)) {
                            ad.destroy();
                            return;
                        }
                        AddCachedAd(ad);
                        // Bound to the ad's own identity, never to the
                        // load generation: ads keep loading while this one
                        // is still on screen, and a generation guard would
                        // silence the revenue event of the ad the player
                        // is actually watching.
                        BindPaidEvent(
                                ad
                              , requestAdUnitId
                              , () -> OwnsAd(ad));
                        // Only the head earns a face - it is the one the
                        // next Show takes. Those behind it get theirs on
                        // reaching the front.
                        if (IsHeadAd(ad)) {
                            PrepareFace(activity, ad, configuredStyle);
                        }
                    })
                    .withNativeAdOptions(nativeAdOptions)
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            if (!IsCurrentLoadGeneration(generation)) return;

                            isAdLoading = false;
                            NotifyCurrentState();
                            NotifyLoadingCompleted(
                                    error.getCode(), error.getMessage());
                        }

                        @Override
                        public void onAdLoaded() {
                            if (!IsCurrentLoadGeneration(generation)) return;

                            isAdLoading = false;
                            NotifyCurrentState();
                            NotifyLoadingCompleted(0, "");
                            // One request per ad: this chains on until the
                            // cache is full, and the last one simply finds
                            // no seat left.
                            StartLoad(
                                    activity
                                  , requestAdUnitId
                                  , configuredStyle);
                        }

                        @Override
                        public void onAdClicked() {
                            String activitySessionId =
                                    activeActivitySessionId;
                            if (activitySessionId != null) {
                                OverlayAdActivity.CommitAdClick(
                                        activitySessionId);
                                return;
                            }
                            NativeAdPresentation activePresentation =
                                    presentation;
                            if (activePresentation != null) {
                                activePresentation.OnAdClicked();
                            }
                        }
                    })
                    .build();

            adLoader.loadAd(new AdRequest.Builder().build());
        } catch (RuntimeException exception) {
            if (IsCurrentLoadGeneration(generation)) {
                isAdLoading = false;
                NotifyCurrentState();
                Log.e(TAG, "Native ad load failed before callback"
                      , exception);
                NotifyLoadingCompleted(
                        INTERNAL_LOAD_ERROR
                      , "Failed to load ad: "
                                + String.valueOf(exception.getMessage()));
            }
        }
    }

    public void Show(
            final Activity activity
          , final NativeAdCompletedListener onCompleted) {
        // The config boundary of one show: updates completed before this
        // Show call apply to it, later updates belong to the next one.
        final OverlayAdStyle requestedShowStyle = configuredStyle;

        RunOnMainThread(() -> {
            if (released) {
                NotifyCompleted(onCompleted, "Ad released", false);
                return;
            }
            if (!configured || IsShowingInternal()
                    || activeNativeAd != null) {
                NotifyCompleted(
                        onCompleted
                      , !configured
                                ? "Ad not configured"
                                : "Ad already showing"
                      , false);
                return;
            }
            cacheActivity = activity;
            if (RemoveExpiredCachedAds()) {
                RefreshHeadFace();
                NotifyCurrentState();
                StartLoad(activity, adUnitId, configuredStyle);
            }
            if (cachedAds.isEmpty() || requestedShowStyle == null
                    || !IsActivityUsable(activity)) {
                NotifyCompleted(onCompleted, "Ad not ready", false);
                return;
            }

            final NativeAd shownAd = TakeCachedAd();
            activeNativeAd = shownAd;
            activeShowCompleted = onCompleted;
            NotifyCurrentState();
            // The seat this show emptied is refilled at once, and whoever
            // is at the head now earns a face. Both wait a turn so this
            // show takes the face it was promised first.
            main.post(() -> {
                if (released) return;

                StartLoad(activity, adUnitId, configuredStyle);
                NativeAd head = HeadAd();
                if (head != null) {
                    PrepareFace(activity, head, configuredStyle);
                }
            });

            if (requestedShowStyle.fullscreen) {
                ReleasePreparedPresentation();
                PreparedFullScreenContent preparedContent =
                        TakePreparedFullScreenContent(activity, shownAd);
                String sessionId =
                        OverlayAdActivity.RegisterSession(
                                activity
                              , this
                              , shownAd
                              , requestedShowStyle
                              , preparedContent);
                activeActivitySessionId = sessionId;
                if (sessionId == null
                        || !OverlayAdActivity.StartSession(
                                activity
                              , sessionId)) {
                    activeActivitySessionId = null;
                    OverlayAdActivity.CancelSession(sessionId);
                    CompletePresentation(
                            shownAd
                          , "Failed to start native full-screen Activity");
                }
                return;
            }

            try {
                OverlayAdPresentation createdPresentation =
                        TakePreparedPresentation(
                                activity
                              , shownAd
                              , requestedShowStyle);
                if (createdPresentation == null) {
                    createdPresentation = CreatePresentation(
                            activity
                          , shownAd
                          , requestedShowStyle);
                    presentation = createdPresentation;
                    if (!createdPresentation.Prepare()) {
                        createdPresentation.Release();
                        presentation = null;
                        CompletePresentation(
                                shownAd
                              , "Failed to prepare ad presentation");
                        return;
                    }
                }
                presentation = createdPresentation;

                if (!createdPresentation.Show()) {
                    createdPresentation.Release();
                    if (presentation == createdPresentation) {
                        presentation = null;
                    }
                    CompletePresentation(shownAd, "");
                }
            } catch (RuntimeException exception) {
                Log.e(TAG, "Failed to materialize native ad", exception);
                CleanupFailedPresentation();
                CompletePresentation(
                        shownAd
                      , "Failed to show ad: "
                                + String.valueOf(exception.getMessage()));
            }
        });
    }

    public void Hide() {
        RunOnMainThread(() -> {
            String activitySessionId = activeActivitySessionId;
            if (activitySessionId != null) {
                OverlayAdActivity.DismissSession(
                        activitySessionId);
                return;
            }

            OverlayAdPresentation currentPresentation =
                    presentation;
            if (currentPresentation != null
                    && currentPresentation.IsShowing()) {
                currentPresentation.Dismiss();
            }
        });
    }

    public boolean IsReady() {
        return configured && !released && activeNativeAd == null
                && cachedCount > 0;
    }

    public boolean IsLoading() {
        return configured && !released && isAdLoading;
    }

    private void NotifyCurrentState() {
        NotifyStateChanged(IsReady(), IsLoading());
    }

    public void Release() {
        released = true;
        InvalidateLoadGeneration();
        main.removeCallbacksAndMessages(null);
        RunOnMainThread(() -> {
            isAdLoading = false;

            ReleasePreparedPresentation();
            ReleasePreparedFullScreenContent();

            String activitySessionId = activeActivitySessionId;
            activeActivitySessionId = null;
            OverlayAdActivity.CancelSession(
                    activitySessionId);

            OverlayAdPresentation currentPresentation =
                    presentation;
            presentation = null;
            if (currentPresentation != null) {
                try {
                    currentPresentation.Release();
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Failed to release native ad presentation"
                          , exception);
                }
            }
            for (CachedAd cached : cachedAds) ForgetAd(cached.ad);
            cachedAds.clear();
            cachedCount = 0;
            cacheActivity = null;
            ForgetAd(activeNativeAd);
            activeNativeAd = null;
            activeShowCompleted = null;

            configuredStyle = null;
            ClearLoadListener();
        });
    }

    private void AddCachedAd(NativeAd ad) {
        ownedAds.add(ad);
        cachedAds.addLast(
                new CachedAd(ad, SystemClock.elapsedRealtime()));
        cachedCount = cachedAds.size();
        ScheduleCacheExpiry();
    }

    // Pops for one show; the caller starts the replacement.
    private NativeAd TakeCachedAd() {
        CachedAd next = cachedAds.pollFirst();
        cachedCount = cachedAds.size();
        ScheduleCacheExpiry();
        return next == null ? null : next.ad;
    }

    private NativeAd HeadAd() {
        CachedAd head = cachedAds.peekFirst();
        return head == null ? null : head.ad;
    }

    // An hour after it arrived a warm ad may no longer be shown, so it is
    // destroyed and replaced. The head ages out first, which is also the
    // one holding a prepared face - hence the rebuild.
    private void HandleCacheExpiry() {
        if (released) return;

        if (RemoveExpiredCachedAds()) {
            RefreshHeadFace();
            NotifyCurrentState();
        }
        StartLoad(cacheActivity, adUnitId, configuredStyle);
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
              , Math.max(
                    0L
                  , nextExpiryAtMs - SystemClock.elapsedRealtime()));
    }

    // Also swept on the way into a Show: the timer runs on uptime, which
    // stops while the device sleeps, so a phone woken after a long night
    // can hold ads older than the timer believes.
    private boolean RemoveExpiredCachedAds() {
        long nowMs = SystemClock.elapsedRealtime();
        CachedAd head = cachedAds.peekFirst();
        // Oldest first, so nothing behind the head can be stale while the
        // head is not: one look answers for the whole queue.
        if (head == null
                || nowMs - head.loadedAtMs < MAX_CACHED_AD_AGE_MS) {
            return false;
        }

        // The prepared face is built on the ad about to be destroyed, so it
        // goes first. Releasing a presentation whose creative is already
        // gone is not a road worth walking.
        ReleasePreparedPresentation();
        ReleasePreparedFullScreenContent();
        for (Iterator<CachedAd> it = cachedAds.iterator(); it.hasNext(); ) {
            CachedAd cached = it.next();
            if (nowMs - cached.loadedAtMs < MAX_CACHED_AD_AGE_MS) continue;

            ForgetAd(cached.ad);
            it.remove();
        }
        cachedCount = cachedAds.size();
        return true;
    }

    // The face went out with the ad it was built on; whoever stands at the
    // front now gets one of their own.
    private void RefreshHeadFace() {
        NativeAd head = HeadAd();
        if (head == null) return;

        PrepareFace(cacheActivity, head, configuredStyle);
    }

    // Ours until destroyed - cached or on screen, both count. The paid
    // event asks this from the SDK's thread.
    private boolean OwnsAd(NativeAd ad) {
        return !released && ad != null && ownedAds.contains(ad);
    }

    private boolean IsHeadAd(NativeAd ad) {
        return ad != null && HeadAd() == ad;
    }

    private void ForgetAd(NativeAd ad) {
        if (ad == null) return;

        ownedAds.remove(ad);
        ad.destroy();
    }

    // The face of the ad at the head, prepaid before any show asks for it:
    // the bottom-slice kind prebuilds a whole presentation, the full-screen
    // kind the Activity's content view. Only one of the two is ever held.
    private void PrepareFace(
            Activity activity
          , NativeAd ad
          , OverlayAdStyle style) {
        if (style == null) {
            ReleasePreparedPresentation();
            ReleasePreparedFullScreenContent();
            return;
        }

        if (style.fullscreen) {
            ReleasePreparedPresentation();
            PrepareFullScreenContent(activity, ad, style);
        } else {
            ReleasePreparedFullScreenContent();
            PreparePresentation(activity, ad, style);
        }
    }

    private OverlayAdPresentation CreatePresentation(
            Activity activity
          , NativeAd ad
          , OverlayAdStyle style) {
        boolean closeOnLeftForPresentation = style.ResolveCloseOnLeft();
        OverlayAdPresentation createdPresentation =
                new OverlayAdPresentation(
                activity
              , ad
              , style.cooldown
              , closeOnLeftForPresentation
              , style.ResolveTimerOnLeft(closeOnLeftForPresentation)
              , style.fullscreen
              , style.heightRatio
              , style.backgroundAlpha
              , style.fakeCloseAutoDismiss);
        createdPresentation.setOnShowListener(ignored -> {
            if (!released && activeNativeAd == ad) NotifyDisplayed();
        });
        createdPresentation.setOnDismissListener(
                ignored -> CompletePresentation(ad, ""));
        return createdPresentation;
    }

    private boolean PreparePresentation(
            Activity activity
          , NativeAd ad
          , OverlayAdStyle style) {
        ReleasePreparedPresentation();
        if (released
                || ad == null
                || style == null
                || !IsHeadAd(ad)
                || !IsActivityUsable(activity)) {
            return false;
        }

        OverlayAdPresentation createdPresentation = null;
        try {
            createdPresentation = CreatePresentation(activity, ad, style);
            if (!createdPresentation.Prepare()
                    || released
                    || !IsHeadAd(ad)
                    || configuredStyle != style
                    || !IsActivityUsable(activity)) {
                createdPresentation.Release();
                return false;
            }

            preparedPresentation = createdPresentation;
            preparedActivity = activity;
            preparedNativeAd = ad;
            preparedStyle = style;
            preparedPresentationMediaSignature = MediaSignature(ad);
            return true;
        } catch (RuntimeException exception) {
            if (createdPresentation != null) {
                try {
                    createdPresentation.Release();
                } catch (RuntimeException cleanupException) {
                    Log.e(TAG, "Failed to clean prepared native ad"
                          , cleanupException);
                }
            }
            Log.e(TAG, "Failed to prepare native ad presentation"
                  , exception);
            return false;
        }
    }

    private void RequestPreparedFaceRebuild(
            OverlayAdStyle requestedStyle) {
        // Every runtime presentation setter must publish a fresh style
        // snapshot and pass through here, so a prepared UI never keeps
        // stale config.
        RunOnMainThread(() -> {
            // A show in progress is no reason to skip: the rebuild only
            // ever touches the prepared face of the ad at the HEAD of
            // the cache, never the one on screen, and skipping it left the
            // replacement fetched during the show holding a stale
            // countdown, which then had to be rebuilt at show time.
            //
            // Both faces rebuild, not just the bottom-slice one. The
            // full-screen face carries the close side, the timer side and
            // the redirect flag baked in at build time, and the take-back
            // check compares ad identity and assets - never the style - so
            // a face left standing here is a face that would be shown with
            // last plan's controls.
            if (released
                    || requestedStyle == null
                    || configuredStyle != requestedStyle) {
                return;
            }

            NativeAd head = HeadAd();
            if (head == null) return;

            PrepareFace(cacheActivity, head, requestedStyle);
        });
    }

    private OverlayAdPresentation TakePreparedPresentation(
            Activity activity
          , NativeAd ad
          , OverlayAdStyle style) {
        if (preparedPresentation == null
                || preparedActivity != activity
                || preparedNativeAd != ad
                || preparedStyle != style
                || !MediaSignature(ad).equals(
                        preparedPresentationMediaSignature)) {
            ReleasePreparedPresentation();
            return null;
        }

        OverlayAdPresentation result = preparedPresentation;
        preparedPresentation = null;
        preparedActivity = null;
        preparedNativeAd = null;
        preparedStyle = null;
        return result;
    }

    private void ReleasePreparedPresentation() {
        OverlayAdPresentation prepared = preparedPresentation;
        preparedPresentation = null;
        preparedActivity = null;
        preparedNativeAd = null;
        preparedStyle = null;
        if (prepared == null) return;

        try {
            prepared.Release();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to release prepared native ad presentation"
                  , exception);
        }
    }

    // The Activity remains the full-screen stage, but its content is built
    // here, at load time: binding a creative - a video above all - is the
    // slow half of the first frame, and prepaying it leaves the show with
    // only the Activity switch itself to spend.
    private void PrepareFullScreenContent(
            Activity activity
          , NativeAd ad
          , OverlayAdStyle style) {
        ReleasePreparedFullScreenContent();
        if (released
                || ad == null
                || style == null
                || !IsHeadAd(ad)
                || !IsActivityUsable(activity)) {
            return;
        }

        try {
            boolean hasVideoContent = ad.getMediaContent() != null
                    && ad.getMediaContent().hasVideoContent();
            int requestedPanelHeight =
                    OverlayAdContentView.ResolveInitialPanelHeight(
                            activity
                          , true
                          , style.heightRatio
                          , hasVideoContent);
            boolean closeOnLeft = style.ResolveCloseOnLeft();
            OverlayAdActivity.CloseRelay closeRelay =
                    new OverlayAdActivity.CloseRelay();
            OverlayAdContentView createdContentView =
                    new OverlayAdContentView(
                            activity
                          , ad
                          , style.cooldown * 1000L
                          , closeOnLeft
                          , style.ResolveTimerOnLeft(closeOnLeft)
                          , true
                          , style.backgroundAlpha
                          , style.fakeCloseAutoDismiss
                          , requestedPanelHeight
                          , closeRelay);
            preparedContentView = createdContentView;
            preparedContentActivity = activity;
            preparedContentAd = ad;
            preparedContentCloseOnLeft = closeOnLeft;
            preparedContentCloseRelay = closeRelay;
            preparedContentMediaSignature = MediaSignature(ad);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to prepare full-screen ad content"
                  , exception);
            ReleasePreparedFullScreenContent();
        }
    }

    private PreparedFullScreenContent TakePreparedFullScreenContent(
            Activity activity
          , NativeAd ad) {
        // Ad identity and the assets' face are the keys - the style is
        // not among them, because SetClose rebuilds this content outright
        // rather than leaving a stale one to be caught here. What this does
        // catch is assets that finished arriving after the build and
        // changed the layout the ad needs.
        if (preparedContentView == null
                || preparedContentActivity != activity
                || preparedContentAd != ad
                || preparedContentCloseRelay == null
                || !MediaSignature(ad).equals(
                        preparedContentMediaSignature)) {
            ReleasePreparedFullScreenContent();
            return null;
        }

        PreparedFullScreenContent result = new PreparedFullScreenContent(
                preparedContentView
              , preparedContentCloseOnLeft
              , preparedContentCloseRelay);
        preparedContentView = null;
        preparedContentActivity = null;
        preparedContentAd = null;
        preparedContentCloseRelay = null;
        return result;
    }

    private void ReleasePreparedFullScreenContent() {
        OverlayAdContentView prepared = preparedContentView;
        preparedContentView = null;
        preparedContentActivity = null;
        preparedContentAd = null;
        preparedContentCloseRelay = null;
        if (prepared == null) return;

        try {
            prepared.Release();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to release prepared full-screen ad content"
                  , exception);
        }
    }

    void OnActivityPresentationDisplayed(
            String sessionId
          , NativeAd shownAd) {
        if (released
                || sessionId == null
                || !sessionId.equals(activeActivitySessionId)
                || activeNativeAd != shownAd) {
            return;
        }
        NotifyDisplayed();
    }

    void OnActivityPresentationCompleted(
            String sessionId
          , NativeAd shownAd
          , String errorMessage) {
        if (sessionId == null
                || !sessionId.equals(activeActivitySessionId)
                || activeNativeAd != shownAd) {
            return;
        }
        activeActivitySessionId = null;
        CompletePresentation(shownAd, errorMessage);
    }

    private void CompletePresentation(
            NativeAd shownAd
          , String errorMessage) {
        // Identity guard dam bao completion va destroy chi chay mot lan.
        if (activeNativeAd != shownAd) return;

        NativeAdCompletedListener onCompleted = activeShowCompleted;
        activeShowCompleted = null;
        activeNativeAd = null;
        activeActivitySessionId = null;
        ForgetAd(shownAd);
        presentation = null;

        // Unity runs the game callback on its main thread first, then the
        // replacement Load. adConsumed distinguishes a completion that
        // spent the cached ad from a Show rejected early.
        NotifyCompleted(
                onCompleted
              , errorMessage == null ? "" : errorMessage
              , true);
        // The wrapper assumes a consumed show leaves nothing cached, which
        // is no longer true once a replacement has been fetched during the
        // show: the truth follows the completion so readiness is right.
        NotifyCurrentState();
    }

    private void CleanupFailedPresentation() {
        OverlayAdPresentation failedPresentation = presentation;
        presentation = null;
        if (failedPresentation == null) return;
        try {
            failedPresentation.Release();
        } catch (RuntimeException cleanupException) {
            Log.e(TAG, "Failed to clean native ad presentation"
                  , cleanupException);
        }
    }

    private boolean IsShowingInternal() {
        String activitySessionId = activeActivitySessionId;
        if (activitySessionId != null) {
            return OverlayAdActivity.IsSessionActive(
                    activitySessionId);
        }
        OverlayAdPresentation currentPresentation = presentation;
        return currentPresentation != null
                && currentPresentation.IsShowing();
    }

    private void NotifyCompleted(
            NativeAdCompletedListener listener
          , String errorMessage
          , boolean adConsumed) {
        if (listener == null) return;
        try {
            listener.OnAdCompleted(errorMessage, adConsumed);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnAdCompleted callback failed", exception);
        }
    }
}
