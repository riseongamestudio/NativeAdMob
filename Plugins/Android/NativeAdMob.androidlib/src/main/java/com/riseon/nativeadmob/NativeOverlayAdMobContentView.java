package com.riseon.nativeadmob;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.Layout;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.List;

final class NativeOverlayAdMobContentView extends FrameLayout {
    private static final class SingleClickNativeAdContainer
            extends FrameLayout {
        private boolean clickCommitted;

        SingleClickNativeAdContainer(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (clickCommitted) return true;
            return super.dispatchTouchEvent(event);
        }

        void CommitAdClick() {
            clickCommitted = true;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            if (hasWindowFocus) clickCommitted = false;
        }
    }

    private static final String TAG = "NativeOverlayAdMob";
    private static final String ATTRIBUTION_TEXT = "NativeAdMob";
    private static final String SECONDARY_TEXT_COLOR = "#CCFFFFFF";
    private static final String ATTRIBUTION_BACKGROUND_COLOR = "#FFFFC107";
    private static final String TIMER_BACKGROUND_COLOR = "#66000000";
    private static final String CLOSE_BACKGROUND_COLOR = "#AA000000";
    private static final String CTA_BACKGROUND_COLOR = "#FF2196F3";
    private static final String CTA_BORDER_COLOR = "#FF1565C0";
    private static final String CTA_TEXT_COLOR = "#FFFFFFFF";
    private static final String CTA_RIPPLE_COLOR = "#55FFFFFF";
    private static final int CTA_BORDER_WIDTH_DP = 1;
    private static final String CLOSE_CONTENT_DESCRIPTION = "Close ad";

    private static final float MIN_VIDEO_MEDIA_SIZE_DP = 120f;
    // The 120dp floor is video's; a creative with no video keeps its picture
    // in shorter panels instead of handing the band to the icon.
    private static final float MIN_IMAGE_MEDIA_SIZE_DP = 48f;
    private static final float DEFAULT_MEDIA_ASPECT_RATIO = 1f;
    private static final float DEFAULT_HEIGHT_RATIO = 0.5f;
    private static final float MAX_COLOR_CHANNEL = 255f;
    private static final float FULL_SCREEN_DEFAULT_ALPHA = 0.80f;
    private static final float COLLAPSIBLE_DEFAULT_ALPHA = 0.95f;
    private static final int FULL_SCREEN_BACKGROUND_RGB = 0x000000;
    private static final int COLLAPSIBLE_BACKGROUND_RGB = 0x1B2029;
    // 20dp a side spent 40dp of every screen on nothing the ad needed.
    private static final int HORIZONTAL_PADDING_DP = 8;
    private static final int CONTROL_STRIP_HEIGHT_DP = 34;
    private static final int CONTROL_GAP_DP = 2;
    private static final int RIGHT_CONTROL_INSET_DP = 18;
    private static final int MIN_BADGE_SIZE_PX = 15;
    private static final int ATTRIBUTION_WIDTH_DP = 24;
    private static final int ATTRIBUTION_HEIGHT_DP = 18;
    private static final int AD_CHOICES_WIDTH_PX = 76;
    private static final int AD_CHOICES_SIZE_DP = 24;
    private static final int MIN_ICON_SIZE_DP = 36;
    private static final int MAX_ICON_SIZE_DP = 64;
    private static final int ICON_GAP_DP = 8;
    // The floor gives ground in a tight panel; the ceiling is what a roomy
    // panel is allowed to spend, and lowering it would take from exactly the
    // case that has room to spare.
    private static final int MIN_CALL_TO_ACTION_HEIGHT_DP = 44;
    private static final int MAX_CALL_TO_ACTION_HEIGHT_DP = 56;
    private static final int MIN_IDENTITY_VERTICAL_PADDING_DP = 2;
    private static final int MAX_IDENTITY_VERTICAL_PADDING_DP = 6;
    private static final int MIN_BODY_BOTTOM_PADDING_DP = 4;
    private static final int MAX_BODY_BOTTOM_PADDING_DP = 8;
    private static final int MIN_HEADLINE_TEXT_SIZE_SP = 15;
    private static final int MAX_HEADLINE_TEXT_SIZE_SP = 20;
    private static final int MIN_ADVERTISER_TEXT_SIZE_SP = 12;
    private static final int MAX_ADVERTISER_TEXT_SIZE_SP = 14;
    private static final int MIN_BODY_TEXT_SIZE_SP = 13;
    private static final int MAX_BODY_TEXT_SIZE_SP = 16;
    private static final int MIN_CALL_TO_ACTION_TEXT_SIZE_SP = 14;
    private static final int MAX_CALL_TO_ACTION_TEXT_SIZE_SP = 17;
    private static final int COLLAPSIBLE_HEADLINE_MAX_LINE_COUNT = 2;
    private static final int MIN_BODY_LINE_COUNT = 1;
    private static final int MAX_BODY_LINE_COUNT = 3;
    private static final int RESPONSIVE_SCALE_SEARCH_ITERATIONS = 8;
    private static final float MAX_ICON_ROW_WIDTH_RATIO = 0.33f;
    // A line that moves is harder to read than one that sits still, so text
    // that has to scroll never does it at the size that failed to fit whole.
    private static final float MARQUEE_TEXT_SHRINK = 0.8f;
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long COUNTDOWN_INTERVAL_MS = 250L;
    // A portrait creative in a centred column leaves dead gutters both sides.
    // Below this aspect the media takes the panel's full height on the left
    // and everything else moves into a rail beside it - provided the media
    // still meets its policy minimum and the rail keeps enough width to read.
    private static final float SIDE_MEDIA_MAX_ASPECT = 0.85f;
    private static final float SIDE_MEDIA_MAX_WIDTH_SHARE = 0.62f;
    private static final int SIDE_MEDIA_MIN_RAIL_DP = 120;
    // Ratio first: the panel is the height the game asked for. Media needs its
    // policy minimum plus a usable row of content under it; a panel that
    // cannot host that drops the media rather than growing past the request,
    // and only a panel under the absolute floor is ever grown - minimally.
    private static final int MIN_MEDIA_LOWER_CONTENT_DP = 88;
    private static final int MIN_PANEL_HEIGHT_DP = 48;
    // Below this the panel is a strip, and the strip is one row: icon and
    // headline sharing the line with the call to action, the corner controls
    // overlaid at its ends, the headline scrolling when long.
    private static final int TICKER_MAX_PANEL_DP = 120;
    private static final int TICKER_CTA_MIN_HEIGHT_DP = 32;

    private final com.google.android.gms.ads.nativead.NativeAd nativeAd;
    private long countDownRemainingMs;
    private boolean sideMediaLayout;
    private final boolean closeOnLeft;
    private final boolean numberOpposite;
    private final boolean fullscreen;
    private final float backgroundAlpha;
    private final Runnable onClose;
    private int resolvedPanelHeight;
    private CountDownTimer timer;
    private SingleClickNativeAdContainer nativeAdContainer;
    private NativeAdView nativeAdView;
    private TextView countdown;
    private TextView close;
    private boolean released;

