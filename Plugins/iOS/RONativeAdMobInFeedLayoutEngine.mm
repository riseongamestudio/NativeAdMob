#import "RONativeAdMobInFeedLayoutEngine.h"

static NSString *const kROTag = @"InFeed";

static const CGFloat kROMinNativeAdSize = 32; // dp read as pt
static const NSInteger kROMediaExpansionIterations = 14;
static const NSInteger kROMaxExpandedMediaPlans = 8;
static const CGFloat kROUnknownVideoAspectRatio = 1;
static const CGFloat kRODefaultImageAspectRatio = 1.91f;
static const CGFloat kROMinMediaAspectRatio = 0.2f;
static const CGFloat kROMaxMediaAspectRatio = 5.0f;
static const CGFloat kROMinVisibleTextSlotPx = 1;

typedef NS_ENUM(NSInteger, HBTextMode) {
    HBTextModeWrapped = 0
  , HBTextModeSecondaryMarquee
  , HBTextModeAllMarquee
};

// Text is worth more still than moving, and worth more big than small - in
// that order only while it stays readable. Whole text gets the first rungs
// down to 0.7x; past that, one secondary line scrolling at a larger size
// beats dragging the entire group into illegibility, so the scrolling rungs
// interleave from there - still always below a size wrapping has already
// failed at, with the headline joining the scroll last of all.
static const HBTextMode kROTextLadderModes[] = {
    HBTextModeWrapped
  , HBTextModeWrapped
  , HBTextModeWrapped
  , HBTextModeSecondaryMarquee
  , HBTextModeSecondaryMarquee
  , HBTextModeWrapped
  , HBTextModeSecondaryMarquee
  , HBTextModeAllMarquee
  , HBTextModeAllMarquee
  , HBTextModeAllMarquee
};
static const CGFloat kROTextLadderScales[] = {
    1, 0.85, 0.7, 0.8, 0.65, 0.55, 0.5, 0.8, 0.65, 0.5
};
static const NSInteger kROTextLadderRungCount =
        sizeof(kROTextLadderScales) / sizeof(kROTextLadderScales[0]);

CGFloat HBClamp(CGFloat value, CGFloat minimum, CGFloat maximum) {
    return MAX(minimum, MIN(maximum, value));
}

@implementation ROInFeedLayoutPlan

- (instancetype)init {
    self = [super init];
    if (self != nil) _textScale = 1;
    return self;
}

@end

NSString *ROInFeedTemplateName(ROInFeedTemplate layoutTemplate) {
    if (layoutTemplate == ROInFeedTemplateCompactRow) return @"COMPACT_ROW";
    if (layoutTemplate == ROInFeedTemplateCompactColumn) {
        return @"COMPACT_COLUMN";
    }
    if (layoutTemplate == ROInFeedTemplateMediaLeft) return @"MEDIA_LEFT";
    if (layoutTemplate == ROInFeedTemplateMediaTop) return @"MEDIA_TOP";
    if (layoutTemplate == ROInFeedTemplateMediaBackground) {
        return @"MEDIA_BACKGROUND";
    }
    return @"MEDIA_RIGHT";
}

NSString *ROInFeedTierName(ROInFeedTier tier) {
    if (tier == ROInFeedTierCompact) return @"COMPACT";
    if (tier == ROInFeedTierRegular) return @"REGULAR";
    return @"ROOMY";
}

NSString *ROInFeedMediaName(ROInFeedLayoutPlan *plan) {
    if (!plan.showMedia) return @"NONE";
    return plan.renderVideo ? @"VIDEO" : @"IMAGE";
}

NSString *ROInFeedDescribePlan(ROInFeedLayoutPlan *plan) {
    if (plan == nil) return @"none";
    return [NSString stringWithFormat:
            @"%@/%@/%@/icon=%@/body=%@/advertiser=%@/rating=%@/textx%g/marq=%@"
          , ROInFeedTemplateName(plan.layoutTemplate)
          , ROInFeedTierName(plan.tier)
          , ROInFeedMediaName(plan)
          , plan.showIcon ? @"true" : @"false"
          , plan.showBody ? @"true" : @"false"
          , plan.showAdvertiser ? @"true" : @"false"
          , plan.showRating ? @"true" : @"false"
          , plan.textScale
          , plan.marqueeHeadline
                    ? @"all"
                    : (plan.marqueeSecondary ? @"sec" : @"none")];
}

// The probe cache entry: text scale and marquee mode are not part of the
// key - configureProbeLayout re-applies them so one tree serves the whole
// ladder.
@interface HBCachedProbeLayout : NSObject
@property (nonatomic, strong) ROInFeedProbeLayout *probe;
@property (nonatomic) BOOL showBody;
@property (nonatomic) BOOL showAdvertiser;
@property (nonatomic) BOOL showRating;
@property (nonatomic) BOOL showIcon;
@end

@implementation HBCachedProbeLayout
@end

