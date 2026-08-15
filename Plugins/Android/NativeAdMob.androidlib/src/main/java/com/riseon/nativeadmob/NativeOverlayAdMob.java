package com.riseon.nativeadmob;

import android.app.Activity;
import android.util.Log;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAdOptions;

public final class NativeOverlayAdMob extends NativeAdMob {

    static final class NativeOverlayAdMobStyle {
        final boolean fullscreen;
        final int countdownSec;
        final boolean xRandomSide;
        final boolean numberOppositeSide;
        final float heightRatio;
        final float backgroundAlpha;

        NativeOverlayAdMobStyle(
                boolean fullscreen
              , int countdownSec
              , boolean xRandomSide
              , boolean numberOppositeSide
              , float heightRatio
              , float backgroundAlpha) {
            this.fullscreen = fullscreen;
            this.countdownSec = Math.max(0, countdownSec);
            this.xRandomSide = xRandomSide;
            this.numberOppositeSide = numberOppositeSide;
            this.heightRatio = heightRatio;
            this.backgroundAlpha = backgroundAlpha;
        }

        NativeOverlayAdMobStyle WithCountdownSec(int newCountdownSec) {
            return new NativeOverlayAdMobStyle(
                    fullscreen
                  , newCountdownSec
                  , xRandomSide
                  , numberOppositeSide
                  , heightRatio
                  , backgroundAlpha);
        }

        boolean PausesGame() {
            return fullscreen;
        }
    }

    private final String adUnitId;
    private volatile NativeOverlayAdMobStyle configuredStyle;

    private volatile com.google.android.gms.ads.nativead.NativeAd nativeAd;
    private volatile com.google.android.gms.ads.nativead.NativeAd activeNativeAd;
    private volatile boolean configured;
    private volatile boolean isAdLoading;
    private NativeOverlayAdMobPresentation presentation;
    private NativeOverlayAdMobPresentation preparedPresentation;
    private Activity preparedActivity;
    private com.google.android.gms.ads.nativead.NativeAd preparedNativeAd;
    private NativeOverlayAdMobStyle preparedStyle;
    private NativeAdMobCompletedListener activeShowCompleted;
    private String activeActivitySessionId;

