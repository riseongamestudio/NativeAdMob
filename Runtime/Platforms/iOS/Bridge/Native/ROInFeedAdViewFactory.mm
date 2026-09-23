#import "ROInFeedAdViewFactory.h"

#import <objc/runtime.h>

static NSString *const kROTag = @"InFeed";

// Values carried over verbatim; dp reads as points, px floors convert
// through the screen scale where noted.
// The 120pt floor belongs to video: the SDK warning that enforces it is
// conditional on the media view showing video, and whether it ever will is
// the creative's property, known before layout.
static const CGFloat kROMinVideoMediaSize = 120;
static const CGFloat kROMinImageMediaSize = 48;
// The least line width worth giving a headline next to an icon. Below it
// even a marquee reads as a sliver, so the icon leaves the line and the
// text takes the full width instead.
static const CGFloat kROInFeedMinIconRowTextWidth = 72;
// The dimming over the ambient backdrop - dark enough that the fitted
// creative in front stays the one that reads as the picture.
static const CGFloat kROInFeedAmbientDimAlpha = 0.55f;
static const CGFloat kROMinAttributionSizePx = 15;
static const CGFloat kROAttributionWidth = 24;
static const CGFloat kROAttributionHeight = 18;
static const CGFloat kROAdChoicesMinWidthPx = 19;
// On a 96dp slot the short-side ratio lands on this floor every time, so
// the floor is the height; 24dp presses (the whole rect is clickable) and
// leaves the assets above it their share.
static const CGFloat kROCtaMinHeight = 24;
static const CGFloat kROVideoFooterCtaHeight = 24;
static const CGFloat kROVideoFooterHorizontalPadding = 4;
static const CGFloat kROVideoFooterBottomPadding = 2;
static const CGFloat kROVideoRailMinWidthCompact = 72;
static const CGFloat kROVideoRailMinWidthRegular = 88;
static const CGFloat kROVideoRailMinWidthRoomy = 104;
static const CGFloat kROVideoRailIconCompact = 36;
static const CGFloat kROVideoRailIconRegular = 40;
static const CGFloat kROVideoRailIconRoomy = 48;
static const CGFloat kROVideoRailCtaHeightCompact = 32;
static const CGFloat kROVideoRailCtaHeightRegular = 36;
static const CGFloat kROVideoRailCtaHeightRoomy = 40;
static const CGFloat kROVideoRailEdgePadding = 2;
static const CGFloat kROVideoHeadlineHorizontalPadding = 4;
static const CGFloat kROVideoHeadlineVerticalPadding = 2;
static const CGFloat kROMediaLeftTextWidthCompact = 80;
static const CGFloat kROMediaLeftTextWidthRegular = 96;
static const CGFloat kROMediaLeftTextWidthRoomy = 120;
// The floor for an icon standing in for the media: anything under this
// reads as decoration, not as an asset worth the space it was given.
static const CGFloat kROMinFillerIcon = 24;
// Rating and advertiser stop riding the scale ladder below this.
static const CGFloat kROOptionalTextMinSize = 10;
static const CGFloat kROBadgeShortSideRatio = 0.10f;
static const CGFloat kROAttributionAspectRatio = 4.0f / 3.0f;
static const CGFloat kROAttributionTextHeightRatio = 0.55f;
// Boxes are sized from the ad, labels are sized from their box; the call to
// action box is deliberately 1.8x the badge box.
static const CGFloat kROCtaShortSideRatio = 0.18f;
static const CGFloat kROCtaMaxHeight = 36;
static const CGFloat kROCtaTextHeightRatio = 0.45f;
static const double kROMaxStarRating = 5;
// The tier is a ladder of text sizes, not a claim about the cell: a 96pt
// cell can win on the roomy rung, and when it does its borders must still
// be a small cell's borders. Every spacing the tier hands out is therefore
// capped by the cell's own short side.
static const CGFloat kROCellSpacingRatio = 0.02f;
static const CGFloat kROCellCtaPaddingRatio = 0.035f;
static const CGFloat kROCellEdgePaddingRatio = 0.015f;
// How far a straight-edged asset has to stand back from a rounded corner
// of radius r so that its own corner just touches the arc: the arc's
// center sits r in from both edges, the asset's corner sits d in from
// both, and the two are r apart exactly when (r - d) * sqrt(2) = r, so
// d = r * (1 - 1 / sqrt(2)). Less and the corner is cut off; more and the
// cell wastes border it was never asked for.
static const double kROCornerInsetRatio = 1.0 - M_SQRT1_2;

static UIColor *ROInFeedArgb(uint32_t argb) {
    return [UIColor colorWithRed:((argb >> 16) & 0xFF) / 255.0
                           green:((argb >> 8) & 0xFF) / 255.0
                            blue:(argb & 0xFF) / 255.0
                           alpha:((argb >> 24) & 0xFF) / 255.0];
}

@implementation ROInFeedAssetViews
@end

@implementation ROInFeedProbeLayout
@end

@implementation ROInFeedNativeAdViewResult
@end

// The height handed to the button is a minimum; the row it sits in can
// stretch it, and the label always follows the height the button actually
// ends up with.
@interface ROInFeedCallToActionButton : UIButton
- (void)ro_applyLabelSizeForHeight:(CGFloat)height;
@end

@implementation ROInFeedCallToActionButton

- (void)layoutSubviews {
    [super layoutSubviews];
    [self ro_applyLabelSizeForHeight:self.bounds.size.height];
}

- (void)ro_applyLabelSizeForHeight:(CGFloat)height {
    CGFloat target = MAX(1, height) * kROCtaTextHeightRatio;
    if (fabs(self.titleLabel.font.pointSize - target) < 0.5) return;
    self.titleLabel.font = [self.titleLabel.font fontWithSize:target];
}

- (void)setHighlighted:(BOOL)highlighted {
    [super setHighlighted:highlighted];
    // Stands in for the Android ripple: the button still answers a finger.
    self.alpha = highlighted ? 0.7 : 1.0;
}

@end

// The filler absorbs leftover height, so what it asks for is only the
// smallest icon worth showing; the weighted slot then stretches it over
// whatever the fitted text left free. Without this an unconstrained measure
// asks the image for its bitmap size - routinely taller than the slot - and
// every icon variant is rejected as not fitting.
@interface ROInFeedIconFillerView : HBFrameLayoutView
@property (nonatomic) CGFloat reservedHeight;
@end

@implementation ROInFeedIconFillerView

- (void)ro_measureWithWidthSpec:(HBMeasureSpec)widthSpec
                     heightSpec:(HBMeasureSpec)heightSpec {
    if (heightSpec.mode == HBMeasureSpecExactly) {
        [super ro_measureWithWidthSpec:widthSpec heightSpec:heightSpec];
        return;
    }

    CGFloat height = self.reservedHeight;
    if (heightSpec.mode == HBMeasureSpecAtMost) {
        height = MIN(height, heightSpec.size);
    }
    [super ro_measureWithWidthSpec:widthSpec
                        heightSpec:HBMeasureSpecMake(
                                HBMeasureSpecExactly, height)];
}

@end

// A GADMediaView whose still-image fallback child always fills it.
@interface ROInFeedMediaView : GADMediaView
@property (nonatomic, strong, nullable) UIImageView *fallbackImageView;
@property (nonatomic, strong, nullable) UIView *ambientBackdropView;
@end

@implementation ROInFeedMediaView

- (void)layoutSubviews {
    [super layoutSubviews];
    self.ambientBackdropView.frame = self.bounds;
    self.fallbackImageView.frame = self.bounds;
}

@end

// One anchor per block: where the icon stands alone above the text, the
// text centers under it. A centered icon over left-aligned lines reads as
// two blocks that never agreed with each other.
static void ROInFeedCentreUnderIcon(UIView *view) {
    if (view == nil) return;

    if ([view isKindOfClass:UILabel.class]) {
        ((UILabel *)view).textAlignment = NSTextAlignmentCenter;
    }
    view.ro_layoutGravity = HBGravityCenterHorizontal;
}

static void ROInFeedCentreBlock(ROInFeedAssetViews *views) {
    ROInFeedCentreUnderIcon(views.headline);
    ROInFeedCentreUnderIcon(views.body);
    ROInFeedCentreUnderIcon(views.advertiser);
    ROInFeedCentreUnderIcon(views.rating);
}

@implementation ROInFeedAdViewFactory {
    GADNativeAd *_nativeAd;
    CGFloat _slotShortSide;
    CGFloat _screenScale;
    // The clearance every asset keeps from the cell's rounded corners; 0
    // on a square cell. Spent once, as the outer column's padding on every
    // template but the scrim one, where the background picture is meant
    // to run under the curve and only the block in front stands back.
    CGFloat _cornerInset;
}

- (instancetype)initWithNativeAd:(GADNativeAd *)nativeAd
                 slotShortSidePt:(CGFloat)slotShortSidePt
                   roundCornerPt:(CGFloat)roundCornerPt {
    self = [super init];
    if (self == nil) return nil;
    _nativeAd = nativeAd;
    _slotShortSide = MAX(1, slotShortSidePt);
    _screenScale = MAX(1, UIScreen.mainScreen.nativeScale);
    _cornerInset = roundCornerPt <= 0
            ? 0
            : ceil(roundCornerPt * kROCornerInsetRatio);
    return self;
}

- (CGFloat)ro_ptFromPx:(CGFloat)px {
    return px / _screenScale;
}

// ---------------------------------------------------------------------------
// Asset queries
// ---------------------------------------------------------------------------