@implementation RONativeAdMobInFeedLayoutEngine {
    GADNativeAd *_nativeAd;
    CGFloat _requestedX;
    CGFloat _requestedY;
    CGFloat _requestedWidth;
    CGFloat _requestedHeight;
    RONativeAdMobInFeedViewFactory *_viewFactory;
    RONativeAdMobInFeedLayoutValidator *_validator;
    NSMutableArray<HBCachedProbeLayout *> *_probeLayouts;
    NSMutableDictionary<NSNumber *, NSString *> *_rejectionReasons;
    CGFloat _screenScale;
}

- (instancetype)initWithNativeAd:(GADNativeAd *)nativeAd
                      requestedX:(CGFloat)requestedX
                      requestedY:(CGFloat)requestedY
                  requestedWidth:(CGFloat)requestedWidth
                 requestedHeight:(CGFloat)requestedHeight
                     viewFactory:(RONativeAdMobInFeedViewFactory *)viewFactory
                       validator:(RONativeAdMobInFeedLayoutValidator *)validator {
    self = [super init];
    if (self == nil) return nil;
    _nativeAd = nativeAd;
    _requestedX = requestedX;
    _requestedY = requestedY;
    _requestedWidth = requestedWidth;
    _requestedHeight = requestedHeight;
    _viewFactory = viewFactory;
    _validator = validator;
    _probeLayouts = [NSMutableArray array];
    _rejectionReasons = [NSMutableDictionary dictionary];
    _screenScale = MAX(1, UIScreen.mainScreen.nativeScale);
    return self;
}

- (void)setPositionX:(CGFloat)xPt y:(CGFloat)yPt {
    _requestedX = xPt;
    _requestedY = yPt;
}

- (void)clear {
    [_probeLayouts removeAllObjects];
}

// Keeps the first few distinct reasons per template rather than only the
// first: with one slot, every diagnosis of a slot that renders nothing was
// made from whichever variant happened to fail first, which repeatedly
// pointed at the wrong constraint.
static const NSInteger kROMaxRejectionReasonsPerTemplate = 3;

// Within one layout, whole always beats moving when nothing else is traded
// away: a text a hair too long for its lines is shrunk alone, in small steps
// down to a floor, before any rung is allowed to scroll it.
static const CGFloat kRONudgeTextStep = 0.92f;
static const CGFloat kRONudgeTextFloor = 0.7f;
static const NSInteger kRONudgeTextMaxSteps = 5;

- (void)ro_nudgeCutTextsWholeInProbe:(ROInFeedProbeLayout *)probeLayout
                                plan:(ROInFeedLayoutPlan *)plan
                               width:(CGFloat)width
                              height:(CGFloat)height {
    ROInFeedAssetViews *views = probeLayout.views;
    [self ro_nudgeTextWhole:plan.marqueeHeadline ? nil : views.headline
                    inProbe:probeLayout.root
                      width:width
                     height:height];
    [self ro_nudgeTextWhole:plan.marqueeSecondary ? nil : views.body
                    inProbe:probeLayout.root
                      width:width
                     height:height];
    [self ro_nudgeTextWhole:plan.marqueeSecondary ? nil : views.advertiser
                    inProbe:probeLayout.root
                      width:width
                     height:height];
}

- (void)ro_nudgeTextWhole:(ROAdTextLabel *)text
                  inProbe:(UIView *)probeRoot
                    width:(CGFloat)width
                   height:(CGFloat)height {
    if (text == nil || text.hidden) return;

    CGFloat floorSize = text.font.pointSize * kRONudgeTextFloor;
    for (NSInteger step = 0; step < kRONudgeTextMaxSteps; ++step) {
        CGFloat labelWidth = text.frame.size.width;
        if (labelWidth <= 0) labelWidth = text.ro_measuredSize.width;
        if ([text ro_showsEntireTextForWidth:labelWidth]) return;

        CGFloat next = text.font.pointSize * kRONudgeTextStep;
        if (next < floorSize) return;

        text.font = [text.font fontWithSize:next];
        [probeRoot ro_measureWithWidthSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, width)
                                 heightSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, height)];
        [probeRoot ro_layoutWithFrame:CGRectMake(0, 0, width, height)];
    }
}

- (void)ro_recordRejectionForTemplate:(ROInFeedTemplate)layoutTemplate
                                 tier:(ROInFeedTier)tier
                               reason:(NSString *)reason {
    NSNumber *key = @(layoutTemplate);
    if (reason == nil) return;

    NSString *entry = [NSString stringWithFormat:
            @"%@: %@", ROInFeedTierName(tier), reason];
    NSString *existing = _rejectionReasons[key];
    if (existing == nil) {
        _rejectionReasons[key] = entry;
        return;
    }
    if ([existing containsString:reason]) return;

    NSArray<NSString *> *kept =
            [existing componentsSeparatedByString:@" | "];
    if ((NSInteger)kept.count >= kROMaxRejectionReasonsPerTemplate) return;

    _rejectionReasons[key] =
            [existing stringByAppendingFormat:@" | %@", entry];
}

