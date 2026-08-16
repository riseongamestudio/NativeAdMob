package com.riseon.nativeadmob;

import android.app.Activity;
import android.util.Log;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAdOptions;

public final class OverlayAd extends NativeAd {

    static final class OverlayAdStyle {
        final boolean fullscreen;
        final int countdownSec;
        final boolean xRandomSide;
        final boolean numberOppositeSide;
        final float heightRatio;
        final float backgroundAlpha;

        OverlayAdStyle(
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

        OverlayAdStyle WithCountdownSec(int newCountdownSec) {
            return new OverlayAdStyle(
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
    private volatile OverlayAdStyle configuredStyle;

    private volatile com.google.android.gms.ads.nativead.NativeAd nativeAd;
    private volatile com.google.android.gms.ads.nativead.NativeAd activeNativeAd;
    private volatile boolean configured;
    private volatile boolean isAdLoading;
    private OverlayAdPresentation presentation;
    private OverlayAdPresentation preparedPresentation;
    private Activity preparedActivity;
    private com.google.android.gms.ads.nativead.NativeAd preparedNativeAd;
    private OverlayAdStyle preparedStyle;
    private OverlayAdContentView preparedContentView;
    private Activity preparedContentActivity;
    private com.google.android.gms.ads.nativead.NativeAd preparedContentAd;
    private boolean preparedContentCloseOnLeft;
    private OverlayAdActivity.CloseRelay preparedContentCloseRelay;
    private String preparedContentMediaSignature;
    private String preparedPresentationMediaSignature;

    // The face of the creative's assets at one moment. A presentation built
    // at load time is only valid while this stays the same: assets that
    // finish arriving later change the layout the ad needs, and a stale
    // face must be rebuilt rather than shown.
    private static String MediaSignature(
            com.google.android.gms.ads.nativead.NativeAd nativeAd) {
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
        java.util.List<com.google.android.gms.ads.nativead.NativeAd.Image>
                images = nativeAd.getImages();
        if (images != null) {
            for (com.google.android.gms.ads.nativead.NativeAd.Image image
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
                  , "Configure may only be called once per Ad instance");
            return;
        }

        configuredStyle = new OverlayAdStyle(
                fullscreen
              , countdownSec
              , xRandomSide
              , numberOppositeSide
              , heightRatio
              , backgroundAlpha);
        configured = true;
    }

    public void SetCountdownSec(int countdownSec) {
        OverlayAdStyle currentStyle = configuredStyle;
        if (!configured || released || currentStyle == null) {
            Log.e(TAG, "SetCountdownSec ignored before Configure or after Release");
            return;
        }
        OverlayAdStyle updatedStyle =
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
        final OverlayAdStyle loadStyle = configuredStyle;

        RunOnMainThread(() -> {
            if (released || !configured || isAdLoading
                    || !IsActivityUsable(activity)) {
                return;
            }

            // The replacement is fetched while the current ad is still on
            // screen. Waiting for the dismissal costs the player a whole
            // opening: by the time the next placement asks, the load has
            // only just started and there is nothing to show.
            boolean showing =
                    activeNativeAd != null || IsShowingInternal();
            if (showing && nativeAd != null) return;

            if (nativeAd != null) {
                ReleasePreparedPresentation();
                ReleasePreparedFullScreenContent();
                nativeAd.destroy();
                nativeAd = null;
            }
            DoLoadAd(activity, requestAdUnitId, loadStyle);
        });
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
                        if (nativeAd != null) nativeAd.destroy();
                        nativeAd = ad;
                        // Bound to the ad's own identity, never to the
                        // load generation: the replacement now loads while
                        // this ad is still on screen, and a generation
                        // guard would silence the revenue event of the ad
                        // the player is actually watching.
                        BindPaidEvent(
                                ad
                              , requestAdUnitId
                              , () -> nativeAd == ad
                                      || activeNativeAd == ad);
                        OverlayAdStyle presentationStyle =
                                configuredStyle;
                        if (presentationStyle != null
                                && !presentationStyle.fullscreen) {
                            ReleasePreparedFullScreenContent();
                            PreparePresentation(
                                    activity
                                  , ad
                                  , presentationStyle);
                        } else if (presentationStyle != null) {
                            ReleasePreparedPresentation();
                            PrepareFullScreenContent(
                                    activity
                                  , ad
                                  , presentationStyle);
                        } else {
                            ReleasePreparedPresentation();
                            ReleasePreparedFullScreenContent();
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

    public void ShowAd(
            final Activity activity
          , final NativeAdCompletedListener onCompleted) {
        // The config boundary of one show: updates completed before this
        // ShowAd call apply to it, later updates belong to the next one.
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
            if (nativeAd == null || requestedShowStyle == null
                    || !IsActivityUsable(activity)) {
                NotifyCompleted(onCompleted, "Ad not ready", false);
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

    public void HideAd() {
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

    private OverlayAdPresentation CreatePresentation(
            Activity activity
          , com.google.android.gms.ads.nativead.NativeAd ad
          , OverlayAdStyle style) {
        OverlayAdPresentation createdPresentation =
                new OverlayAdPresentation(
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
          , OverlayAdStyle style) {
        ReleasePreparedPresentation();
        if (released
                || ad == null
                || style == null
                || nativeAd != ad
                || !IsActivityUsable(activity)) {
            return false;
        }

        OverlayAdPresentation createdPresentation = null;
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

    private void RequestPreparedPresentationRebuild(
            OverlayAdStyle requestedStyle) {
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

    private OverlayAdPresentation TakePreparedPresentation(
            Activity activity
          , com.google.android.gms.ads.nativead.NativeAd ad
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
          , com.google.android.gms.ads.nativead.NativeAd ad
          , OverlayAdStyle style) {
        ReleasePreparedFullScreenContent();
        if (released
                || ad == null
                || style == null
                || nativeAd != ad
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
            boolean closeOnLeft =
                    style.xRandomSide && Math.random() < 0.5d;
            OverlayAdActivity.CloseRelay closeRelay =
                    new OverlayAdActivity.CloseRelay();
            OverlayAdContentView createdContentView =
                    new OverlayAdContentView(
                            activity
                          , ad
                          , style.countdownSec * 1000L
                          , closeOnLeft
                          , style.numberOppositeSide
                          , true
                          , style.backgroundAlpha
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
          , com.google.android.gms.ads.nativead.NativeAd ad) {
        // Ad identity and the assets' face are the keys. After Configure
        // the style can only change its countdown, which the presented view
        // refreshes on resume - but assets that finished arriving after the
        // build changed the layout the ad needs, so a stale face rebuilds.
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

        NativeAdCompletedListener onCompleted = activeShowCompleted;
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
