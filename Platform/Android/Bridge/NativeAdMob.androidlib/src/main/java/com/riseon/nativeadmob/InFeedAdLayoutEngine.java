package com.riseon.nativeadmob;

import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.ads.MediaContent;

import java.util.ArrayList;

final class InFeedAdLayoutEngine {
    static final int TEMPLATE_COMPACT_ROW = 0;
    static final int TEMPLATE_COMPACT_COLUMN = 1;
    static final int TEMPLATE_MEDIA_LEFT = 2;
    static final int TEMPLATE_MEDIA_TOP = 3;
    // The media is the whole slot, everything else lives in an opaque scrim
    // over its bottom edge. This is the template that serves squarish slots
    // where neither a media band nor a media column fits beside the text.
    static final int TEMPLATE_MEDIA_BACKGROUND = 4;
    // Mirror of MEDIA_LEFT, and the asymmetry pays on the badge side:
    // AdChoices - the wider badge - lands on the media, so only the text
    // column reserves the strip, for the attribution corner alone.
    static final int TEMPLATE_MEDIA_RIGHT = 5;
    private static final int TEMPLATE_COUNT = TEMPLATE_MEDIA_RIGHT + 1;

    static final int TIER_COMPACT = 0;
    static final int TIER_REGULAR = 1;
    static final int TIER_ROOMY = 2;

    private static final String TAG = "InFeedAd";
    private static final float MIN_NATIVE_AD_DP = 32f;
    private static final int WIDTH_REFINEMENT_PASSES = 4;
    private static final int WIDTH_REFINEMENT_SAMPLES = 6;
    private static final int MEDIA_EXPANSION_ITERATIONS = 14;
    private static final int OPTIONAL_BODY = 1;
    private static final int OPTIONAL_ADVERTISER = 1 << 1;
    private static final int OPTIONAL_RATING = 1 << 2;
    // The icon is optional in exactly the way body, advertiser and rating
    // are. It is the widest of them, so in a narrow slot dropping it is
    // often what leaves the headline enough room to satisfy its minimum.
    private static final int OPTIONAL_ICON = 1 << 3;
    private static final int OPTIONAL_VARIANT_COUNT = 1 << 4;
    private static final int MIN_VISIBLE_TEXT_SLOT_PX = 1;
    private static final int MAX_EXPANDED_MEDIA_PLANS = 8;
    private static final float UNKNOWN_VIDEO_ASPECT_RATIO = 1f;
    private static final float DEFAULT_IMAGE_ASPECT_RATIO = 1.91f;
    private static final float MIN_MEDIA_ASPECT_RATIO = 0.2f;
    private static final float MAX_MEDIA_ASPECT_RATIO = 5f;
    // Within one layout, whole always beats moving when nothing else is
    // traded away: a text a hair too long for its lines is shrunk alone, in
    // small steps down to a floor, before any rung is allowed to scroll it.
    private static final float NUDGE_TEXT_STEP = 0.92f;
    private static final float NUDGE_TEXT_FLOOR = 0.7f;
    private static final int NUDGE_TEXT_MAX_STEPS = 5;
    private static final int TEXT_MODE_WRAPPED = 0;
    private static final int TEXT_MODE_SECONDARY_MARQUEE = 1;
    private static final int TEXT_MODE_ALL_MARQUEE = 2;
    // Text is worth more still than moving, and worth more big than small -
    // in that order only while it stays readable. Whole text gets the first
    // rungs down to 0.7x; past that, one secondary line scrolling at a larger
    // size beats dragging the entire group into illegibility just so a long
    // body can sit whole, so the scrolling rungs interleave from there. A
    // scrolling rung still always sits below a size that wrapping has already
    // failed at, and the headline joins the scroll only after every stiller
    // rung is exhausted. Rungs whose mode has no text to act on are skipped.
    private static final int[] TEXT_LADDER_MODES = {
            TEXT_MODE_WRAPPED
          , TEXT_MODE_WRAPPED
          , TEXT_MODE_WRAPPED
          , TEXT_MODE_SECONDARY_MARQUEE
          , TEXT_MODE_SECONDARY_MARQUEE
          , TEXT_MODE_WRAPPED
          , TEXT_MODE_SECONDARY_MARQUEE
          , TEXT_MODE_ALL_MARQUEE
          , TEXT_MODE_ALL_MARQUEE
          , TEXT_MODE_ALL_MARQUEE
    };
    private static final float[] TEXT_LADDER_SCALES = {
            1f, 0.85f, 0.7f, 0.8f, 0.65f, 0.55f, 0.5f, 0.8f, 0.65f, 0.5f
    };

    static final class LayoutPlan {
        int template;
        int tier;
        boolean showMedia;
        boolean renderVideo;
        boolean showBody;
        boolean showAdvertiser;
        boolean showRating;
        boolean showIcon;
        int width;
        int height;
        int mediaWidth;
        int mediaHeight;
        int measuredContentHeight;
        float textScale = 1f;
        // Marquee is decided per text, not per plan: the headline only ever
        // scrolls after scrolling just the secondary lines was not enough,
        // so a layout never moves more text than the space actually demands.
        boolean marqueeHeadline;
        boolean marqueeSecondary;
        long score;
    }

    // Text scale and marquee mode are not part of the key: ConfigureProbeLayout
    // re-applies them to a cached tree, so one tree serves the whole scale
    // ladder instead of a fresh build per attempt.
    private static final class CachedProbeLayout {
        final InFeedAdViewFactory.ProbeLayout probe;
        final boolean showBody;
        final boolean showAdvertiser;
        final boolean showRating;
        final boolean showIcon;