- (NSString *)describeLastRejections {
    NSMutableArray<NSString *> *parts = [NSMutableArray array];
    for (NSInteger layoutTemplate = ROInFeedTemplateCompactRow;
         layoutTemplate <= ROInFeedTemplateMediaRight;
         ++layoutTemplate) {
        NSString *reason = _rejectionReasons[@(layoutTemplate)];
        if (reason == nil) continue;
        [parts addObject:[NSString stringWithFormat:@"%@=%@"
              , ROInFeedTemplateName((ROInFeedTemplate)layoutTemplate)
              , reason]];
    }
    return [parts componentsJoinedByString:@"; "];
}

- (NSArray<ROInFeedLayoutPlan *> *)choosePlansForScreenWidth:(CGFloat)screenWidth
                                                screenHeight:(CGFloat)screenHeight
                                                    hasVideo:(BOOL)hasVideo
                                                hasMainImage:(BOOL)hasMainImage {
    [_rejectionReasons removeAllObjects];
    NSMutableArray<ROInFeedLayoutPlan *> *plans = [NSMutableArray array];
    if (_requestedWidth < kROMinNativeAdSize
            || _requestedHeight < kROMinNativeAdSize) {
        NSLog(@"%@: In-feed requested rect is below the %gdp minimum"
              , kROTag, kROMinNativeAdSize);
        return plans;
    }
    CGFloat desiredWidth = MIN(screenWidth, _requestedWidth);
    CGFloat desiredHeight = MIN(screenHeight, _requestedHeight);
    if (desiredWidth < kROMinNativeAdSize
            || desiredHeight < kROMinNativeAdSize) {
        NSLog(@"%@: In-feed content surface clamps requested rect below "
                "the %gdp minimum", kROTag, kROMinNativeAdSize);
        return plans;
    }

    NSMutableArray<ROInFeedLayoutPlan *> *generalVariants =
            [NSMutableArray array];
    NSMutableArray<ROInFeedLayoutPlan *> *videoVariants =
            [NSMutableArray array];
    // Media-free candidates a video creative drops down to when the rect
    // cannot host a policy-safe video.
    NSMutableArray<ROInFeedLayoutPlan *> *fallbackVariants =
            [NSMutableArray array];
    BOOL hasBody = _nativeAd.body.length > 0;
    BOOL hasAdvertiser = _nativeAd.advertiser.length > 0;
    BOOL hasRating = [_viewFactory hasValidStarRating];
    BOOL hasIcon = [_viewFactory hasRenderableIcon];
    CGFloat width = MAX(1, MIN(screenWidth, desiredWidth));
    for (NSInteger tier = ROInFeedTierCompact;
         tier <= ROInFeedTierRoomy;
         ++tier) {
        for (NSInteger optionalMask = 0; optionalMask < 16; ++optionalMask) {
            BOOL showBody = (optionalMask & 1) != 0;
            BOOL showAdvertiser = (optionalMask & 2) != 0;
            BOOL showRating = (optionalMask & 4) != 0;
            // The icon is optional exactly the way the texts are; it is the
            // widest of them, so dropping it is often what leaves the
            // headline its minimum.
            BOOL showIcon = (optionalMask & 8) != 0;
            if ((showBody && !hasBody)
                    || (showAdvertiser && !hasAdvertiser)
                    || (showRating && !hasRating)
                    || (showIcon && !hasIcon)) {
                continue;
            }

            if (hasVideo) {
                [self ro_updateVariantBest:videoVariants
                                 candidate:[self ro_evaluatePlanForTemplate:ROInFeedTemplateMediaLeft
                                        tier:(ROInFeedTier)tier
                                   showMedia:YES
                                 renderVideo:YES
                                    showBody:showBody
                              showAdvertiser:showAdvertiser
                                  showRating:showRating
                                    showIcon:showIcon
                              candidateWidth:width
                               desiredHeight:desiredHeight
                                 screenWidth:screenWidth
                                screenHeight:screenHeight]];
                [self ro_updateVariantBest:videoVariants
                                 candidate:[self ro_evaluatePlanForTemplate:ROInFeedTemplateMediaBackground
                                        tier:(ROInFeedTier)tier
                                   showMedia:YES
                                 renderVideo:YES
                                    showBody:showBody
                              showAdvertiser:showAdvertiser
                                  showRating:showRating
                                    showIcon:showIcon
                              candidateWidth:width
                               desiredHeight:desiredHeight
                                 screenWidth:screenWidth
                                screenHeight:screenHeight]];
                // No still-image fallback for a video creative: the only
                // safe way down from a video plan that does not fit is a
                // layout with no media at all.
                for (NSInteger compactTemplate = ROInFeedTemplateCompactRow;
                     compactTemplate <= ROInFeedTemplateCompactColumn;
                     ++compactTemplate) {
                    [self ro_updateVariantBest:fallbackVariants
                                     candidate:[self ro_evaluatePlanForTemplate:(ROInFeedTemplate)compactTemplate
                                            tier:(ROInFeedTier)tier
                                       showMedia:NO
                                     renderVideo:NO
                                        showBody:showBody
                                  showAdvertiser:showAdvertiser
                                      showRating:showRating
                                        showIcon:showIcon
                                  candidateWidth:width
                                   desiredHeight:desiredHeight
                                     screenWidth:screenWidth
                                    screenHeight:screenHeight]];
                }
                continue;
            }

            for (NSInteger compactTemplate = ROInFeedTemplateCompactRow;
                 compactTemplate <= ROInFeedTemplateCompactColumn;
                 ++compactTemplate) {
                [self ro_updateVariantBest:generalVariants
                                 candidate:[self ro_evaluatePlanForTemplate:(ROInFeedTemplate)compactTemplate
                                        tier:(ROInFeedTier)tier
                                   showMedia:NO
                                 renderVideo:NO
                                    showBody:showBody
                              showAdvertiser:showAdvertiser
                                  showRating:showRating
                                    showIcon:showIcon
                              candidateWidth:width
                               desiredHeight:desiredHeight
                                 screenWidth:screenWidth
                                screenHeight:screenHeight]];
            }

            if (hasMainImage) {
                for (NSInteger mediaTemplate = ROInFeedTemplateMediaLeft;
                     mediaTemplate <= ROInFeedTemplateMediaRight;
                     ++mediaTemplate) {
                    [self ro_updateVariantBest:generalVariants
                                     candidate:[self ro_evaluatePlanForTemplate:(ROInFeedTemplate)mediaTemplate
                                            tier:(ROInFeedTier)tier
                                       showMedia:YES
                                     renderVideo:NO
                                        showBody:showBody
                                  showAdvertiser:showAdvertiser
                                      showRating:showRating
                                        showIcon:showIcon
                                  candidateWidth:width
                                   desiredHeight:desiredHeight
                                     screenWidth:screenWidth
                                    screenHeight:screenHeight]];
                }
            }
        }
    }

    NSMutableArray<ROInFeedLayoutPlan *> *primaryVariants =
            hasVideo ? videoVariants : generalVariants;
    [plans addObjectsFromArray:primaryVariants];
    [self ro_finalizePlanList:plans];

    // Video is always attempted first; the remaining candidates fall back
    // to a media-free layout.
    if (hasVideo) {
        NSMutableArray<ROInFeedLayoutPlan *> *fallbackPlans =
                [NSMutableArray arrayWithArray:fallbackVariants];
        [self ro_finalizePlanList:fallbackPlans];
        [plans addObjectsFromArray:fallbackPlans];
    }

    [self clear];
    return plans;
}