- (BOOL)hasVideoContent {
    return _nativeAd.mediaContent.hasVideoContent;
}

- (BOOL)hasValidStarRating {
    return [self resolveStarRating] > 0;
}

- (double)resolveStarRating {
    NSDecimalNumber *starRating = _nativeAd.starRating;
    double value = starRating.doubleValue;
    if (starRating == nil || value <= 0 || isnan(value) || isinf(value)) {
        return 0;
    }
    return MIN(kROMaxStarRating, value);
}

- (BOOL)canRenderMainImage:(UIImage *)mainImage {
    return mainImage != nil;
}

- (BOOL)hasUnrenderableIcon {
    GADNativeAdImage *icon = _nativeAd.icon;
    return icon != nil && icon.image == nil;
}

- (BOOL)hasRenderableIcon {
    return _nativeAd.icon.image != nil;
}

- (UIImage *)findMainImage {
    for (GADNativeAdImage *image in _nativeAd.images) {
        if (image.image != nil) return image.image;
    }
    GADMediaContent *content = _nativeAd.mediaContent;
    if (content != nil && !content.hasVideoContent) {
        return content.mainImage;
    }
    return nil;
}

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

- (CGFloat)minimumWidthForTier:(ROInFeedTier)tier {
    return [self mediaPolicyFloor] + [self paddingForTier:tier] * 2;
}

// Video's floor for a video creative, the image floor otherwise.
- (CGFloat)mediaPolicyFloor {
    return [self hasVideoContent]
            ? kROMinVideoMediaSize
            : kROMinImageMediaSize;
}

- (CGFloat)mediaSizeForTier:(ROInFeedTier)tier {
    if ([self hasVideoContent]) {
        if (tier == ROInFeedTierCompact) return kROMinVideoMediaSize;
        if (tier == ROInFeedTierRegular) return 144;
        return 180;
    }
    if (tier == ROInFeedTierCompact) return 56;
    if (tier == ROInFeedTierRegular) return 88;
    return 120;
}

// The slot is already bounded by the card it sits in, so a tier's own edge
// inset bought nothing. The one inset the cell keeps is the corner
// clearance, the same on every tier: what the outer column spends, and so
// what the engine has to budget for on both sides.
- (CGFloat)paddingForTier:(ROInFeedTier)tier {
    return _cornerInset;
}

- (CGFloat)gapForTier:(ROInFeedTier)tier {
    CGFloat tierGap;
    if (tier == ROInFeedTierCompact) {
        tierGap = 1;
    } else if (tier == ROInFeedTierRegular) {
        tierGap = 3;
    } else {
        tierGap = 5;
    }
    return MIN(tierGap, [self ro_cellSpacingCap]);
}

// The scrim block's own inset from the cell's edges - a hair on a small
// cell, a few points on a large one, never a tier's whim.
- (CGFloat)ro_cellEdgePadding {
    return MAX(1, MIN(4, round(_slotShortSide * kROCellEdgePaddingRatio)));
}

// What this cell can afford to spend between its rows, whatever rung the
// scorer climbed to.
- (CGFloat)ro_cellSpacingCap {
    return MAX(1, MIN(5, round(_slotShortSide * kROCellSpacingRatio)));
}

- (ROInFeedTier)preferredTierForWidth:(CGFloat)width height:(CGFloat)height {
    CGFloat shortSide = MIN(width, height);
    if (shortSide >= 280) return ROInFeedTierRoomy;
    if (shortSide >= 150) return ROInFeedTierRegular;
    return ROInFeedTierCompact;
}

- (CGFloat)minimumMediaLeftTextSlotWidthForPlan:(ROInFeedLayoutPlan *)plan {
    if (plan.renderVideo) {
        if (plan.tier == ROInFeedTierRoomy) return kROVideoRailMinWidthRoomy;
        if (plan.tier == ROInFeedTierRegular) {
            return kROVideoRailMinWidthRegular;
        }
        return kROVideoRailMinWidthCompact;
    }
    if (plan.tier == ROInFeedTierRoomy) return kROMediaLeftTextWidthRoomy;
    if (plan.tier == ROInFeedTierRegular) return kROMediaLeftTextWidthRegular;
    return kROMediaLeftTextWidthCompact;
}

- (CGFloat)callToActionHeightForPlan:(ROInFeedLayoutPlan *)plan {
    if (plan.layoutTemplate == ROInFeedTemplateMediaLeft
            && plan.renderVideo) {
        if (plan.tier == ROInFeedTierCompact) return kROVideoRailCtaHeightCompact;
        if (plan.tier == ROInFeedTierRegular) return kROVideoRailCtaHeightRegular;
        return kROVideoRailCtaHeightRoomy;
    }
    if (plan.layoutTemplate == ROInFeedTemplateMediaTop
            && plan.renderVideo) {
        return kROVideoFooterCtaHeight;
    }
    // Sized from the slot rather than from any one plan, so the button comes
    // out the same whichever layout wins.
    CGFloat desired = _slotShortSide * kROCtaShortSideRatio;
    return MAX(kROCtaMinHeight, MIN(kROCtaMaxHeight, desired));
}

- (CGFloat)ro_iconSizeForTier:(ROInFeedTier)tier {
    if (tier == ROInFeedTierCompact) return 24;
    if (tier == ROInFeedTierRegular) return 36;
    return 48;
}

- (CGFloat)ro_videoRailIconSizeForTier:(ROInFeedTier)tier {
    if (tier == ROInFeedTierCompact) return kROVideoRailIconCompact;
    if (tier == ROInFeedTierRegular) return kROVideoRailIconRegular;
    return kROVideoRailIconRoomy;
}

- (CGFloat)ro_ctaVerticalPaddingForTier:(ROInFeedTier)tier {
    if (tier == ROInFeedTierCompact) return 1;
    if (tier == ROInFeedTierRegular) return 2;
    return 4;
}

- (CGFloat)ro_ctaHorizontalPaddingForTier:(ROInFeedTier)tier {
    CGFloat tierPadding;
    if (tier == ROInFeedTierCompact) {
        tierPadding = 3;
    } else if (tier == ROInFeedTierRegular) {
        tierPadding = 8;
    } else {
        tierPadding = 10;
    }
    return MIN(tierPadding, round(_slotShortSide * kROCellCtaPaddingRatio));
}

// Every text size passes through the plan's scale, so a candidate that has
// to shrink keeps its proportions.
- (CGFloat)ro_scaled:(CGFloat)size plan:(ROInFeedLayoutPlan *)plan {
    return size * MAX(0.1, plan.textScale);
}

- (CGFloat)ro_headlineSizeForPlan:(ROInFeedLayoutPlan *)plan {
    CGFloat base = plan.tier == ROInFeedTierCompact
            ? 12
            : (plan.tier == ROInFeedTierRegular ? 16 : 17);
    return [self ro_scaled:base plan:plan];
}

- (CGFloat)ro_bodySizeForPlan:(ROInFeedLayoutPlan *)plan {
    CGFloat base = plan.tier == ROInFeedTierCompact
            ? 12
            : (plan.tier == ROInFeedTierRegular ? 13 : 14);
    return [self ro_scaled:base plan:plan];
}

// One short line is never what makes a candidate too tall; below the floor
// it stops reading as information.
- (CGFloat)ro_optionalSizeForPlan:(ROInFeedLayoutPlan *)plan {
    CGFloat base = plan.tier == ROInFeedTierCompact ? 11 : 12;
    return MAX(kROOptionalTextMinSize, [self ro_scaled:base plan:plan]);
}

- (NSInteger)ro_headlineMaxLinesForPlan:(ROInFeedLayoutPlan *)plan {
    if (plan.renderVideo) return 1;
    if (ROInFeedTemplateIsMediaSide(plan.layoutTemplate)) {
        return plan.tier == ROInFeedTierRoomy ? 3 : 2;
    }
    if (plan.layoutTemplate == ROInFeedTemplateMediaTop) {
        return plan.tier == ROInFeedTierCompact ? 2 : 3;
    }
    if (plan.layoutTemplate == ROInFeedTemplateCompactColumn) {
        return plan.tier == ROInFeedTierRoomy ? 3 : 2;
    }
    return 2;
}

- (BOOL)ro_shouldPrioritizeMediaSizeForPlan:(ROInFeedLayoutPlan *)plan {
    if (plan.renderVideo) return YES;
    if (!ROInFeedTemplateIsMediaSide(plan.layoutTemplate)) return NO;
    return plan.mediaWidth <= 72
            || plan.height <= plan.mediaHeight + 12
            || plan.tier == ROInFeedTierCompact;
}

- (NSInteger)ro_bodyMaxLinesForPlan:(ROInFeedLayoutPlan *)plan {
    if (plan.renderVideo) return 0;
    if (ROInFeedTemplateIsMediaSide(plan.layoutTemplate)) {
        if ([self ro_shouldPrioritizeMediaSizeForPlan:plan]) return 1;
        return plan.tier == ROInFeedTierRoomy ? 3 : 2;
    }
    if (plan.layoutTemplate == ROInFeedTemplateMediaTop
            || plan.layoutTemplate == ROInFeedTemplateCompactColumn) {
        return plan.tier == ROInFeedTierCompact ? 2 : 3;
    }
    return plan.tier == ROInFeedTierCompact ? 1 : 2;
}