        CachedProbeLayout(
                InFeedAdViewFactory.ProbeLayout probe
              , LayoutPlan plan) {
            this.probe = probe;
            showBody = plan.showBody;
            showAdvertiser = plan.showAdvertiser;
            showRating = plan.showRating;
            showIcon = plan.showIcon;
        }
    }

    private final com.google.android.gms.ads.nativead.NativeAd nativeAd;
    private int requestedX;
    private int requestedY;
    private final int requestedWidth;
    private final int requestedHeight;
    private final InFeedAdViewFactory viewFactory;
    private final InFeedAdLayoutValidator validator;
    private final ArrayList<CachedProbeLayout> probeLayouts =
            new ArrayList<>();
    private final String[] rejectionReasons = new String[TEMPLATE_COUNT];

    InFeedAdLayoutEngine(
            com.google.android.gms.ads.nativead.NativeAd nativeAd
          , int requestedX
          , int requestedY
          , int requestedWidth
          , int requestedHeight
          , InFeedAdViewFactory viewFactory
          , InFeedAdLayoutValidator validator) {
        this.nativeAd = nativeAd;
        this.requestedX = requestedX;
        this.requestedY = requestedY;
        this.requestedWidth = requestedWidth;
        this.requestedHeight = requestedHeight;
        this.viewFactory = viewFactory;
        this.validator = validator;
    }

    void SetPosition(int xPx, int yPx) {
        requestedX = xPx;
        requestedY = yPx;
    }

    LayoutPlan ChoosePlan(
            int screenWidth
          , int screenHeight
          , boolean hasVideo
          , boolean hasMainImage) {
        ArrayList<LayoutPlan> plans = ChoosePlans(
                screenWidth
              , screenHeight
              , hasVideo
              , hasMainImage);
        return plans.isEmpty() ? null : plans.get(0);
    }