- (void)ro_finalizePlanList:(NSMutableArray<ROInFeedLayoutPlan *> *)plans {
    [plans sortWithOptions:NSSortStable
           usingComparator:^NSComparisonResult(ROInFeedLayoutPlan *first
                                             , ROInFeedLayoutPlan *second) {
        if (first.score < second.score) return NSOrderedAscending;
        if (first.score > second.score) return NSOrderedDescending;
        return NSOrderedSame;
    }];
    [self ro_pruneDominatedPlans:plans];
    NSInteger expandedMediaPlans = 0;
    for (NSUInteger index = 0;
         index < plans.count && expandedMediaPlans < kROMaxExpandedMediaPlans;
         ++index) {
        ROInFeedLayoutPlan *plan = plans[index];
        if (!plan.showMedia) continue;
        [self ro_expandMediaWithinPlan:plan];
        ++expandedMediaPlans;
    }
}

- (void)ro_pruneDominatedPlans:(NSMutableArray<ROInFeedLayoutPlan *> *)plans {
    NSMutableArray<ROInFeedLayoutPlan *> *selected = [NSMutableArray array];
    for (ROInFeedLayoutPlan *plan in plans) {
        BOOL hasFamily = NO;
        BOOL hasRequiredOnlyFallback = NO;
        for (ROInFeedLayoutPlan *kept in selected) {
            if (kept.layoutTemplate != plan.layoutTemplate
                    || kept.tier != plan.tier
                    || kept.showMedia != plan.showMedia
                    || kept.renderVideo != plan.renderVideo) {
                continue;
            }
            hasFamily = YES;
            if (!kept.showBody && !kept.showAdvertiser && !kept.showRating) {
                hasRequiredOnlyFallback = YES;
            }
        }
        BOOL requiredOnly =
                !plan.showBody && !plan.showAdvertiser && !plan.showRating;
        if (!hasFamily || (requiredOnly && !hasRequiredOnlyFallback)) {
            [selected addObject:plan];
        }
    }
    [plans removeAllObjects];
    [plans addObjectsFromArray:selected];
}

// Best per variant key; the icon is part of the key so an icon variant and
// its iconless twin both survive as fallbacks.
- (void)ro_updateVariantBest:(NSMutableArray<ROInFeedLayoutPlan *> *)variants
                   candidate:(ROInFeedLayoutPlan *)candidate {
    if (candidate == nil) return;
    for (NSUInteger index = 0; index < variants.count; ++index) {
        ROInFeedLayoutPlan *current = variants[index];
        if (current.layoutTemplate == candidate.layoutTemplate
                && current.tier == candidate.tier
                && current.showMedia == candidate.showMedia
                && current.renderVideo == candidate.renderVideo
                && current.showBody == candidate.showBody
                && current.showAdvertiser == candidate.showAdvertiser
                && current.showRating == candidate.showRating
                && current.showIcon == candidate.showIcon) {
            if (candidate.score < current.score) {
                variants[index] = candidate;
            }
            return;
        }
    }
    [variants addObject:candidate];
}