- (NSInteger)ro_advertiserMaxLinesForPlan:(ROInFeedLayoutPlan *)plan {
    if (plan.renderVideo) return 0;
    if (ROInFeedTemplateIsMediaSide(plan.layoutTemplate)) {
        if ([self ro_shouldPrioritizeMediaSizeForPlan:plan]) return 1;
        return plan.tier == ROInFeedTierRoomy ? 2 : 1;
    }
    return plan.tier == ROInFeedTierRoomy ? 2 : 1;
}

// ---------------------------------------------------------------------------
// Probe and final builds
// ---------------------------------------------------------------------------

- (ROInFeedProbeLayout *)createProbeForPlan:(ROInFeedLayoutPlan *)plan {
    ROInFeedProbeLayout *probe = [[ROInFeedProbeLayout alloc] init];
    probe.layoutTemplate = plan.layoutTemplate;
    probe.tier = plan.tier;
    probe.showMedia = plan.showMedia;
    probe.renderVideo = plan.renderVideo;
    probe.views = [[ROInFeedAssetViews alloc] init];
    UIView *content = [self ro_buildContentForPlan:plan
                                             probe:YES
                                             views:probe.views
                                         mainImage:nil];
    HBFrameLayoutView *root = [[HBFrameLayoutView alloc] init];
    content.ro_layoutWidth = ROLayoutMatchParent;
    content.ro_layoutHeight = ROLayoutMatchParent;
    [root addSubview:content];
    probe.root = root;
    return probe;
}

- (void)configureProbeLayout:(ROInFeedProbeLayout *)probe
                     forPlan:(ROInFeedLayoutPlan *)plan {
    [self ro_configureInsetContent:probe.views plan:plan];
    [self ro_configureBadgeOverlays:probe.views plan:plan];
    [self ro_applyPlanTextConfiguration:probe.views plan:plan];

    if (probe.views.mediaSlot != nil) {
        probe.views.mediaSlot.ro_layoutWidth = plan.mediaWidth;
        probe.views.mediaSlot.ro_layoutHeight = plan.mediaHeight;
    }
    if (probe.views.videoRail != nil) {
        probe.views.videoRail.ro_layoutHeight = plan.mediaHeight;
    }
}

// A cached probe tree was built for some earlier rung of the ladder; this
// walks it back onto the plan being tried. Sizes and modes are the only
// things the ladder varies - everything structural is in the cache key.
- (void)ro_applyPlanTextConfiguration:(ROInFeedAssetViews *)views
                                 plan:(ROInFeedLayoutPlan *)plan {
    if (views.headline != nil) {
        views.headline.font = [UIFont boldSystemFontOfSize:
                [self ro_headlineSizeForPlan:plan]];
        if (!plan.renderVideo) {
            [self ro_applyTextMode:plan.marqueeHeadline
                             label:views.headline
                          maxLines:[self ro_headlineMaxLinesForPlan:plan]];
        }
    }
    if (views.body != nil) {
        views.body.font = [UIFont systemFontOfSize:
                [self ro_bodySizeForPlan:plan]];
        [self ro_applyTextMode:plan.marqueeSecondary
                         label:views.body
                      maxLines:[self ro_bodyMaxLinesForPlan:plan]];
    }
    if (views.advertiser != nil) {
        views.advertiser.font = [UIFont systemFontOfSize:
                [self ro_optionalSizeForPlan:plan]];
        [self ro_applyTextMode:plan.marqueeSecondary
                         label:views.advertiser
                      maxLines:[self ro_advertiserMaxLinesForPlan:plan]];
    }
    if (views.rating != nil) {
        views.rating.font = [UIFont systemFontOfSize:
                [self ro_optionalSizeForPlan:plan]];
    }
}

- (ROInFeedNativeAdViewResult *)buildNativeAdViewForPlan:(ROInFeedLayoutPlan *)plan
                                               mainImage:(UIImage *)mainImage {
    GADNativeAdView *nativeAdView = [[GADNativeAdView alloc] init];
    ROInFeedAssetViews *views = [[ROInFeedAssetViews alloc] init];
    UIView *content = [self ro_buildContentForPlan:plan
                                             probe:NO
                                             views:views
                                         mainImage:mainImage];
    content.ro_layoutWidth = ROLayoutMatchParent;
    content.ro_layoutHeight = ROLayoutMatchParent;
    [nativeAdView addSubview:content];

    if (views.headline != nil) nativeAdView.headlineView = views.headline;
    if (views.body != nil) nativeAdView.bodyView = views.body;
    if (views.icon != nil) nativeAdView.iconView = views.icon;
    if (views.callToAction != nil) {
        nativeAdView.callToActionView = views.callToAction;
    }
    if (views.advertiser != nil) {
        nativeAdView.advertiserView = views.advertiser;
    }
    if (views.rating != nil) nativeAdView.starRatingView = views.rating;
    if (plan.showMedia && views.media != nil) {
        // The primary asset must always register through GADMediaView -
        // registering an image view is what made AdMob reject every request
        // for the Android unit with error code 3.
        if (![views.media isKindOfClass:GADMediaView.class]) {
            [NSException raise:@"ROInFeedBindException"
                        format:@"Selected media layout created an "
                                "incompatible view"];
        }
        nativeAdView.mediaView = (GADMediaView *)views.media;
    }

    nativeAdView.nativeAd = _nativeAd;
    ROInFeedNativeAdViewResult *result =
            [[ROInFeedNativeAdViewResult alloc] init];
    result.nativeAdView = nativeAdView;
    result.assetViews = views;
    return result;
}

// ---------------------------------------------------------------------------
// Content assembly
// ---------------------------------------------------------------------------