    NativeOverlayAdMobContentView(
            Context context
          , com.google.android.gms.ads.nativead.NativeAd nativeAd
          , int countDownSec
          , boolean xRandom
          , boolean numberOpposite
          , boolean fullscreen
          , float backgroundAlpha
          , int requestedPanelHeight
          , Runnable onClose) {
        this(
                context
              , nativeAd
              , Math.max(0, countDownSec) * MILLIS_PER_SECOND
              , xRandom && Math.random() < 0.5d
              , numberOpposite
              , fullscreen
              , backgroundAlpha
              , requestedPanelHeight
              , onClose);
    }

    NativeOverlayAdMobContentView(
            Context context
          , com.google.android.gms.ads.nativead.NativeAd nativeAd
          , long countDownRemainingMs
          , boolean closeOnLeft
          , boolean numberOpposite
          , boolean fullscreen
          , float backgroundAlpha
          , int requestedPanelHeight
          , Runnable onClose) {
        super(context);
        this.nativeAd = nativeAd;
        this.countDownRemainingMs = Math.max(0L, countDownRemainingMs);
        this.closeOnLeft = closeOnLeft;
        this.numberOpposite = numberOpposite;
        this.fullscreen = fullscreen;
        this.backgroundAlpha = backgroundAlpha;
        this.onClose = onClose;
        Build(requestedPanelHeight);
        ApplyFullscreenContentInset();
    }

    static int ResolveInitialPanelHeight(
            Context context
          , boolean fullscreen
          , float heightRatio
          , boolean hasVideoContent) {
        DisplayMetrics displayMetrics =
                context.getResources().getDisplayMetrics();
        if (fullscreen) return displayMetrics.heightPixels;

        float density = displayMetrics.density;
        int minimumMediaSize =
                (int) Math.ceil(
                        (hasVideoContent
                                ? MIN_VIDEO_MEDIA_SIZE_DP
                                : MIN_IMAGE_MEDIA_SIZE_DP)
                                * density);
        int controlStripHeight =
                (int) (CONTROL_STRIP_HEIGHT_DP * density);
        float ratio = heightRatio;
        if (Float.isNaN(ratio) || Float.isInfinite(ratio)) {
            Log.w(TAG, "heightRatio is not finite; using 0.5");
            ratio = DEFAULT_HEIGHT_RATIO;
        }
        ratio = Math.max(0f, Math.min(1f, ratio));
        int requestedHeight =
                (int) (displayMetrics.heightPixels * ratio);
        requestedHeight = Math.max(
                requestedHeight
              , (int) (MIN_PANEL_HEIGHT_DP * density));
        return Math.min(displayMetrics.heightPixels, requestedHeight);
    }

    int GetResolvedPanelHeight() {
        return resolvedPanelHeight;
    }

    void Release() {
        if (released) return;
        released = true;

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        NativeAdView currentNativeAdView = nativeAdView;
        nativeAdView = null;
        if (currentNativeAdView != null) {
            try {
                currentNativeAdView.destroy();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Failed to release native ad view", exception);
            }
        }

        removeAllViews();
        nativeAdContainer = null;
        countdown = null;
        close = null;
    }

    void OnPresented() {
        OnPresented(countDownRemainingMs);
    }

    void OnPresented(long remainingMs) {
        if (released) return;
        countDownRemainingMs = Math.max(0L, remainingMs);
        StartCountdown();
    }

    void OnPaused() {
        if (timer == null) return;
        timer.cancel();
        timer = null;
    }

    void CommitAdClick() {
        if (nativeAdContainer != null) {
            nativeAdContainer.CommitAdClick();
        }
    }

