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

final class OverlayAdContentView extends FrameLayout {
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

    private static final String TAG = "OverlayAd";
    private static final String ATTRIBUTION_TEXT = "Ad";
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
    // A probe value only, for the fit checks that need a number. It never
    // frames the media: a creative with an unreported ratio is handed the
    // whole band and renders inside it as it pleases.
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
    // Strictly portrait only: a square creative belongs stacked on top -
    // MEDIA_TOP wins there - and only media clearly taller than wide earns
    // the rail beside it.
    private static final float SIDE_MEDIA_MAX_ASPECT = 0.85f;
    // Just over half: enough width for a portrait creative to stay
    // imposing, while the rail keeps room for whole text and a real button.
    private static final float SIDE_MEDIA_MAX_WIDTH_SHARE = 0.56f;
    private static final int SIDE_MEDIA_MIN_RAIL_DP = 120;
    // The rail's side padding follows the rail's width; a narrow column
    // cannot afford the full 8dp on each side.
    private static final float RAIL_SIDE_PADDING_RATIO = 0.03f;
    private static final int RAIL_MIN_SIDE_PADDING_DP = 2;
    private static final int RAIL_ICON_GAP_DP = 4;
    // Text no larger than the rail can wear: the scale ceiling follows the
    // rail's width, reaching full size only in a genuinely wide rail.
    private static final int RAIL_SCALE_CAP_MIN_WIDTH_DP = 160;
    private static final int RAIL_SCALE_CAP_RANGE_DP = 240;
    // Whole text before size, size before scrolling: how many size steps
    // the rail trades away before a line is allowed to scroll.
    private static final int RAIL_FULL_TEXT_STEPS = 3;
    private static final float RAIL_FULL_TEXT_SCALE_STEP = 0.34f;
    private static final int RAIL_HEADLINE_MAX_LINE_COUNT = 4;
    // A panel meaningfully taller than wide reads as a page: media belongs
    // stacked on top of it, not beside it. Side media only suits panels near
    // screen proportions, where a portrait creative would otherwise sit in a
    // centred column between dead gutters.
    private static final float SIDE_MEDIA_MAX_PANEL_HEIGHT_RATIO = 1.3f;
    // The rail is narrow by construction, so the icon never shares a line
    // with text there: it stands alone and the identity stack follows below
    // at the rail's full width.
    private static final float SIDE_RAIL_ICON_WIDTH_RATIO = 0.3f;
    // Slack absorption in the rail: how many whole body lines it may take,
    // and how far the icon may grow, before free height is left alone.
    private static final int RAIL_BODY_MAX_LINE_COUNT = 6;
    private static final int RAIL_ICON_GROWTH_STEP_DP = 8;
    private static final float RAIL_ICON_MAX_WIDTH_RATIO = 0.6f;
    // Extra ground the strip-avoiding layout may give before it surrenders
    // and lets the corner controls overlay the media instead.
    private static final int AVOID_CALL_TO_ACTION_HEIGHT_DP = 36;
    private static final int AVOID_ICON_SIZE_DP = 28;
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
    private int sideMediaWidthPx;
    private boolean mediaAvoidsControlStrip;
    private boolean mediaAspectReported;
    private boolean controlAvoidanceActive;
    private boolean controlsAtEdgesBelowBadges;
    private LinearLayout avoidanceColumn;
    private MediaView avoidanceMediaView;
    private float avoidanceMediaAspect;
    private int avoidanceMinimumMediaSize;
    private int avoidanceHorizontalPadding;
    private int avoidancePanelHeight;
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