- (UIView *)ro_buildContentForPlan:(ROInFeedLayoutPlan *)plan
                             probe:(BOOL)probe
                             views:(ROInFeedAssetViews *)views
                         mainImage:(UIImage *)mainImage {
    CGFloat gap = [self gapForTier:plan.tier];
    HBFrameLayoutView *root = [[HBFrameLayoutView alloc] init];

    HBLinearLayoutView *outer = [[HBLinearLayoutView alloc] init];
    views.outer = outer;
    outer.ro_vertical = YES;
    outer.ro_gravity = HBGravityTop | HBGravityCenterHorizontal;
    // The corner clearance, spent here once for everything the column
    // holds - media band, side rail, texts, button. The scrim template
    // spends none: its picture is the cell's background and runs under the
    // curve; the block in front adds the clearance to its own pad.
    BOOL mediaIsBackground =
            plan.layoutTemplate == ROInFeedTemplateMediaBackground;
    CGFloat outerInset = mediaIsBackground ? 0 : _cornerInset;
    outer.ro_padding = UIEdgeInsetsMake(
            outerInset, outerInset, outerInset, outerInset);

    if (mediaIsBackground) {
        UIView *backgroundMedia = [self ro_createMediaViewForPlan:plan
                                                            views:views
                                                            probe:probe
                                                        mainImage:mainImage];
        // The ambient backdrop lives BESIDE the MediaView, never inside it
        // - the SDK owns the MediaView's subviews and they do not survive
        // binding. The media ground is transparent on this template so the
        // backdrop shows through what the fitted picture leaves.
        if (mainImage != nil) {
            UIImageView *ambientBackdrop =
                    [[UIImageView alloc] initWithImage:mainImage];
            ambientBackdrop.contentMode = UIViewContentModeScaleAspectFill;
            ambientBackdrop.clipsToBounds = YES;
            UIView *ambientDim = [[UIView alloc] init];
            ambientDim.backgroundColor =
                    [UIColor colorWithWhite:0
                                      alpha:kROInFeedAmbientDimAlpha];
            ambientDim.autoresizingMask = UIViewAutoresizingFlexibleWidth
                    | UIViewAutoresizingFlexibleHeight;
            ambientDim.frame = ambientBackdrop.bounds;
            [ambientBackdrop addSubview:ambientDim];
            ambientBackdrop.ro_layoutWidth = ROLayoutMatchParent;
            ambientBackdrop.ro_layoutHeight = ROLayoutMatchParent;
            [root addSubview:ambientBackdrop];
        }
        backgroundMedia.ro_layoutWidth = ROLayoutMatchParent;
        backgroundMedia.ro_layoutHeight = ROLayoutMatchParent;
        [root addSubview:backgroundMedia];

        // One veil over the whole cell, not a band that stops short of the
        // top edge: the media reads as the panel's background through it and
        // no uncovered strip is left above the text.
        UIView *veil = [[UIView alloc] init];
        veil.backgroundColor = ROInFeedArgb(0xB3000000);
        veil.userInteractionEnabled = NO;
        veil.ro_layoutWidth = ROLayoutMatchParent;
        veil.ro_layoutHeight = ROLayoutMatchParent;
        [root addSubview:veil];

        HBLinearLayoutView *scrim = [[HBLinearLayoutView alloc] init];
        scrim.ro_vertical = YES;
        // Slim borders: the veil already separates the text from the
        // picture, so the block spends only the tier's own gap on every
        // side - a tiny cell cannot afford more.
        // A hair of breathing room, sized by the cell instead of by the
        // tier: enough that the text is not printed onto the very edge,
        // never enough to read as a border. The corner clearance the outer
        // column did not spend on this template is a FLOOR here, not an
        // addend: both measure from the same cell edge, so a block already
        // standing back far enough to clear the arc is already standing off
        // the edge. Adding them spent the same border twice.
        CGFloat scrimPad = MAX([self ro_cellEdgePadding], _cornerInset);
        scrim.ro_padding = UIEdgeInsetsMake(
                scrimPad, scrimPad, scrimPad, scrimPad);

        views.headline = [self ro_createTextWithValue:_nativeAd.headline
                                                 size:[self ro_headlineSizeForPlan:plan]
                                                 bold:YES];
        [self ro_applyTextMode:plan.marqueeHeadline
                         label:views.headline
                      maxLines:[self ro_headlineMaxLinesForPlan:plan]];
        // The icon only shares the headline's line while the line keeps a
        // usable width for text - a marquee squeezed into a sliver reads
        // worse than no icon row at all. Below that floor the icon stands
        // alone and every text follows at full width.
        CGFloat scrimContentWidth =
                MAX(0, plan.width - 2 * scrimPad);
        BOOL iconStandsAlone = plan.showIcon
                && [self hasRenderableIcon]
                && scrimContentWidth
                            - [self ro_iconSizeForTier:plan.tier]
                            - gap
                        < kROInFeedMinIconRowTextWidth;
        if (iconStandsAlone) {
            [self ro_addIconTo:scrim views:views tier:plan.tier gap:0];
            if (views.icon != nil) {
                views.icon.ro_layoutGravity = HBGravityCenterHorizontal;
                views.icon.ro_layoutMargins =
                        UIEdgeInsetsMake(0, 0, gap, 0);
            }
            views.headline.ro_layoutWidth = ROLayoutMatchParent;
            [scrim addSubview:views.headline];
        } else {
            HBLinearLayoutView *headlineRow =
                    [[HBLinearLayoutView alloc] init];
            headlineRow.ro_vertical = NO;
            headlineRow.ro_gravity = HBGravityCenterVertical;
            if (plan.showIcon) {
                [self ro_addIconTo:headlineRow
                             views:views
                              tier:plan.tier
                               gap:gap];
            }
            HBFrameLayoutView *headlineSlot =
                    [[HBFrameLayoutView alloc] init];
            [headlineSlot addSubview:views.headline];
            headlineSlot.ro_layoutWidth = 0;
            headlineSlot.ro_layoutWeight = 1;
            [headlineRow addSubview:headlineSlot];
            headlineRow.ro_layoutWidth = ROLayoutMatchParent;
            [scrim addSubview:headlineRow];
        }
        [self ro_addBodyAndOptionalTo:scrim views:views plan:plan];
        [self ro_addCallToActionTo:scrim
                             views:views
                              plan:plan
                               gap:gap
                         fullWidth:YES];
        if (iconStandsAlone) ROInFeedCentreBlock(views);

        // Not the inset content: the inset pass would reset the scrim's own
        // padding to the slot edge.
        views.scrim = scrim;
        outer.ro_gravity = HBGravityBottom | HBGravityCenterHorizontal;
        scrim.ro_layoutWidth = ROLayoutMatchParent;
        [outer addSubview:scrim];
        return [self ro_finishContentRoot:root outer:outer views:views plan:plan];
    }

    if (plan.layoutTemplate == ROInFeedTemplateCompactRow) {
        HBLinearLayoutView *row = [[HBLinearLayoutView alloc] init];
        row.ro_vertical = NO;
        row.ro_gravity = HBGravityCenterVertical;
        views.insetContent = row;
        views.insetContentAvoidsBadges = YES;
        if (plan.showIcon) {
            [self ro_addIconTo:row views:views tier:plan.tier gap:gap];
        }

        HBLinearLayoutView *texts = [self ro_buildTextStack:views plan:plan];
        texts.ro_layoutWidth = 0;
        texts.ro_layoutWeight = 1;
        [row addSubview:texts];
        [self ro_addCallToActionTo:row
                             views:views
                              plan:plan
                               gap:gap
                         fullWidth:NO];
        // One row of assets in a tall slot looked broken glued to the top
        // edge; centering moves where it sits without touching what the
        // engine measures.
        outer.ro_gravity = HBGravityCenterVertical | HBGravityCenterHorizontal;
        row.ro_layoutWidth = ROLayoutMatchParent;
        [outer addSubview:row];
        return [self ro_finishContentRoot:root outer:outer views:views plan:plan];
    }

    if (plan.layoutTemplate == ROInFeedTemplateCompactColumn) {
        HBLinearLayoutView *content =
                [self ro_buildHeadlineAndActionStack:views plan:plan];
        views.insetContent = content;
        // ro_buildHeadlineAndActionStack decides the badge reserve: it knows
        // whether the top band holds the icon or the headline.
        content.ro_layoutWidth = ROLayoutMatchParent;
        content.ro_layoutHeight = 0;
        content.ro_layoutWeight = 1;
        [outer addSubview:content];
        return [self ro_finishContentRoot:root outer:outer views:views plan:plan];
    }

    UIView *mediaView = [self ro_createMediaViewForPlan:plan
                                                  views:views
                                                  probe:probe
                                              mainImage:mainImage];
    if (plan.layoutTemplate == ROInFeedTemplateMediaLeft) {
        if (plan.renderVideo) {
            [self ro_buildVideoMediaLeftContent:outer
                                      mediaView:mediaView
                                          views:views
                                           plan:plan];
            return [self ro_finishContentRoot:root
                                        outer:outer
                                        views:views
                                         plan:plan];
        }

        HBLinearLayoutView *row = [[HBLinearLayoutView alloc] init];
        row.ro_vertical = NO;
        row.ro_gravity = HBGravityCenterVertical;
        mediaView.ro_layoutWidth = plan.mediaWidth;
        mediaView.ro_layoutHeight = plan.mediaHeight;
        mediaView.ro_layoutMargins = UIEdgeInsetsMake(0, 0, 0, gap);
        [row addSubview:mediaView];

        CGFloat leftover =
                [self ro_sideLayoutLeftoverHeightForPlan:plan];
        BOOL callToActionBelow =
                leftover >= [self callToActionHeightForPlan:plan] + gap;
        HBLinearLayoutView *content =
                [self ro_buildIdentityAndText:views
                                         plan:plan
                          includeCallToAction:!callToActionBelow];
        views.insetContent = content;
        views.insetContentAvoidsBadges = YES;
        content.ro_layoutWidth = 0;
        content.ro_layoutHeight = plan.mediaHeight;
        content.ro_layoutWeight = 1;
        [row addSubview:content];
        row.ro_layoutWidth = ROLayoutMatchParent;
        [outer addSubview:row];
        if (callToActionBelow) {
            [self ro_addCallToActionTo:outer
                                 views:views
                                  plan:plan
                                   gap:gap
                             fullWidth:YES];
        }
        return [self ro_finishContentRoot:root outer:outer views:views plan:plan];
    }

    if (plan.layoutTemplate == ROInFeedTemplateMediaRight) {
        HBLinearLayoutView *row = [[HBLinearLayoutView alloc] init];
        row.ro_vertical = NO;
        row.ro_gravity = HBGravityCenterVertical;

        // The same leftover test the mirrored template runs. Without it
        // this branch kept the button inside the text column while
        // MEDIA_LEFT moved it under the row, so one creative got two
        // different buttons depending on which side its picture landed.
        CGFloat leftover =
                [self ro_sideLayoutLeftoverHeightForPlan:plan];
        BOOL callToActionBelow =
                leftover >= [self callToActionHeightForPlan:plan] + gap;
        HBLinearLayoutView *content =
                [self ro_buildIdentityAndText:views
                                         plan:plan
                          includeCallToAction:!callToActionBelow];
        views.insetContent = content;
        // The mirror's asymmetry pays on the badge side: AdChoices, the wider
        // badge, lands on the media, and only the text column reserves the
        // strip, for the attribution corner alone.
        views.insetContentAvoidsBadges = YES;
        content.ro_layoutWidth = 0;
        content.ro_layoutHeight = plan.mediaHeight;
        content.ro_layoutWeight = 1;
        [row addSubview:content];

        mediaView.ro_layoutWidth = plan.mediaWidth;
        mediaView.ro_layoutHeight = plan.mediaHeight;
        mediaView.ro_layoutMargins = UIEdgeInsetsMake(0, gap, 0, 0);
        [row addSubview:mediaView];
        row.ro_layoutWidth = ROLayoutMatchParent;
        [outer addSubview:row];
        if (callToActionBelow) {
            [self ro_addCallToActionTo:outer
                                 views:views
                                  plan:plan
                                   gap:gap
                             fullWidth:YES];
        }
        return [self ro_finishContentRoot:root outer:outer views:views plan:plan];
    }

    if (plan.renderVideo) {
        mediaView.ro_layoutWidth = plan.mediaWidth;
        mediaView.ro_layoutHeight = plan.mediaHeight;
        mediaView.ro_layoutGravity = HBGravityCenterHorizontal;
        mediaView.ro_layoutMargins = UIEdgeInsetsMake(0, 0, gap, 0);
        [outer addSubview:mediaView];

        HBLinearLayoutView *footer = [self ro_buildVideoFooterRow:views
                                                             plan:plan];
        views.insetContent = footer;
        views.insetContentAvoidsBadges = NO;
        footer.ro_layoutWidth = ROLayoutMatchParent;
        [outer addSubview:footer];
    } else {
        // The image media takes the plan's size as its floor and absorbs
        // whatever height the slot has left over, so free space enlarges the
        // picture instead of sitting as an empty band inside the text block.
        // The minimums keep the engine's natural measure - and so the
        // validator's math - exactly the plan's.
        mediaView.ro_minimumSize =
                CGSizeMake(plan.mediaWidth, plan.mediaHeight);
        mediaView.ro_layoutWidth = plan.mediaWidth;
        mediaView.ro_layoutHeight = 0;
        mediaView.ro_layoutWeight = 1;
        mediaView.ro_layoutGravity = HBGravityCenterHorizontal;
        mediaView.ro_layoutMargins = UIEdgeInsetsMake(0, 0, gap, 0);
        [outer addSubview:mediaView];

        HBLinearLayoutView *content =
                [self ro_buildHeadlineAndActionStack:views plan:plan];
        views.insetContent = content;
        views.insetContentAvoidsBadges = NO;
        content.ro_layoutWidth = ROLayoutMatchParent;
        content.ro_layoutHeight = ROLayoutWrapContent;
        [outer addSubview:content];
    }
    return [self ro_finishContentRoot:root outer:outer views:views plan:plan];
}