    private void Build(int requestedPanelHeight) {
        Context context = getContext();
        DisplayMetrics displayMetrics =
                context.getResources().getDisplayMetrics();
        float density = displayMetrics.density;
        int horizontalPadding =
                (int) (HORIZONTAL_PADDING_DP * density);
        com.google.android.gms.ads.MediaContent mediaContent =
                nativeAd.getMediaContent();
        boolean hasVideoContent =
                mediaContent != null
                        && mediaContent.hasVideoContent();
        Drawable mainMediaImage =
                mediaContent != null
                        ? mediaContent.getMainImage()
                        : null;
        Drawable fallbackMediaImage =
                !hasVideoContent
                        && mainMediaImage == null
                        ? FindFallbackMediaImage()
                        : null;
        boolean hasDisplayableMedia =
                hasVideoContent
                        || mainMediaImage != null
                        || fallbackMediaImage != null;
        int minimumMediaSize =
                (int) Math.ceil(
                        (hasVideoContent
                                ? MIN_VIDEO_MEDIA_SIZE_DP
                                : MIN_IMAGE_MEDIA_SIZE_DP)
                                * density);
        float mediaAspectRatio =
                GetMediaAspectRatio(fallbackMediaImage);
        int controlStripHeight =
                (int) (CONTROL_STRIP_HEIGHT_DP * density);
        boolean panelHostsMedia = fullscreen
                || requestedPanelHeight
                        >= minimumMediaSize
                                + (int) (MIN_MEDIA_LOWER_CONTENT_DP
                                        * density);
        // With the media dropped - or a creative that never had any - the
        // registered icon stands in for it, the way the in-feed slot already
        // works: it leaves the identity row and takes the media's band.
        boolean tickerLayout = !fullscreen
                && requestedPanelHeight
                        < (int) (TICKER_MAX_PANEL_DP * density);
        boolean iconHero = !fullscreen
                && !tickerLayout
                && (!hasDisplayableMedia || !panelHostsMedia)
                && nativeAd.getIcon() != null
                && nativeAd.getIcon().getDrawable() != null;
        hasDisplayableMedia = hasDisplayableMedia
                && panelHostsMedia
                && !tickerLayout;

        ConfigureBackground();

        nativeAdView = new NativeAdView(context);
        LinearLayout contentColumn = new LinearLayout(context);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        contentColumn.setGravity(Gravity.CENTER);
        contentColumn.setPadding(
                horizontalPadding
              , controlStripHeight
              , horizontalPadding
              , 0);
        // The media is the one child allowed to ignore the side padding: it
        // bleeds edge to edge through negative margins while every text stays
        // inset, so the padding cannot cost the picture anything.
        contentColumn.setClipToPadding(false);

        MediaView mediaView = new MediaView(context);
        mediaView.setMinimumWidth(minimumMediaSize);
        mediaView.setMinimumHeight(minimumMediaSize);
        mediaView.setBackgroundColor(Color.BLACK);
        mediaView.setImageScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams mediaCreateParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , minimumMediaSize);
        mediaCreateParams.leftMargin = -horizontalPadding;
        mediaCreateParams.rightMargin = -horizontalPadding;
        mediaView.setLayoutParams(mediaCreateParams);
        if (hasVideoContent || mainMediaImage != null) {
            mediaView.setMediaContent(mediaContent);
        } else if (fallbackMediaImage != null) {
            ImageView fallbackImageView = new ImageView(context);
            fallbackImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            fallbackImageView.setImageDrawable(fallbackMediaImage);
            mediaView.addView(
                    fallbackImageView
                  , new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.MATCH_PARENT));
        }

        LinearLayout identityRow = new LinearLayout(context);
        identityRow.setOrientation(LinearLayout.HORIZONTAL);
        identityRow.setGravity(Gravity.CENTER_VERTICAL);
        identityRow.setPadding(
                0
              , (int) ((fullscreen
                        ? MAX_IDENTITY_VERTICAL_PADDING_DP
                        : MIN_IDENTITY_VERTICAL_PADDING_DP)
                        * density)
              , 0
              , (int) (MIN_IDENTITY_VERTICAL_PADDING_DP * density));
        identityRow.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams iconLayoutParams =
                new LinearLayout.LayoutParams(
                        (int) (MIN_ICON_SIZE_DP * density)
                      , (int) (MIN_ICON_SIZE_DP * density));
        iconLayoutParams.setMarginEnd((int) (ICON_GAP_DP * density));
        if (iconHero) {
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            identityRow.addView(icon, iconLayoutParams);
        }

        LinearLayout identityText = new LinearLayout(context);
        identityText.setOrientation(LinearLayout.VERTICAL);
        identityText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams identityTextLayoutParams =
                new LinearLayout.LayoutParams(
                        0
                      , ViewGroup.LayoutParams.WRAP_CONTENT
                      , 1f);

        TextView headline = new TextView(context);
        headline.setTextColor(Color.WHITE);
        headline.setTextSize(
                fullscreen
                        ? 18f
                        : MIN_HEADLINE_TEXT_SIZE_SP);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headline.setSingleLine(false);

        TextView advertiser = new TextView(context);
        advertiser.setTextColor(Color.parseColor(SECONDARY_TEXT_COLOR));
        advertiser.setTextSize(MIN_ADVERTISER_TEXT_SIZE_SP);
        advertiser.setMaxLines(1);
        MarqueeWhenTooLong(advertiser);

        NativeAdMobStarRatingView starRating =
                new NativeAdMobStarRatingView(context);
        LinearLayout.LayoutParams starRatingLayoutParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT);
        starRatingLayoutParams.gravity = Gravity.START;

        identityText.addView(headline);
        identityText.addView(advertiser);
        identityText.addView(starRating, starRatingLayoutParams);
        identityRow.addView(identityText, identityTextLayoutParams);

        TextView body = new TextView(context);
        body.setTextColor(Color.parseColor(SECONDARY_TEXT_COLOR));
        body.setTextSize(MIN_BODY_TEXT_SIZE_SP);
        body.setMaxLines(MAX_BODY_LINE_COUNT);
        body.setPadding(
                0
              , 0
              , 0
              , (int) (MIN_BODY_BOTTOM_PADDING_DP * density));

        Button callToAction = new Button(context);
        StyleCallToAction(callToAction, density);
        callToAction.setTextSize(MIN_CALL_TO_ACTION_TEXT_SIZE_SP);
        callToAction.setSingleLine(false);
        callToAction.setMinHeight(
                (int) (MIN_CALL_TO_ACTION_HEIGHT_DP * density));
        callToAction.setMinimumHeight(
                (int) (MIN_CALL_TO_ACTION_HEIGHT_DP * density));

        int sidePanelHeight = fullscreen
                ? displayMetrics.heightPixels
                : requestedPanelHeight;
        sideMediaLayout = hasDisplayableMedia
                && ShouldUseSideMedia(
                        displayMetrics
                      , mediaAspectRatio
                      , sidePanelHeight);
        if (sideMediaLayout) {
            int sideMediaWidth = SideMediaWidth(
                    displayMetrics
                  , mediaAspectRatio
                  , sidePanelHeight);
            Log.i(
                    TAG
                  , "Full screen side-media layout: media "
                            + sideMediaWidth + "x" + sidePanelHeight);
            contentColumn.setPadding(0, 0, 0, 0);
            LinearLayout sideRow = new LinearLayout(context);
            sideRow.setOrientation(LinearLayout.HORIZONTAL);
            sideRow.addView(
                    mediaView
                  , new LinearLayout.LayoutParams(
                        sideMediaWidth
                      , ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout rail = new LinearLayout(context);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setGravity(Gravity.CENTER_VERTICAL);
            // The corner controls and AdChoices live over the rail's top, so
            // the rail alone keeps the strip inset; the media needs none.
            rail.setPadding(
                    horizontalPadding
                  , controlStripHeight
                  , horizontalPadding
                  , 0);
            rail.addView(identityRow);
            rail.addView(body);
            rail.addView(
                    callToAction
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
            sideRow.addView(
                    rail
                  , new LinearLayout.LayoutParams(
                        0
                      , ViewGroup.LayoutParams.MATCH_PARENT
                      , 1f));
            contentColumn.addView(
                    sideRow
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , sidePanelHeight));
        } else if (tickerLayout) {
            contentColumn.setPadding(0, 0, 0, 0);
            body.setVisibility(View.GONE);
            advertiser.setVisibility(View.GONE);
            starRating.setVisibility(View.GONE);
            headline.setSingleLine(true);
            headline.setMaxLines(1);
            callToAction.setMinHeight(
                    (int) (TICKER_CTA_MIN_HEIGHT_DP * density));
            callToAction.setMinimumHeight(
                    (int) (TICKER_CTA_MIN_HEIGHT_DP * density));

            LinearLayout ticker = new LinearLayout(context);
            ticker.setOrientation(LinearLayout.HORIZONTAL);
            ticker.setGravity(Gravity.CENTER_VERTICAL);
            int tickerControlReserve =
                    (int) ((CONTROL_STRIP_HEIGHT_DP
                                    + RIGHT_CONTROL_INSET_DP)
                            * density);
            ticker.setPadding(
                    tickerControlReserve
                  , 0
                  , tickerControlReserve
                  , 0);
            identityRow.setPadding(0, 0, 0, 0);
            ticker.addView(
                    identityRow
                  , new LinearLayout.LayoutParams(
                        0
                      , ViewGroup.LayoutParams.WRAP_CONTENT
                      , 1f));
            ticker.addView(
                    callToAction
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
            contentColumn.addView(
                    ticker
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            if (hasDisplayableMedia) {
                contentColumn.addView(mediaView);
            } else if (iconHero) {
                contentColumn.addView(
                        icon
                      , new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT
                          , 0
                          , 1f));
            }
            contentColumn.addView(identityRow);
            contentColumn.addView(body);
            contentColumn.addView(
                    callToAction
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        nativeAdView.addView(
                contentColumn
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.MATCH_PARENT));

        TextView attribution = CreateAttributionView(context, density);
        FrameLayout.LayoutParams attributionLayoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT
                      , Gravity.TOP | Gravity.START);
        nativeAdView.addView(attribution, attributionLayoutParams);

        int adChoicesReserveWidth = Math.max(
                (int) Math.ceil(AD_CHOICES_SIZE_DP * density)
              , AD_CHOICES_WIDTH_PX);
        int adChoicesReserveHeight = Math.max(
                (int) Math.ceil(AD_CHOICES_SIZE_DP * density)
              , MIN_BADGE_SIZE_PX);
        View adChoicesReserve = new View(context);
        adChoicesReserve.setClickable(false);
        adChoicesReserve.setFocusable(false);
        nativeAdView.addView(
                adChoicesReserve
              , new FrameLayout.LayoutParams(
                    adChoicesReserveWidth
                  , adChoicesReserveHeight
                  , Gravity.TOP | Gravity.END));

        if (hasDisplayableMedia) {
            nativeAdView.setMediaView(mediaView);
        }
        nativeAdView.setIconView(icon);
        nativeAdView.setHeadlineView(headline);
        nativeAdView.setAdvertiserView(advertiser);
        nativeAdView.setStarRatingView(starRating);
        nativeAdView.setBodyView(body);
        nativeAdView.setCallToActionView(callToAction);
        BindAssets(
                headline
              , advertiser
              , starRating
              , body
              , icon
              , callToAction);
        KeepTextWholeOrScrolling(headline);
        KeepTextWholeOrScrolling(body);
        KeepTextWholeOrScrolling(advertiser);
        if (!fullscreen && !tickerLayout) {
            ConfigureResponsiveCollapsibleContent(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , hasDisplayableMedia && !sideMediaLayout
                  , requestedPanelHeight
                  , density
                  , contentColumn
                  , mediaView
                  , identityRow
                  , headline
                  , advertiser
                  , starRating
                  , body
                  , iconHero ? null : icon
                  , callToAction);
        }
        if (!iconHero) {
            MatchIconSizeToIdentityText(
                    icon
                  , identityText
                  , identityRow
                  , density);
        }
        nativeAdView.setNativeAd(nativeAd);
        nativeAdContainer = new SingleClickNativeAdContainer(context);
        nativeAdContainer.addView(
                nativeAdView
               , new FrameLayout.LayoutParams(
                     ViewGroup.LayoutParams.MATCH_PARENT
                   , ViewGroup.LayoutParams.MATCH_PARENT));
        addView(
                nativeAdContainer
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.MATCH_PARENT));

        int closeGravity = Gravity.TOP
                | (closeOnLeft ? Gravity.START : Gravity.END);
        int numberGravity = numberOpposite
                ? Gravity.TOP
                    | (closeOnLeft ? Gravity.END : Gravity.START)
                : closeGravity;
        int controlSize = (int) (CONTROL_STRIP_HEIGHT_DP * density);
        int controlGap = Math.max(
                1
              , (int) (CONTROL_GAP_DP * density));
        int leftControlInset = attribution.getMinWidth() + controlGap;
        int rightControlInset =
                (int) (RIGHT_CONTROL_INSET_DP * density);

        countdown = CreateControlView(
                context
              , String.valueOf(
                        (int) Math.ceil(
                                countDownRemainingMs
                                        / (double) MILLIS_PER_SECOND))
              , 15f
              , TIMER_BACKGROUND_COLOR);
        FrameLayout.LayoutParams countdownLayoutParams =
                new FrameLayout.LayoutParams(
                        controlSize
                      , controlSize
                      , numberGravity);
        countdownLayoutParams.setMargins(
                leftControlInset
              , 0
              , rightControlInset
              , 0);
        countdown.setLayoutParams(countdownLayoutParams);

        close = CreateControlView(
                context
              , "\u2715"
              , 16f
              , CLOSE_BACKGROUND_COLOR);
        close.setVisibility(View.GONE);
        close.setContentDescription(CLOSE_CONTENT_DESCRIPTION);
        FrameLayout.LayoutParams closeLayoutParams =
                new FrameLayout.LayoutParams(
                        controlSize
                      , controlSize
                      , closeGravity);
        closeLayoutParams.setMargins(
                leftControlInset
              , 0
              , rightControlInset
              , 0);
        close.setLayoutParams(closeLayoutParams);
        close.setOnClickListener(view -> {
            if (onClose != null) onClose.run();
        });

        addView(countdown);
        addView(close);
        ResolveContentHeight(
                displayMetrics
              , horizontalPadding
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , requestedPanelHeight
              , contentColumn
              , mediaView);
        if (hasDisplayableMedia && !sideMediaLayout) {
            ObserveMediaSize(
                    minimumMediaSize
                  , contentColumn
                  , mediaView
                  , fallbackMediaImage);
        }
        ObserveBadgePositions(
                attribution
              , countdown
              , close
              , controlGap
              , rightControlInset);
    }

    private void ConfigureResponsiveCollapsibleContent(
            DisplayMetrics displayMetrics
          , int horizontalPadding
          , int minimumMediaSize
          , float mediaAspectRatio
          , boolean hasDisplayableMedia
          , int requestedPanelHeight
          , float density
          , LinearLayout contentColumn
          , MediaView mediaView
          , LinearLayout identityRow
          , TextView headline
          , TextView advertiser
          , NativeAdMobStarRatingView starRating
          , TextView body
          , ImageView icon
          , Button callToAction) {
        headline.setMaxLines(COLLAPSIBLE_HEADLINE_MAX_LINE_COUNT);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        body.setMaxLines(MAX_BODY_LINE_COUNT);
        body.setEllipsize(TextUtils.TruncateAt.END);
        ApplyResponsiveContentScale(
                0f
              , density
              , identityRow
              , headline
              , advertiser
              , body
              , icon
              , callToAction);

        boolean contentFits = ContentFitsWithMinimumMedia(
                displayMetrics
              , horizontalPadding
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , requestedPanelHeight
              , contentColumn
              , mediaView);
        while (body.getVisibility() == View.VISIBLE
                && body.getMaxLines() > MIN_BODY_LINE_COUNT
                && !contentFits) {
            body.setMaxLines(body.getMaxLines() - 1);
            contentFits = ContentFitsWithMinimumMedia(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , hasDisplayableMedia
                  , requestedPanelHeight
                  , contentColumn
                  , mediaView);
        }

        if (!contentFits && body.getVisibility() == View.VISIBLE) {
            body.setVisibility(View.GONE);
            contentFits = ContentFitsWithMinimumMedia(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , hasDisplayableMedia
                  , requestedPanelHeight
                  , contentColumn
                  , mediaView);
        }
        if (!contentFits && starRating.getVisibility() == View.VISIBLE) {
            starRating.setVisibility(View.GONE);
            contentFits = ContentFitsWithMinimumMedia(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , hasDisplayableMedia
                  , requestedPanelHeight
                  , contentColumn
                  , mediaView);
        }
        if (!contentFits && advertiser.getVisibility() == View.VISIBLE) {
            advertiser.setVisibility(View.GONE);
            contentFits = ContentFitsWithMinimumMedia(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , hasDisplayableMedia
                  , requestedPanelHeight
                  , contentColumn
                  , mediaView);
        }

        if (!contentFits) return;
        if (!ContentFitsWithNaturalMedia(
                displayMetrics
              , horizontalPadding
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , requestedPanelHeight
              , contentColumn
              , mediaView)) {
            return;
        }

        ApplyResponsiveContentScale(
                1f
              , density
              , identityRow
              , headline
              , advertiser
              , body
              , icon
              , callToAction);
        if (ContentFitsWithNaturalMedia(
                displayMetrics
              , horizontalPadding
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , requestedPanelHeight
              , contentColumn
              , mediaView)) {
            return;
        }

        float minimumScale = 0f;
        float maximumScale = 1f;
        for (int iteration = 0;
                iteration < RESPONSIVE_SCALE_SEARCH_ITERATIONS;
                iteration++) {
            float candidateScale =
                    (minimumScale + maximumScale) / 2f;
            ApplyResponsiveContentScale(
                    candidateScale
                  , density
                  , identityRow
                  , headline
                  , advertiser
                  , body
                  , icon
                  , callToAction);
            if (ContentFitsWithNaturalMedia(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , hasDisplayableMedia
                  , requestedPanelHeight
                  , contentColumn
                  , mediaView)) {
                minimumScale = candidateScale;
            } else {
                maximumScale = candidateScale;
            }
        }
        ApplyResponsiveContentScale(
                minimumScale
              , density
              , identityRow
              , headline
              , advertiser
              , body
              , icon
              , callToAction);
    }

    private static void ApplyResponsiveContentScale(
            float scale
          , float density
          , LinearLayout identityRow
          , TextView headline
          , TextView advertiser
          , TextView body
          , ImageView icon
          , Button callToAction) {
        float resolvedScale = Math.max(0f, Math.min(1f, scale));
        headline.setTextSize(
                Interpolate(
                        MIN_HEADLINE_TEXT_SIZE_SP
                      , MAX_HEADLINE_TEXT_SIZE_SP
                      , resolvedScale));
        advertiser.setTextSize(
                Interpolate(
                        MIN_ADVERTISER_TEXT_SIZE_SP
                      , MAX_ADVERTISER_TEXT_SIZE_SP
                      , resolvedScale));
        body.setTextSize(
                Interpolate(
                        MIN_BODY_TEXT_SIZE_SP
                      , MAX_BODY_TEXT_SIZE_SP
                      , resolvedScale));
        callToAction.setTextSize(
                Interpolate(
                        MIN_CALL_TO_ACTION_TEXT_SIZE_SP
                      , MAX_CALL_TO_ACTION_TEXT_SIZE_SP
                      , resolvedScale));

        int identityVerticalPadding = Math.round(
                Interpolate(
                        MIN_IDENTITY_VERTICAL_PADDING_DP
                      , MAX_IDENTITY_VERTICAL_PADDING_DP
                      , resolvedScale)
                        * density);
        identityRow.setPadding(
                0
              , identityVerticalPadding
              , 0
              , identityVerticalPadding);
        body.setPadding(
                0
              , 0
              , 0
              , Math.round(
                    Interpolate(
                            MIN_BODY_BOTTOM_PADDING_DP
                          , MAX_BODY_BOTTOM_PADDING_DP
                          , resolvedScale)
                            * density));

        if (icon != null) {
            int iconSize = Math.round(
                    Interpolate(
                            MIN_ICON_SIZE_DP
                          , MAX_ICON_SIZE_DP
                          , resolvedScale)
                            * density);
            LinearLayout.LayoutParams iconLayoutParams =
                    (LinearLayout.LayoutParams) icon.getLayoutParams();
            iconLayoutParams.width = iconSize;
            iconLayoutParams.height = iconSize;
            iconLayoutParams.setMarginEnd(
                    Math.round(ICON_GAP_DP * density));
            icon.setLayoutParams(iconLayoutParams);
        }

        int callToActionHeight = Math.round(
                Interpolate(
                        MIN_CALL_TO_ACTION_HEIGHT_DP
                      , MAX_CALL_TO_ACTION_HEIGHT_DP
                      , resolvedScale)
                        * density);
        callToAction.setMinHeight(callToActionHeight);
        callToAction.setMinimumHeight(callToActionHeight);
    }

    private static boolean ContentFitsWithMinimumMedia(
            DisplayMetrics displayMetrics
          , int horizontalPadding
          , int minimumMediaSize
          , float mediaAspectRatio
          , boolean hasDisplayableMedia
          , int panelHeight
          , LinearLayout contentColumn
          , MediaView mediaView) {
        int contentWidth = Math.max(
                0
              , displayMetrics.widthPixels - 2 * horizontalPadding);
        return ContentFitsWithMediaHeight(
                contentWidth
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , minimumMediaSize
              , panelHeight
              , contentColumn
              , mediaView);
    }

    private static boolean ContentFitsWithNaturalMedia(
            DisplayMetrics displayMetrics
          , int horizontalPadding
          , int minimumMediaSize
          , float mediaAspectRatio
          , boolean hasDisplayableMedia
          , int panelHeight
          , LinearLayout contentColumn
          , MediaView mediaView) {
        int contentWidth = Math.max(
                0
              , displayMetrics.widthPixels - 2 * horizontalPadding);
        int naturalMediaHeight = Math.max(
                minimumMediaSize
              , Math.round(contentWidth / mediaAspectRatio));
        return ContentFitsWithMediaHeight(
                contentWidth
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , naturalMediaHeight
              , panelHeight
              , contentColumn
              , mediaView);
    }

    private static boolean ContentFitsWithMediaHeight(
            int contentWidth
          , int minimumMediaSize
          , float mediaAspectRatio
          , boolean hasDisplayableMedia
          , int mediaHeight
          , int panelHeight
          , LinearLayout contentColumn
          , MediaView mediaView) {
        if (hasDisplayableMedia) {
            int resolvedMediaHeight = Math.max(
                    minimumMediaSize
                  , mediaHeight);
            int resolvedMediaWidth = Math.min(
                    contentWidth
                  , Math.max(
                        minimumMediaSize
                      , Math.round(
                            resolvedMediaHeight * mediaAspectRatio)));
            LinearLayout.LayoutParams mediaLayoutParams =
                    (LinearLayout.LayoutParams) mediaView.getLayoutParams();
            mediaLayoutParams.width = resolvedMediaWidth;
            mediaLayoutParams.height = resolvedMediaHeight;
            mediaLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            mediaView.setLayoutParams(mediaLayoutParams);
        }

        int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(
                contentWidth
              , View.MeasureSpec.EXACTLY);
        int unspecifiedHeightSpec = View.MeasureSpec.makeMeasureSpec(
                0
              , View.MeasureSpec.UNSPECIFIED);
        contentColumn.measure(contentWidthSpec, unspecifiedHeightSpec);
        return contentColumn.getMeasuredHeight() <= panelHeight;
    }

    private static float Interpolate(
            float minimum
          , float maximum
          , float scale) {
        return minimum + (maximum - minimum) * scale;
    }

    private static void MatchIconSizeToIdentityText(
            ImageView icon
          , LinearLayout identityText
          , LinearLayout identityRow
          , float density) {
        identityRow.addOnLayoutChangeListener(
                (view
               , left
               , top
               , right
               , bottom
               , oldLeft
               , oldTop
               , oldRight
               , oldBottom) -> {
                    int textHeight = identityText.getHeight();
                    int rowWidth = right - left;
                    if (textHeight <= 0 || rowWidth <= 0) return;

                    LinearLayout.LayoutParams layoutParams =
                            (LinearLayout.LayoutParams)
                                    icon.getLayoutParams();
                    int minimumIconSize = Math.max(
                            Math.round(MIN_ICON_SIZE_DP * density)
                          , Math.max(
                                layoutParams.width
                              , layoutParams.height));
                    int maximumIconSize = Math.max(
                            minimumIconSize
                          , Math.min(
                                Math.round(MAX_ICON_SIZE_DP * density)
                              , Math.round(
                                    rowWidth
                                            * MAX_ICON_ROW_WIDTH_RATIO)));
                    int resolvedIconSize = Math.max(
                            minimumIconSize
                          , Math.min(textHeight, maximumIconSize));
                    if (layoutParams.width == resolvedIconSize
                            && layoutParams.height == resolvedIconSize) {
                        return;
                    }

                    layoutParams.width = resolvedIconSize;
                    layoutParams.height = resolvedIconSize;
                    layoutParams.setMarginEnd(
                            Math.round(ICON_GAP_DP * density));
                    icon.setLayoutParams(layoutParams);
                });
    }

    private void ConfigureBackground() {
        int backgroundRgb = fullscreen
                ? FULL_SCREEN_BACKGROUND_RGB
                : COLLAPSIBLE_BACKGROUND_RGB;
        float defaultAlpha = fullscreen
                ? FULL_SCREEN_DEFAULT_ALPHA
                : COLLAPSIBLE_DEFAULT_ALPHA;
        float alpha = backgroundAlpha;
        if (Float.isNaN(alpha) || Float.isInfinite(alpha)) {
            Log.w(
                    TAG
                  , "backgroundAlpha is not finite; using the mode default");
            alpha = defaultAlpha;
        } else if (alpha < 0f) {
            alpha = defaultAlpha;
        } else {
            float clampedAlpha = Math.max(0f, Math.min(1f, alpha));
            if (clampedAlpha != alpha) {
                Log.w(
                        TAG
                      , "backgroundAlpha must be within [0,1]; clamping it");
            }
            alpha = clampedAlpha;
        }
        int backgroundColor = Color.argb(
                Math.round(alpha * MAX_COLOR_CHANNEL)
              , backgroundRgb >> 16 & 0xFF
              , backgroundRgb >> 8 & 0xFF
              , backgroundRgb & 0xFF);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(backgroundColor);
        setBackground(background);
        setClipToOutline(false);
    }

    private void ApplyFullscreenContentInset() {
        if (!fullscreen
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        setOnApplyWindowInsetsListener((view, windowInsets) -> {
            android.view.DisplayCutout displayCutout =
                    windowInsets.getDisplayCutout();
            int topInset = displayCutout == null
                    ? 0
                    : displayCutout.getSafeInsetTop();
            view.setPadding(
                    view.getPaddingLeft()
                  , topInset
                  , view.getPaddingRight()
                  , view.getPaddingBottom());
            return windowInsets;
        });
        requestApplyInsets();
    }

    private TextView CreateAttributionView(
            Context context
          , float density) {
        TextView attribution = new TextView(context);
        attribution.setText(ATTRIBUTION_TEXT);
        attribution.setTextColor(Color.BLACK);
        attribution.setTextSize(10f);
        attribution.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        attribution.setGravity(Gravity.CENTER);
        attribution.setMinWidth(
                Math.max(
                        (int) (ATTRIBUTION_WIDTH_DP * density)
                      , MIN_BADGE_SIZE_PX));
        attribution.setMinHeight(
                Math.max(
                        (int) (ATTRIBUTION_HEIGHT_DP * density)
                      , MIN_BADGE_SIZE_PX));
        attribution.setPadding(
                (int) (5 * density)
              , (int) density
              , (int) (5 * density)
              , (int) density);
        attribution.setClickable(false);
        attribution.setFocusable(false);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(
                Color.parseColor(ATTRIBUTION_BACKGROUND_COLOR));
        attribution.setBackground(background);
        return attribution;
    }

    private TextView CreateControlView(
            Context context
          , String text
          , float textSize
          , String backgroundColor) {
        TextView control = new TextView(context);
        control.setText(text);
        control.setTextColor(Color.WHITE);
        control.setTextSize(textSize);
        control.setGravity(Gravity.CENTER);
        control.setBackgroundColor(Color.parseColor(backgroundColor));
        return control;
    }

    // Same treatment as the in-feed button: its own filled and bordered
    // background rather than the platform default, with a ripple so it still
    // answers a finger.
    private static void StyleCallToAction(
            Button callToAction
          , float density) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Color.parseColor(CTA_BACKGROUND_COLOR));
        background.setStroke(
                (int) Math.ceil(CTA_BORDER_WIDTH_DP * density)
              , Color.parseColor(CTA_BORDER_COLOR));
        callToAction.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(
                                Color.parseColor(CTA_RIPPLE_COLOR))
                      , background
                      , null));
        callToAction.setTextColor(Color.parseColor(CTA_TEXT_COLOR));
        callToAction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        callToAction.setAllCaps(false);
        callToAction.setGravity(Gravity.CENTER);
    }

    // A single line that does not fit scrolls rather than being cut off, so the
    // whole value stays readable. Platform timings: 1200ms still, 30dp per
    // second, 1200ms before it repeats.
    private static void MarqueeWhenTooLong(TextView text) {
        if (text == null) return;

        text.setSingleLine(true);
        text.setMaxLines(1);
        text.setHorizontallyScrolling(true);
        text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        text.setMarqueeRepeatLimit(-1);
        text.setSelected(true);
    }

    // The same rule the in-feed layout follows: a value is on screen whole, or
    // it is on one line that scrolls - never cut. There is no candidate ladder
    // here to re-run, so the switch happens on the laid-out view: a text that
    // could not show everything in the lines it was given becomes a scrolling
    // line, and any line that actually scrolls does so below the size that
    // failed to fit still.
    private static void KeepTextWholeOrScrolling(TextView text) {
        if (text == null) return;

        final boolean[] shrunk = new boolean[1];
        text.addOnLayoutChangeListener(
                (view
               , left
               , top
               , right
               , bottom
               , oldLeft
               , oldTop
               , oldRight
               , oldBottom) -> {
                    if (text.getVisibility() != View.VISIBLE) return;
                    Layout layout = text.getLayout();
                    if (layout == null || layout.getLineCount() == 0) return;

                    if (text.getEllipsize()
                            == TextUtils.TruncateAt.MARQUEE) {
                        if (shrunk[0]) return;
                        int window = text.getWidth()
                                - text.getPaddingLeft()
                                - text.getPaddingRight();
                        if (window > 0
                                && layout.getLineWidth(0) > window) {
                            shrunk[0] = true;
                            text.setTextSize(
                                    TypedValue.COMPLEX_UNIT_PX
                                  , text.getTextSize()
                                            * MARQUEE_TEXT_SHRINK);
                        }
                        return;
                    }

                    boolean truncated = false;
                    int lineCount = layout.getLineCount();
                    for (int line = 0;
                         line < lineCount && !truncated;
                         ++line) {
                        truncated = layout.getEllipsisCount(line) > 0;
                    }
                    if (!truncated) {
                        truncated = layout.getLineEnd(lineCount - 1)
                                < text.getText().length();
                    }
                    if (!truncated) return;

                    shrunk[0] = true;
                    float shrunkSize =
                            text.getTextSize() * MARQUEE_TEXT_SHRINK;
                    MarqueeWhenTooLong(text);
                    text.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX
                          , shrunkSize);
                });
    }

    private void BindAssets(
            TextView headline
          , TextView advertiser
          , NativeAdMobStarRatingView starRating
          , TextView body
          , ImageView icon
          , Button callToAction) {
        headline.setText(nativeAd.getHeadline());

        com.google.android.gms.ads.nativead.NativeAd.Image iconAsset =
                nativeAd.getIcon();
        if (iconAsset == null || iconAsset.getDrawable() == null) {
            icon.setVisibility(View.GONE);
        } else {
            icon.setImageDrawable(iconAsset.getDrawable());
        }

        String advertiserValue = nativeAd.getAdvertiser();
        if (TextUtils.isEmpty(advertiserValue)) {
            advertiser.setVisibility(View.GONE);
        } else {
            advertiser.setText(advertiserValue);
        }

        Double starRatingValue = nativeAd.getStarRating();
        if (starRatingValue == null
                || starRatingValue <= 0d
                || starRatingValue.isNaN()
                || starRatingValue.isInfinite()) {
            starRating.setVisibility(View.GONE);
        } else {
            starRating.SetRating(
                    Math.max(
                            0f
                          , Math.min(
                                5f
                              , starRatingValue.floatValue())));
        }

        String bodyValue = nativeAd.getBody();
        if (TextUtils.isEmpty(bodyValue)) {
            body.setVisibility(View.GONE);
        } else {
            body.setText(bodyValue);
        }

        String callToActionValue = nativeAd.getCallToAction();
        if (TextUtils.isEmpty(callToActionValue)) {
            callToAction.setVisibility(View.GONE);
        } else {
            callToAction.setText(callToActionValue);
        }
    }

    private void ResolveContentHeight(
            DisplayMetrics displayMetrics
          , int horizontalPadding
          , int minimumMediaSize
          , float mediaAspectRatio
          , boolean hasDisplayableMedia
          , int requestedPanelHeight
          , LinearLayout contentColumn
          , MediaView mediaView) {
        // The strip inset exists so nothing that matters can sit under the
        // corner controls - but the controls and badges draw on top with their
        // own opaque backgrounds, exactly how Google's interstitial overlays
        // its close button on the creative. With media as the first child the
        // strip can only ever cover media, never text: a portrait column is
        // too narrow to reach the corners, a landscape one simply runs
        // underneath them. Either way the inset's height belongs to the media.
        // Only a media-free layout keeps it, because there the top row is
        // text.
        if (hasDisplayableMedia && !sideMediaLayout) {
            SetTopInset(contentColumn, 0);
            SizeMediaForBudget(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , requestedPanelHeight
                  , contentColumn
                  , mediaView);
        }

        int contentWidth = Math.max(
                0
              , displayMetrics.widthPixels - 2 * horizontalPadding);
        contentColumn.measure(
                View.MeasureSpec.makeMeasureSpec(
                        contentWidth
                      , View.MeasureSpec.EXACTLY)
              , View.MeasureSpec.makeMeasureSpec(
                        0
                      , View.MeasureSpec.UNSPECIFIED));
        int requiredPanelHeight = contentColumn.getMeasuredHeight();
        resolvedPanelHeight = fullscreen
                ? displayMetrics.heightPixels
                : Math.min(
                        displayMetrics.heightPixels
                      , Math.max(requestedPanelHeight, requiredPanelHeight));
    }

    private int SizeMediaForBudget(
            DisplayMetrics displayMetrics
          , int horizontalPadding
          , int minimumMediaSize
          , float mediaAspectRatio
          , int requestedPanelHeight
          , LinearLayout contentColumn
          , MediaView mediaView) {
        int contentWidth = Math.max(
                0
              , displayMetrics.widthPixels - 2 * horizontalPadding);
        int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(
                contentWidth
              , View.MeasureSpec.EXACTLY);
        int unspecifiedHeightSpec = View.MeasureSpec.makeMeasureSpec(
                0
              , View.MeasureSpec.UNSPECIFIED);
        contentColumn.measure(contentWidthSpec, unspecifiedHeightSpec);
        int lowerContentHeight = Math.max(
                0
              , contentColumn.getMeasuredHeight() - minimumMediaSize);
        int panelHeight = fullscreen
                ? displayMetrics.heightPixels
                : requestedPanelHeight;
        int availableMediaHeight = panelHeight - lowerContentHeight;
        int naturalMediaHeight = Math.max(
                minimumMediaSize
              , Math.round(
                    displayMetrics.widthPixels / mediaAspectRatio));
        int resolvedMediaHeight = Math.max(
                minimumMediaSize
              , Math.min(
                    naturalMediaHeight
                  , Math.max(minimumMediaSize, availableMediaHeight)));
        int resolvedMediaWidth = Math.min(
                displayMetrics.widthPixels
              , Math.max(
                    minimumMediaSize
                  , Math.round(
                        resolvedMediaHeight * mediaAspectRatio)));
        LinearLayout.LayoutParams mediaLayoutParams =
                (LinearLayout.LayoutParams)
                        mediaView.getLayoutParams();
        mediaLayoutParams.width = resolvedMediaWidth;
        mediaLayoutParams.height = resolvedMediaHeight;
        mediaLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        mediaView.setLayoutParams(mediaLayoutParams);
        return resolvedMediaWidth;
    }

    private boolean ShouldUseSideMedia(
            DisplayMetrics displayMetrics
          , float mediaAspectRatio
          , int panelHeight) {
        if (mediaAspectRatio >= SIDE_MEDIA_MAX_ASPECT) return false;

        float density = displayMetrics.density;
        int mediaWidth = SideMediaWidth(
                displayMetrics
              , mediaAspectRatio
              , panelHeight);
        int minimumMediaSize = (int) Math.ceil(
                MIN_VIDEO_MEDIA_SIZE_DP * density);
        int railWidth = displayMetrics.widthPixels - mediaWidth;
        return mediaWidth >= minimumMediaSize
                && railWidth >= (int) (SIDE_MEDIA_MIN_RAIL_DP * density);
    }

    private static int SideMediaWidth(
            DisplayMetrics displayMetrics
          , float mediaAspectRatio
          , int panelHeight) {
        return Math.min(
                Math.round(panelHeight * mediaAspectRatio)
              , Math.round(
                    displayMetrics.widthPixels
                            * SIDE_MEDIA_MAX_WIDTH_SHARE));
    }

    private static void SetTopInset(LinearLayout column, int topInset) {
        column.setPadding(
                column.getPaddingLeft()
              , topInset
              , column.getPaddingRight()
              , column.getPaddingBottom());
    }


    private void ObserveMediaSize(
            int minimumMediaSize
          , LinearLayout contentColumn
          , MediaView mediaView
          , Drawable fallbackMediaImage) {
        Runnable matchMediaSize = () -> MatchMediaSizeToAvailableSpace(
                minimumMediaSize
              , contentColumn
              , mediaView
              , fallbackMediaImage);
        contentColumn.getViewTreeObserver().addOnGlobalLayoutListener(
                matchMediaSize::run);
        contentColumn.post(matchMediaSize);
    }

    private void MatchMediaSizeToAvailableSpace(
            int minimumMediaSize
          , LinearLayout contentColumn
          , MediaView mediaView
          , Drawable fallbackMediaImage) {
        // Full column width, padding included: the media bleeds through the
        // side padding by negative margins, so capping it at the padded width
        // here would shrink it right back after layout.
        int availableWidth = contentColumn.getWidth();
        int availableHeight =
                contentColumn.getHeight()
                        - ResolveNonMediaContentHeight(
                                contentColumn
                              , mediaView);
        if (availableWidth <= 0 || availableHeight <= 0) return;

        float mediaAspectRatio =
                GetMediaAspectRatio(fallbackMediaImage);
        int resolvedMediaWidth = availableWidth;
        int resolvedMediaHeight =
                Math.round(resolvedMediaWidth / mediaAspectRatio);
        if (resolvedMediaHeight > availableHeight) {
            resolvedMediaHeight = availableHeight;
            resolvedMediaWidth =
                    Math.round(
                            resolvedMediaHeight * mediaAspectRatio);
        }

        int resolvedMinimumWidth =
                Math.min(minimumMediaSize, availableWidth);
        int resolvedMinimumHeight =
                Math.min(minimumMediaSize, availableHeight);
        resolvedMediaWidth = Math.min(
                availableWidth
              , Math.max(resolvedMinimumWidth, resolvedMediaWidth));
        resolvedMediaHeight = Math.min(
                availableHeight
              , Math.max(resolvedMinimumHeight, resolvedMediaHeight));

        LinearLayout.LayoutParams mediaLayoutParams =
                (LinearLayout.LayoutParams) mediaView.getLayoutParams();
        if (mediaLayoutParams.width == resolvedMediaWidth
                && mediaLayoutParams.height == resolvedMediaHeight) {
            return;
        }

        mediaLayoutParams.width = resolvedMediaWidth;
        mediaLayoutParams.height = resolvedMediaHeight;
        mediaLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        mediaView.setLayoutParams(mediaLayoutParams);
    }

    private static int ResolveNonMediaContentHeight(
            LinearLayout contentColumn
          , MediaView mediaView) {
        int resolvedHeight =
                contentColumn.getPaddingTop()
                        + contentColumn.getPaddingBottom();
        for (int index = 0;
                index < contentColumn.getChildCount();
                index++) {
            View child = contentColumn.getChildAt(index);
            if (child == mediaView || child.getVisibility() == View.GONE) {
                continue;
            }

            resolvedHeight += Math.max(
                    child.getHeight()
                  , child.getMeasuredHeight());
            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLayoutParams =
                        (ViewGroup.MarginLayoutParams) layoutParams;
                resolvedHeight +=
                        marginLayoutParams.topMargin
                                + marginLayoutParams.bottomMargin;
            }
        }
        return resolvedHeight;
    }

    private void ObserveBadgePositions(
            TextView attribution
          , TextView countdown
          , TextView close
          , int controlGap
          , int rightControlInset) {
        Runnable alignControlsToBadges = () -> {
            int measuredLeftInset = attribution.getRight() + controlGap;
            UpdateControlInsets(
                    countdown
                  , measuredLeftInset
                  , rightControlInset);
            UpdateControlInsets(
                    close
                  , measuredLeftInset
                  , rightControlInset);
        };
        addOnLayoutChangeListener(
                (view
               , left
               , top
               , right
               , bottom
               , oldLeft
               , oldTop
               , oldRight
               , oldBottom) -> alignControlsToBadges.run());
        getViewTreeObserver().addOnGlobalLayoutListener(
                alignControlsToBadges::run);
        post(alignControlsToBadges);
    }

    private void StartCountdown() {
        if (countdown == null || close == null) return;
        if (timer != null) timer.cancel();
        timer = null;

        long remainingMs = Math.max(0L, countDownRemainingMs);
        countdown.setText(
                String.valueOf(
                        (int) Math.ceil(
                                remainingMs / (double) MILLIS_PER_SECOND)));
        countdown.setVisibility(View.VISIBLE);
        close.setVisibility(View.GONE);
        if (remainingMs <= 0L) {
            countdown.setVisibility(View.GONE);
            close.setVisibility(View.VISIBLE);
            return;
        }

        timer = new CountDownTimer(
                remainingMs
              , COUNTDOWN_INTERVAL_MS) {
            @Override
            public void onTick(long millisecondsUntilFinished) {
                countDownRemainingMs = Math.max(
                        0L
                      , millisecondsUntilFinished);
                countdown.setText(
                        String.valueOf(
                                (int) Math.ceil(
                                        millisecondsUntilFinished
                                                / (double) MILLIS_PER_SECOND)));
            }

            @Override
            public void onFinish() {
                countDownRemainingMs = 0L;
                timer = null;
                countdown.setVisibility(View.GONE);
                close.setVisibility(View.VISIBLE);
            }
        }.start();
    }

    private Drawable FindFallbackMediaImage() {
        List<com.google.android.gms.ads.nativead.NativeAd.Image> images =
                nativeAd.getImages();
        if (images == null) return null;

        for (com.google.android.gms.ads.nativead.NativeAd.Image image
                : images) {
            if (image != null && image.getDrawable() != null) {
                return image.getDrawable();
            }
        }
        return null;
    }

    private float GetMediaAspectRatio(Drawable fallbackMediaImage) {
        if (fallbackMediaImage != null) {
            return GetDrawableAspectRatio(fallbackMediaImage);
        }

        com.google.android.gms.ads.MediaContent mediaContent =
                nativeAd.getMediaContent();
        if (mediaContent == null) {
            return DEFAULT_MEDIA_ASPECT_RATIO;
        }

        if (!mediaContent.hasVideoContent()) {
            Drawable mainImage = mediaContent.getMainImage();
            if (mainImage != null
                    && mainImage.getIntrinsicWidth() > 0
                    && mainImage.getIntrinsicHeight() > 0) {
                return mainImage.getIntrinsicWidth()
                        / (float) mainImage.getIntrinsicHeight();
            }
        }

        float aspectRatio = mediaContent.getAspectRatio();
        if (aspectRatio <= 0f
                || Float.isNaN(aspectRatio)
                || Float.isInfinite(aspectRatio)) {
            return DEFAULT_MEDIA_ASPECT_RATIO;
        }
        return aspectRatio;
    }

    private static float GetDrawableAspectRatio(Drawable image) {
        if (image == null
                || image.getIntrinsicWidth() <= 0
                || image.getIntrinsicHeight() <= 0) {
            return DEFAULT_MEDIA_ASPECT_RATIO;
        }
        return image.getIntrinsicWidth()
                / (float) image.getIntrinsicHeight();
    }

    private static void UpdateControlInsets(
            View control
          , int leftInset
          , int rightInset) {
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) control.getLayoutParams();
        if (layoutParams.leftMargin == leftInset
                && layoutParams.rightMargin == rightInset) {
            return;
        }
        layoutParams.setMargins(leftInset, 0, rightInset, 0);
        control.setLayoutParams(layoutParams);
    }
}