    ArrayList<LayoutPlan> ChoosePlans(
            int screenWidth
          , int screenHeight
          , boolean hasVideo
          , boolean hasMainImage) {
        ResetRejectionReasons();
        ArrayList<LayoutPlan> plans = new ArrayList<>();
        int minAdPx = viewFactory.Dp(MIN_NATIVE_AD_DP);
        if (requestedWidth < minAdPx || requestedHeight < minAdPx) {
            Log.e(
                    TAG
                  , "In-feed requested rect is below the "
                            + MIN_NATIVE_AD_DP + "dp minimum");
            return plans;
        }
        int desiredWidth = Math.min(screenWidth, requestedWidth);
        int desiredHeight = Math.min(screenHeight, requestedHeight);
        if (desiredWidth < minAdPx || desiredHeight < minAdPx) {
            Log.e(
                    TAG
                  , "In-feed content surface clamps requested rect below the "
                            + MIN_NATIVE_AD_DP + "dp minimum");
            return plans;
        }

        ArrayList<Integer> widths = new ArrayList<>();
        int widthStep = 1;
        AddWidth(widths, desiredWidth, screenWidth);

        ArrayList<LayoutPlan> generalVariants = new ArrayList<>();
        ArrayList<LayoutPlan> videoVariants = new ArrayList<>();
        // Media-free candidates a video creative drops down to when the rect
        // cannot host a policy-safe video.
        ArrayList<LayoutPlan> fallbackVariants = new ArrayList<>();
        boolean hasBody = !TextUtils.isEmpty(nativeAd.getBody());
        boolean hasAdvertiser =
                !TextUtils.isEmpty(nativeAd.getAdvertiser());
        boolean hasRating = viewFactory.HasValidStarRating();
        boolean hasIcon = viewFactory.HasRenderableIcon();
        for (int tier = TIER_COMPACT; tier <= TIER_ROOMY; ++tier) {
            for (int width : widths) {
                for (int optionalMask = 0;
                     optionalMask < OPTIONAL_VARIANT_COUNT;
                     ++optionalMask) {
                    boolean showBody =
                            (optionalMask & OPTIONAL_BODY) != 0;
                    boolean showAdvertiser =
                            (optionalMask & OPTIONAL_ADVERTISER) != 0;
                    boolean showRating =
                            (optionalMask & OPTIONAL_RATING) != 0;
                    boolean showIcon =
                            (optionalMask & OPTIONAL_ICON) != 0;
                    if ((showBody && !hasBody)
                            || (showAdvertiser && !hasAdvertiser)
                            || (showRating && !hasRating)
                            || (showIcon && !hasIcon)) {
                        continue;
                    }

                    if (hasVideo) {
                        UpdateVariantBest(
                                videoVariants
                              , EvaluatePlan(
                                    TEMPLATE_MEDIA_LEFT
                                  , tier
                                  , true
                                  , true
                                  , showBody
                                  , showAdvertiser
                                  , showRating
                                  , showIcon
                                  , width
                                  , desiredWidth
                                  , desiredHeight
                                  , screenWidth
                                  , screenHeight));
                        // Video never serves as a background: a veil over a
                        // playing video is a viewability problem, not a
                        // layout. The background template is images only.
                        // No still-image fallback for a video creative. The
                        // validator flags any MediaView registered below the
                        // video minimum once the bound ad carries video, no
                        // matter what is drawn inside it, so the only safe way
                        // down from a video plan that does not fit is a layout
                        // with no media at all.
                        UpdateVariantBest(
                                fallbackVariants
                              , EvaluatePlan(
                                    TEMPLATE_COMPACT_ROW
                                  , tier
                                  , false
                                  , false
                                  , showBody
                                  , showAdvertiser
                                  , showRating
                                  , showIcon
                                  , width
                                  , desiredWidth
                                  , desiredHeight
                                  , screenWidth
                                  , screenHeight));
                        UpdateVariantBest(
                                fallbackVariants
                              , EvaluatePlan(
                                    TEMPLATE_COMPACT_COLUMN
                                  , tier
                                  , false
                                  , false
                                  , showBody
                                  , showAdvertiser
                                  , showRating
                                  , showIcon
                                  , width
                                  , desiredWidth
                                  , desiredHeight
                                  , screenWidth
                                  , screenHeight));
                        continue;
                    }

                    UpdateVariantBest(
                            generalVariants
                          , EvaluatePlan(
                                TEMPLATE_COMPACT_ROW
                              , tier
                              , false
                              , false
                              , showBody
                              , showAdvertiser
                              , showRating
                              , showIcon
                              , width
                              , desiredWidth
                              , desiredHeight
                              , screenWidth
                              , screenHeight));
                    UpdateVariantBest(
                            generalVariants
                          , EvaluatePlan(
                                TEMPLATE_COMPACT_COLUMN
                              , tier
                              , false
                              , false
                              , showBody
                              , showAdvertiser
                              , showRating
                              , showIcon
                              , width
                              , desiredWidth
                              , desiredHeight
                              , screenWidth
                              , screenHeight));

                    if (hasMainImage) {
                        LayoutPlan imageLeft = EvaluatePlan(
                                TEMPLATE_MEDIA_LEFT
                              , tier
                              , true
                              , false
                              , showBody
                              , showAdvertiser
                              , showRating
                              , showIcon
                              , width
                              , desiredWidth
                              , desiredHeight
                              , screenWidth
                              , screenHeight);
                        LayoutPlan imageTop = EvaluatePlan(
                                TEMPLATE_MEDIA_TOP
                              , tier
                              , true
                              , false
                              , showBody
                              , showAdvertiser
                              , showRating
                              , showIcon
                              , width
                              , desiredWidth
                              , desiredHeight
                              , screenWidth
                              , screenHeight);
                        UpdateVariantBest(generalVariants, imageLeft);
                        UpdateVariantBest(generalVariants, imageTop);
                        UpdateVariantBest(
                                generalVariants
                              , EvaluatePlan(
                                    TEMPLATE_MEDIA_BACKGROUND
                                  , tier
                                  , true
                                  , false
                                  , showBody
                                  , showAdvertiser
                                  , showRating
                                  , showIcon
                                  , width
                                  , desiredWidth
                                  , desiredHeight
                                  , screenWidth
                                  , screenHeight));
                        UpdateVariantBest(
                                generalVariants
                              , EvaluatePlan(
                                    TEMPLATE_MEDIA_RIGHT
                                  , tier
                                  , true
                                  , false
                                  , showBody
                                  , showAdvertiser
                                  , showRating
                                  , showIcon
                                  , width
                                  , desiredWidth
                                  , desiredHeight
                                  , screenWidth
                                  , screenHeight));
                    }
                }
            }
        }

        ArrayList<LayoutPlan> primaryVariants =
                hasVideo ? videoVariants : generalVariants;
        AddRefinedPlans(
                plans
              , primaryVariants
              , widthStep
              , desiredWidth
              , desiredHeight
              , screenWidth
              , screenHeight);
        FinalizePlanList(plans);

        // Video is always attempted first. If no policy-safe video plan fits,
        // or if a bound video plan later fails final geometry validation, the
        // remaining candidates fall back to the creative's still image, then to
        // a media-free layout.
        if (hasVideo) {
            ArrayList<LayoutPlan> imageFallbackPlans = new ArrayList<>();
            AddRefinedPlans(
                    imageFallbackPlans
                  , fallbackVariants
                  , widthStep
                  , desiredWidth
                  , desiredHeight
                  , screenWidth
                  , screenHeight);
            FinalizePlanList(imageFallbackPlans);
            plans.addAll(imageFallbackPlans);
        }

        Clear();
        return plans;
    }


    private void AddRefinedPlans(
            ArrayList<LayoutPlan> destination
          , ArrayList<LayoutPlan> variants
          , int widthStep
          , int desiredWidth
          , int desiredHeight
          , int screenWidth
          , int screenHeight) {
        for (LayoutPlan variant : variants) {
            LayoutPlan refined = RefinePlanWidth(
                    variant
                  , widthStep
                  , desiredWidth
                  , desiredHeight
                  , screenWidth
                  , screenHeight);
            if (refined != null) destination.add(refined);
        }
    }

    private void FinalizePlanList(ArrayList<LayoutPlan> plans) {
        SortPlansByScore(plans);
        PruneDominatedPlans(plans);
        int expandedMediaPlans = 0;
        for (int index = 0;
             index < plans.size()
                     && expandedMediaPlans < MAX_EXPANDED_MEDIA_PLANS;
             ++index) {
            LayoutPlan plan = plans.get(index);
            if (!plan.showMedia) continue;

            LayoutPlan expanded = ExpandMediaWithinPlan(plan);
            if (expanded != null) plans.set(index, expanded);
            ++expandedMediaPlans;
        }
    }

    void Clear() {
        probeLayouts.clear();
    }

    String DescribeLastRejections() {
        StringBuilder result = new StringBuilder();
        for (int template = 0; template < rejectionReasons.length; ++template) {
            String reason = rejectionReasons[template];
            if (reason == null) continue;
            if (result.length() > 0) result.append("; ");
            result.append(TemplateName(template))
                    .append('=')
                    .append(reason);
        }
        return result.toString();
    }