    OverlayAdContentView(
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

    OverlayAdContentView(
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
        int sidePanelHeight = fullscreen
                ? displayMetrics.heightPixels
                : requestedPanelHeight;
        sideMediaLayout = hasDisplayableMedia
                && ShouldUseSideMedia(
                        displayMetrics
                      , mediaAspectRatio
                      , sidePanelHeight);

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
        // Deliberately black, not the panel colour: when a creative reports
        // one ratio but renders less inside it, the black ground makes the
        // shortfall visible instead of hiding it.
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
        } else if (!sideMediaLayout) {
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

        NativeAdStarRatingView starRating =
                new NativeAdStarRatingView(context);
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

        LinearLayout sideRail = null;
        if (sideMediaLayout) {
            int sideMediaWidth = SideMediaWidth(
                    displayMetrics
                  , mediaAspectRatio
                  , sidePanelHeight);
            sideMediaWidthPx = sideMediaWidth;
            // The media column is exactly as tall as the creative can fill
            // at its own aspect, centred - never a band of dead backfill
            // painted to the panel's height.
            int sideMediaHeight = Math.min(
                    sidePanelHeight
                  , Math.round(sideMediaWidth / mediaAspectRatio));
            Log.i(
                    TAG
                  , "Side-media layout: media "
                            + sideMediaWidth + "x" + sideMediaHeight
                            + " in panel height " + sidePanelHeight);
            contentColumn.setPadding(0, 0, 0, 0);
            LinearLayout sideRow = new LinearLayout(context);
            sideRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams sideMediaParams =
                    new LinearLayout.LayoutParams(
                            sideMediaWidth
                          , sideMediaHeight);
            sideMediaParams.gravity = Gravity.CENTER_VERTICAL;
            sideRow.addView(mediaView, sideMediaParams);
            LinearLayout rail = new LinearLayout(context);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setGravity(Gravity.CENTER_VERTICAL);
            // The corner controls and AdChoices live over the rail's top, so
            // the rail alone keeps the strip inset; the media needs none.
            // Side padding follows the rail's width - a narrow column keeps
            // its ground for content.
            int railOuterWidth = Math.max(
                    0
                  , displayMetrics.widthPixels - sideMediaWidth);
            int railPad = Math.max(
                    Math.round(RAIL_MIN_SIDE_PADDING_DP * density)
                  , Math.min(
                        horizontalPadding
                      , Math.round(
                            railOuterWidth * RAIL_SIDE_PADDING_RATIO)));
            rail.setPadding(
                    railPad
                  , controlStripHeight
                  , railPad
                  , 0);
            // The rail is narrow, so the icon never shares a line with text
            // here: it stands alone and the identity stack follows below at
            // the rail's full width.
            if (!iconHero) {
                int railWidth = Math.max(
                        0
                      , railOuterWidth - 2 * railPad);
                int railIconSize = Math.max(
                        Math.round(MIN_ICON_SIZE_DP * density)
                      , Math.min(
                            Math.round(MAX_ICON_SIZE_DP * density)
                          , Math.round(
                                railWidth * SIDE_RAIL_ICON_WIDTH_RATIO)));
                LinearLayout.LayoutParams railIconParams =
                        new LinearLayout.LayoutParams(
                                railIconSize
                              , railIconSize);
                railIconParams.gravity = Gravity.CENTER_HORIZONTAL;
                railIconParams.bottomMargin =
                        Math.round(RAIL_ICON_GAP_DP * density);
                rail.addView(icon, railIconParams);
            }
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
            sideRail = rail;
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
        // In the side-media layout even the badge steps off the picture:
        // it hugs the media's right edge, and the left corner control then
        // lines up beside it.
        if (sideMediaLayout) {
            attributionLayoutParams.leftMargin =
                    sideMediaWidthPx
                            + Math.max(1, (int) (CONTROL_GAP_DP * density));
        }
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
            if (sideMediaLayout) {
                ConfigureResponsiveSideRail(
                        sideRail
                      , displayMetrics.widthPixels - sideMediaWidthPx
                      , requestedPanelHeight
                      , density
                      , identityRow
                      , headline
                      , advertiser
                      , starRating
                      , body
                      , iconHero ? null : icon
                      , callToAction);
            } else {
                ConfigureResponsiveCollapsibleContent(
                        displayMetrics
                      , horizontalPadding
                      , minimumMediaSize
                      , mediaAspectRatio
                      , hasDisplayableMedia
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
        }
        if (!iconHero && !sideMediaLayout) {
            MatchIconSizeToIdentityText(
                    icon
                  , identityText
                  , identityRow
                  , density);
        }
        if (fullscreen && hasDisplayableMedia && !sideMediaLayout) {
            EnableControlAvoidance(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , contentColumn
                  , mediaView
                  , displayMetrics.heightPixels);
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
        if (hasDisplayableMedia && !sideMediaLayout
                && !controlAvoidanceActive) {
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
          , NativeAdStarRatingView starRating
          , TextView body
          , ImageView icon
          , Button callToAction) {
        // The strip-avoiding attempt comes first: the media inset like every
        // other element and the control strip's band kept above it, so close
        // and timer overlay nothing. Shrinking spends the call to action, the
        // icon and the paddings before the attempt surrenders; only when even
        // those floors cannot host the content does the layout fall back to
        // bleeding the media edge to edge under the strip.
        if (hasDisplayableMedia) {
            SetMediaSideBleed(mediaView, 0);
            mediaAvoidsControlStrip = true;
            boolean contentFits = RunCollapsibleFitPipeline(
                    displayMetrics
                  , horizontalPadding
                  , minimumMediaSize
                  , mediaAspectRatio
                  , true
                  , requestedPanelHeight
                  , density
                  , contentColumn
                  , mediaView
                  , identityRow
                  , headline
                  , advertiser
                  , starRating
                  , body
                  , icon
                  , callToAction);
            if (!contentFits) {
                ApplyStripAvoidingFloors(
                        density
                      , icon
                      , callToAction
                      , identityRow
                      , body);
                contentFits = ContentFitsWithMinimumMedia(
                        displayMetrics
                      , horizontalPadding
                      , minimumMediaSize
                      , mediaAspectRatio
                      , true
                      , requestedPanelHeight
                      , contentColumn
                      , mediaView);
            }
            if (contentFits) {
                // The compact chrome is not a last resort but the standing
                // dress: the height it frees goes straight to the media.
                ApplyStripAvoidingFloors(
                        density
                      , icon
                      , callToAction
                      , identityRow
                      , body);
                // The half panel runs the same maximiser as the full
                // screen: the media's band grows toward the controls and
                // the controls move where the band grows largest.
                EnableControlAvoidance(
                        displayMetrics
                      , horizontalPadding
                      , minimumMediaSize
                      , mediaAspectRatio
                      , contentColumn
                      , mediaView
                      , requestedPanelHeight);
                return;
            }

            mediaAvoidsControlStrip = false;
            SetMediaSideBleed(mediaView, horizontalPadding);
            RestoreOptionalRows(advertiser, starRating, body);
        }
        RunCollapsibleFitPipeline(
                displayMetrics
              , horizontalPadding
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , requestedPanelHeight
              , density
              , contentColumn
              , mediaView
              , identityRow
              , headline
              , advertiser
              , starRating
              , body
              , icon
              , callToAction);
    }

    private boolean RunCollapsibleFitPipeline(
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
          , NativeAdStarRatingView starRating
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

        if (!contentFits) return false;
        if (!ContentFitsWithNaturalMedia(
                displayMetrics
              , horizontalPadding
              , minimumMediaSize
              , mediaAspectRatio
              , hasDisplayableMedia
              , requestedPanelHeight
              , contentColumn
              , mediaView)) {
            return true;
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
            return true;
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
        return true;
    }

    // The rail owns a fixed height beside the media, so the fit that matters
    // is the rail's own stack against that height. The search mirrors the
    // stacked panel's: the largest text that keeps every element inside the
    // rail, shedding body lines and then optional rows when even the floor
    // does not fit.
    private void ConfigureResponsiveSideRail(
            LinearLayout rail
          , int railOuterWidth
          , int railHeight
          , float density
          , LinearLayout identityRow
          , TextView headline
          , TextView advertiser
          , NativeAdStarRatingView starRating
          , TextView body
          , ImageView railIcon
          , Button callToAction) {
        if (rail == null) return;

        headline.setMaxLines(COLLAPSIBLE_HEADLINE_MAX_LINE_COUNT);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        body.setMaxLines(MAX_BODY_LINE_COUNT);
        body.setEllipsize(TextUtils.TruncateAt.END);

        ApplyRailContentScale(
                0f
              , density
              , identityRow
              , headline
              , advertiser
              , body
              , callToAction);
        boolean railFits = RailFits(rail, railOuterWidth, railHeight);
        while (!railFits
                && body.getVisibility() == View.VISIBLE
                && body.getMaxLines() > MIN_BODY_LINE_COUNT) {
            body.setMaxLines(body.getMaxLines() - 1);
            railFits = RailFits(rail, railOuterWidth, railHeight);
        }
        if (!railFits && body.getVisibility() == View.VISIBLE) {
            body.setVisibility(View.GONE);
            railFits = RailFits(rail, railOuterWidth, railHeight);
        }
        if (!railFits && starRating.getVisibility() == View.VISIBLE) {
            starRating.setVisibility(View.GONE);
            railFits = RailFits(rail, railOuterWidth, railHeight);
        }
        if (!railFits && advertiser.getVisibility() == View.VISIBLE) {
            advertiser.setVisibility(View.GONE);
            railFits = RailFits(rail, railOuterWidth, railHeight);
        }
        if (!railFits) return;

        // Text no larger than the rail can wear: the ceiling follows the
        // rail's width, so a narrow column keeps modest sizes even with
        // height to spare.
        float railScaleCap = Math.max(
                0f
              , Math.min(
                    1f
                  , (railOuterWidth
                            - RAIL_SCALE_CAP_MIN_WIDTH_DP * density)
                            / (RAIL_SCALE_CAP_RANGE_DP * density)));
        float railScale = railScaleCap;
        ApplyRailContentScale(
                railScale
              , density
              , identityRow
              , headline
              , advertiser
              , body
              , callToAction);
        if (!RailFits(rail, railOuterWidth, railHeight)) {
            float minimumScale = 0f;
            float maximumScale = railScaleCap;
            for (int iteration = 0;
                    iteration < RESPONSIVE_SCALE_SEARCH_ITERATIONS;
                    iteration++) {
                float candidateScale =
                        (minimumScale + maximumScale) / 2f;
                ApplyRailContentScale(
                        candidateScale
                      , density
                      , identityRow
                      , headline
                      , advertiser
                      , body
                      , callToAction);
                if (RailFits(rail, railOuterWidth, railHeight)) {
                    minimumScale = candidateScale;
                } else {
                    maximumScale = candidateScale;
                }
            }
            railScale = minimumScale;
            ApplyRailContentScale(
                    railScale
                  , density
                  , identityRow
                  , headline
                  , advertiser
                  , body
                  , callToAction);
        }

        // Whole text before size, size before scrolling: give lines while
        // they fit, and when a text still cannot show itself whole, trade
        // the scale down a step and try again. Only what survives this may
        // ever scroll.
        for (int attempt = 0; attempt <= RAIL_FULL_TEXT_STEPS; ++attempt) {
            RailFits(rail, railOuterWidth, railHeight);
            GrowTextLines(
                    headline
                  , RAIL_HEADLINE_MAX_LINE_COUNT
                  , rail
                  , railOuterWidth
                  , railHeight);
            GrowTextLines(
                    body
                  , RAIL_BODY_MAX_LINE_COUNT
                  , rail
                  , railOuterWidth
                  , railHeight);
            RailFits(rail, railOuterWidth, railHeight);
            if (!RailTextTruncated(headline)
                    && !RailTextTruncated(body)
                    && !RailTextTruncated(advertiser)) {
                break;
            }
            if (railScale <= 0f) break;

            railScale = Math.max(
                    0f
                  , railScale - RAIL_FULL_TEXT_SCALE_STEP);
            ApplyRailContentScale(
                    railScale
                  , density
                  , identityRow
                  , headline
                  , advertiser
                  , body
                  , callToAction);
        }

        GrowRailIcon(railIcon, rail, railOuterWidth, railHeight, density);
    }

    // The rail's own dress code on top of the shared scale: the button keeps
    // the avoidance floor and the rows drop their optional padding - a
    // narrow column spends its ground on text and media, not on chrome.
    private void ApplyRailContentScale(
            float scale
          , float density
          , LinearLayout identityRow
          , TextView headline
          , TextView advertiser
          , TextView body
          , Button callToAction) {
        ApplyResponsiveContentScale(
                scale
              , density
              , identityRow
              , headline
              , advertiser
              , body
              , null
              , callToAction);
        int railCallToActionHeight =
                Math.round(AVOID_CALL_TO_ACTION_HEIGHT_DP * density);
        callToAction.setMinHeight(railCallToActionHeight);
        callToAction.setMinimumHeight(railCallToActionHeight);
        identityRow.setPadding(0, 0, 0, 0);
        body.setPadding(0, 0, 0, 0);
    }

    private void GrowTextLines(
            TextView text
          , int maximumLineCount
          , LinearLayout rail
          , int railOuterWidth
          , int railHeight) {
        if (text == null || text.getVisibility() != View.VISIBLE) return;

        while (text.getMaxLines() < maximumLineCount) {
            text.setMaxLines(text.getMaxLines() + 1);
            if (!RailFits(rail, railOuterWidth, railHeight)) {
                text.setMaxLines(text.getMaxLines() - 1);
                return;
            }
        }
    }

    // Valid right after a measure pass: the text's layout tells whether it
    // shows everything it holds within the lines it was given.
    private static boolean RailTextTruncated(TextView text) {
        if (text == null || text.getVisibility() != View.VISIBLE) {
            return false;
        }
        Layout layout = text.getLayout();
        if (layout == null || layout.getLineCount() == 0) return false;

        int lineCount = layout.getLineCount();
        for (int line = 0; line < lineCount; ++line) {
            if (layout.getEllipsisCount(line) > 0) return true;
        }
        return layout.getLineEnd(lineCount - 1)
                < text.getText().length();
    }

    // Height that remains after the text has everything belongs to the
    // icon: it grows into the slack, never past the rail's proportion.
    private void GrowRailIcon(
            ImageView railIcon
          , LinearLayout rail
          , int railOuterWidth
          , int railHeight
          , float density) {
        if (railIcon == null || railIcon.getParent() == null) return;
        if (!RailFits(rail, railOuterWidth, railHeight)) return;

        LinearLayout.LayoutParams iconParams =
                (LinearLayout.LayoutParams) railIcon.getLayoutParams();
        int growthStep = Math.round(RAIL_ICON_GROWTH_STEP_DP * density);
        int maximumIconSize = Math.round(
                railOuterWidth * RAIL_ICON_MAX_WIDTH_RATIO);
        while (iconParams.width + growthStep <= maximumIconSize) {
            iconParams.width += growthStep;
            iconParams.height += growthStep;
            railIcon.setLayoutParams(iconParams);
            if (!RailFits(rail, railOuterWidth, railHeight)) {
                iconParams.width -= growthStep;
                iconParams.height -= growthStep;
                railIcon.setLayoutParams(iconParams);
                break;
            }
        }
    }

    private static boolean RailFits(
            LinearLayout rail
          , int railOuterWidth
          , int railHeight) {
        rail.measure(
                View.MeasureSpec.makeMeasureSpec(
                        Math.max(0, railOuterWidth)
                      , View.MeasureSpec.EXACTLY)
              , View.MeasureSpec.makeMeasureSpec(
                        0
                      , View.MeasureSpec.UNSPECIFIED));
        return rail.getMeasuredHeight() <= railHeight;
    }

    // The floors the strip-avoiding layout may fall to before it surrenders:
    // a shorter call to action, a smaller icon and no optional padding, each
    // a price worth paying to keep the corner controls off the media.
    private static void ApplyStripAvoidingFloors(
            float density
          , ImageView icon
          , Button callToAction
          , LinearLayout identityRow
          , TextView body) {
        int callToActionHeight =
                Math.round(AVOID_CALL_TO_ACTION_HEIGHT_DP * density);
        callToAction.setMinHeight(callToActionHeight);
        callToAction.setMinimumHeight(callToActionHeight);
        if (icon != null) {
            LinearLayout.LayoutParams iconLayoutParams =
                    (LinearLayout.LayoutParams) icon.getLayoutParams();
            int iconSize = Math.round(AVOID_ICON_SIZE_DP * density);
            iconLayoutParams.width = iconSize;
            iconLayoutParams.height = iconSize;
            icon.setLayoutParams(iconLayoutParams);
        }
        identityRow.setPadding(0, 0, 0, 0);
        body.setPadding(0, 0, 0, 0);
    }

    private void RestoreOptionalRows(
            TextView advertiser
          , NativeAdStarRatingView starRating
          , TextView body) {
        advertiser.setVisibility(
                TextUtils.isEmpty(nativeAd.getAdvertiser())
                        ? View.GONE
                        : View.VISIBLE);
        Double starRatingValue = nativeAd.getStarRating();
        boolean hasStarRating = starRatingValue != null
                && starRatingValue > 0d
                && !starRatingValue.isNaN()
                && !starRatingValue.isInfinite();
        starRating.setVisibility(
                hasStarRating ? View.VISIBLE : View.GONE);
        body.setVisibility(
                TextUtils.isEmpty(nativeAd.getBody())
                        ? View.GONE
                        : View.VISIBLE);
        body.setMaxLines(MAX_BODY_LINE_COUNT);
    }

    private static void SetMediaSideBleed(
            MediaView mediaView
          , int bleedPx) {
        LinearLayout.LayoutParams mediaLayoutParams =
                (LinearLayout.LayoutParams) mediaView.getLayoutParams();
        mediaLayoutParams.leftMargin = -bleedPx;
        mediaLayoutParams.rightMargin = -bleedPx;
        mediaView.setLayoutParams(mediaLayoutParams);
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

    // ------------------------------------------------------------------
    // Full-screen control avoidance. The stack hugs the bottom and the
    // media grows until it touches the close and timer controls - through
    // the gap between them when it is narrow enough to fit. Two control
    // placements compete: the top row beside the badges, and the panel's
    // edges just below the badges; whichever placement lets the media end
    // up largest wins. Badges may sit over media; controls avoid it.
    // ------------------------------------------------------------------

    private void EnableControlAvoidance(
            DisplayMetrics displayMetrics
          , int horizontalPadding
          , int minimumMediaSize
          , float mediaAspectRatio
          , LinearLayout contentColumn
          , MediaView mediaView
          , int panelHeight) {
        controlAvoidanceActive = true;
        avoidanceColumn = contentColumn;
        avoidanceMediaView = mediaView;
        avoidanceMediaAspect = mediaAspectRatio;
        avoidanceMinimumMediaSize = minimumMediaSize;
        avoidanceHorizontalPadding = horizontalPadding;
        avoidancePanelHeight = panelHeight;
        contentColumn.setGravity(
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        SetMediaSideBleed(mediaView, 0);
        RecomputeMediaControlAvoidance(
                displayMetrics.widthPixels
              , panelHeight);
    }

    @Override
    protected void onSizeChanged(
            int width
          , int height
          , int oldWidth
          , int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (controlAvoidanceActive && width > 0 && height > 0) {
            RecomputeMediaControlAvoidance(
                    width
                  , fullscreen
                            ? height - getPaddingTop()
                            : Math.min(height, avoidancePanelHeight));
        }
    }

    private void RecomputeMediaControlAvoidance(
            int panelWidth
          , int panelHeight) {
        if (!controlAvoidanceActive
                || avoidanceColumn == null
                || avoidanceMediaView == null
                || panelWidth <= 0
                || panelHeight <= 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int controlSize = (int) (CONTROL_STRIP_HEIGHT_DP * density);
        int badgeHeight = (int) (ATTRIBUTION_HEIGHT_DP * density);
        int controlGap = Math.max(
                1
              , (int) (CONTROL_GAP_DP * density));
        // The lower stack's height, measured with the media collapsed.
        LinearLayout.LayoutParams mediaLayoutParams =
                (LinearLayout.LayoutParams)
                        avoidanceMediaView.getLayoutParams();
        mediaLayoutParams.width = 0;
        mediaLayoutParams.height = 0;
        avoidanceMediaView.setLayoutParams(mediaLayoutParams);
        avoidanceColumn.setPadding(
                avoidanceColumn.getPaddingLeft()
              , 0
              , avoidanceColumn.getPaddingRight()
              , 0);
        avoidanceColumn.measure(
                View.MeasureSpec.makeMeasureSpec(
                        panelWidth
                      , View.MeasureSpec.EXACTLY)
              , View.MeasureSpec.makeMeasureSpec(
                        0
                      , View.MeasureSpec.UNSPECIFIED));
        int lowerContentHeight = avoidanceColumn.getMeasuredHeight();
        int availableHeight = Math.max(
                0
              , panelHeight - lowerContentHeight);

        // Both corner controls share one spot until the countdown ends, so
        // a side is an obstacle when either of them lives there.
        boolean numberLeft = numberOpposite ? !closeOnLeft : closeOnLeft;
        boolean leftOccupied = closeOnLeft || numberLeft;
        boolean rightOccupied = !closeOnLeft || !numberLeft;
        int half = panelWidth / 2;
        int topRowLeftLimit = leftOccupied
                ? Math.max(
                        0
                      , half
                            - (int) (ATTRIBUTION_WIDTH_DP * density)
                            - controlGap
                            - controlSize
                            - controlGap)
                : half;
        int topRowRightLimit = rightOccupied
                ? Math.max(
                        0
                      , panelWidth
                            - (int) (RIGHT_CONTROL_INSET_DP * density)
                            - controlSize
                            - controlGap
                            - half)
                : half;
        int topRowGapWidth = Math.min(
                panelWidth
              , 2 * Math.min(topRowLeftLimit, topRowRightLimit));
        int edgeLimit = Math.max(0, half - controlSize - controlGap);
        int edgeGapWidth = Math.min(
                panelWidth
              , 2 * Math.min(
                    leftOccupied ? edgeLimit : half
                  , rightOccupied ? edgeLimit : half));

        // {media top, media width cap, controls at edges}. Below the
        // controls the media may bleed edge to edge - the panel's full
        // width, not the inset content width.
        int[][] candidates = {
                { 0, topRowGapWidth, 0 }
              , { controlSize + controlGap, panelWidth, 0 }
              , { badgeHeight + controlGap, edgeGapWidth, 1 }
              , { badgeHeight + controlSize + 2 * controlGap
                  , panelWidth
                  , 1 }
        };
        // A reported ratio sizes the box to the creative itself: it grows
        // until it touches whichever limit binds, and the band's slack
        // splits so the block sits centred between the controls and the
        // bottom edge. Only a creative that never reported its proportions
        // is handed the whole band - no number exists to size or validate
        // it - and renders inside as it pleases on the black ground.
        long bestScore = -1L;
        int bestBoxWidth = Math.max(panelWidth, avoidanceMinimumMediaSize);
        int bestBoxHeight = Math.max(
                avoidanceMinimumMediaSize
              , availableHeight);
        int bestTop = 0;
        boolean bestEdges = false;
        for (int[] candidate : candidates) {
            int top = candidate[0];
            int widthCap = candidate[1];
            if (widthCap < avoidanceMinimumMediaSize) continue;

            int bandHeight = availableHeight - top;
            if (bandHeight < avoidanceMinimumMediaSize) continue;

            int boxWidth;
            int boxHeight;
            if (mediaAspectReported) {
                boxHeight = Math.min(
                        bandHeight
                      , Math.round(widthCap / avoidanceMediaAspect));
                boxWidth = Math.min(
                        widthCap
                      , Math.round(boxHeight * avoidanceMediaAspect));
                if (boxWidth < avoidanceMinimumMediaSize
                        || boxHeight < avoidanceMinimumMediaSize) {
                    continue;
                }
            } else {
                boxWidth = widthCap;
                boxHeight = bandHeight;
            }

            long score = (long) boxWidth * boxHeight;
            if (score > bestScore) {
                bestScore = score;
                bestBoxWidth = boxWidth;
                bestBoxHeight = boxHeight;
                bestTop = top;
                bestEdges = candidate[2] == 1;
            }
        }

        int slack = mediaAspectReported
                ? Math.max(
                        0
                      , availableHeight - bestTop - bestBoxHeight)
                : 0;
        controlsAtEdgesBelowBadges = bestEdges;
        avoidanceColumn.setPadding(
                avoidanceColumn.getPaddingLeft()
              , bestTop
              , avoidanceColumn.getPaddingRight()
              , slack / 2);
        mediaLayoutParams.width = bestBoxWidth;
        mediaLayoutParams.height = bestBoxHeight;
        mediaLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        avoidanceMediaView.setLayoutParams(mediaLayoutParams);
        requestLayout();
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
          , NativeAdStarRatingView starRating
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
        if (hasDisplayableMedia && !sideMediaLayout
                && !controlAvoidanceActive) {
            if (!mediaAvoidsControlStrip) SetTopInset(contentColumn, 0);
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
        // A strip-avoiding media is inset like every other element, so its
        // width basis is the content width, not the bleed's screen width.
        int mediaWidthBasis = mediaAvoidsControlStrip
                ? contentWidth
                : displayMetrics.widthPixels;
        int naturalMediaHeight = Math.max(
                minimumMediaSize
              , Math.round(
                    mediaWidthBasis / mediaAspectRatio));
        int resolvedMediaHeight = Math.max(
                minimumMediaSize
              , Math.min(
                    naturalMediaHeight
                  , Math.max(minimumMediaSize, availableMediaHeight)));
        int resolvedMediaWidth = Math.min(
                mediaWidthBasis
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
        if (panelHeight > displayMetrics.widthPixels
                * SIDE_MEDIA_MAX_PANEL_HEIGHT_RATIO) {
            return false;
        }

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
        // here would shrink it right back after layout. A strip-avoiding
        // media keeps the inset instead, so its cap is the padded width.
        int availableWidth = mediaAvoidsControlStrip
                ? contentColumn.getWidth()
                        - contentColumn.getPaddingLeft()
                        - contentColumn.getPaddingRight()
                : contentColumn.getWidth();
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
            // The left control follows the attribution badge - which in the
            // side-media layout already stepped off the picture to the
            // media's edge. When the avoidance pass chose the edge
            // placement, the controls hug the panel's sides just below the
            // badges instead of the top row.
            int measuredLeftInset;
            int measuredRightInset;
            int measuredTopInset;
            if (controlsAtEdgesBelowBadges) {
                measuredLeftInset = 0;
                measuredRightInset = 0;
                measuredTopInset = attribution.getBottom() + controlGap;
            } else {
                measuredLeftInset = attribution.getRight() + controlGap;
                measuredRightInset = rightControlInset;
                measuredTopInset = 0;
            }
            UpdateControlInsets(
                    countdown
                  , measuredLeftInset
                  , measuredRightInset
                  , measuredTopInset);
            UpdateControlInsets(
                    close
                  , measuredLeftInset
                  , measuredRightInset
                  , measuredTopInset);
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
        mediaAspectReported = true;
        if (fallbackMediaImage != null
                && fallbackMediaImage.getIntrinsicWidth() > 0
                && fallbackMediaImage.getIntrinsicHeight() > 0) {
            return fallbackMediaImage.getIntrinsicWidth()
                    / (float) fallbackMediaImage.getIntrinsicHeight();
        }

        com.google.android.gms.ads.MediaContent mediaContent =
                nativeAd.getMediaContent();
        if (mediaContent != null) {
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
            if (aspectRatio > 0f
                    && !Float.isNaN(aspectRatio)
                    && !Float.isInfinite(aspectRatio)) {
                return aspectRatio;
            }
        }

        mediaAspectReported = false;
        return DEFAULT_MEDIA_ASPECT_RATIO;
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
          , int rightInset
          , int topInset) {
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) control.getLayoutParams();
        if (layoutParams.leftMargin == leftInset
                && layoutParams.rightMargin == rightInset
                && layoutParams.topMargin == topInset) {
            return;
        }
        layoutParams.setMargins(leftInset, topInset, rightInset, 0);
        control.setLayoutParams(layoutParams);
    }
}