- (UIView *)ro_finishContentRoot:(HBFrameLayoutView *)root
                           outer:(HBLinearLayoutView *)outer
                           views:(ROInFeedAssetViews *)views
                            plan:(ROInFeedLayoutPlan *)plan {
    BOOL fillsAvailableHeight =
            plan.layoutTemplate == ROInFeedTemplateCompactColumn
                    || plan.layoutTemplate == ROInFeedTemplateMediaTop
                    || plan.layoutTemplate
                            == ROInFeedTemplateMediaBackground;
    outer.ro_layoutWidth = ROLayoutMatchParent;
    outer.ro_layoutHeight = fillsAvailableHeight
            ? ROLayoutMatchParent
            : ROLayoutWrapContent;
    [root addSubview:outer];
    [self ro_addBadgeOverlaysTo:root views:views];
    [self ro_configureInsetContent:views plan:plan];
    [self ro_configureBadgeOverlays:views plan:plan];
    return root;
}

- (void)ro_addBadgeOverlaysTo:(HBFrameLayoutView *)root
                        views:(ROInFeedAssetViews *)views {
    UILabel *attribution = [[UILabel alloc] init];
    attribution.text = @"Ad";
    attribution.textColor = UIColor.blackColor;
    attribution.font = [UIFont boldSystemFontOfSize:10];
    attribution.textAlignment = NSTextAlignmentCenter;
    attribution.backgroundColor = ROInFeedArgb(0xFFFFC107);
    attribution.userInteractionEnabled = NO;
    views.attribution = attribution;
    [root addSubview:attribution];

    // GADNativeAdView places its own AdChoices top-right; the reserve keeps
    // that corner clear the way the Android reserve view does.
    UIView *adChoicesReserve = [[UIView alloc] init];
    adChoicesReserve.userInteractionEnabled = NO;
    views.adChoicesReserve = adChoicesReserve;
    [root addSubview:adChoicesReserve];
}

- (CGFloat)ro_badgeHeightForPlan:(ROInFeedLayoutPlan *)plan {
    CGFloat desired = round(_slotShortSide * kROBadgeShortSideRatio);
    CGFloat minimum = [self ro_ptFromPx:kROMinAttributionSizePx];
    return MAX(minimum, MIN(kROAttributionHeight, desired));
}

- (void)ro_configureInsetContent:(ROInFeedAssetViews *)views
                            plan:(ROInFeedLayoutPlan *)plan {
    UIView *insetContent = views.insetContent;
    if (insetContent == nil
            || ![insetContent isKindOfClass:ROLayoutContainerView.class]) {
        return;
    }
    ROLayoutContainerView *container = (ROLayoutContainerView *)insetContent;

    if (plan.layoutTemplate == ROInFeedTemplateMediaTop
            && plan.renderVideo) {
        container.ro_padding = UIEdgeInsetsMake(
                0
              , kROVideoFooterHorizontalPadding
              , kROVideoFooterBottomPadding
              , kROVideoFooterHorizontalPadding);
        return;
    }
    if (plan.layoutTemplate == ROInFeedTemplateMediaLeft
            && plan.renderVideo) {
        container.ro_padding = UIEdgeInsetsMake(
                [self ro_badgeHeightForPlan:plan] + [self gapForTier:plan.tier]
              , kROVideoRailEdgePadding
              , kROVideoRailEdgePadding
              , kROVideoRailEdgePadding);
        return;
    }

    // Only the badge strip is reserved - the badges have to stay visible.
    // Everything else meets the column's edge, the way the badges already
    // do; the corner clearance is the outer column's padding, spent once,
    // so nothing is added here.
    CGFloat topPadding = views.insetContentAvoidsBadges
            ? [self ro_badgeHeightForPlan:plan]
            : 0;
    container.ro_padding = UIEdgeInsetsMake(topPadding, 0, 0, 0);
}

- (void)ro_configureBadgeOverlays:(ROInFeedAssetViews *)views
                             plan:(ROInFeedLayoutPlan *)plan {
    CGFloat badgeHeight = [self ro_badgeHeightForPlan:plan];
    CGFloat minimumPt = [self ro_ptFromPx:kROMinAttributionSizePx];
    CGFloat edgeInset = [self ro_badgeEdgeInset];
    if (views.attribution != nil) {
        CGFloat desiredWidth = round(badgeHeight * kROAttributionAspectRatio);
        CGFloat maximumWidth = MAX(minimumPt, plan.width - badgeHeight);
        CGFloat attributionWidth = MAX(
                minimumPt
              , MIN(desiredWidth, MIN(kROAttributionWidth, maximumWidth)));
        views.attribution.font = [UIFont boldSystemFontOfSize:
                MAX(1, badgeHeight * kROAttributionTextHeightRatio)];
        views.attribution.ro_layoutWidth = attributionWidth;
        views.attribution.ro_layoutHeight = badgeHeight;
        views.attribution.ro_layoutGravity = HBGravityTop | HBGravityLeft;
        // The badges sit on the root, outside the outer column, so they
        // carry the corner clearance themselves - on every template, the
        // scrim one included: only the picture runs under the curve.
        views.attribution.ro_layoutMargins =
                UIEdgeInsetsMake(edgeInset, edgeInset, 0, 0);
    }
    if (views.adChoicesReserve != nil) {
        CGFloat reserveWidth = MAX(
                [self ro_ptFromPx:kROAdChoicesMinWidthPx]
              , badgeHeight);
        CGFloat maximumWidth = MAX(minimumPt, plan.width);
        views.adChoicesReserve.ro_layoutWidth = MIN(reserveWidth, maximumWidth);
        views.adChoicesReserve.ro_layoutHeight = badgeHeight;
        views.adChoicesReserve.ro_layoutGravity = HBGravityTop | HBGravityRight;
        views.adChoicesReserve.ro_layoutMargins =
                UIEdgeInsetsMake(edgeInset, 0, 0, edgeInset);
    }
}

// Where every corner mark sits: the attribution, the AdChoices reserve and -
// through insetSdkAdChoicesInNativeAdView: - the SDK's own AdChoices
// container.
- (CGFloat)ro_badgeEdgeInset {
    return _cornerInset;
}

// ---------------------------------------------------------------------------
// The SDK's AdChoices container
//
// The AdChoices mark is the SDK's, drawn in a container it adds to
// GADNativeAdView itself and pins to that view's top and right EDGES with
// two constraints of constant 0 - the cell's raw corner, which the
// presentation's rounded clip cuts into. Android moves the same mark by
// padding its NativeAdView, because there the SDK's layer fills the padded
// area. Here the SDK pins its container to the edges, which no margin or
// padding reaches, so the two pins themselves are moved in by the badge
// inset: the container keeps its own size, only its corner moves.
//
// Everything this rests on is the SDK's private layout, measured on device
// with Google-Mobile-Ads-SDK 13.9.0 (2026-09-21), not promised by any API:
// the container's class name and the shape of the two pins. So nothing is
// forced. Anything unexpected is left alone - the mark stays where the SDK
// put it, which is how every cell looked before - and reported once per run
// with what was found instead, so an SDK upgrade tells us how to follow it.
// ---------------------------------------------------------------------------

static NSString *const kROSdkAdChoicesContainerClass =
        @"GADNativeAdAttributionView";
static char kROSdkAdChoicesInsetAppliedKey;

// The SDK's version, for the log. Read through the runtime rather than a
// named SDK function: this file only compiles on a Mac, and a guessed name
// would break the build for the sake of a log line.
//
// No @try: this file is compiled without -fobjc-exceptions (its .meta sets
// no flags), and a @try here broke the first Mac build. Nothing below can
// throw anyway - the getter is checked for before KVC asks for it, and the
// buffer handed to getValue:size: is checked to match the boxed size.
static NSString *ROGoogleMobileAdsVersion(void) {
    GADMobileAds *mobileAds = [GADMobileAds sharedInstance];
    if (![mobileAds respondsToSelector:
                    NSSelectorFromString(@"versionNumber")]) {
        return @"unknown";
    }
    id value = [mobileAds valueForKey:@"versionNumber"];
    if (![value isKindOfClass:NSValue.class]) return @"unknown";

    NSValue *boxed = (NSValue *)value;
    NSUInteger size = 0;
    NSGetSizeAndAlignment(boxed.objCType, &size, NULL);
    if (size != sizeof(NSInteger) * 3) return @"unknown";

    NSInteger parts[3] = {0, 0, 0};
    [boxed getValue:parts size:sizeof(parts)];
    return [NSString stringWithFormat:@"%ld.%ld.%ld"
          , (long)parts[0]
          , (long)parts[1]
          , (long)parts[2]];
}

static NSString *ROClassNames(NSArray<UIView *> *views) {
    NSMutableArray<NSString *> *names =
            [NSMutableArray arrayWithCapacity:views.count];
    for (UIView *view in views) {
        [names addObject:NSStringFromClass(view.class)];
    }
    return [names componentsJoinedByString:@", "];
}