    private LayoutPlan EvaluatePlan(
            int template
          , int tier
          , boolean showMedia
          , boolean renderVideo
          , boolean showBody
          , boolean showAdvertiser
          , boolean showRating
          , boolean showIcon
          , int candidateWidth
          , int desiredWidth
          , int desiredHeight
          , int screenWidth
          , int screenHeight) {
        int minWidth = viewFactory.MinimumWidth(tier);
        boolean compactWithoutMedia = !renderVideo
                && !showMedia
                && (template == TEMPLATE_COMPACT_ROW
                        || template == TEMPLATE_COMPACT_COLUMN);
        if (!compactWithoutMedia && candidateWidth < minWidth) {
            RecordRejection(
                    template
                  , tier
                  , "width " + candidateWidth + "px < " + minWidth + "px");
            return null;
        }
        int width = candidateWidth;
        if (width <= 0 || width > screenWidth) {
            RecordRejection(
                    template
                  , tier
                  , "width " + width + "px is outside screen width "
                            + screenWidth + "px");
            return null;
        }

        LayoutPlan plan = new LayoutPlan();
        plan.template = template;
        plan.tier = tier;
        plan.showMedia = showMedia;
        plan.renderVideo = renderVideo;
        plan.showBody = showBody
                && !TextUtils.isEmpty(nativeAd.getBody());
        plan.showAdvertiser = showAdvertiser
                && !TextUtils.isEmpty(nativeAd.getAdvertiser());
        plan.showIcon = showIcon;
        plan.showRating = showRating
                && viewFactory.HasValidStarRating();
        if (renderVideo) {
            plan.showBody = false;
            plan.showAdvertiser = false;
            plan.showRating = false;
            plan.showIcon = false;
        }
        plan.width = width;

        plan.height = desiredHeight;
        if (!ResolveMediaDimensions(plan)) return null;
        // Shrinking to fit mutates the media dimensions on the plan. Each
        // rung of the ladder has to start from the resolved size, or a shrink
        // that a later validation rejects poisons every attempt after it.
        int baseMediaWidth = plan.mediaWidth;
        int baseMediaHeight = plan.mediaHeight;

        int naturalContentHeight = 0;
        String lastFailure = null;
        boolean fitted = false;
        // Text is shown whole or it scrolls; it is never left cut off. The
        // ladder decides which, per rung: see TEXT_LADDER_MODES for the order
        // in which stillness and size are traded away.
        boolean hasSecondaryText = plan.showBody || plan.showAdvertiser;
        for (int rung = 0; rung < TEXT_LADDER_MODES.length; ++rung) {
            int textMode = TEXT_LADDER_MODES[rung];
            if (textMode == TEXT_MODE_SECONDARY_MARQUEE
                    && !hasSecondaryText) {
                continue;
            }
            plan.marqueeHeadline = textMode == TEXT_MODE_ALL_MARQUEE;
            plan.marqueeSecondary = textMode != TEXT_MODE_WRAPPED;
            plan.textScale = TEXT_LADDER_SCALES[rung];
            plan.mediaWidth = baseMediaWidth;
            plan.mediaHeight = baseMediaHeight;
            InFeedAdViewFactory.ProbeLayout probeLayout =
                    GetProbeLayout(plan);
            viewFactory.ConfigureProbeLayout(probeLayout, plan);
            View finalProbe = probeLayout.root;
            naturalContentHeight = MeasureNaturalContentHeight(
                    finalProbe
                  , width);
            if (naturalContentHeight > desiredHeight) {
                naturalContentHeight = ShrinkImageTopToFitHeight(
                        plan
                      , probeLayout
                      , width
                      , desiredHeight
                      , naturalContentHeight);
            }
            if (naturalContentHeight > desiredHeight) {
                lastFailure = "natural height " + naturalContentHeight
                        + "px > " + desiredHeight + "px at text x"
                        + plan.textScale;
                continue;
            }

            finalProbe.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            width
                          , View.MeasureSpec.EXACTLY)
                  , View.MeasureSpec.makeMeasureSpec(
                            desiredHeight
                          , View.MeasureSpec.EXACTLY));
            finalProbe.layout(0, 0, width, desiredHeight);
            NudgeCutTextsWhole(
                    finalProbe
                  , probeLayout.views
                  , plan
                  , width
                  , desiredHeight);
            if (!validator.ValidateAssetGeometry(
                    finalProbe
                  , probeLayout.views
                  , plan
                  , false)) {
                String validationReason = validator.GetLastFailureReason();
                lastFailure = (validationReason == null
                        ? "asset geometry rejected"
                        : validationReason) + " at text x" + plan.textScale;
                continue;
            }