- (ROInFeedProbeLayout *)ro_probeLayoutForPlan:(ROInFeedLayoutPlan *)plan {
    for (HBCachedProbeLayout *cached in _probeLayouts) {
        ROInFeedProbeLayout *probe = cached.probe;
        if (probe.layoutTemplate == plan.layoutTemplate
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

    ROInFeedProbeLayout *probe = [_viewFactory createProbeForPlan:plan];
    HBCachedProbeLayout *cached = [[HBCachedProbeLayout alloc] init];
    cached.probe = probe;
    cached.showBody = plan.showBody;
    cached.showAdvertiser = plan.showAdvertiser;
    cached.showRating = plan.showRating;
    cached.showIcon = plan.showIcon;
    [_probeLayouts addObject:cached];
    return probe;
}

- (CGFloat)ro_measureNaturalContentHeight:(UIView *)view
                                    width:(CGFloat)width {
    [view ro_measureWithWidthSpec:HBMeasureSpecMake(HBMeasureSpecExactly, width)
                       heightSpec:HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    return MAX(kROMinNativeAdSize, view.ro_measuredSize.height);
}

- (ROInFeedLayoutPlan *)ro_evaluatePlanForTemplate:(ROInFeedTemplate)layoutTemplate
                                              tier:(ROInFeedTier)tier
                                         showMedia:(BOOL)showMedia
                                       renderVideo:(BOOL)renderVideo
                                          showBody:(BOOL)showBody
                                    showAdvertiser:(BOOL)showAdvertiser
                                        showRating:(BOOL)showRating
                                          showIcon:(BOOL)showIcon
                                    candidateWidth:(CGFloat)candidateWidth
                                     desiredHeight:(CGFloat)desiredHeight
                                       screenWidth:(CGFloat)screenWidth
                                      screenHeight:(CGFloat)screenHeight {
    CGFloat minWidth = [_viewFactory minimumWidthForTier:tier];
    BOOL compactWithoutMedia = !renderVideo
            && !showMedia
            && (layoutTemplate == ROInFeedTemplateCompactRow
                    || layoutTemplate == ROInFeedTemplateCompactColumn);
    if (!compactWithoutMedia && candidateWidth < minWidth) {
        [self ro_recordRejectionForTemplate:layoutTemplate
                                       tier:tier
                                     reason:[NSString stringWithFormat:
                                            @"width %g < %g"
                                          , candidateWidth, minWidth]];
        return nil;
    }
    if (candidateWidth <= 0 || candidateWidth > screenWidth) {
        [self ro_recordRejectionForTemplate:layoutTemplate
                                       tier:tier
                                     reason:[NSString stringWithFormat:
                                            @"width %g is outside screen "
                                             "width %g"
                                          , candidateWidth, screenWidth]];
        return nil;
    }

    ROInFeedLayoutPlan *plan = [[ROInFeedLayoutPlan alloc] init];
    plan.layoutTemplate = layoutTemplate;
    plan.tier = tier;
    plan.showMedia = showMedia;
    plan.renderVideo = renderVideo;
    plan.showBody = showBody && _nativeAd.body.length > 0;
    plan.showAdvertiser = showAdvertiser && _nativeAd.advertiser.length > 0;
    plan.showIcon = showIcon;
    plan.showRating = showRating && [_viewFactory hasValidStarRating];
    if (renderVideo) {
        plan.showBody = NO;
        plan.showAdvertiser = NO;
        plan.showRating = NO;
        plan.showIcon = NO;
    }
    plan.width = candidateWidth;
    plan.height = desiredHeight;
    if (![self ro_resolveMediaDimensionsForPlan:plan]) return nil;
    // Shrinking to fit mutates the media dimensions on the plan; each rung
    // starts from the resolved size or a rejected shrink poisons the rest.
    CGFloat baseMediaWidth = plan.mediaWidth;
    CGFloat baseMediaHeight = plan.mediaHeight;

    CGFloat naturalContentHeight = 0;
    NSString *lastFailure = nil;
    BOOL fitted = NO;
    BOOL hasSecondaryText = plan.showBody || plan.showAdvertiser;
    for (NSInteger rung = 0; rung < kROTextLadderRungCount; ++rung) {
        HBTextMode textMode = kROTextLadderModes[rung];
        if (textMode == HBTextModeSecondaryMarquee && !hasSecondaryText) {
            continue;
        }
        plan.marqueeHeadline = textMode == HBTextModeAllMarquee;
        plan.marqueeSecondary = textMode != HBTextModeWrapped;
        plan.textScale = kROTextLadderScales[rung];
        plan.mediaWidth = baseMediaWidth;
        plan.mediaHeight = baseMediaHeight;

        ROInFeedProbeLayout *probeLayout = [self ro_probeLayoutForPlan:plan];
        [_viewFactory configureProbeLayout:probeLayout forPlan:plan];
        UIView *finalProbe = probeLayout.root;
        naturalContentHeight =
                [self ro_measureNaturalContentHeight:finalProbe
                                               width:plan.width];
        if (naturalContentHeight > desiredHeight) {
            naturalContentHeight =
                    [self ro_shrinkImageTopToFitHeightForPlan:plan
                                                        probe:probeLayout
                                                        width:plan.width
                                                desiredHeight:desiredHeight
                                        originalNaturalHeight:naturalContentHeight];
        }
        if (naturalContentHeight > desiredHeight) {
            lastFailure = [NSString stringWithFormat:
                    @"natural height %g > %g at text x%g"
                  , naturalContentHeight, desiredHeight, plan.textScale];
            continue;
        }

        [finalProbe ro_measureWithWidthSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, plan.width)
                                 heightSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, desiredHeight)];
        [finalProbe ro_layoutWithFrame:
                CGRectMake(0, 0, plan.width, desiredHeight)];
        [self ro_nudgeCutTextsWholeInProbe:probeLayout
                                      plan:plan
                                     width:plan.width
                                    height:desiredHeight];
        if (![_validator validateAssetGeometryForRoot:finalProbe
                                                views:probeLayout.views
                                                 plan:plan
                                           logFailure:NO]) {
            NSString *validationReason = _validator.lastFailureReason;
            lastFailure = [NSString stringWithFormat:@"%@ at text x%g"
                  , validationReason ?: @"asset geometry rejected"
                  , plan.textScale];
            continue;
        }

        fitted = YES;
        break;
    }
    if (!fitted) {
        [self ro_recordRejectionForTemplate:layoutTemplate
                                       tier:tier
                                     reason:lastFailure];
        return nil;
    }

    plan.measuredContentHeight = naturalContentHeight;

    int64_t movement = llabs((int64_t)llround(
            HBClamp(_requestedX, 0, MAX(0, screenWidth - plan.width))
                    - _requestedX))
            + llabs((int64_t)llround(
            HBClamp(_requestedY, 0, MAX(0, screenHeight - desiredHeight))
                    - _requestedY));
    int64_t whitespace = MAX(
            0
          , (int64_t)llround(
                (desiredHeight - naturalContentHeight) * _screenScale));
    NSInteger richness = 0;
    if (showMedia) richness += 8;
    if (renderVideo) richness += 3;
    if (plan.showBody) richness += 1;
    if (plan.showAdvertiser) richness += 1;
    if (plan.showRating) richness += 1;
    if (plan.showIcon) richness += 1;
    richness += tier;

    ROInFeedTier preferredTier =
            [_viewFactory preferredTierForWidth:plan.width
                                         height:desiredHeight];
    int64_t tierPenalty = labs((long)(tier - preferredTier));
    // Counted per scrolling text: scrolling is a price paid for an element
    // or for the group's legibility, never a preference of its own.
    int64_t marqueePenalty = 0;
    if (plan.marqueeHeadline) ++marqueePenalty;
    if (plan.marqueeSecondary && hasSecondaryText) ++marqueePenalty;
    // Between two plans carrying the same assets, the one giving the media
    // more of the slot wins - what the priority order always said and
    // whitespace only approximated.
    int64_t mediaAreaPercent = 0;
    if (showMedia && plan.width > 0 && desiredHeight > 0) {
        mediaAreaPercent = MIN(
                100LL
              , (int64_t)llround(
                    100.0 * plan.mediaWidth * plan.mediaHeight
                            / (plan.width * desiredHeight)));
    }
    // Media sits on the left by preference - most people are
    // right-handed, so the text and the button belong under the thumb.
    int64_t sidePenalty =
            plan.layoutTemplate == ROInFeedTemplateMediaRight ? 1 : 0;
    plan.score = movement * 1000000LL
            + whitespace * 100LL
            + tierPenalty * 1000LL
            + marqueePenalty * 3000LL
            + sidePenalty * 500LL
            - mediaAreaPercent * 30LL
            - (int64_t)richness * 10000LL;
    return plan;
}

- (CGFloat)ro_shrinkImageTopToFitHeightForPlan:(ROInFeedLayoutPlan *)plan
                                         probe:(ROInFeedProbeLayout *)probeLayout
                                         width:(CGFloat)width
                                 desiredHeight:(CGFloat)desiredHeight
                         originalNaturalHeight:(CGFloat)originalNaturalHeight {
    if (plan.layoutTemplate != ROInFeedTemplateMediaTop
            || !plan.showMedia
            || plan.renderVideo
            || plan.mediaWidth <= 0
            || plan.mediaHeight <= 0) {
        return originalNaturalHeight;
    }

    CGFloat originalMediaWidth = plan.mediaWidth;
    CGFloat originalMediaHeight = plan.mediaHeight;
    // The validator holds any MediaView to the media policy floor, so
    // shrinking below it manufactures plans that can never validate.
    CGFloat minimumMediaSide = [_viewFactory mediaPolicyFloor];
    CGFloat minimumScale = MAX(
            minimumMediaSide / originalMediaWidth
          , minimumMediaSide / originalMediaHeight);
    if (minimumScale >= 1) return originalNaturalHeight;

    plan.mediaWidth = MAX(
            minimumMediaSide, ceil(originalMediaWidth * minimumScale));
    plan.mediaHeight = MAX(
            minimumMediaSide, ceil(originalMediaHeight * minimumScale));
    [_viewFactory configureProbeLayout:probeLayout forPlan:plan];
    CGFloat minimumNaturalHeight =
            [self ro_measureNaturalContentHeight:probeLayout.root width:width];
    if (minimumNaturalHeight > desiredHeight) {
        plan.mediaWidth = originalMediaWidth;
        plan.mediaHeight = originalMediaHeight;
        [_viewFactory configureProbeLayout:probeLayout forPlan:plan];
        return originalNaturalHeight;
    }

    CGFloat bestMediaWidth = plan.mediaWidth;
    CGFloat bestMediaHeight = plan.mediaHeight;
    CGFloat bestNaturalHeight = minimumNaturalHeight;
    CGFloat low = minimumScale;
    CGFloat high = 1;
    for (NSInteger iteration = 0;
         iteration < kROMediaExpansionIterations;
         ++iteration) {
        CGFloat scale = (low + high) / 2;
        plan.mediaWidth = MAX(
                minimumMediaSide, floor(originalMediaWidth * scale));
        plan.mediaHeight = MAX(
                minimumMediaSide, floor(originalMediaHeight * scale));
        [_viewFactory configureProbeLayout:probeLayout forPlan:plan];
        CGFloat naturalHeight =
                [self ro_measureNaturalContentHeight:probeLayout.root
                                               width:width];
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
    [_viewFactory configureProbeLayout:probeLayout forPlan:plan];
    return bestNaturalHeight;
}

- (void)ro_expandMediaWithinPlan:(ROInFeedLayoutPlan *)plan {
    if (plan == nil
            || !plan.showMedia
            || plan.mediaWidth <= 0
            || plan.mediaHeight <= 0) {
        return;
    }

    CGFloat maxMediaWidth = plan.width;
    if (ROInFeedTemplateIsMediaSide(plan.layoutTemplate)) {
        maxMediaWidth -= [_viewFactory gapForTier:plan.tier]
                + [self ro_requiredTextSlotWidthForPlan:plan];
    }
    if (maxMediaWidth <= plan.mediaWidth) return;

    CGFloat maxScale = MIN(
            maxMediaWidth / plan.mediaWidth
          , plan.height / plan.mediaHeight);
    if (maxScale <= 1) return;

    CGFloat baseWidth = plan.mediaWidth;
    CGFloat baseHeight = plan.mediaHeight;
    CGFloat bestWidth = baseWidth;
    CGFloat bestHeight = baseHeight;
    CGFloat bestNaturalHeight = plan.measuredContentHeight;
    CGFloat low = 1;
    CGFloat high = maxScale;
    ROInFeedProbeLayout *probe = [self ro_probeLayoutForPlan:plan];

    for (NSInteger iteration = 0;
         iteration < kROMediaExpansionIterations;
         ++iteration) {
        CGFloat scale = (low + high) / 2;
        plan.mediaWidth = MAX(baseWidth, floor(baseWidth * scale));
        plan.mediaHeight = MAX(baseHeight, floor(baseHeight * scale));
        [_viewFactory configureProbeLayout:probe forPlan:plan];
        CGFloat naturalHeight =
                [self ro_measureNaturalContentHeight:probe.root
                                               width:plan.width];

        BOOL fits = naturalHeight <= plan.height;
        if (fits) {
            [probe.root ro_measureWithWidthSpec:
                            HBMeasureSpecMake(HBMeasureSpecExactly, plan.width)
                                     heightSpec:
                            HBMeasureSpecMake(HBMeasureSpecExactly, plan.height)];
            [probe.root ro_layoutWithFrame:
                    CGRectMake(0, 0, plan.width, plan.height)];
            fits = [_validator validateAssetGeometryForRoot:probe.root
                                                      views:probe.views
                                                       plan:plan
                                                 logFailure:NO];
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
}

- (CGFloat)ro_requiredTextSlotWidthForPlan:(ROInFeedLayoutPlan *)plan {
    if (ROInFeedTemplateIsMediaSide(plan.layoutTemplate)) {
        return [_viewFactory minimumMediaLeftTextSlotWidthForPlan:plan];
    }
    return kROMinVisibleTextSlotPx / _screenScale
            + 2 * [_viewFactory paddingForTier:plan.tier];
}

- (BOOL)ro_resolveMediaDimensionsForPlan:(ROInFeedLayoutPlan *)plan {
    if (!plan.showMedia) {
        plan.mediaWidth = 0;
        plan.mediaHeight = 0;
        return YES;
    }

    CGFloat contentWidth = plan.width;
    if (contentWidth <= 0) {
        [self ro_recordRejectionForTemplate:plan.layoutTemplate
                                       tier:plan.tier
                                     reason:[NSString stringWithFormat:
                                            @"safe content width is %g"
                                          , contentWidth]];
        return NO;
    }

    CGFloat aspect = [self ro_mediaAspectRatioForVideo:plan.renderVideo];
    if (plan.layoutTemplate == ROInFeedTemplateMediaBackground) {
        // Both dimensions carry the registered MediaView, so both are
        // held to the media minimum - video's floor for a video creative,
        // the far lower image floor otherwise.
        CGFloat minSide = [_viewFactory mediaPolicyFloor];
        if (plan.width < minSide || plan.height < minSide) {
            [self ro_recordRejectionForTemplate:plan.layoutTemplate
                                           tier:plan.tier
                                         reason:[NSString stringWithFormat:
                                                @"slot %gx%g is under the "
                                                 "media minimum"
                                              , plan.width, plan.height]];
            return NO;
        }
        plan.mediaWidth = plan.width;
        plan.mediaHeight = plan.height;
        return YES;
    }
    if (plan.layoutTemplate == ROInFeedTemplateMediaTop) {
        CGFloat width;
        CGFloat height;
        if (plan.renderVideo) {
            CGSize videoSize = [self ro_resolveVideoSlotWithPreferredHeight:
                            [_viewFactory mediaSizeForTier:plan.tier]
                                                              maximumWidth:contentWidth
                                                            creativeAspect:aspect];
            if (videoSize.width <= 0) {
                [self ro_recordRejectionForTemplate:plan.layoutTemplate
                                               tier:plan.tier
                                             reason:[NSString stringWithFormat:
                                                    @"video cannot fit safe "
                                                     "width %g", contentWidth]];
                return NO;
            }
            width = videoSize.width;
            height = videoSize.height;
        } else {
            width = contentWidth;
            height = MAX(1, ceil(width / aspect));
        }
        plan.mediaWidth = width;
        plan.mediaHeight = height;
        return YES;
    }

    CGFloat height = [_viewFactory mediaSizeForTier:plan.tier];
    CGFloat width;
    if (plan.renderVideo) {
        CGFloat maximumMediaWidth = contentWidth
                - [_viewFactory gapForTier:plan.tier]
                - [self ro_requiredTextSlotWidthForPlan:plan];
        CGSize videoSize = [self ro_resolveVideoSlotWithPreferredHeight:height
                                                          maximumWidth:maximumMediaWidth
                                                        creativeAspect:aspect];
        if (videoSize.width <= 0) {
            [self ro_recordRejectionForTemplate:plan.layoutTemplate
                                           tier:plan.tier
                                         reason:[NSString stringWithFormat:
                                                @"video cannot fit media "
                                                 "width %g", maximumMediaWidth]];
            return NO;
        }
        width = videoSize.width;
        height = videoSize.height;
    } else {
        width = MAX(1, ceil(height * aspect));
    }

    CGFloat minimumTextWidth = [self ro_requiredTextSlotWidthForPlan:plan];
    CGFloat requiredWidth = width
            + [_viewFactory gapForTier:plan.tier]
            + minimumTextWidth;
    if (requiredWidth > contentWidth) {
        [self ro_recordRejectionForTemplate:plan.layoutTemplate
                                       tier:plan.tier
                                     reason:[NSString stringWithFormat:
                                            @"media and text need %g > safe "
                                             "width %g"
                                          , requiredWidth, contentWidth]];
        return NO;
    }
    plan.mediaWidth = width;
    plan.mediaHeight = height;
    return YES;
}

- (CGSize)ro_resolveVideoSlotWithPreferredHeight:(CGFloat)preferredHeight
                                    maximumWidth:(CGFloat)maximumWidth
                                  creativeAspect:(CGFloat)creativeAspect {
    CGFloat requiredWidth =
            [_validator requiredVideoWidthForAspect:creativeAspect];
    CGFloat requiredHeight =
            [_validator requiredVideoHeightForAspect:creativeAspect];
    if (maximumWidth < requiredWidth) return CGSizeZero;

    CGFloat height = MAX(requiredHeight, preferredHeight);
    CGFloat aspectWidth = MAX(requiredWidth, ceil(height * creativeAspect));
    CGFloat width = MIN(maximumWidth, aspectWidth);

    if (![_validator isPolicySafeVideoSizeWithWidth:width height:height]) {
        return CGSizeZero;
    }
    return CGSizeMake(width, height);
}

- (CGFloat)ro_mediaAspectRatioForVideo:(BOOL)renderVideo {
    if (renderVideo) {
        GADMediaContent *content = _nativeAd.mediaContent;
        if (content != nil && content.aspectRatio > 0) {
            return HBClamp(
                    content.aspectRatio
                  , kROMinMediaAspectRatio
                  , kROMaxMediaAspectRatio);
        }
        return kROUnknownVideoAspectRatio;
    }

    UIImage *image = [_viewFactory findMainImage];
    if (image != nil && image.size.width > 0 && image.size.height > 0) {
        return HBClamp(
                image.size.width / image.size.height
              , kROMinMediaAspectRatio
              , kROMaxMediaAspectRatio);
    }
    return kRODefaultImageAspectRatio;
}

@end