- (void)insetSdkAdChoicesInNativeAdView:(GADNativeAdView *)nativeAdView
                              logResult:(BOOL)logResult {
    CGFloat inset = [self ro_badgeEdgeInset];
    if (nativeAdView == nil || inset <= 0) return;

    Class containerClass = NSClassFromString(kROSdkAdChoicesContainerClass);
    UIView *container = nil;
    if (containerClass != Nil) {
        for (UIView *subview in nativeAdView.subviews) {
            if ([subview isKindOfClass:containerClass]) {
                container = subview;
                break;
            }
        }
    }
    if (container == nil) {
        if (logResult) {
            [self ro_reportSdkAdChoicesInset:@"SKIPPED"
                                     message:[NSString stringWithFormat:
                    @"no %@ among the GADNativeAdView's subviews (%@)"
                  , kROSdkAdChoicesContainerClass
                  , ROClassNames(nativeAdView.subviews)]];
        }
        return;
    }

    NSLayoutConstraint *topPin = nil;
    NSLayoutConstraint *rightPin = nil;
    CGFloat topTarget = 0;
    CGFloat rightTarget = 0;
    NSMutableArray<NSString *> *mentions = [NSMutableArray array];
    for (NSLayoutConstraint *constraint in nativeAdView.constraints) {
        BOOL containerFirst = constraint.firstItem == container
                && constraint.secondItem == nativeAdView;
        BOOL containerSecond = constraint.firstItem == nativeAdView
                && constraint.secondItem == container;
        if (!containerFirst && !containerSecond) continue;

        [mentions addObject:constraint.description];
        if (constraint.relation != NSLayoutRelationEqual
                || constraint.multiplier != 1
                || constraint.firstAttribute != constraint.secondAttribute) {
            continue;
        }
        // container.edge == view.edge + k, or the same written the other
        // way round, which flips the sign that moves the container inward.
        CGFloat inward = containerFirst ? inset : -inset;
        switch (constraint.firstAttribute) {
            case NSLayoutAttributeTop:
                topPin = constraint;
                topTarget = inward;
                break;
            case NSLayoutAttributeRight:
            case NSLayoutAttributeTrailing:
                rightPin = constraint;
                rightTarget = -inward;
                break;
            default:
                break;
        }
    }
    if (topPin == nil || rightPin == nil) {
        if (logResult) {
            [self ro_reportSdkAdChoicesInset:@"SKIPPED"
                                     message:[NSString stringWithFormat:
                    @"%@ is not pinned by top == top and right == right to "
                     "the GADNativeAdView; constraints naming it: [%@]"
                  , kROSdkAdChoicesContainerClass
                  , [mentions componentsJoinedByString:@"; "]]];
        }
        return;
    }
    BOOL topKnown = topPin.constant == 0 || topPin.constant == topTarget;
    BOOL rightKnown = rightPin.constant == 0 || rightPin.constant == rightTarget;
    if (!topKnown || !rightKnown) {
        if (logResult) {
            [self ro_reportSdkAdChoicesInset:@"SKIPPED"
                                     message:[NSString stringWithFormat:
                    @"the pins carry constants this code did not set: "
                     "%@; %@"
                  , topPin.description
                  , rightPin.description]];
        }
        return;
    }

    BOOL appliedBefore = [objc_getAssociatedObject(
            nativeAdView, &kROSdkAdChoicesInsetAppliedKey) boolValue];
    BOOL reset = appliedBefore
            && (topPin.constant == 0 || rightPin.constant == 0);
    // Only when they differ: every layout pass comes through here, and a
    // pass where nothing moved has no business touching the layout engine.
    if (topPin.constant != topTarget) topPin.constant = topTarget;
    if (rightPin.constant != rightTarget) rightPin.constant = rightTarget;
    objc_setAssociatedObject(
            nativeAdView, &kROSdkAdChoicesInsetAppliedKey, @YES
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    // Said whatever the pass: the SDK taking its pins back is news on its
    // own, and the next pass only puts them back again.
    if (reset) {
        [self ro_reportSdkAdChoicesInset:@"RESET"
                                 message:@"the SDK put its pins back to 0 "
                                          "after they were moved; moved "
                                          "them again"];
    }
    if (logResult) {
        [self ro_reportSdkAdChoicesInset:@"applied"
                                 message:[NSString stringWithFormat:
                @"SDK container inset by %gpt", inset]];
    }
}

// Once per run for each outcome: the same SDK builds every ad the same way,
// so the first report says everything the rest would.
- (void)ro_reportSdkAdChoicesInset:(NSString *)outcome
                           message:(NSString *)message {
    static NSMutableSet<NSString *> *reported;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        reported = [NSMutableSet set];
    });
    if ([reported containsObject:outcome]) return;

    [reported addObject:outcome];
    NSLog(@"%@: In-feed AdChoices inset %@ (GMA %@): %@%@"
          , kROTag
          , outcome
          , ROGoogleMobileAdsVersion()
          , message
          , [outcome isEqualToString:@"applied"]
                    ? @""
                    : @" - update insetSdkAdChoicesInNativeAdView: for this SDK");
}

// ---------------------------------------------------------------------------
// Template pieces
// ---------------------------------------------------------------------------

- (HBLinearLayoutView *)ro_buildIdentityAndText:(ROInFeedAssetViews *)views
                                           plan:(ROInFeedLayoutPlan *)plan {
    return [self ro_buildIdentityAndText:views
                                    plan:plan
                    includeCallToAction:YES];
}

// The band the media leaves unused below a side layout. Anything that fits
// there is better off spanning the panel than squeezed into the column
// beside the picture.
- (CGFloat)ro_sideLayoutLeftoverHeightForPlan:(ROInFeedLayoutPlan *)plan {
    return MAX(0, plan.height - plan.mediaHeight);
}

- (HBLinearLayoutView *)ro_buildIdentityAndText:(ROInFeedAssetViews *)views
                                           plan:(ROInFeedLayoutPlan *)plan
                            includeCallToAction:(BOOL)includeCallToAction {
    CGFloat gap = [self gapForTier:plan.tier];
    HBLinearLayoutView *content = [[HBLinearLayoutView alloc] init];
    content.ro_vertical = YES;
    content.ro_gravity = HBGravityTop;

    // The same floor the scrim template applies: the icon only shares the
    // headline's line while the line keeps a usable text width. In a narrow
    // column beside the media it stands alone instead, and the texts follow
    // below at the column's full width.
    CGFloat textColumnWidth = MAX(0, plan.width - plan.mediaWidth - gap);
    BOOL iconStandsAlone = plan.showIcon
            && [self hasRenderableIcon]
            && textColumnWidth
                        - [self ro_iconSizeForTier:plan.tier]
                        - gap
                    < kROInFeedMinIconRowTextWidth;
    if (iconStandsAlone) {
        [self ro_addIconTo:content views:views tier:plan.tier gap:0];
        if (views.icon != nil) {
            views.icon.ro_layoutGravity = HBGravityLeft;
            views.icon.ro_layoutMargins = UIEdgeInsetsMake(0, 0, gap, 0);
        }
    }

    HBLinearLayoutView *identity = [[HBLinearLayoutView alloc] init];
    identity.ro_vertical = NO;
    identity.ro_gravity = HBGravityCenterVertical;
    if (plan.showIcon && !iconStandsAlone) {
        [self ro_addIconTo:identity views:views tier:plan.tier gap:gap];
    }

    views.headline = [self ro_createTextWithValue:_nativeAd.headline
                                             size:[self ro_headlineSizeForPlan:plan]
                                             bold:YES];
    [self ro_applyTextMode:plan.marqueeHeadline
                     label:views.headline
                  maxLines:[self ro_headlineMaxLinesForPlan:plan]];
    HBFrameLayoutView *headlineSlot = [[HBFrameLayoutView alloc] init];
    [headlineSlot addSubview:views.headline];
    headlineSlot.ro_layoutWidth = 0;
    headlineSlot.ro_layoutWeight = 1;
    [identity addSubview:headlineSlot];
    identity.ro_layoutWidth = ROLayoutMatchParent;
    [content addSubview:identity];

    [self ro_addBodyAndOptionalTo:content views:views plan:plan];

    UIView *spacer = [[UIView alloc] init];
    spacer.ro_layoutWidth = ROLayoutMatchParent;
    spacer.ro_layoutHeight = 0;
    spacer.ro_layoutWeight = 1;
    [content addSubview:spacer];

    if (includeCallToAction) {
        [self ro_addCallToActionTo:content
                             views:views
                              plan:plan
                               gap:gap
                         fullWidth:YES];
    }
    if (iconStandsAlone) ROInFeedCentreBlock(views);
    return content;
}

