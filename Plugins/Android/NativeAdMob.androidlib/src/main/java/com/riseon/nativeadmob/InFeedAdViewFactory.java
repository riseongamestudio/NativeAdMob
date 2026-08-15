package com.riseon.nativeadmob;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.MediaContent;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.List;

final class InFeedAdViewFactory {
    private static final String ATTRIBUTION_TEXT = "Ad";
    private static final String ATTRIBUTION_BACKGROUND_COLOR = "#FFFFC107";
    private static final String SECONDARY_TEXT_COLOR = "#CCFFFFFF";
    private static final String RATING_TEXT_COLOR = "#FFFFC107";
    private static final String MEDIA_CONTENT_REQUIRED_MESSAGE =
            "MediaContent is required for a selected media layout";

    private static final int NO_POLICY_TEXT_LIMIT_UNITS = 0;
    // The 120dp floor belongs to video: the SDK warning that enforces it is
    // conditional on the media view showing video, and whether it ever will is
    // the creative's property, known before layout. A creative with no video
    // keeps its still image in a smaller registered MediaView, so a slot too
    // small for video does not lose the picture; only a video creative falls
    // back to media-free layouts there.
    private static final float MIN_VIDEO_MEDIA_SIZE_DP = 120f;
    private static final float MIN_IMAGE_MEDIA_SIZE_DP = 48f;
    private static final int MIN_ATTRIBUTION_SIZE_PX = 15;
    private static final int ATTRIBUTION_WIDTH_DP = 24;
    private static final int ATTRIBUTION_HEIGHT_DP = 18;
    private static final int AD_CHOICES_MIN_WIDTH_PX = 19;
    private static final int CONTENT_EDGE_INSET_PX = 0;
    // On a 96dp slot the short-side ratio lands on this floor every time, so
    // the floor is the height. 18dp was under anything worth pressing; 28dp
    // outweighed everything else in the slot - a full-width bar taking a
    // quarter of the height reads as the ad's main content, which the button
    // is not. 24dp still presses (the whole rect is clickable anyway) and
    // returns the difference to the assets above it.
    private static final int CTA_MIN_HEIGHT_DP = 24;
    private static final int VIDEO_FOOTER_CTA_HEIGHT_DP = 24;
    private static final int VIDEO_FOOTER_HORIZONTAL_PADDING_DP = 4;
    private static final int VIDEO_FOOTER_BOTTOM_PADDING_DP = 2;
    private static final int VIDEO_RAIL_MIN_WIDTH_COMPACT_DP = 72;
    private static final int VIDEO_RAIL_MIN_WIDTH_REGULAR_DP = 88;
    private static final int VIDEO_RAIL_MIN_WIDTH_ROOMY_DP = 104;
    private static final int VIDEO_RAIL_ICON_COMPACT_DP = 36;
    private static final int VIDEO_RAIL_ICON_REGULAR_DP = 40;
    private static final int VIDEO_RAIL_ICON_ROOMY_DP = 48;
    private static final int VIDEO_RAIL_CTA_HEIGHT_COMPACT_DP = 32;
    private static final int VIDEO_RAIL_CTA_HEIGHT_REGULAR_DP = 36;
    private static final int VIDEO_RAIL_CTA_HEIGHT_ROOMY_DP = 40;
    private static final int VIDEO_RAIL_EDGE_PADDING_DP = 2;
    private static final int VIDEO_HEADLINE_HORIZONTAL_PADDING_DP = 4;
    private static final int VIDEO_HEADLINE_VERTICAL_PADDING_DP = 2;
    private static final int MEDIA_LEFT_TEXT_WIDTH_COMPACT_DP = 80;
    private static final int MEDIA_LEFT_TEXT_WIDTH_REGULAR_DP = 96;
    private static final int MEDIA_LEFT_TEXT_WIDTH_ROOMY_DP = 120;
    // The floor for an icon standing in for the media: anything under this
    // reads as decoration, not as an asset worth the space it was given.
    private static final int MIN_FILLER_ICON_DP = 24;
    private static final float OPTIONAL_TEXT_MIN_SP = 10f;
    private static final float BADGE_SHORT_SIDE_RATIO = 0.10f;
    private static final float ATTRIBUTION_ASPECT_RATIO = 4f / 3f;
    private static final String CTA_BACKGROUND_COLOR = "#FF2196F3";
    private static final String CTA_BORDER_COLOR = "#FF1565C0";
    private static final String CTA_TEXT_COLOR = "#FFFFFFFF";
    private static final String CTA_RIPPLE_COLOR = "#55FFFFFF";
    // Opaque enough that text over any picture stays readable - the same
    // warrant the badges carry for sitting on the media. Painted as one veil
    // over the whole cell, never as a band that stops short of an edge.
    private static final String SCRIM_BACKGROUND_COLOR = "#B3000000";
    // The dimming over the ambient backdrop - dark enough that the fitted
    // creative in front stays the one that reads as the picture.
    private static final int AMBIENT_DIM_COLOR = 0x8C000000;
    // The least line width worth giving a headline next to an icon. Below
    // it even a marquee reads as a sliver, so the icon leaves the line and
    // the text takes the full width instead.
    private static final int MIN_ICON_ROW_TEXT_WIDTH_DP = 72;
    private static final int CTA_BORDER_WIDTH_DP = 1;
    private static final float ATTRIBUTION_TEXT_HEIGHT_RATIO = 0.55f;
    // Boxes are sized from the ad, labels are sized from their box. The two
    // short-side ratios are deliberately related - the call to action box is
    // 1.8x the badge box - so the labels inside them stay in the same
    // proportion without either one knowing about the other.
    private static final float CTA_SHORT_SIDE_RATIO = 0.18f;
    private static final int CTA_MAX_HEIGHT_DP = 36;
    private static final float CTA_TEXT_HEIGHT_RATIO = 0.45f;
    private static final double MAX_STAR_RATING = 5d;

    // The height handed to the button is a minimum; the row it sits in
    // stretches it well past that, and a label sized from the minimum then sits
    // lost inside a much taller box. Take the size from the height the button
    // actually ends up with.
    private static final class CallToActionButton
            extends android.widget.Button {
        private static final float TEXT_SIZE_EPSILON_PX = 0.5f;

        CallToActionButton(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(
                int width
              , int height
              , int oldWidth
              , int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            ApplyLabelSize(height);
        }

        void ApplyLabelSize(int heightPx) {
            // Against the whole button, not what is left after its padding:
            // taking the ratio of the remainder left the label at barely a
            // third of the height it sits in.
            float target = Math.max(1f, heightPx) * CTA_TEXT_HEIGHT_RATIO;
            // Converges in a pass: without the tolerance every resize would set
            // a size that triggers another resize.
            if (Math.abs(getTextSize() - target) < TEXT_SIZE_EPSILON_PX) return;

            setTextSize(TypedValue.COMPLEX_UNIT_PX, target);
        }
    }

    static final class AssetViews {
        TextView headline;
        TextView body;
        TextView advertiser;
        TextView rating;
        ImageView icon;
        Button callToAction;
        View media;
        TextView attribution;
        View adChoicesReserve;
        LinearLayout outer;
        View mediaSlot;
        View videoRail;
        View scrim;
        View insetContent;
        boolean insetContentAvoidsBadges;
    }

    static final class ProbeLayout {
        int template;
        int tier;
        boolean showMedia;
        boolean renderVideo;
        View root;
        AssetViews views;
    }

    static final class NativeAdViewResult {
        final NativeAdView nativeAdView;
        final AssetViews assetViews;

        NativeAdViewResult(
                NativeAdView nativeAdView
              , AssetViews assetViews) {
            this.nativeAdView = nativeAdView;
            this.assetViews = assetViews;
        }
    }

    private final Activity activity;
    private final com.google.android.gms.ads.nativead.NativeAd nativeAd;
    private final float density;
    private final int slotShortSidePx;

    InFeedAdViewFactory(
            Activity activity
          , com.google.android.gms.ads.nativead.NativeAd nativeAd
          , float density
          , int slotShortSidePx) {
        this.activity = activity;
        this.nativeAd = nativeAd;
        this.density = density;
        this.slotShortSidePx = Math.max(1, slotShortSidePx);
    }

    ProbeLayout CreateProbe(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        ProbeLayout probe = new ProbeLayout();
        probe.template = plan.template;
        probe.tier = plan.tier;
        probe.showMedia = plan.showMedia;
        probe.renderVideo = plan.renderVideo;
        probe.views = new AssetViews();
        View content = BuildContent(plan, true, probe.views, null);
        FrameLayout root = new FrameLayout(activity);
        root.addView(
                content
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.MATCH_PARENT));
        probe.root = root;
        return probe;
    }