            fitted = true;
            break;
        }
        if (!fitted) {
            RecordRejection(template, tier, lastFailure);
            return null;
        }

        plan.measuredContentHeight = naturalContentHeight;

        long movement = Math.abs(
                (long) Clamp(
                        requestedX
                      , 0
                      , Math.max(0, screenWidth - width))
                        - (long) requestedX)
                + Math.abs(
                (long) Clamp(
                        requestedY
                      , 0
                      , Math.max(0, screenHeight - desiredHeight))
                        - (long) requestedY);
        long whitespace = Math.max(0, desiredHeight - naturalContentHeight);
        int richness = 0;
        if (showMedia) richness += 8;
        if (renderVideo) richness += 3;
        if (plan.showBody) richness += 1;
        if (plan.showAdvertiser) richness += 1;
        if (plan.showRating) richness += 1;
        if (plan.showIcon) richness += 1;
        richness += tier;

        int preferredTier = viewFactory.PreferredTier(
                desiredWidth
              , desiredHeight);
        long tierPenalty = Math.abs(tier - preferredTier);
        // Weighted between a tier step and an asset, counted per scrolling
        // text: a layout that scrolls loses to one that shows the same text
        // whole, and one that scrolls everything loses to one that keeps its
        // headline still, but neither ever loses to a layout carrying fewer
        // assets. Scrolling is a price paid for an element, not a preference
        // of its own.
        long marqueePenalty = 0;
        if (plan.marqueeHeadline) ++marqueePenalty;
        if (plan.marqueeSecondary && hasSecondaryText) ++marqueePenalty;
        long mediaAreaPercent = 0;
        if (plan.showMedia && width > 0 && desiredHeight > 0) {
            mediaAreaPercent = Math.min(
                    100L
                  , 100L * plan.mediaWidth * plan.mediaHeight
                            / ((long) width * desiredHeight));
        }
        // Media sits on the left by preference - most people are
        // right-handed, so the text and the button belong under the thumb.
        // The mirror stays available for when only it fits, but it never wins
        // a tie.
        long sidePenalty =
                plan.template == TEMPLATE_MEDIA_RIGHT ? 1 : 0;
        // The background template is how media survives a cell that no band
        // layout can host, not a first choice: its full-cell media area must
        // never outscore a dedicated band showing the same assets, so the
        // penalty outweighs the whole media-area reward.
        long backgroundPenalty =
                plan.template == TEMPLATE_MEDIA_BACKGROUND ? 6_000L : 0L;
        plan.score = movement * 1_000_000L
                + whitespace * 100L
                + tierPenalty * 1_000L
                + marqueePenalty * 3_000L
                + sidePenalty * 500L
                + backgroundPenalty
                - mediaAreaPercent * 30L
                - richness * 10_000L;
        return plan;
    }

    private int ShrinkImageTopToFitHeight(
            LayoutPlan plan
          , InFeedAdViewFactory.ProbeLayout probeLayout
          , int width
          , int desiredHeight
          , int originalNaturalHeight) {
        if (plan.template != TEMPLATE_MEDIA_TOP
                || !plan.showMedia
                || plan.renderVideo
                || plan.mediaWidth <= 0
                || plan.mediaHeight <= 0) {
            return originalNaturalHeight;
        }

        int originalMediaWidth = plan.mediaWidth;
        int originalMediaHeight = plan.mediaHeight;
        // The validator holds any MediaView to the media policy floor, so
        // shrinking below it manufactures plans that can never validate.
        int minimumMediaSide = viewFactory.MediaPolicyFloorPx();
        double minimumScale = Math.max(
                minimumMediaSide / (double) originalMediaWidth
              , minimumMediaSide / (double) originalMediaHeight);
        if (minimumScale >= 1.0) return originalNaturalHeight;

        plan.mediaWidth = Math.max(
                minimumMediaSide
              , (int) Math.ceil(originalMediaWidth * minimumScale));
        plan.mediaHeight = Math.max(
                minimumMediaSide
              , (int) Math.ceil(originalMediaHeight * minimumScale));
        viewFactory.ConfigureProbeLayout(probeLayout, plan);
        int minimumNaturalHeight = MeasureNaturalContentHeight(
                probeLayout.root
              , width);
        if (minimumNaturalHeight > desiredHeight) {
            plan.mediaWidth = originalMediaWidth;
            plan.mediaHeight = originalMediaHeight;
            viewFactory.ConfigureProbeLayout(probeLayout, plan);
            return originalNaturalHeight;
        }

        int bestMediaWidth = plan.mediaWidth;
        int bestMediaHeight = plan.mediaHeight;
        int bestNaturalHeight = minimumNaturalHeight;
        double low = minimumScale;
        double high = 1.0;
        for (int iteration = 0;
             iteration < MEDIA_EXPANSION_ITERATIONS;
             ++iteration) {
            double scale = (low + high) / 2.0;
            plan.mediaWidth = Math.max(
                    minimumMediaSide
                  , (int) Math.floor(originalMediaWidth * scale));
            plan.mediaHeight = Math.max(
                    minimumMediaSide
                  , (int) Math.floor(originalMediaHeight * scale));
            viewFactory.ConfigureProbeLayout(probeLayout, plan);
            int naturalHeight = MeasureNaturalContentHeight(
                    probeLayout.root
                  , width);
            if (naturalHeight <= desiredHeight) {
                bestMediaWidth = plan.mediaWidth;
                bestMediaHeight = plan.mediaHeight;
                bestNaturalHeight = naturalHeight;
                low = scale;
            } else {
                high = scale;
            }
        }

        plan.mediaWidth = bestMediaWidth;
        plan.mediaHeight = bestMediaHeight;
        viewFactory.ConfigureProbeLayout(probeLayout, plan);
        return bestNaturalHeight;
    }

    private int MeasureNaturalContentHeight(View view, int width) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(
                        width
                      , View.MeasureSpec.EXACTLY)
              , View.MeasureSpec.makeMeasureSpec(
                        0
                      , View.MeasureSpec.UNSPECIFIED));
        return Math.max(
                viewFactory.Dp(MIN_NATIVE_AD_DP)
              , view.getMeasuredHeight());
    }

    private LayoutPlan PickBetter(LayoutPlan current, LayoutPlan candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.score < current.score) return candidate;
        return current;
    }

    private static void SortPlansByScore(ArrayList<LayoutPlan> plans) {
        for (int current = 1; current < plans.size(); ++current) {
            LayoutPlan plan = plans.get(current);
            int insertion = current;
            while (insertion > 0
                    && plan.score < plans.get(insertion - 1).score) {
                plans.set(insertion, plans.get(insertion - 1));
                --insertion;
            }
            plans.set(insertion, plan);
        }
    }

    private static void PruneDominatedPlans(
            ArrayList<LayoutPlan> plans) {
        ArrayList<LayoutPlan> selected = new ArrayList<>();
        for (LayoutPlan plan : plans) {
            boolean hasFamily = false;
            boolean hasRequiredOnlyFallback = false;
            for (LayoutPlan kept : selected) {
                if (!IsSameLayoutFamily(kept, plan)) continue;
                hasFamily = true;
                if (IsRequiredOnly(kept)) {
                    hasRequiredOnlyFallback = true;
                }
            }

            if (!hasFamily
                    || IsRequiredOnly(plan)
                            && !hasRequiredOnlyFallback) {
                selected.add(plan);
            }
        }
        plans.clear();
        plans.addAll(selected);
    }

    private static boolean IsSameLayoutFamily(
            LayoutPlan first
          , LayoutPlan second) {
        return first.template == second.template
                && first.tier == second.tier
                && first.showMedia == second.showMedia
                && first.renderVideo == second.renderVideo;
    }

    private static boolean IsRequiredOnly(LayoutPlan plan) {
        return !plan.showBody
                && !plan.showAdvertiser
                && !plan.showRating;
    }

    private void NudgeCutTextsWhole(
            View probeRoot
          , InFeedAdViewFactory.AssetViews views
          , LayoutPlan plan
          , int width
          , int height) {
        NudgeTextWhole(
                probeRoot
              , plan.marqueeHeadline ? null : views.headline
              , width
              , height);
        NudgeTextWhole(
                probeRoot
              , plan.marqueeSecondary ? null : views.body
              , width
              , height);
        NudgeTextWhole(
                probeRoot
              , plan.marqueeSecondary ? null : views.advertiser
              , width
              , height);
    }

    private static void NudgeTextWhole(
            View probeRoot
          , TextView text
          , int width
          , int height) {
        if (text == null || text.getVisibility() != View.VISIBLE) return;

        float floor = text.getTextSize() * NUDGE_TEXT_FLOOR;
        for (int step = 0; step < NUDGE_TEXT_MAX_STEPS; ++step) {
            if (!IsTextCut(text)) return;

            float next = text.getTextSize() * NUDGE_TEXT_STEP;
            if (next < floor) return;

            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, next);
            probeRoot.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            width
                          , View.MeasureSpec.EXACTLY)
                  , View.MeasureSpec.makeMeasureSpec(
                            height
                          , View.MeasureSpec.EXACTLY));
            probeRoot.layout(0, 0, width, height);
        }
    }

    private static boolean IsTextCut(TextView text) {
        Layout layout = text.getLayout();
        if (layout == null || layout.getLineCount() == 0) return false;
        for (int line = 0; line < layout.getLineCount(); ++line) {
            if (layout.getEllipsisCount(line) > 0) return true;
        }
        return layout.getLineEnd(layout.getLineCount() - 1)
                < text.getText().length();
    }

    private InFeedAdViewFactory.ProbeLayout GetProbeLayout(
            LayoutPlan plan) {
        for (CachedProbeLayout cached : probeLayouts) {
            InFeedAdViewFactory.ProbeLayout probe = cached.probe;
            if (probe.template == plan.template
                    && probe.tier == plan.tier
                    && probe.showMedia == plan.showMedia
                    && probe.renderVideo == plan.renderVideo
                    && cached.showBody == plan.showBody
                    && cached.showAdvertiser == plan.showAdvertiser
                    && cached.showRating == plan.showRating
                    && cached.showIcon == plan.showIcon) {
                return probe;
            }
        }

        InFeedAdViewFactory.ProbeLayout probe =
                viewFactory.CreateProbe(plan);
        probeLayouts.add(new CachedProbeLayout(probe, plan));
        return probe;
    }

    private LayoutPlan RefinePlanWidth(
            LayoutPlan selected
          , int coarseStep
          , int desiredWidth
          , int desiredHeight
          , int screenWidth
          , int screenHeight) {
        if (selected == null || coarseStep <= 1) return selected;

        int start = Math.max(desiredWidth, selected.width - coarseStep);
        int end = Math.min(screenWidth, selected.width + coarseStep);
        LayoutPlan best = selected;
        for (int pass = 0;
             pass < WIDTH_REFINEMENT_PASSES && start <= end;
             ++pass) {
            int span = end - start;
            int stride = Math.max(
                    1
                  , (int) Math.ceil(
                        span / (double) WIDTH_REFINEMENT_SAMPLES));
            for (int width = start; width <= end; width += stride) {
                best = PickBetter(
                        best
                      , EvaluatePlan(
                            selected.template
                          , selected.tier
                          , selected.showMedia
                          , selected.renderVideo
                          , selected.showBody
                          , selected.showAdvertiser
                          , selected.showRating
                          , selected.showIcon
                          , width
                          , desiredWidth
                          , desiredHeight
                          , screenWidth
                          , screenHeight));
            }
            best = PickBetter(
                    best
                  , EvaluatePlan(
                        selected.template
                      , selected.tier
                      , selected.showMedia
                      , selected.renderVideo
                      , selected.showBody
                      , selected.showAdvertiser
                      , selected.showRating
                      , selected.showIcon
                      , end
                      , desiredWidth
                      , desiredHeight
                      , screenWidth
                      , screenHeight));
            if (stride == 1) break;
            start = Math.max(desiredWidth, best.width - stride);
            end = Math.min(screenWidth, best.width + stride);
        }

        for (int width = start; width <= end; ++width) {
            best = PickBetter(
                    best
                  , EvaluatePlan(
                        selected.template
                      , selected.tier
                      , selected.showMedia
                      , selected.renderVideo
                      , selected.showBody
                      , selected.showAdvertiser
                      , selected.showRating
                      , selected.showIcon
                      , width
                      , desiredWidth
                      , desiredHeight
                      , screenWidth
                      , screenHeight));
        }
        return best;
    }

    private void UpdateVariantBest(
            ArrayList<LayoutPlan> variants
          , LayoutPlan candidate) {
        if (candidate == null) return;
        for (int index = 0; index < variants.size(); ++index) {
            LayoutPlan current = variants.get(index);
            if (current.template == candidate.template
                    && current.tier == candidate.tier
                    && current.showMedia == candidate.showMedia
                    && current.renderVideo == candidate.renderVideo
                    && current.showBody == candidate.showBody
                    && current.showAdvertiser == candidate.showAdvertiser
                    && current.showRating == candidate.showRating
                    && current.showIcon == candidate.showIcon) {
                if (candidate.score < current.score) {
                    variants.set(index, candidate);
                }
                return;
            }
        }
        variants.add(candidate);
    }

    private LayoutPlan ExpandMediaWithinPlan(LayoutPlan plan) {
        if (plan == null
                || !plan.showMedia
                || plan.mediaWidth <= 0
                || plan.mediaHeight <= 0) {
            return plan;
        }

        int contentWidth = plan.width;
        int maxMediaWidth = contentWidth;
        if (IsMediaSide(plan.template)) {
            maxMediaWidth -= viewFactory.GapForTier(plan.tier)
                    + RequiredTextSlotWidth(plan);
        }
        if (maxMediaWidth <= plan.mediaWidth) return plan;

        double maxScale = Math.min(
                maxMediaWidth / (double) plan.mediaWidth
              , plan.height / (double) plan.mediaHeight);
        if (maxScale <= 1.0) return plan;

        int baseWidth = plan.mediaWidth;
        int baseHeight = plan.mediaHeight;
        int bestWidth = baseWidth;
        int bestHeight = baseHeight;
        int bestNaturalHeight = plan.measuredContentHeight;
        double low = 1.0;
        double high = maxScale;
        InFeedAdViewFactory.ProbeLayout probe = GetProbeLayout(plan);

        for (int iteration = 0;
             iteration < MEDIA_EXPANSION_ITERATIONS;
             ++iteration) {
            double scale = (low + high) / 2.0;
            plan.mediaWidth = Math.max(
                    baseWidth
                  , (int) Math.floor(baseWidth * scale));
            plan.mediaHeight = Math.max(
                    baseHeight
                  , (int) Math.floor(baseHeight * scale));
            viewFactory.ConfigureProbeLayout(probe, plan);
            probe.root.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            plan.width
                          , View.MeasureSpec.EXACTLY)
                  , View.MeasureSpec.makeMeasureSpec(
                            0
                          , View.MeasureSpec.UNSPECIFIED));
            int naturalHeight = Math.max(
                    viewFactory.Dp(MIN_NATIVE_AD_DP)
                  , probe.root.getMeasuredHeight());

            boolean fits = naturalHeight <= plan.height;
            if (fits) {
                probe.root.measure(
                        View.MeasureSpec.makeMeasureSpec(
                                plan.width
                              , View.MeasureSpec.EXACTLY)
                      , View.MeasureSpec.makeMeasureSpec(
                                plan.height
                              , View.MeasureSpec.EXACTLY));
                probe.root.layout(0, 0, plan.width, plan.height);
                fits = validator.ValidateAssetGeometry(
                        probe.root
                      , probe.views
                      , plan
                      , false);
            }

            if (fits) {
                bestWidth = plan.mediaWidth;
                bestHeight = plan.mediaHeight;
                bestNaturalHeight = naturalHeight;
                low = scale;
            } else {
                high = scale;
            }
        }

        plan.mediaWidth = bestWidth;
        plan.mediaHeight = bestHeight;
        plan.measuredContentHeight = bestNaturalHeight;
        return plan;
    }

    private boolean ResolveMediaDimensions(LayoutPlan plan) {
        if (!plan.showMedia) {
            plan.mediaWidth = 0;
            plan.mediaHeight = 0;
            return true;
        }

        int contentWidth = plan.width;
        if (contentWidth <= 0) {
            RecordRejection(
                    plan.template
                  , plan.tier
                  , "safe content width is " + contentWidth + "px");
            return false;
        }

        float aspect = MediaAspectRatio(plan.renderVideo);
        if (plan.template == TEMPLATE_MEDIA_BACKGROUND) {
            // Both dimensions carry the registered MediaView, so both are
            // held to the media minimum - video's floor for a video creative,
            // the far lower image floor otherwise.
            int minSide = viewFactory.MediaPolicyFloorPx();
            if (plan.width < minSide || plan.height < minSide) {
                RecordRejection(
                        plan.template
                      , plan.tier
                      , "slot " + plan.width + "x" + plan.height
                                + "px is under the media minimum");
                return false;
            }
            plan.mediaWidth = plan.width;
            plan.mediaHeight = plan.height;
            return true;
        }
        if (plan.template == TEMPLATE_MEDIA_TOP) {
            int width;
            int height;
            if (plan.renderVideo) {
                int[] videoSize = ResolveVideoSlot(
                        viewFactory.MediaSizeForTier(plan.tier)
                      , contentWidth
                      , aspect);
                if (videoSize == null) {
                    RecordRejection(
                            plan.template
                          , plan.tier
                          , "video cannot fit safe width "
                                    + contentWidth + "px");
                    return false;
                }
                width = videoSize[0];
                height = videoSize[1];
            } else {
                width = contentWidth;
                height = Math.max(1, (int) Math.ceil(width / aspect));
            }
            plan.mediaWidth = width;
            plan.mediaHeight = height;
            return true;
        }

        int height = viewFactory.MediaSizeForTier(plan.tier);
        int width;
        if (plan.renderVideo) {
            int maximumMediaWidth = contentWidth
                    - viewFactory.GapForTier(plan.tier)
                    - RequiredTextSlotWidth(plan);
            int[] videoSize = ResolveVideoSlot(
                    height
                  , maximumMediaWidth
                  , aspect);
            if (videoSize == null) {
                RecordRejection(
                        plan.template
                      , plan.tier
                      , "video cannot fit media width "
                                + maximumMediaWidth + "px");
                return false;
            }
            width = videoSize[0];
            height = videoSize[1];
        } else {
            width = Math.max(1, (int) Math.ceil(height * aspect));
        }

        int minimumTextWidth = RequiredTextSlotWidth(plan);
        int requiredWidth = width
                + viewFactory.GapForTier(plan.tier)
                + minimumTextWidth;
        if (requiredWidth > contentWidth) {
            RecordRejection(
                    plan.template
                  , plan.tier
                  , "media and text need " + requiredWidth
                            + "px > safe width " + contentWidth + "px");
            return false;
        }
        plan.mediaWidth = width;
        plan.mediaHeight = height;
        return true;
    }

    static boolean IsMediaSide(int template) {
        return template == TEMPLATE_MEDIA_LEFT
                || template == TEMPLATE_MEDIA_RIGHT;
    }

    private int RequiredTextSlotWidth(LayoutPlan plan) {
        if (IsMediaSide(plan.template)) {
            return viewFactory.MinimumMediaLeftTextSlotWidth(plan);
        }
        return MIN_VISIBLE_TEXT_SLOT_PX
                + 2 * viewFactory.PaddingForTier(plan.tier);
    }

    private int[] ResolveVideoSlot(
            int preferredHeight
          , int maximumWidth
          , float creativeAspect) {
        int requiredWidth = validator.RequiredVideoWidth(creativeAspect);
        int requiredHeight = validator.RequiredVideoHeight(creativeAspect);
        if (maximumWidth < requiredWidth) return null;

        int height = Math.max(requiredHeight, preferredHeight);
        int aspectWidth = Math.max(
                requiredWidth
              , (int) Math.ceil(height * creativeAspect));
        int width = Math.min(maximumWidth, aspectWidth);

        if (!validator.IsPolicySafeVideoSize(width, height)) return null;
        return new int[] { width, height };
    }

    private float MediaAspectRatio(boolean renderVideo) {
        if (renderVideo) {
            MediaContent content = nativeAd.getMediaContent();
            if (content != null && content.getAspectRatio() > 0f) {
                return ClampAspect(content.getAspectRatio());
            }
            return UNKNOWN_VIDEO_ASPECT_RATIO;
        }

        Drawable image = viewFactory.FindMainImage();
        if (image != null
                && image.getIntrinsicWidth() > 0
                && image.getIntrinsicHeight() > 0) {
            return ClampAspect(
                    image.getIntrinsicWidth()
                            / (float) image.getIntrinsicHeight());
        }
        return DEFAULT_IMAGE_ASPECT_RATIO;
    }

    private static float ClampAspect(float aspect) {
        return Math.max(
                MIN_MEDIA_ASPECT_RATIO
              , Math.min(MAX_MEDIA_ASPECT_RATIO, aspect));
    }

    private void ResetRejectionReasons() {
        for (int template = 0; template < rejectionReasons.length; ++template) {
            rejectionReasons[template] = null;
        }
    }

    // Keeps the first few distinct reasons per template rather than only the
    // first: with one slot, every diagnosis of a slot that renders nothing was
    // made from whichever variant happened to fail first, which repeatedly
    // pointed at the wrong constraint.
    private static final int MAX_REJECTION_REASONS_PER_TEMPLATE = 3;

    private void RecordRejection(int template, int tier, String reason) {
        if (template < 0 || template >= rejectionReasons.length) return;

        String entry = TierName(tier) + ": " + reason;
        String existing = rejectionReasons[template];
        if (existing == null) {
            rejectionReasons[template] = entry;
            return;
        }
        if (existing.contains(reason)) return;

        int kept = existing.split(" \\| ", -1).length;
        if (kept >= MAX_REJECTION_REASONS_PER_TEMPLATE) return;

        rejectionReasons[template] = existing + " | " + entry;
    }

    private static void AddWidth(
            ArrayList<Integer> widths
          , int width
          , int screenWidth) {
        int resolved = Math.max(1, Math.min(screenWidth, width));
        if (!widths.contains(resolved)) widths.add(resolved);
    }

    static int Clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static String TemplateName(int template) {
        if (template == TEMPLATE_COMPACT_ROW) return "COMPACT_ROW";
        if (template == TEMPLATE_COMPACT_COLUMN) return "COMPACT_COLUMN";
        if (template == TEMPLATE_MEDIA_LEFT) return "MEDIA_LEFT";
        if (template == TEMPLATE_MEDIA_TOP) return "MEDIA_TOP";
        if (template == TEMPLATE_MEDIA_BACKGROUND) {
            return "MEDIA_BACKGROUND";
        }
        return "MEDIA_RIGHT";
    }

    static String TierName(int tier) {
        if (tier == TIER_COMPACT) return "COMPACT";
        if (tier == TIER_REGULAR) return "REGULAR";
        return "ROOMY";
    }

    static String MediaName(LayoutPlan plan) {
        if (!plan.showMedia) return "NONE";
        return plan.renderVideo ? "VIDEO" : "IMAGE";
    }
}