- (HBLinearLayoutView *)ro_buildHeadlineAndActionStack:(ROInFeedAssetViews *)views
                                                  plan:(ROInFeedLayoutPlan *)plan {
    CGFloat gap = [self gapForTier:plan.tier];
    HBLinearLayoutView *content = [[HBLinearLayoutView alloc] init];
    content.ro_vertical = YES;
    content.ro_gravity = HBGravityTop;

    UIView *filler = [self ro_createIconFiller:views plan:plan];
    // Standing in for the picture, the icon takes the picture's place at the
    // top; with nothing to put there the weighted band goes below the text.
    BOOL fillerCarriesIcon = views.icon != nil;
    // A badge may sit over the icon, never over text. When the icon holds the
    // top band the badge reserve is dropped and the band starts at the slot
    // edge, the same way a media layout starts with its picture. The band's
    // base height keeps it at least badge-tall, so the headline below it
    // never rises under the badges however tight the slot gets.
    views.insetContentAvoidsBadges = !fillerCarriesIcon;
    if (fillerCarriesIcon) {
        filler.ro_layoutWidth = ROLayoutMatchParent;
        filler.ro_layoutHeight = [self ro_badgeHeightForPlan:plan];
        filler.ro_layoutWeight = 1;
        filler.ro_layoutMargins = UIEdgeInsetsMake(0, 0, gap, 0);
        [content addSubview:filler];
    }

    // With neither media nor icon above it, the text takes the whole
    // remaining band and centers itself rather than clinging to the top.
    HBLinearLayoutView *textParent = content;
    if (!fillerCarriesIcon) {
        textParent = [[HBLinearLayoutView alloc] init];
        textParent.ro_vertical = YES;
        textParent.ro_gravity = HBGravityCenterVertical;
    }

    views.headline = [self ro_createTextWithValue:_nativeAd.headline
                                             size:[self ro_headlineSizeForPlan:plan]
                                             bold:YES];
    [self ro_applyTextMode:plan.marqueeHeadline
                     label:views.headline
                  maxLines:[self ro_headlineMaxLinesForPlan:plan]];
    views.headline.ro_layoutWidth = ROLayoutMatchParent;
    [textParent addSubview:views.headline];
    [self ro_addBodyAndOptionalTo:textParent views:views plan:plan];

    if (!fillerCarriesIcon) {
        textParent.ro_layoutWidth = ROLayoutMatchParent;
        textParent.ro_layoutHeight = 0;
        textParent.ro_layoutWeight = 1;
        [content addSubview:textParent];
    }

    HBLinearLayoutView *actionRow = [[HBLinearLayoutView alloc] init];
    actionRow.ro_vertical = NO;
    actionRow.ro_gravity = HBGravityCenterVertical;
    if (views.icon == nil && plan.showIcon) {
        // The icon rides the button's row, so it takes the button's height -
        // a tier-sized icon would stretch the whole row and spend height the
        // media above it needs more.
        [self ro_addSizedIconTo:actionRow
                          views:views
                           size:MIN(
                                [self ro_iconSizeForTier:plan.tier]
                              , [self callToActionHeightForPlan:plan])
                            gap:gap];
    }
    [self ro_addCallToActionTo:actionRow
                         views:views
                          plan:plan
                           gap:gap
                     fullWidth:NO];
    if (views.callToAction != nil) {
        views.callToAction.ro_layoutWidth = 0;
        views.callToAction.ro_layoutWeight = 1;
        UIEdgeInsets margins = views.callToAction.ro_layoutMargins;
        margins.left = 0;
        views.callToAction.ro_layoutMargins = margins;
    }
    // With an icon beside it, the icon already fixes this row's height; a
    // shorter button saves nothing back and only breaks the row's line.
    if (views.callToAction != nil
            && views.icon != nil
            && views.icon.superview == actionRow) {
        CGFloat rowHeight = MIN(
                [self ro_iconSizeForTier:plan.tier]
              , [self callToActionHeightForPlan:plan]);
        views.callToAction.ro_minimumSize = CGSizeMake(0, rowHeight);
        [(ROInFeedCallToActionButton *)views.callToAction
                ro_applyLabelSizeForHeight:rowHeight];
    }

    actionRow.ro_layoutWidth = ROLayoutMatchParent;
    actionRow.ro_layoutMargins = UIEdgeInsetsMake(gap, 0, 0, 0);
    [content addSubview:actionRow];
    // The filler icon occupies the picture's band above the text, so the
    // text centers under it exactly as it does beside a side media.
    if (fillerCarriesIcon) ROInFeedCentreBlock(views);
    return content;
}

- (HBLinearLayoutView *)ro_buildTextStack:(ROInFeedAssetViews *)views
                                     plan:(ROInFeedLayoutPlan *)plan {
    HBLinearLayoutView *texts = [[HBLinearLayoutView alloc] init];
    texts.ro_vertical = YES;
    texts.ro_gravity = HBGravityCenterVertical;

    views.headline = [self ro_createTextWithValue:_nativeAd.headline
                                             size:[self ro_headlineSizeForPlan:plan]
                                             bold:YES];
    [self ro_applyTextMode:plan.marqueeHeadline
                     label:views.headline
                  maxLines:[self ro_headlineMaxLinesForPlan:plan]];
    [texts addSubview:views.headline];
    [self ro_addBodyAndOptionalTo:texts views:views plan:plan];
    return texts;
}

- (void)ro_addBodyAndOptionalTo:(HBLinearLayoutView *)parent
                          views:(ROInFeedAssetViews *)views
                           plan:(ROInFeedLayoutPlan *)plan {
    NSString *bodyValue = _nativeAd.body;
    if (plan.showBody && bodyValue.length > 0) {
        views.body = [self ro_createTextWithValue:bodyValue
                                             size:[self ro_bodySizeForPlan:plan]
                                             bold:NO];
        [self ro_applyTextMode:plan.marqueeSecondary
                         label:views.body
                      maxLines:[self ro_bodyMaxLinesForPlan:plan]];
        [parent addSubview:views.body];
    }

    if (plan.showAdvertiser) {
        views.advertiser = [self ro_createTextWithValue:_nativeAd.advertiser
                                                   size:[self ro_optionalSizeForPlan:plan]
                                                   bold:NO];
        views.advertiser.textColor = ROInFeedArgb(0xCCFFFFFF);
        [self ro_applyTextMode:plan.marqueeSecondary
                         label:views.advertiser
                      maxLines:[self ro_advertiserMaxLinesForPlan:plan]];
        [parent addSubview:views.advertiser];
    }

    double starRating = [self resolveStarRating];
    if (plan.showRating && starRating > 0) {
        views.rating = [self ro_createTextWithValue:
                        [NSString stringWithFormat:@"★ %.1f", starRating]
                                               size:[self ro_optionalSizeForPlan:plan]
                                               bold:NO];
        views.rating.textColor = ROInFeedArgb(0xFFFFC107);
        [parent addSubview:views.rating];
    }
}

// A media-free layout leaves a hole where the picture would have been; the
// icon is the one image asset such a creative still carries and takes it.
- (UIView *)ro_createIconFiller:(ROInFeedAssetViews *)views
                           plan:(ROInFeedLayoutPlan *)plan {
    if (plan.showMedia || !plan.showIcon || ![self hasRenderableIcon]) {
        return [[UIView alloc] init];
    }

    ROInFeedIconFillerView *holder = [[ROInFeedIconFillerView alloc] init];
    holder.reservedHeight = kROMinFillerIcon;
    UIImageView *icon = [[UIImageView alloc] init];
    icon.contentMode = UIViewContentModeScaleAspectFit;
    icon.image = _nativeAd.icon.image;
    icon.ro_layoutWidth = ROLayoutMatchParent;
    icon.ro_layoutHeight = ROLayoutMatchParent;
    [holder addSubview:icon];
    views.icon = icon;
    return holder;
}

- (void)ro_addIconTo:(HBLinearLayoutView *)parent
               views:(ROInFeedAssetViews *)views
                tier:(ROInFeedTier)tier
                 gap:(CGFloat)gap {
    [self ro_addSizedIconTo:parent
                      views:views
                       size:[self ro_iconSizeForTier:tier]
                        gap:gap];
}

- (void)ro_addSizedIconTo:(HBLinearLayoutView *)parent
                    views:(ROInFeedAssetViews *)views
                     size:(CGFloat)size
                      gap:(CGFloat)gap {
    if (![self hasRenderableIcon]) return;

    UIImageView *icon = [[UIImageView alloc] init];
    UIImage *iconImage = _nativeAd.icon.image;
    icon.image = iconImage;
    // CENTER_INSIDE on the Java side: shrink to fit, never enlarge. UIKit
    // has no single mode for that, so the choice is made from the asset -
    // an icon already inside the box is drawn at its own size rather than
    // blown up soft to fill it. The filler icon keeps ScaleAspectFit,
    // matching FIT_CENTER over there.
    icon.contentMode =
            iconImage != nil
                    && iconImage.size.width <= size
                    && iconImage.size.height <= size
                    ? UIViewContentModeCenter
                    : UIViewContentModeScaleAspectFit;
    icon.ro_layoutWidth = size;
    icon.ro_layoutHeight = size;
    icon.ro_layoutMargins = UIEdgeInsetsMake(0, 0, 0, gap);
    [parent addSubview:icon];
    views.icon = icon;
}