    void ConfigureProbeLayout(
            ProbeLayout probe
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        ConfigureInsetContent(probe.views, plan);
        ConfigureBadgeOverlays(probe.views, plan);
        ApplyPlanTextConfiguration(probe.views, plan);

        if (probe.views.mediaSlot != null) {
            ViewGroup.LayoutParams layoutParams =
                    probe.views.mediaSlot.getLayoutParams();
            layoutParams.width = plan.mediaWidth;
            layoutParams.height = plan.mediaHeight;
            probe.views.mediaSlot.setLayoutParams(layoutParams);
        }
        if (probe.views.videoRail != null) {
            ViewGroup.LayoutParams layoutParams =
                    probe.views.videoRail.getLayoutParams();
            layoutParams.height = plan.mediaHeight;
            probe.views.videoRail.setLayoutParams(layoutParams);
        }
    }

    // A cached probe tree was built for some earlier point on the scale
    // ladder; this walks it back onto the plan being tried. Sizes and modes
    // are the only things the ladder varies - everything structural is part
    // of the probe cache key.
    private void ApplyPlanTextConfiguration(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        if (views.headline != null) {
            views.headline.setTextSize(HeadlineSp(plan));
            if (!plan.renderVideo) {
                ApplyTextMode(
                        plan.marqueeHeadline
                      , views.headline
                      , HeadlineMaxLines(plan));
            }
        }
        if (views.body != null) {
            views.body.setTextSize(BodySp(plan));
            ApplyTextMode(
                    plan.marqueeSecondary
                  , views.body
                  , BodyMaxLines(plan));
        }
        if (views.advertiser != null) {
            views.advertiser.setTextSize(OptionalSp(plan));
            ApplyTextMode(
                    plan.marqueeSecondary
                  , views.advertiser
                  , AdvertiserMaxLines(plan));
        }
        if (views.rating != null) {
            views.rating.setTextSize(OptionalSp(plan));
        }
    }