    public NativeOverlayAdMob(String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "NativeOverlayAdMob requires a non-empty adUnitId");
        }
        this.adUnitId = adUnitId;
    }

    // The full-screen listener surface stays flat (no slot index), so this
    // format keeps the NativeAdMobLoadListener shape and owns its delivery.
    private volatile NativeAdMobLoadListener loadListener;

    public void SetListener(NativeAdMobLoadListener listener) {
        if (released) return;
        loadListener = listener;
        NotifyCurrentState();
    }

    private void ClearLoadListener() {
        loadListener = null;
    }

    private void NotifyLoadingStarted() {
        NativeAdMobLoadListener listener = loadListener;
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
        NativeAdMobLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnStateChanged(isReady, isLoading);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnStateChanged callback failed", exception);
        }
    }

    private void NotifyShowNotReady() {
        NativeAdMobLoadListener listener = loadListener;
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
        NativeAdMobLoadListener listener = loadListener;
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
        NativeAdMobLoadListener listener = loadListener;
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
        NativeAdMobLoadListener listener = loadListener;
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
        NativeAdMobLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnPresentationFailed(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnPresentationFailed callback failed", exception);
        }
    }

    public synchronized void Configure(
            boolean fullscreen
          , int countdownSec
          , boolean xRandomSide
          , boolean numberOppositeSide
          , float heightRatio
          , float backgroundAlpha) {
        if (released) {
            Log.e(
                    TAG
                  , "Configure ignored after Release; create a new instance");
            return;
        }
        if (configured) {
            Log.e(
                    TAG
                  , "Configure may only be called once per NativeAdMob instance");
            return;
        }

        configuredStyle = new NativeOverlayAdMobStyle(
                fullscreen
              , countdownSec
              , xRandomSide
              , numberOppositeSide
              , heightRatio
              , backgroundAlpha);
        configured = true;
    }

    public void SetCountdownSec(int countdownSec) {
        NativeOverlayAdMobStyle currentStyle = configuredStyle;
        if (!configured || released || currentStyle == null) {
            Log.e(TAG, "SetCountdownSec ignored before Configure or after Release");
            return;
        }
        NativeOverlayAdMobStyle updatedStyle =
                currentStyle.WithCountdownSec(countdownSec);
        configuredStyle = updatedStyle;
        RequestPreparedPresentationRebuild(updatedStyle);
    }

    public void LoadAd(final Activity activity) {
        if (!configured || released) {
            Log.e(TAG, "LoadAd ignored before Configure or after Release");
            return;
        }
        if (!IsActivityUsable(activity)) {
            Log.e(TAG, "LoadAd ignored because Activity is not usable");
            return;
        }

        // LoadAd chi snapshot phan style can cho request (hien tai la mute
        // policy). Presentation style se duoc snapshot muon hon tai ShowAd.
        final String requestAdUnitId = adUnitId;
        final NativeOverlayAdMobStyle loadStyle = configuredStyle;

        RunOnMainThread(() -> {
            if (released || !configured || isAdLoading
                    || activeNativeAd != null || IsShowingInternal()
                    || !IsActivityUsable(activity)) {
                return;
            }
            if (nativeAd != null) {
                ReleasePreparedPresentation();
                nativeAd.destroy();
                nativeAd = null;
            }
            DoLoadAd(activity, requestAdUnitId, loadStyle);
        });
    }

    private void DoLoadAd(
            final Activity activity
          , final String requestAdUnitId
          , final NativeOverlayAdMobStyle loadStyle) {
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
                        if (nativeAd != null) nativeAd.destroy();
                        nativeAd = ad;
                        BindPaidEvent(
                                ad
                              , requestAdUnitId
                              , () -> IsCurrentLoadGeneration(generation));
                        NativeOverlayAdMobStyle presentationStyle =
                                configuredStyle;
                        if (presentationStyle != null
                                && !presentationStyle.fullscreen) {
                            PreparePresentation(
                                    activity
                                  , ad
                                  , presentationStyle);
                        } else {
                            ReleasePreparedPresentation();
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
                        }

                        @Override
                        public void onAdClicked() {
                            String activitySessionId =
                                    activeActivitySessionId;
                            if (activitySessionId != null) {
                                NativeOverlayAdMobActivity.CommitAdClick(
                                        activitySessionId);
                                return;
                            }
                            NativeAdMobPresentation activePresentation =
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

    public void ShowAd(
            final Activity activity
          , final NativeAdMobCompletedListener onCompleted) {
        // The config boundary of one show: updates completed before this
        // ShowAd call apply to it, later updates belong to the next one.
        final NativeOverlayAdMobStyle requestedShowStyle = configuredStyle;

        RunOnMainThread(() -> {
            if (released) {
                NotifyCompleted(onCompleted, "NativeAdMob released", false);
                return;
            }
            if (!configured || IsShowingInternal()
                    || activeNativeAd != null) {
                NotifyCompleted(
                        onCompleted
                      , !configured
                                ? "NativeAdMob not configured"
                                : "NativeAdMob already showing"
                      , false);
                return;
            }
            if (nativeAd == null || requestedShowStyle == null
                    || !IsActivityUsable(activity)) {
                NotifyCompleted(onCompleted, "NativeAdMob not ready", false);
                return;
            }

            final com.google.android.gms.ads.nativead.NativeAd shownAd =
                    nativeAd;
            nativeAd = null;
            activeNativeAd = shownAd;
            activeShowCompleted = onCompleted;
            NotifyCurrentState();

            if (requestedShowStyle.fullscreen) {
                ReleasePreparedPresentation();
                String sessionId =
                        NativeOverlayAdMobActivity.RegisterSession(
                                activity
                              , this
                              , shownAd
                              , requestedShowStyle);
                activeActivitySessionId = sessionId;
                if (sessionId == null
                        || !NativeOverlayAdMobActivity.StartSession(
                                activity
                              , sessionId)) {
                    activeActivitySessionId = null;
                    NativeOverlayAdMobActivity.CancelSession(sessionId);
                    CompletePresentation(
                            shownAd
                          , "Failed to start native full-screen Activity");
                }
                return;
            }

            try {
                NativeOverlayAdMobPresentation createdPresentation =
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

    public void HideAd() {
        RunOnMainThread(() -> {
            String activitySessionId = activeActivitySessionId;
            if (activitySessionId != null) {
                NativeOverlayAdMobActivity.DismissSession(
                        activitySessionId);
                return;
            }

            NativeOverlayAdMobPresentation currentPresentation =
                    presentation;
            if (currentPresentation != null
                    && currentPresentation.IsShowing()) {
                currentPresentation.Dismiss();
            }
        });
    }

    public boolean IsAdReady() {
        return configured && !released && activeNativeAd == null
                && nativeAd != null;
    }

    public boolean IsAdLoading() {
        return configured && !released && isAdLoading;
    }

    private void NotifyCurrentState() {
        NotifyStateChanged(IsAdReady(), IsAdLoading());
    }

    public void Release() {
        released = true;
        InvalidateLoadGeneration();
        main.removeCallbacksAndMessages(null);
        RunOnMainThread(() -> {
            isAdLoading = false;

            ReleasePreparedPresentation();

            String activitySessionId = activeActivitySessionId;
            activeActivitySessionId = null;
            NativeOverlayAdMobActivity.CancelSession(
                    activitySessionId);

            NativeOverlayAdMobPresentation currentPresentation =
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
            if (nativeAd != null) {
                nativeAd.destroy();
                nativeAd = null;
            }
            if (activeNativeAd != null) {
                activeNativeAd.destroy();
                activeNativeAd = null;
            }
            activeShowCompleted = null;

            configuredStyle = null;
            ClearLoadListener();
        });
    }

    private NativeOverlayAdMobPresentation CreatePresentation(
            Activity activity
          , com.google.android.gms.ads.nativead.NativeAd ad
          , NativeOverlayAdMobStyle style) {
        NativeOverlayAdMobPresentation createdPresentation =
                new NativeOverlayAdMobPresentation(
                activity
              , ad
              , style.countdownSec
              , style.xRandomSide
              , style.numberOppositeSide
              , style.fullscreen
              , style.heightRatio
              , style.backgroundAlpha);
        createdPresentation.setOnShowListener(ignored -> {
            if (!released && activeNativeAd == ad) NotifyDisplayed();
        });
        createdPresentation.setOnDismissListener(
                ignored -> CompletePresentation(ad, ""));
        return createdPresentation;
    }

    private boolean PreparePresentation(
            Activity activity
          , com.google.android.gms.ads.nativead.NativeAd ad
          , NativeOverlayAdMobStyle style) {
        ReleasePreparedPresentation();
        if (released
                || ad == null
                || style == null
                || nativeAd != ad
                || !IsActivityUsable(activity)) {
            return false;
        }

        NativeOverlayAdMobPresentation createdPresentation = null;
        try {
            createdPresentation = CreatePresentation(activity, ad, style);
            if (!createdPresentation.Prepare()
                    || released
                    || nativeAd != ad
                    || configuredStyle != style
                    || !IsActivityUsable(activity)) {
                createdPresentation.Release();
                return false;
            }

            preparedPresentation = createdPresentation;
            preparedActivity = activity;
            preparedNativeAd = ad;
            preparedStyle = style;
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

    private void RequestPreparedPresentationRebuild(
            NativeOverlayAdMobStyle requestedStyle) {
        // Every runtime presentation setter must publish a fresh style
        // snapshot and pass through here, so a prepared UI never keeps
        // stale config.
        RunOnMainThread(() -> {
            if (released
                    || requestedStyle == null
                    || requestedStyle.fullscreen
                    || configuredStyle != requestedStyle
                    || nativeAd == null
                    || activeNativeAd != null) {
                return;
            }

            Activity activity = preparedActivity;
            com.google.android.gms.ads.nativead.NativeAd ad = nativeAd;
            ReleasePreparedPresentation();
            if (IsActivityUsable(activity)) {
                PreparePresentation(activity, ad, requestedStyle);
            }
        });
    }

    private NativeOverlayAdMobPresentation TakePreparedPresentation(
            Activity activity
          , com.google.android.gms.ads.nativead.NativeAd ad
          , NativeOverlayAdMobStyle style) {
        if (preparedPresentation == null
                || preparedActivity != activity
                || preparedNativeAd != ad
                || preparedStyle != style) {
            ReleasePreparedPresentation();
            return null;
        }

        NativeOverlayAdMobPresentation result = preparedPresentation;
        preparedPresentation = null;
        preparedActivity = null;
        preparedNativeAd = null;
        preparedStyle = null;
        return result;
    }

    private void ReleasePreparedPresentation() {
        NativeOverlayAdMobPresentation prepared = preparedPresentation;
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

    void OnActivityPresentationDisplayed(
            String sessionId
          , com.google.android.gms.ads.nativead.NativeAd shownAd) {
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
          , com.google.android.gms.ads.nativead.NativeAd shownAd
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
            com.google.android.gms.ads.nativead.NativeAd shownAd
          , String errorMessage) {
        // Identity guard dam bao completion va destroy chi chay mot lan.
        if (activeNativeAd != shownAd) return;

        NativeAdMobCompletedListener onCompleted = activeShowCompleted;
        activeShowCompleted = null;
        activeNativeAd = null;
        activeActivitySessionId = null;
        if (shownAd != null) shownAd.destroy();
        presentation = null;

        // Unity runs the game callback on its main thread first, then the
        // replacement LoadAd. adConsumed distinguishes a completion that
        // spent the cached ad from a ShowAd rejected early.
        NotifyCompleted(
                onCompleted
              , errorMessage == null ? "" : errorMessage
              , true);
    }

    private void CleanupFailedPresentation() {
        NativeOverlayAdMobPresentation failedPresentation = presentation;
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
            return NativeOverlayAdMobActivity.IsSessionActive(
                    activitySessionId);
        }
        NativeOverlayAdMobPresentation currentPresentation = presentation;
        return currentPresentation != null
                && currentPresentation.IsShowing();
    }

    private void NotifyCompleted(
            NativeAdMobCompletedListener listener
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