- (void)ro_addCallToActionTo:(HBLinearLayoutView *)parent
                       views:(ROInFeedAssetViews *)views
                        plan:(ROInFeedLayoutPlan *)plan
                         gap:(CGFloat)gap
                   fullWidth:(BOOL)fullWidth {
    NSString *callToActionValue = _nativeAd.callToAction;
    if (callToActionValue.length == 0) return;

    ROInFeedCallToActionButton *callToAction =
            [ROInFeedCallToActionButton buttonWithType:UIButtonTypeCustom];
    [callToAction setTitle:callToActionValue forState:UIControlStateNormal];
    [callToAction setTitleColor:UIColor.whiteColor
                       forState:UIControlStateNormal];
    callToAction.titleLabel.font = [UIFont boldSystemFontOfSize:14];
    callToAction.titleLabel.numberOfLines = 0;
    callToAction.titleLabel.textAlignment = NSTextAlignmentCenter;
    callToAction.titleLabel.lineBreakMode = NSLineBreakByTruncatingTail;
    callToAction.backgroundColor = ROInFeedArgb(0xFF2196F3);
    callToAction.layer.borderColor = ROInFeedArgb(0xFF1565C0).CGColor;
    callToAction.layer.borderWidth = 1;
    CGFloat callToActionHeight = [self callToActionHeightForPlan:plan];
    callToAction.ro_minimumSize = CGSizeMake(0, callToActionHeight);
    [callToAction ro_applyLabelSizeForHeight:callToActionHeight];
    CGFloat verticalPadding = [self ro_ctaVerticalPaddingForTier:plan.tier];
    CGFloat horizontalPadding =
            [self ro_ctaHorizontalPaddingForTier:plan.tier];
    callToAction.contentEdgeInsets = UIEdgeInsetsMake(
            verticalPadding
          , horizontalPadding
          , verticalPadding
          , horizontalPadding);

    if (fullWidth) {
        callToAction.ro_layoutWidth = ROLayoutMatchParent;
        callToAction.ro_layoutMargins = UIEdgeInsetsMake(gap, 0, 0, 0);
    } else {
        callToAction.ro_layoutWidth = ROLayoutWrapContent;
        callToAction.ro_layoutMargins = UIEdgeInsetsMake(0, gap, 0, 0);
    }
    [parent addSubview:callToAction];
    views.callToAction = callToAction;
}

- (UIView *)ro_createMediaViewForPlan:(ROInFeedLayoutPlan *)plan
                                views:(ROInFeedAssetViews *)views
                                probe:(BOOL)probe
                            mainImage:(UIImage *)mainImage {
    // The primary asset is always a GADMediaView; drawing pixels through an
    // image view is fine, registering one is what stops the fill.
    ROInFeedMediaView *mediaView = [[ROInFeedMediaView alloc] init];
    BOOL backgroundTemplate =
            plan.layoutTemplate == ROInFeedTemplateMediaBackground;
    // The picture is always shown whole. A band media sits on a
    // deliberately black ground - when a creative reports one ratio but
    // renders less inside it, the black exposes the shortfall. The
    // background template is transparent instead: its ground is the
    // ambient backdrop standing behind the MediaView.
    mediaView.backgroundColor = backgroundTemplate
            ? UIColor.clearColor
            : UIColor.blackColor;
    mediaView.clipsToBounds = YES;
    mediaView.contentMode = UIViewContentModeScaleAspectFit;
    views.media = mediaView;
    views.mediaSlot = mediaView;
    if (probe) return mediaView;

    if (plan.renderVideo) {
        GADMediaContent *mediaContent = _nativeAd.mediaContent;
        if (mediaContent == nil || !mediaContent.hasVideoContent) {
            [NSException raise:@"ROInFeedBindException"
                        format:@"MediaContent is required for a selected "
                                "media layout"];
        }
        mediaView.mediaContent = mediaContent;
        return mediaView;
    }

    // Still fallback drawn inside the MediaView, so a video creative can
    // never auto-play in a slot below the video minimum.
    if (mainImage == nil) {
        [NSException raise:@"ROInFeedBindException"
                    format:@"Main image is required for an image fallback "
                            "layout"];
    }
    UIImageView *fallbackImageView =
            [[UIImageView alloc] initWithImage:mainImage];
    fallbackImageView.contentMode = UIViewContentModeScaleAspectFit;
    fallbackImageView.clipsToBounds = YES;
    [mediaView addSubview:fallbackImageView];
    mediaView.fallbackImageView = fallbackImageView;
    return mediaView;
}

- (void)ro_buildVideoMediaLeftContent:(HBLinearLayoutView *)outer
                            mediaView:(UIView *)mediaView
                                views:(ROInFeedAssetViews *)views
                                 plan:(ROInFeedLayoutPlan *)plan {
    CGFloat gap = [self gapForTier:plan.tier];
    HBLinearLayoutView *row = [[HBLinearLayoutView alloc] init];
    row.ro_vertical = NO;
    row.ro_gravity = HBGravityTop;

    mediaView.ro_layoutWidth = plan.mediaWidth;
    mediaView.ro_layoutHeight = plan.mediaHeight;
    mediaView.ro_layoutMargins = UIEdgeInsetsMake(0, 0, 0, gap);
    [row addSubview:mediaView];

    HBLinearLayoutView *rail = [self ro_buildVideoSideRail:views plan:plan];
    views.videoRail = rail;
    views.insetContent = rail;
    views.insetContentAvoidsBadges = YES;
    rail.ro_layoutWidth = 0;
    rail.ro_layoutHeight = plan.mediaHeight;
    rail.ro_layoutWeight = 1;
    [row addSubview:rail];
    row.ro_layoutWidth = ROLayoutMatchParent;
    [outer addSubview:row];

    views.headline = [self ro_createTextWithValue:_nativeAd.headline
                                             size:[self ro_headlineSizeForPlan:plan]
                                             bold:YES];
    // One line by design, so it marquees rather than being cut.
    [self ro_applyTextMode:NO label:views.headline maxLines:1];
    views.headline.ro_padding = UIEdgeInsetsMake(
            kROVideoHeadlineVerticalPadding
          , kROVideoHeadlineHorizontalPadding
          , kROVideoHeadlineVerticalPadding
          , kROVideoHeadlineHorizontalPadding);
    views.headline.ro_layoutWidth = ROLayoutMatchParent;
    views.headline.ro_layoutMargins = UIEdgeInsetsMake(gap, 0, 0, 0);
    [outer addSubview:views.headline];
}

- (HBLinearLayoutView *)ro_buildVideoSideRail:(ROInFeedAssetViews *)views
                                         plan:(ROInFeedLayoutPlan *)plan {
    CGFloat gap = [self gapForTier:plan.tier];
    HBLinearLayoutView *rail = [[HBLinearLayoutView alloc] init];
    rail.ro_vertical = YES;
    rail.ro_gravity = HBGravityCenterHorizontal;

    HBLinearLayoutView *iconRow = [[HBLinearLayoutView alloc] init];
    iconRow.ro_vertical = NO;
    iconRow.ro_gravity = HBGravityCenterHorizontal | HBGravityCenterVertical;
    [self ro_addSizedIconTo:iconRow
                      views:views
                       size:[self ro_videoRailIconSizeForTier:plan.tier]
                        gap:0];
    if (views.icon != nil) {
        iconRow.ro_layoutWidth = ROLayoutMatchParent;
        [rail addSubview:iconRow];
    }

    UIView *spacer = [[UIView alloc] init];
    spacer.ro_layoutWidth = ROLayoutMatchParent;
    spacer.ro_layoutHeight = 0;
    spacer.ro_layoutWeight = 1;
    [rail addSubview:spacer];

    [self ro_addCallToActionTo:rail views:views plan:plan gap:gap fullWidth:YES];
    if (views.callToAction != nil) {
        views.callToAction.titleLabel.numberOfLines = 2;
    }
    return rail;
}

- (HBLinearLayoutView *)ro_buildVideoFooterRow:(ROInFeedAssetViews *)views
                                          plan:(ROInFeedLayoutPlan *)plan {
    CGFloat gap = [self gapForTier:plan.tier];
    HBLinearLayoutView *footer = [[HBLinearLayoutView alloc] init];
    footer.ro_vertical = NO;
    footer.ro_gravity = HBGravityCenterVertical;

    views.headline = [self ro_createTextWithValue:_nativeAd.headline
                                             size:[self ro_headlineSizeForPlan:plan]
                                             bold:YES];
    // One line by design, so it marquees rather than clipping.
    [self ro_applyTextMode:NO label:views.headline maxLines:1];
    views.headline.ro_layoutWidth = 0;
    views.headline.ro_layoutWeight = 1;
    [footer addSubview:views.headline];

    [self ro_addCallToActionTo:footer views:views plan:plan gap:gap fullWidth:NO];
    if (views.callToAction != nil) {
        views.callToAction.titleLabel.numberOfLines = 1;
    }
    return footer;
}

// Two modes, no middle ground - and per text: wrapped shows the value whole
// over its allowed lines or the candidate is rejected; marquee is one line
// that scrolls. A single-line grant marquees regardless.
// One allowed line is not a license to scroll: a single-line text that fits -
// or can be nudged to fit - is shown still and whole, and only the ladder's
// scrolling rungs may move it.
- (void)ro_applyTextMode:(BOOL)marquee
                   label:(ROAdTextLabel *)label
                maxLines:(NSInteger)maxLines {
    if (label == nil) return;
    label.maxLines = MAX(1, maxLines);
    label.marquee = marquee;
}

- (ROAdTextLabel *)ro_createTextWithValue:(NSString *)value
                                     size:(CGFloat)size
                                     bold:(BOOL)bold {
    ROAdTextLabel *text = [[ROAdTextLabel alloc] init];
    text.text = value ?: @"";
    text.textColor = UIColor.whiteColor;
    text.font = bold
            ? [UIFont boldSystemFontOfSize:size]
            : [UIFont systemFontOfSize:size];
    // An asset the creative never sent is not a blank line to reserve: it
    // leaves no trace at all.
    NSString *trimmed = [text.text
            stringByTrimmingCharactersInSet:
                    NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (trimmed.length == 0) {
        text.ro_gone = YES;
        text.hidden = YES;
    }
    return text;
}

@end