    NativeAdViewResult BuildNativeAdView(
            InFeedAdLayoutEngine.LayoutPlan plan
          , Drawable mainImage) {
        NativeAdView nativeAdView = new NativeAdView(activity);
        boolean completed = false;
        try {
            // Left clickable so a tap anywhere in the slot reaches the
            // advertiser, matching OverlayAdContentView, which never
            // takes the click off its own NativeAdView.
            AssetViews views = new AssetViews();
            View content = BuildContent(plan, false, views, mainImage);
            nativeAdView.addView(
                    content
                  , new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.MATCH_PARENT));

            if (views.headline != null) {
                nativeAdView.setHeadlineView(views.headline);
            }
            if (views.body != null) nativeAdView.setBodyView(views.body);
            if (views.icon != null) nativeAdView.setIconView(views.icon);
            if (views.callToAction != null) {
                nativeAdView.setCallToActionView(views.callToAction);
            }
            if (views.advertiser != null) {
                nativeAdView.setAdvertiserView(views.advertiser);
            }
            if (views.rating != null) {
                nativeAdView.setStarRatingView(views.rating);
            }
            if (plan.showMedia && views.media != null) {
                // The primary asset must always be registered through
                // MediaView. Registering it with setImageView() makes AdMob
                // reject every request for this unit with error code 3
                // ("Asset uses ImageView, not MediaView").
                if (!(views.media instanceof MediaView)) {
                    throw new IllegalStateException(
                            "Selected media layout created an incompatible view");
                }
                nativeAdView.setMediaView((MediaView) views.media);
            }

            nativeAdView.setNativeAd(nativeAd);
            completed = true;
            return new NativeAdViewResult(nativeAdView, views);
        } finally {
            if (!completed) nativeAdView.destroy();
        }
    }

    boolean HasVideoContent() {
        MediaContent content = nativeAd.getMediaContent();
        return content != null && content.hasVideoContent();
    }

    boolean HasValidStarRating() {
        return ResolveStarRating() > 0d;
    }

    double ResolveStarRating() {
        Double starRating = nativeAd.getStarRating();
        if (starRating == null
                || starRating <= 0d
                || starRating.isNaN()
                || starRating.isInfinite()) {
            return 0d;
        }
        return Math.min(MAX_STAR_RATING, starRating);
    }

    boolean CanRenderMainImage(Drawable mainImage) {
        // A video creative can also expose a fallback image through getImages().
        // Image layouts draw it inside the MediaView instead of handing over
        // MediaContent, so the video never plays in an undersized slot.
        return mainImage != null;
    }

    boolean HasUnrenderableIcon() {
        com.google.android.gms.ads.nativead.NativeAd.Image icon =
                nativeAd.getIcon();
        return icon != null && icon.getDrawable() == null;
    }

    boolean HasRenderableIcon() {
        com.google.android.gms.ads.nativead.NativeAd.Image icon =
                nativeAd.getIcon();
        return icon != null && icon.getDrawable() != null;
    }

    Drawable FindMainImage() {
        List<com.google.android.gms.ads.nativead.NativeAd.Image> images =
                nativeAd.getImages();
        if (images != null) {
            for (com.google.android.gms.ads.nativead.NativeAd.Image image
                    : images) {
                if (image != null && image.getDrawable() != null) {
                    return image.getDrawable();
                }
            }
        }

        MediaContent content = nativeAd.getMediaContent();
        if (content != null && !content.hasVideoContent()) {
            return content.getMainImage();
        }
        return null;
    }

    int MinimumWidth(int tier) {
        int horizontalPadding = PaddingForTier(tier) * 2;
        return MediaPolicyFloorPx() + horizontalPadding;
    }

    // The smallest a registered MediaView may end up on either side:
    // video's floor for a video creative, the image floor otherwise.
    int MediaPolicyFloorPx() {
        return Dp(
                HasVideoContent()
                        ? MIN_VIDEO_MEDIA_SIZE_DP
                        : MIN_IMAGE_MEDIA_SIZE_DP);
    }

    int MediaSizeForTier(int tier) {
        if (HasVideoContent()) {
            if (tier == InFeedAdLayoutEngine.TIER_COMPACT) {
                return Dp(MIN_VIDEO_MEDIA_SIZE_DP);
            }
            if (tier == InFeedAdLayoutEngine.TIER_REGULAR) {
                return Dp(144);
            }
            return Dp(180);
        }
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return Dp(56);
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return Dp(88);
        return Dp(120);
    }

    // Edge inset only, and the slot is already bounded by the card it sits in,
    // so this buys nothing that the surrounding layout does not already give.
    // On a 96dp slot the old roomy value spent 8% of the width on it.
    int PaddingForTier(int tier) {
        return 0;
    }

    int GapForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return Dp(1);
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return Dp(3);
        return Dp(5);
    }

    int PreferredTier(int width, int height) {
        int shortSide = Math.min(width, height);
        if (shortSide >= Dp(280)) {
            return InFeedAdLayoutEngine.TIER_ROOMY;
        }
        if (shortSide >= Dp(150)) {
            return InFeedAdLayoutEngine.TIER_REGULAR;
        }
        return InFeedAdLayoutEngine.TIER_COMPACT;
    }

    int MinimumMediaLeftTextSlotWidth(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        int minimumDp;
        if (plan.renderVideo) {
            if (plan.tier == InFeedAdLayoutEngine.TIER_ROOMY) {
                minimumDp = VIDEO_RAIL_MIN_WIDTH_ROOMY_DP;
            } else if (plan.tier
                    == InFeedAdLayoutEngine.TIER_REGULAR) {
                minimumDp = VIDEO_RAIL_MIN_WIDTH_REGULAR_DP;
            } else {
                minimumDp = VIDEO_RAIL_MIN_WIDTH_COMPACT_DP;
            }
        } else if (plan.tier
                == InFeedAdLayoutEngine.TIER_ROOMY) {
            minimumDp = MEDIA_LEFT_TEXT_WIDTH_ROOMY_DP;
        } else if (plan.tier
                == InFeedAdLayoutEngine.TIER_REGULAR) {
            minimumDp = MEDIA_LEFT_TEXT_WIDTH_REGULAR_DP;
        } else {
            minimumDp = MEDIA_LEFT_TEXT_WIDTH_COMPACT_DP;
        }
        return Dp(minimumDp);
    }

    int Dp(float value) {
        return (int) Math.ceil(value * density);
    }

    private View BuildContent(
            InFeedAdLayoutEngine.LayoutPlan plan
          , boolean probe
          , AssetViews outViews
          , Drawable mainImage) {
        AssetViews views = outViews != null ? outViews : new AssetViews();
        int gap = GapForTier(plan.tier);
        FrameLayout root = new FrameLayout(activity);

        LinearLayout outer = new LinearLayout(activity);
        views.outer = outer;
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        outer.setPadding(0, 0, 0, 0);

        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_MEDIA_BACKGROUND) {
            View backgroundMedia = CreateMediaView(
                    plan
                  , views
                  , probe
                  , mainImage);
            root.addView(
                    backgroundMedia
                  , new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.MATCH_PARENT));

            // One veil over the whole cell, not a band that stops short of
            // the top edge: the media reads as the panel's background
            // through it and no uncovered strip is left above the text.
            View veil = new View(activity);
            veil.setBackgroundColor(
                    Color.parseColor(SCRIM_BACKGROUND_COLOR));
            veil.setClickable(false);
            veil.setFocusable(false);
            root.addView(
                    veil
                  , new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout scrim = new LinearLayout(activity);
            scrim.setOrientation(LinearLayout.VERTICAL);
            // Slim borders: the veil already separates the text from the
            // picture, so the block spends no more than the tier's own gap
            // vertically and a hair more horizontally.
            int scrimPad = Math.max(gap, Dp(2));
            scrim.setPadding(scrimPad, gap, scrimPad, gap);

            views.headline = CreateText(
                    nativeAd.getHeadline()
                  , HeadlineSp(plan)
                  , true
                  , NO_POLICY_TEXT_LIMIT_UNITS);
            ApplyTextMode(
                    plan.marqueeHeadline
                  , views.headline
                  , HeadlineMaxLines(plan));
            // The icon only shares the headline's line while the line keeps a
            // usable width for text - a marquee squeezed into a sliver reads
            // worse than no icon row at all. Below that floor the icon stands
            // alone and every text follows at full width.
            int scrimContentWidth = Math.max(0, plan.width - 2 * scrimPad);
            boolean iconStandsAlone = plan.showIcon
                    && HasRenderableIcon()
                    && scrimContentWidth
                                - IconSizeForTier(plan.tier)
                                - gap
                            < Dp(MIN_ICON_ROW_TEXT_WIDTH_DP);
            if (iconStandsAlone) {
                AddIcon(scrim, views, plan.tier, 0, probe);
                if (views.icon != null) {
                    LinearLayout.LayoutParams iconParams =
                            (LinearLayout.LayoutParams)
                                    views.icon.getLayoutParams();
                    iconParams.gravity = Gravity.CENTER_HORIZONTAL;
                    iconParams.bottomMargin = gap;
                    views.icon.setLayoutParams(iconParams);
                }
                scrim.addView(
                        views.headline
                      , new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT
                          , ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                LinearLayout headlineRow = new LinearLayout(activity);
                headlineRow.setOrientation(LinearLayout.HORIZONTAL);
                headlineRow.setGravity(Gravity.CENTER_VERTICAL);
                if (plan.showIcon) {
                    AddIcon(headlineRow, views, plan.tier, gap, probe);
                }
                headlineRow.addView(
                        views.headline
                      , new LinearLayout.LayoutParams(
                            0
                          , ViewGroup.LayoutParams.WRAP_CONTENT
                          , 1f));
                scrim.addView(
                        headlineRow
                      , new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT
                          , ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            AddBodyAndOptional(scrim, views, plan);
            AddCallToAction(scrim, views, plan, gap, true);

            // Not the inset content: ConfigureInsetContent would
            // reset the scrim's own padding to the slot edge.
            views.scrim = scrim;
            outer.setGravity(
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            outer.addView(
                    scrim
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
            return FinishContentRoot(root, outer, views, plan);
        }

        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_COMPACT_ROW) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            views.insetContent = row;
            views.insetContentAvoidsBadges = true;
            if (plan.showIcon) AddIcon(row, views, plan.tier, gap, probe);

            LinearLayout texts = BuildTextStack(views, plan);
            row.addView(
                    texts
                  , new LinearLayout.LayoutParams(
                        0
                      , ViewGroup.LayoutParams.WRAP_CONTENT
                      , 1f));
            AddCallToAction(row, views, plan, gap, false);
            // One row of assets in a tall slot looked broken glued to the top
            // edge with the rest of the slot blank under it. Its natural height
            // is unchanged, so this moves where the row sits without touching
            // what the engine measures when it scores the plan.
            outer.setGravity(
                    Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            outer.addView(
                    row
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
            return FinishContentRoot(root, outer, views, plan);
        }

        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_COMPACT_COLUMN) {
            LinearLayout content = BuildHeadlineAndActionStack(
                    views
                  , plan
                  , probe);
            views.insetContent = content;
            // BuildHeadlineAndActionStack decides the badge reserve: it knows
            // whether the top band holds the icon or the headline.
            outer.addView(
                    content
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , 0
                      , 1f));
            return FinishContentRoot(root, outer, views, plan);
        }

        View mediaView = CreateMediaView(
                plan
              , views
              , probe
              , mainImage);
        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_MEDIA_LEFT) {
            if (plan.renderVideo) {
                BuildVideoMediaLeftContent(
                        outer
                      , mediaView
                      , views
                      , plan
                      , probe);
                return FinishContentRoot(root, outer, views, plan);
            }

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams mediaLayoutParams =
                    new LinearLayout.LayoutParams(
                            plan.mediaWidth
                          , plan.mediaHeight);
            mediaLayoutParams.setMarginEnd(gap);
            row.addView(mediaView, mediaLayoutParams);

            LinearLayout content = BuildIdentityAndText(
                    views
                  , plan
                  , probe);
            views.insetContent = content;
            views.insetContentAvoidsBadges = true;
            row.addView(
                    content
                  , new LinearLayout.LayoutParams(
                        0
                      , plan.mediaHeight
                      , 1f));
            outer.addView(
                    row
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
            return FinishContentRoot(root, outer, views, plan);
        }

        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_MEDIA_RIGHT) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout content = BuildIdentityAndText(
                    views
                  , plan
                  , probe);
            views.insetContent = content;
            views.insetContentAvoidsBadges = true;
            row.addView(
                    content
                  , new LinearLayout.LayoutParams(
                        0
                      , plan.mediaHeight
                      , 1f));

            LinearLayout.LayoutParams sideMediaParams =
                    new LinearLayout.LayoutParams(
                            plan.mediaWidth
                          , plan.mediaHeight);
            sideMediaParams.setMarginStart(gap);
            row.addView(mediaView, sideMediaParams);
            outer.addView(
                    row
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
            return FinishContentRoot(root, outer, views, plan);
        }

        if (plan.renderVideo) {
            LinearLayout.LayoutParams mediaLayoutParams =
                    new LinearLayout.LayoutParams(
                            plan.mediaWidth
                          , plan.mediaHeight);
            mediaLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            mediaLayoutParams.bottomMargin = gap;
            outer.addView(mediaView, mediaLayoutParams);

            LinearLayout footer = BuildVideoFooterRow(views, plan);
            views.insetContent = footer;
            views.insetContentAvoidsBadges = false;
            outer.addView(
                    footer
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            // The image media takes the plan's size as its floor and absorbs
            // whatever height the slot has left over, so free space enlarges
            // the picture instead of sitting as an empty band inside the
            // text block. The minimums keep the engine's natural measure -
            // and so the validator's math - exactly the plan's.
            mediaView.setMinimumWidth(plan.mediaWidth);
            mediaView.setMinimumHeight(plan.mediaHeight);
            LinearLayout.LayoutParams mediaLayoutParams =
                    new LinearLayout.LayoutParams(
                            plan.mediaWidth
                          , 0
                          , 1f);
            mediaLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            mediaLayoutParams.bottomMargin = gap;
            outer.addView(mediaView, mediaLayoutParams);

            LinearLayout content = BuildHeadlineAndActionStack(
                    views
                  , plan
                  , probe);
            views.insetContent = content;
            views.insetContentAvoidsBadges = false;
            outer.addView(
                    content
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return FinishContentRoot(root, outer, views, plan);
    }

    private View FinishContentRoot(
            FrameLayout root
          , LinearLayout outer
          , AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        boolean fillsAvailableHeight =
                plan.template
                        == InFeedAdLayoutEngine.TEMPLATE_COMPACT_COLUMN
                || plan.template
                        == InFeedAdLayoutEngine.TEMPLATE_MEDIA_TOP
                || plan.template
                        == InFeedAdLayoutEngine
                                .TEMPLATE_MEDIA_BACKGROUND;
        root.addView(
                outer
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , fillsAvailableHeight
                            ? ViewGroup.LayoutParams.MATCH_PARENT
                            : ViewGroup.LayoutParams.WRAP_CONTENT));
        AddBadgeOverlays(root, views);
        ConfigureInsetContent(views, plan);
        ConfigureBadgeOverlays(views, plan);
        return root;
    }

    private void AddBadgeOverlays(
            FrameLayout root
          , AssetViews views) {
        views.attribution = new TextView(activity);
        views.attribution.setText(ATTRIBUTION_TEXT);
        views.attribution.setTextColor(Color.BLACK);
        views.attribution.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        views.attribution.setGravity(Gravity.CENTER);
        views.attribution.setIncludeFontPadding(false);
        views.attribution.setPadding(0, 0, 0, 0);
        views.attribution.setClickable(false);
        views.attribution.setFocusable(false);
        GradientDrawable attributionBackground = new GradientDrawable();
        attributionBackground.setShape(GradientDrawable.RECTANGLE);
        attributionBackground.setColor(
                Color.parseColor(ATTRIBUTION_BACKGROUND_COLOR));
        views.attribution.setBackground(attributionBackground);
        root.addView(views.attribution);

        if (nativeAd.getAdChoicesInfo() != null) {
            views.adChoicesReserve = new View(activity);
            views.adChoicesReserve.setClickable(false);
            views.adChoicesReserve.setFocusable(false);
            root.addView(views.adChoicesReserve);
        }
    }

    private void ConfigureInsetContent(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        if (views.insetContent == null) return;

        if (plan.template
                    == InFeedAdLayoutEngine.TEMPLATE_MEDIA_TOP
                && plan.renderVideo) {
            views.insetContent.setPadding(
                    Dp(VIDEO_FOOTER_HORIZONTAL_PADDING_DP)
                  , 0
                  , Dp(VIDEO_FOOTER_HORIZONTAL_PADDING_DP)
                  , Dp(VIDEO_FOOTER_BOTTOM_PADDING_DP));
            return;
        }
        if (plan.template
                    == InFeedAdLayoutEngine.TEMPLATE_MEDIA_LEFT
                && plan.renderVideo) {
            int edgePadding = Dp(VIDEO_RAIL_EDGE_PADDING_DP);
            views.insetContent.setPadding(
                    edgePadding
                  , BadgeHeightPx(plan) + GapForTier(plan.tier)
                  , edgePadding
                  , edgePadding);
            return;
        }

        int edgePadding = PaddingForTier(plan.tier);
        // Only the badge strip is reserved - the badges have to stay visible.
        // Everything else meets the slot edge, the way the badges already do.
        int topPadding = views.insetContentAvoidsBadges
                ? BadgeHeightPx(plan)
                : 0;
        views.insetContent.setPadding(
                edgePadding
              , topPadding
              , edgePadding
              , edgePadding);
    }

    private void ConfigureBadgeOverlays(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        int badgeHeight = BadgeHeightPx(plan);
        int edgeInset = CONTENT_EDGE_INSET_PX;
        if (views.attribution != null) {
            int attributionWidth = AttributionWidthPx(plan, badgeHeight);
            views.attribution.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX
                  , Math.max(
                        1f
                      , badgeHeight * ATTRIBUTION_TEXT_HEIGHT_RATIO));
            FrameLayout.LayoutParams attributionParams =
                    new FrameLayout.LayoutParams(
                            attributionWidth
                          , badgeHeight
                          , Gravity.TOP | Gravity.LEFT);
            attributionParams.leftMargin = edgeInset;
            attributionParams.topMargin = edgeInset;
            views.attribution.setLayoutParams(attributionParams);
        }
        if (views.adChoicesReserve != null) {
            int reserveWidth = Math.max(
                    AD_CHOICES_MIN_WIDTH_PX
                  , badgeHeight);
            int maximumWidth = Math.max(
                    MIN_ATTRIBUTION_SIZE_PX
                  , plan.width);
            FrameLayout.LayoutParams adChoicesParams =
                    new FrameLayout.LayoutParams(
                            Math.min(reserveWidth, maximumWidth)
                          , badgeHeight
                          , Gravity.TOP | Gravity.RIGHT);
            adChoicesParams.rightMargin = edgeInset;
            adChoicesParams.topMargin = edgeInset;
            views.adChoicesReserve.setLayoutParams(adChoicesParams);
        }
    }

    private int BadgeHeightPx(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        return Clamp(
                Math.round(slotShortSidePx * BADGE_SHORT_SIDE_RATIO)
              , MIN_ATTRIBUTION_SIZE_PX
              , Dp(ATTRIBUTION_HEIGHT_DP));
    }

    private int AttributionWidthPx(
            InFeedAdLayoutEngine.LayoutPlan plan
          , int badgeHeight) {
        int desiredWidth = Math.round(
                badgeHeight * ATTRIBUTION_ASPECT_RATIO);
        int maximumWidth = Math.max(
                MIN_ATTRIBUTION_SIZE_PX
              , plan.width - badgeHeight);
        return Clamp(
                desiredWidth
              , MIN_ATTRIBUTION_SIZE_PX
              , Math.min(Dp(ATTRIBUTION_WIDTH_DP), maximumWidth));
    }

    private void BuildVideoMediaLeftContent(
            LinearLayout outer
          , View mediaView
          , AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan
          , boolean probe) {
        int gap = GapForTier(plan.tier);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout.LayoutParams mediaLayoutParams =
                new LinearLayout.LayoutParams(
                        plan.mediaWidth
                      , plan.mediaHeight);
        mediaLayoutParams.setMarginEnd(gap);
        row.addView(mediaView, mediaLayoutParams);

        LinearLayout rail = BuildVideoSideRail(views, plan, probe);
        views.videoRail = rail;
        views.insetContent = rail;
        views.insetContentAvoidsBadges = true;
        row.addView(
                rail
              , new LinearLayout.LayoutParams(
                    0
                  , plan.mediaHeight
                  , 1f));
        outer.addView(
                row
              , new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.WRAP_CONTENT));

        views.headline = CreateText(
                nativeAd.getHeadline()
              , HeadlineSp(plan)
              , true
              , NO_POLICY_TEXT_LIMIT_UNITS);
        // One line by design, so it marquees rather than ellipsizing: the
        // headline is either whole or on a line that scrolls, never cut.
        ApplyTextMode(false, views.headline, 1);
        views.headline.setIncludeFontPadding(false);
        views.headline.setGravity(Gravity.CENTER_VERTICAL);
        views.headline.setMinWidth(0);
        views.headline.setMinimumWidth(0);
        views.headline.setPadding(
                Dp(VIDEO_HEADLINE_HORIZONTAL_PADDING_DP)
              , Dp(VIDEO_HEADLINE_VERTICAL_PADDING_DP)
              , Dp(VIDEO_HEADLINE_HORIZONTAL_PADDING_DP)
              , Dp(VIDEO_HEADLINE_VERTICAL_PADDING_DP));
        LinearLayout.LayoutParams headlineParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT);
        headlineParams.topMargin = gap;
        outer.addView(views.headline, headlineParams);
    }

    private LinearLayout BuildVideoSideRail(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan
          , boolean probe) {
        int gap = GapForTier(plan.tier);
        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout iconRow = new LinearLayout(activity);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER);
        AddSizedIcon(
                iconRow
              , views
              , VideoRailIconSizeForTier(plan.tier)
              , 0
              , probe);
        if (views.icon != null) {
            rail.addView(
                    iconRow
                  , new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        View spacer = new View(activity);
        rail.addView(
                spacer
              , new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , 0
                  , 1f));

        AddCallToAction(rail, views, plan, gap, true);
        if (views.callToAction != null) {
            views.callToAction.setSingleLine(false);
            views.callToAction.setMaxLines(2);
            views.callToAction.setGravity(Gravity.CENTER);
        }
        return rail;
    }

    private LinearLayout BuildVideoFooterRow(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        int gap = GapForTier(plan.tier);
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);

        views.headline = CreateText(
                nativeAd.getHeadline()
              , HeadlineSp(plan)
              , true
              , NO_POLICY_TEXT_LIMIT_UNITS);
        // One line by design, so it marquees rather than clipping: the
        // headline is either whole or on a line that scrolls, never cut.
        ApplyTextMode(false, views.headline, 1);
        views.headline.setIncludeFontPadding(false);
        views.headline.setGravity(Gravity.CENTER_VERTICAL);
        views.headline.setMinWidth(0);
        views.headline.setMinimumWidth(0);
        footer.addView(
                views.headline
              , new LinearLayout.LayoutParams(
                    0
                  , ViewGroup.LayoutParams.WRAP_CONTENT
                  , 1f));

        AddCallToAction(footer, views, plan, gap, false);
        if (views.callToAction != null) {
            views.callToAction.setSingleLine(true);
            views.callToAction.setMaxLines(1);
        }
        return footer;
    }

    private LinearLayout BuildIdentityAndText(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan
          , boolean probe) {
        int gap = GapForTier(plan.tier);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP);

        // The same floor the scrim template applies: the icon only shares
        // the headline's line while the line keeps a usable text width. In a
        // narrow column beside the media it stands alone instead, and the
        // texts follow below at the column's full width.
        int textColumnWidth =
                Math.max(0, plan.width - plan.mediaWidth - gap);
        boolean iconStandsAlone = plan.showIcon
                && HasRenderableIcon()
                && textColumnWidth
                            - IconSizeForTier(plan.tier)
                            - gap
                        < Dp(MIN_ICON_ROW_TEXT_WIDTH_DP);
        if (iconStandsAlone) {
            AddIcon(content, views, plan.tier, 0, probe);
            if (views.icon != null) {
                LinearLayout.LayoutParams iconParams =
                        (LinearLayout.LayoutParams)
                                views.icon.getLayoutParams();
                iconParams.gravity = Gravity.START;
                iconParams.bottomMargin = gap;
                views.icon.setLayoutParams(iconParams);
            }
        }

        LinearLayout identity = new LinearLayout(activity);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        if (plan.showIcon && !iconStandsAlone) {
            AddIcon(identity, views, plan.tier, gap, probe);
        }

        views.headline = CreateText(
                nativeAd.getHeadline()
              , HeadlineSp(plan)
              , true
              , NO_POLICY_TEXT_LIMIT_UNITS);
        ApplyTextMode(
                plan.marqueeHeadline
              , views.headline
              , HeadlineMaxLines(plan));
        views.headline.setIncludeFontPadding(false);
        FrameLayout headlineSlot = new FrameLayout(activity);
        headlineSlot.addView(
                views.headline
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT
                  , ViewGroup.LayoutParams.WRAP_CONTENT));
        identity.addView(
                headlineSlot
              , new LinearLayout.LayoutParams(
                    0
                  , ViewGroup.LayoutParams.WRAP_CONTENT
                  , 1f));
        content.addView(
                identity
              , new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.WRAP_CONTENT));

        AddBodyAndOptional(content, views, plan);

        View spacer = new View(activity);
        content.addView(
                spacer
              , new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , 0
                  , 1f));

        AddCallToAction(content, views, plan, gap, true);
        return content;
    }

    private LinearLayout BuildHeadlineAndActionStack(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan
          , boolean probe) {
        int gap = GapForTier(plan.tier);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP);

        View filler = CreateIconFiller(views, plan);
        // Standing in for the picture, the icon takes the picture's place at the
        // top so a creative with no media reads the same way round as one with
        // it. With nothing to put there the same weighted slot goes below the
        // text instead, where it is just the gap above the button.
        boolean fillerCarriesIcon = views.icon != null;
        // A badge may sit over the icon, never over text. So when the icon
        // holds the top band the badge reserve is dropped and the band starts
        // at the slot edge, the same way a media layout starts with its
        // picture. The band's base height keeps it at least badge-tall, so the
        // headline below it never rises under the badges however tight the
        // slot gets - and a slot too tight even for that rejects the candidate
        // instead of showing a sliver of icon.
        views.insetContentAvoidsBadges = !fillerCarriesIcon;
        LinearLayout.LayoutParams fillerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , fillerCarriesIcon ? BadgeHeightPx(plan) : 0
                      , 1f);
        if (fillerCarriesIcon) {
            fillerParams.bottomMargin = gap;
            content.addView(filler, fillerParams);
        }

        // With an icon standing in for the picture the text follows it, the way
        // it follows media. With neither, the text is all there is above the
        // button, so it takes the whole remaining band and centres itself in it
        // rather than clinging to the top edge with the space left underneath.
        LinearLayout textParent = content;
        if (!fillerCarriesIcon) {
            textParent = new LinearLayout(activity);
            textParent.setOrientation(LinearLayout.VERTICAL);
            textParent.setGravity(Gravity.CENTER_VERTICAL);
        }

        views.headline = CreateText(
                nativeAd.getHeadline()
              , HeadlineSp(plan)
              , true
              , NO_POLICY_TEXT_LIMIT_UNITS);
        ApplyTextMode(
                plan.marqueeHeadline
              , views.headline
              , HeadlineMaxLines(plan));
        textParent.addView(
                views.headline
              , new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.WRAP_CONTENT));
        AddBodyAndOptional(textParent, views, plan);

        if (!fillerCarriesIcon) {
            content.addView(textParent, fillerParams);
        }

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        if (views.icon == null) {
            if (plan.showIcon) {
                // The icon rides the button's row, so it takes the button's
                // height - a tier-sized icon would stretch the whole row and
                // spend height the media above it needs more.
                AddSizedIcon(
                        actionRow
                      , views
                      , Math.min(
                            IconSizeForTier(plan.tier)
                          , CallToActionHeightPx(plan))
                      , gap
                      , probe);
            }
        }
        AddCallToAction(actionRow, views, plan, gap, false);
        if (views.callToAction != null) {
            LinearLayout.LayoutParams callToActionParams =
                    (LinearLayout.LayoutParams)
                            views.callToAction.getLayoutParams();
            callToActionParams.width = 0;
            callToActionParams.weight = 1f;
            callToActionParams.setMarginStart(0);
            views.callToAction.setLayoutParams(callToActionParams);
        }
        // With an icon beside it, the icon already fixes this row's height; a
        // shorter button saves nothing back and only breaks the row's line.
        // The button takes the icon's height and its label follows its box.
        if (views.callToAction != null
                && views.icon != null
                && views.icon.getParent() == actionRow) {
            int rowHeight = Math.min(
                    IconSizeForTier(plan.tier)
                  , CallToActionHeightPx(plan));
            views.callToAction.setMinHeight(rowHeight);
            views.callToAction.setMinimumHeight(rowHeight);
            ((CallToActionButton) views.callToAction)
                    .ApplyLabelSize(rowHeight);
        }

        LinearLayout.LayoutParams actionRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowParams.topMargin = gap;
        content.addView(actionRow, actionRowParams);
        return content;
    }

    private LinearLayout BuildTextStack(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);

        views.headline = CreateText(
                nativeAd.getHeadline()
              , HeadlineSp(plan)
              , true
              , NO_POLICY_TEXT_LIMIT_UNITS);
        ApplyTextMode(
                plan.marqueeHeadline
              , views.headline
              , HeadlineMaxLines(plan));
        texts.addView(views.headline, WrapContentParams());
        AddBodyAndOptional(texts, views, plan);
        return texts;
    }

    private void AddBodyAndOptional(
            LinearLayout parent
          , AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        String bodyValue = nativeAd.getBody();
        if (plan.showBody && !TextUtils.isEmpty(bodyValue)) {
            views.body = CreateText(
                    bodyValue
                  , BodySp(plan)
                  , false
                  , NO_POLICY_TEXT_LIMIT_UNITS);
            ApplyTextMode(
                    plan.marqueeSecondary
                  , views.body
                  , BodyMaxLines(plan));
            views.body.setIncludeFontPadding(false);
            parent.addView(views.body, WrapContentParams());
        }

        if (plan.showAdvertiser) {
            views.advertiser = CreateText(
                    nativeAd.getAdvertiser()
                  , OptionalSp(plan)
                  , false
                  , NO_POLICY_TEXT_LIMIT_UNITS);
            views.advertiser.setTextColor(
                    Color.parseColor(SECONDARY_TEXT_COLOR));
            ApplyTextMode(
                    plan.marqueeSecondary
                  , views.advertiser
                  , AdvertiserMaxLines(plan));
            views.advertiser.setIncludeFontPadding(false);
            parent.addView(views.advertiser, WrapContentParams());
        }

        double starRating = ResolveStarRating();
        if (plan.showRating && starRating > 0d) {
            views.rating = CreateText(
                    "\u2605 " + String.valueOf(starRating)
                  , OptionalSp(plan)
                  , false
                  , NO_POLICY_TEXT_LIMIT_UNITS);
            views.rating.setTextColor(Color.parseColor(RATING_TEXT_COLOR));
            parent.addView(views.rating, WrapContentParams());
        }
    }

    private static LinearLayout.LayoutParams WrapContentParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT
              , ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // A media-free layout leaves a hole where the picture would have been, and
    // the icon is the one image asset such a creative still carries. Let it take
    // that space instead of sitting tiny next to the button; the slot is weighted
    // so it only ever absorbs room that was going to be empty anyway.
    private View CreateIconFiller(
            AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan.showMedia
                || !plan.showIcon
                || !HasRenderableIcon()) {
            return new View(activity);
        }

        FrameLayout holder = new IconFillerHolder(
                activity
              , Dp(MIN_FILLER_ICON_DP));
        views.icon = new ImageView(activity);
        views.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        views.icon.setAdjustViewBounds(false);
        views.icon.setImageDrawable(nativeAd.getIcon().getDrawable());
        holder.addView(
                views.icon
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.MATCH_PARENT));
        return holder;
    }

    // The filler absorbs leftover height, so what it asks for is only the
    // smallest icon worth showing; the weighted slot then stretches it over
    // whatever the fitted text left free. Without this an unconstrained
    // measure asks the ImageView instead, gets the icon bitmap's intrinsic
    // size - routinely taller than the whole slot - and every icon variant
    // is rejected as not fitting, which is exactly a slot full of blank
    // space with no icon in it.
    private static final class IconFillerHolder extends FrameLayout {
        private final int reservedHeightPx;

        IconFillerHolder(Context context, int reservedHeightPx) {
            super(context);
            this.reservedHeightPx = reservedHeightPx;
        }

        @Override
        protected void onMeasure(
                int widthMeasureSpec
              , int heightMeasureSpec) {
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (heightMode == MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }

            int height = reservedHeightPx;
            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(
                        height
                      , MeasureSpec.getSize(heightMeasureSpec));
            }
            super.onMeasure(
                    widthMeasureSpec
                  , MeasureSpec.makeMeasureSpec(
                        height
                      , MeasureSpec.EXACTLY));
        }
    }

    private void AddIcon(
            LinearLayout parent
          , AssetViews views
          , int tier
          , int gap
          , boolean probe) {
        AddSizedIcon(
                parent
              , views
              , IconSizeForTier(tier)
              , gap
              , probe);
    }

    private void AddSizedIcon(
            LinearLayout parent
          , AssetViews views
          , int size
          , int gap
          , boolean probe) {
        com.google.android.gms.ads.nativead.NativeAd.Image iconAsset =
                nativeAd.getIcon();
        if (!HasRenderableIcon()) return;

        views.icon = new ImageView(activity);
        views.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        views.icon.setImageDrawable(iconAsset.getDrawable());
        views.icon.setMinimumWidth(size);
        views.icon.setMinimumHeight(size);

        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(size, size);
        layoutParams.setMarginEnd(gap);
        parent.addView(views.icon, layoutParams);
    }

    private void AddCallToAction(
            LinearLayout parent
          , AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan
          , int gap
          , boolean fullWidth) {
        String callToActionValue = nativeAd.getCallToAction();
        if (TextUtils.isEmpty(callToActionValue)) return;

        // A translucent theme keeps the platform's Material button chrome
        // out of the call to action; it paints its own background below.
        views.callToAction = new CallToActionButton(
                new ContextThemeWrapper(
                        activity
                      , android.R.style.Theme_Translucent_NoTitleBar));
        views.callToAction.setText(
                InFeedAdLayoutValidator.TruncatePolicyText(
                        callToActionValue
                      , NO_POLICY_TEXT_LIMIT_UNITS));
        views.callToAction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        views.callToAction.setAllCaps(false);
        views.callToAction.setGravity(Gravity.CENTER);
        views.callToAction.setSingleLine(false);
        views.callToAction.setHorizontallyScrolling(false);
        views.callToAction.setEllipsize(TextUtils.TruncateAt.END);
        views.callToAction.setHyphenationFrequency(
                Layout.HYPHENATION_FREQUENCY_FULL);
        views.callToAction.setIncludeFontPadding(false);
        views.callToAction.setMinWidth(0);
        views.callToAction.setMinimumWidth(0);
        int callToActionHeight = CallToActionHeightPx(plan);
        views.callToAction.setMinHeight(callToActionHeight);
        views.callToAction.setMinimumHeight(callToActionHeight);
        int verticalPadding = CtaVerticalPadding(plan.tier);
        ((CallToActionButton) views.callToAction)
                .ApplyLabelSize(callToActionHeight);
        int horizontalPadding = CtaHorizontalPadding(plan.tier);
        views.callToAction.setPadding(
                horizontalPadding
              , verticalPadding
              , horizontalPadding
              , verticalPadding);
        views.callToAction.setTextColor(Color.parseColor(CTA_TEXT_COLOR));
        GradientDrawable callToActionBackground = new GradientDrawable();
        callToActionBackground.setShape(GradientDrawable.RECTANGLE);
        callToActionBackground.setColor(
                Color.parseColor(CTA_BACKGROUND_COLOR));
        callToActionBackground.setStroke(
                Dp(CTA_BORDER_WIDTH_DP)
              , Color.parseColor(CTA_BORDER_COLOR));
        // The full-screen ad gets this for free from the platform button
        // background; this one paints its own, so the pressed state has to come
        // back with it or the button looks dead under a finger.
        views.callToAction.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(
                                Color.parseColor(CTA_RIPPLE_COLOR))
                      , callToActionBackground
                      , null));

        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(
                        fullWidth
                                ? ViewGroup.LayoutParams.MATCH_PARENT
                                : ViewGroup.LayoutParams.WRAP_CONTENT
                      , ViewGroup.LayoutParams.WRAP_CONTENT);
        if (fullWidth) layoutParams.topMargin = gap;
        else layoutParams.setMarginStart(gap);
        parent.addView(views.callToAction, layoutParams);
    }

    private View CreateMediaView(
            InFeedAdLayoutEngine.LayoutPlan plan
          , AssetViews views
          , boolean probe
          , Drawable mainImage) {
        // The primary asset is always registered as a MediaView. Registering an
        // ImageView through setImageView() is what makes AdMob stop filling the
        // unit, not drawing the pixels with an ImageView.
        MediaView mediaView = new MediaView(activity);
        boolean backgroundTemplate = plan.template
                == InFeedAdLayoutEngine.TEMPLATE_MEDIA_BACKGROUND;
        // The picture is always shown whole; a band media letterboxes
        // against the panel colour instead of black, and only video keeps
        // the black stage its player paints anyway. The background template
        // fills what the fitted picture leaves with the ambient backdrop
        // below.
        mediaView.setBackgroundColor(
                plan.renderVideo ? Color.BLACK : Color.TRANSPARENT);
        mediaView.setImageScaleType(ImageView.ScaleType.FIT_CENTER);
        views.media = mediaView;
        views.mediaSlot = mediaView;
        if (probe) return mediaView;

        if (plan.renderVideo) {
            MediaContent mediaContent = nativeAd.getMediaContent();
            if (mediaContent == null || !mediaContent.hasVideoContent()) {
                throw new IllegalStateException(MEDIA_CONTENT_REQUIRED_MESSAGE);
            }
            mediaView.setMediaContent(mediaContent);
            return mediaView;
        }

        // Still fallback, drawn through a child of the MediaView exactly as
        // OverlayAdContentView does. Handing MediaContent to this view
        // instead would let a video creative auto-play in a slot that is below
        // the video size minimum.
        if (mainImage == null) {
            throw new IllegalStateException(
                    "Main image is required for an image fallback layout");
        }
        // Ambient fill for the background template only: the same picture,
        // cropped to cover and dimmed, stands behind the fitted one so the
        // cell's background is the creative's own colours expanded to the
        // edges - never dead fill, never a cropped-away creative.
        if (backgroundTemplate) {
            Drawable ambientDrawable = mainImage.getConstantState() != null
                    ? mainImage.getConstantState().newDrawable().mutate()
                    : mainImage;
            ImageView ambientBackdrop = new ImageView(activity);
            ambientBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ambientBackdrop.setImageDrawable(ambientDrawable);
            ambientBackdrop.setColorFilter(
                    AMBIENT_DIM_COLOR
                  , android.graphics.PorterDuff.Mode.SRC_ATOP);
            mediaView.addView(
                    ambientBackdrop
                  , new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.MATCH_PARENT));
        }

        ImageView fallbackImageView = new ImageView(activity);
        fallbackImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fallbackImageView.setAdjustViewBounds(false);
        fallbackImageView.setImageDrawable(mainImage);
        mediaView.addView(
                fallbackImageView
              , new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.MATCH_PARENT));
        return mediaView;
    }

    // Two modes, no middle ground. Wrapped: the value is laid out in full over
    // as many lines as it is allowed, and if it does not fit the candidate is
    // rejected rather than cut. Scrolling: one line that marquees, so the whole
    // value is still readable and the layout gets its height back. The timings
    // are the platform's own - 1200ms still, 30dp per second, 1200ms before it
    // repeats - the cadence every Android app that does this uses. The mode is
    // per text - the plan carries separate flags for the headline and for the
    // secondary lines - so the headline stays whole while a body scrolls.
    private void ApplyTextMode(
            boolean marquee
          , TextView text
          , int maxLines) {
        if (text == null) return;

        if (marquee) {
            text.setSingleLine(true);
            text.setMaxLines(1);
            text.setHorizontallyScrolling(true);
            text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            text.setMarqueeRepeatLimit(-1);
            text.setSelected(true);
            return;
        }

        // One allowed line is not a licence to scroll: a single-line text that
        // fits - or can be nudged to fit - is shown still and whole, and only
        // the ladder's scrolling rungs may move it.
        text.setSingleLine(maxLines <= 1);
        text.setMaxLines(Math.max(1, maxLines));
        text.setHorizontallyScrolling(false);
        text.setEllipsize(null);
        text.setSelected(false);
    }

    private int HeadlineMaxLines(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan.renderVideo) return 1;
        if (InFeedAdLayoutEngine.IsMediaSide(plan.template)) {
            return plan.tier == InFeedAdLayoutEngine.TIER_ROOMY
                    ? 3
                    : 2;
        }
        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_MEDIA_TOP) {
            return plan.tier == InFeedAdLayoutEngine.TIER_COMPACT
                    ? 2
                    : 3;
        }
        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_COMPACT_COLUMN) {
            return plan.tier == InFeedAdLayoutEngine.TIER_ROOMY
                    ? 3
                    : 2;
        }
        return 2;
    }

    private int BodyMaxLines(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan.renderVideo) return 0;
        if (InFeedAdLayoutEngine.IsMediaSide(plan.template)) {
            if (ShouldPrioritizeMediaSize(plan)) return 1;
            return plan.tier == InFeedAdLayoutEngine.TIER_ROOMY
                    ? 3
                    : 2;
        }
        if (plan.template
                == InFeedAdLayoutEngine.TEMPLATE_MEDIA_TOP
                || plan.template
                        == InFeedAdLayoutEngine.TEMPLATE_COMPACT_COLUMN) {
            return plan.tier == InFeedAdLayoutEngine.TIER_COMPACT
                    ? 2
                    : 3;
        }
        return plan.tier == InFeedAdLayoutEngine.TIER_COMPACT
                ? 1
                : 2;
    }

    private int AdvertiserMaxLines(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan.renderVideo) return 0;
        if (InFeedAdLayoutEngine.IsMediaSide(plan.template)) {
            if (ShouldPrioritizeMediaSize(plan)) return 1;
            return plan.tier == InFeedAdLayoutEngine.TIER_ROOMY
                    ? 2
                    : 1;
        }
        return plan.tier == InFeedAdLayoutEngine.TIER_ROOMY
                ? 2
                : 1;
    }

    private boolean ShouldPrioritizeMediaSize(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan.renderVideo) return true;
        if (!InFeedAdLayoutEngine.IsMediaSide(plan.template)) {
            return false;
        }

        int compactThreshold = Dp(72);
        int constrainedHeight = plan.mediaHeight + Dp(12);
        return plan.mediaWidth <= compactThreshold
                || plan.height <= constrainedHeight
                || plan.tier == InFeedAdLayoutEngine.TIER_COMPACT;
    }

    private TextView CreateText(
            String value
          , float sizeSp
          , boolean bold
          , int policyTextLimitUnits) {
        TextView text = new TextView(activity);
        text.setText(
                InFeedAdLayoutValidator.TruncatePolicyText(
                        value
                      , policyTextLimitUnits));
        text.setTextColor(Color.WHITE);
        text.setTextSize(sizeSp);
        text.setSingleLine(false);
        text.setHorizontallyScrolling(false);
        text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }


    private int IconSizeForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return Dp(24);
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return Dp(36);
        return Dp(48);
    }

    private int VideoRailIconSizeForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) {
            return Dp(VIDEO_RAIL_ICON_COMPACT_DP);
        }
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) {
            return Dp(VIDEO_RAIL_ICON_REGULAR_DP);
        }
        return Dp(VIDEO_RAIL_ICON_ROOMY_DP);
    }

    private int VideoRailCallToActionHeightForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) {
            return Dp(VIDEO_RAIL_CTA_HEIGHT_COMPACT_DP);
        }
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) {
            return Dp(VIDEO_RAIL_CTA_HEIGHT_REGULAR_DP);
        }
        return Dp(VIDEO_RAIL_CTA_HEIGHT_ROOMY_DP);
    }

    // Every text size passes through the plan's scale, so a candidate that has
    // to shrink to fit shrinks as a whole and keeps its proportions.
    private float Scaled(
            InFeedAdLayoutEngine.LayoutPlan plan
          , float sizeSp) {
        return sizeSp * Math.max(0.1f, plan.textScale);
    }

    private float HeadlineSp(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        return Scaled(plan, HeadlineSpForTier(plan.tier));
    }

    private float BodySp(InFeedAdLayoutEngine.LayoutPlan plan) {
        return Scaled(plan, BodySpForTier(plan.tier));
    }

    // Rating and advertiser are one short line each - they are never what
    // makes a candidate too tall - so they do not ride the scale ladder all
    // the way down with the texts that are. Below this floor they stop
    // reading as information and just dirty the layout.
    private float OptionalSp(InFeedAdLayoutEngine.LayoutPlan plan) {
        return Math.max(
                OPTIONAL_TEXT_MIN_SP
              , Scaled(plan, OptionalSpForTier(plan.tier)));
    }

    private float HeadlineSpForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return 12f;
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return 16f;
        return 17f;
    }

    private float BodySpForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return 12f;
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return 13f;
        return 14f;
    }

    private float OptionalSpForTier(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return 11f;
        return 12f;
    }

    int CallToActionHeightPx(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan.template
                    == InFeedAdLayoutEngine.TEMPLATE_MEDIA_LEFT
                && plan.renderVideo) {
            return VideoRailCallToActionHeightForTier(plan.tier);
        }
        if (plan.template
                    == InFeedAdLayoutEngine.TEMPLATE_MEDIA_TOP
                && plan.renderVideo) {
            return Dp(VIDEO_FOOTER_CTA_HEIGHT_DP);
        }
        // Sized from the slot rather than from the height this particular plan
        // settled on, so the button - and the label derived from it - comes out
        // the same whichever layout wins.
        return Clamp(
                Math.round(slotShortSidePx * CTA_SHORT_SIDE_RATIO)
              , Dp(CTA_MIN_HEIGHT_DP)
              , Dp(CTA_MAX_HEIGHT_DP));
    }

    private int CtaVerticalPadding(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return Dp(1);
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return Dp(2);
        return Dp(4);
    }

    private int CtaHorizontalPadding(int tier) {
        if (tier == InFeedAdLayoutEngine.TIER_COMPACT) return Dp(6);
        if (tier == InFeedAdLayoutEngine.TIER_REGULAR) return Dp(8);
        return Dp(10);
    }

    private static int Clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
