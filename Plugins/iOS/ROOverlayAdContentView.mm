#import "ROOverlayAdContentView.h"

#import "ROAdTextLabel.h"
#import "RONativeAdStarRatingView.h"

static NSString *const kROTag = @"Overlay";
static NSString *const kROAttributionText = @"Ad";

static const CGFloat kROMinVideoMediaSize = 120;
// The 120pt floor is video's; a creative with no video keeps its picture
// in shorter panels instead of handing the band to the icon.
static const CGFloat kROMinImageMediaSize = 48;
// A probe value only, for the fit checks that need a number. It never
// frames the media: a creative with an unreported ratio is handed the
// whole band and renders inside it as it pleases.
static const CGFloat kRODefaultMediaAspectRatio = 1;
static const float kRODefaultHeightRatio = 0.5f;
static const float kROOverlayDefaultAlpha = 0.80f;
static const float kROCollapsibleDefaultAlpha = 0.95f;
static const uint32_t kROOverlayBackgroundRgb = 0x000000;
static const uint32_t kROCollapsibleBackgroundRgb = 0x1B2029;
// 20pt a side spent 40pt of every screen on nothing the ad needed.
static const CGFloat kROHorizontalPadding = 8;
static const CGFloat kROControlStripHeight = 34;
static const CGFloat kROControlGap = 2;
static const CGFloat kRORightControlInset = 18;
// A portrait creative fills the panel's height on the left and everything
// else moves into a rail beside it, provided the media still meets its policy
// minimum and the rail keeps enough width to read. Strictly portrait only:
// a square creative belongs stacked on top - MEDIA_TOP wins there - and only
// media clearly taller than wide earns the rail beside it.
static const CGFloat kROSideMediaMaxAspect = 0.85f;
// Just over half: enough width for a portrait creative to stay imposing,
// while the rail keeps room for whole text and a real button.
static const CGFloat kROSideMediaMaxWidthShare = 0.56f;
static const CGFloat kROSideMediaMinRail = 120;
// The rail's side padding follows the rail's width; a narrow column cannot
// afford the full 8pt on each side.
static const CGFloat kRORailSidePaddingRatio = 0.03f;
static const CGFloat kRORailMinSidePadding = 2;
static const CGFloat kRORailIconGap = 4;
// Text no larger than the rail can wear: the scale ceiling follows the
// rail's width, reaching full size only in a genuinely wide rail.
static const CGFloat kRORailScaleCapMinWidth = 160;
static const CGFloat kRORailScaleCapRange = 240;
// Whole text before size, size before scrolling: how many size steps the
// rail trades away before a line is allowed to scroll.
static const NSInteger kRORailFullTextSteps = 3;
static const CGFloat kRORailFullTextScaleStep = 0.34f;
static const NSInteger kRORailHeadlineMaxLineCount = 4;
// The seam between the media and the identity row below it.
static const CGFloat kROMediaLowerSeam = 6;
// A panel meaningfully taller than wide reads as a page: media belongs
// stacked on top of it, not beside it. Side media only suits panels near
// screen proportions.
static const CGFloat kROSideMediaMaxPanelHeightRatio = 1.3f;
// The rail is narrow by construction, so the icon never shares a line with
// text there: it stands alone and the identity stack follows below.
static const CGFloat kROSideRailIconWidthRatio = 0.3f;
// Slack absorption in the rail: how many whole body lines it may take, and
// how far the icon may grow, before free height is left alone.
static const NSInteger kRORailBodyMaxLineCount = 6;
static const CGFloat kRORailIconGrowthStep = 8;
static const CGFloat kRORailIconMaxWidthRatio = 0.6f;
// Extra ground the strip-avoiding layout may give before it surrenders and
// lets the corner controls overlay the media instead.
static const CGFloat kROAvoidCallToActionHeight = 36;
static const CGFloat kROAvoidIconSize = 28;
// Ratio first: media needs its minimum plus a usable row of content under it;
// a panel that cannot host that drops the media rather than growing past the
// request, and only a panel under the absolute floor is ever grown.
static const CGFloat kROMinMediaLowerContent = 88;
static const CGFloat kROMinPanelHeight = 48;
// Below this the panel is a strip, and the strip is one row.
static const CGFloat kROTickerMaxPanelHeight = 120;
static const CGFloat kROTickerCtaMinHeight = 32;
static const CGFloat kROMinBadgeSize = 15;
static const CGFloat kROAttributionWidth = 24;
static const CGFloat kROAttributionHeight = 18;
static const CGFloat kROAdChoicesReserveSize = 24;
static const CGFloat kROMinIconSize = 36;
static const CGFloat kROMaxIconSize = 64;
static const CGFloat kROIconGap = 8;
// The floor gives ground in a tight panel; the ceiling is what a roomy
// panel is allowed to spend.
static const CGFloat kROMinCallToActionHeight = 44;
static const CGFloat kROMaxCallToActionHeight = 56;
static const CGFloat kROMinIdentityVerticalPadding = 2;
static const CGFloat kROMaxIdentityVerticalPadding = 6;
static const CGFloat kROMinBodyBottomPadding = 4;
static const CGFloat kROMaxBodyBottomPadding = 8;
static const CGFloat kROMinHeadlineTextSize = 15;
static const CGFloat kROMaxHeadlineTextSize = 20;
static const CGFloat kROFullscreenHeadlineTextSize = 18;
static const CGFloat kROMinAdvertiserTextSize = 12;
static const CGFloat kROMaxAdvertiserTextSize = 14;
static const CGFloat kROMinBodyTextSize = 13;
static const CGFloat kROMaxBodyTextSize = 16;
static const CGFloat kROMinCallToActionTextSize = 14;
static const CGFloat kROMaxCallToActionTextSize = 17;
static const NSInteger kROCollapsibleHeadlineMaxLines = 2;
static const NSInteger kROMinBodyLineCount = 1;
static const NSInteger kROMaxBodyLineCount = 3;
static const NSInteger kROResponsiveScaleSearchIterations = 8;
static const CGFloat kROMaxIconRowWidthRatio = 0.33f;
// A line that moves is harder to read than one that sits still, so text
// that has to scroll never does it at the size that failed to fit whole.
static const CGFloat kROMarqueeTextShrink = 0.8f;
static const NSTimeInterval kROCountdownInterval = 0.25;

static UIColor *HBArgb(uint32_t argb) {
    return [UIColor colorWithRed:((argb >> 16) & 0xFF) / 255.0
                           green:((argb >> 8) & 0xFF) / 255.0
                            blue:(argb & 0xFF) / 255.0
                           alpha:((argb >> 24) & 0xFF) / 255.0];
}

// A UILabel with content insets - the attribution badge and the timer and
// close controls all pad their text the way the Android views do.
@interface HBPaddedLabel : UILabel
@property (nonatomic) UIEdgeInsets ro_contentInsets;
@end

@implementation HBPaddedLabel

- (void)drawTextInRect:(CGRect)rect {
    [super drawTextInRect:UIEdgeInsetsInsetRect(rect, self.ro_contentInsets)];
}

- (CGSize)sizeThatFits:(CGSize)size {
    CGSize content = [super sizeThatFits:size];
    UIEdgeInsets insets = self.ro_contentInsets;
    return CGSizeMake(
            content.width + insets.left + insets.right
          , content.height + insets.top + insets.bottom);
}

@end

@implementation ROOverlayAdContentView {
    GADNativeAd *_nativeAd;
    int64_t _countDownRemainingMs;
    BOOL _closeOnLeft;
    BOOL _numberOpposite;
    BOOL _fullscreen;
    float _backgroundAlpha;
    dispatch_block_t _onClose;
    CGFloat _resolvedPanelHeight;

    GADNativeAdView *_nativeAdView;
    HBLinearLayoutView *_contentColumn;
    GADMediaView *_mediaView;
    HBLinearLayoutView *_identityRow;
    HBLinearLayoutView *_identityText;
    UIImageView *_icon;
    ROAdTextLabel *_headline;
    ROAdTextLabel *_advertiser;
    RONativeAdStarRatingView *_starRating;
    ROAdTextLabel *_body;
    UIButton *_callToAction;
    HBPaddedLabel *_attribution;
    UIView *_adChoicesReserve;
    HBPaddedLabel *_countdown;
    HBPaddedLabel *_close;

    BOOL _hasDisplayableMedia;
    BOOL _sideMediaLayout;
    CGFloat _sideMediaWidthPx;
    BOOL _mediaAvoidsControlStrip;
    BOOL _mediaAspectReported;
    BOOL _controlAvoidanceActive;
    BOOL _controlsAtEdgesBelowBadges;
    CGFloat _avoidancePanelHeight;
    HBLinearLayoutView *_sideRail;
    BOOL _iconHero;
    BOOL _tickerLayout;
    CGFloat _minimumMediaSize;
    CGFloat _mediaAspectRatio;
    UIImage *_fallbackMediaImage;
    UIImageView *_fallbackMediaImageView;

    NSTimer *_timer;
    BOOL _clickCommitted;
    BOOL _released;
    // One shrink per label, ever - the whole-or-scrolling switch must not
    // compound across layout passes.
    NSMutableSet<NSValue *> *_shrunkTexts;
}

+ (CGFloat)resolveInitialPanelHeightForFullscreen:(BOOL)fullscreen
                                      heightRatio:(float)heightRatio
                                  hasVideoContent:(BOOL)hasVideoContent {
    CGSize screen = UIScreen.mainScreen.bounds.size;
    if (fullscreen) return screen.height;

    float ratio = heightRatio;
    if (isnan(ratio) || isinf(ratio)) {
        NSLog(@"%@: heightRatio is not finite; using 0.5", kROTag);
        ratio = kRODefaultHeightRatio;
    }
    ratio = MAX(0.0f, MIN(1.0f, ratio));
    CGFloat requestedHeight = screen.height * ratio;
    requestedHeight = MAX(requestedHeight, kROMinPanelHeight);
    return MIN(screen.height, requestedHeight);
}

- (instancetype)initWithNativeAd:(GADNativeAd *)nativeAd
             countDownRemainingMs:(int64_t)countDownRemainingMs
                      closeOnLeft:(BOOL)closeOnLeft
                   numberOpposite:(BOOL)numberOpposite
                       fullscreen:(BOOL)fullscreen
                  backgroundAlpha:(float)backgroundAlpha
             requestedPanelHeight:(CGFloat)requestedPanelHeight
                          onClose:(dispatch_block_t)onClose {
    self = [super initWithFrame:CGRectZero];
    if (self == nil) return nil;

    _nativeAd = nativeAd;
    _countDownRemainingMs = MAX(0, countDownRemainingMs);
    _closeOnLeft = closeOnLeft;
    _numberOpposite = numberOpposite;
    _fullscreen = fullscreen;
    _backgroundAlpha = backgroundAlpha;
    _onClose = [onClose copy];
    _shrunkTexts = [NSMutableSet set];
    [self ro_buildWithRequestedPanelHeight:requestedPanelHeight];

    [NSNotificationCenter.defaultCenter
            addObserver:self
               selector:@selector(ro_applicationDidBecomeActive)
                   name:UIApplicationDidBecomeActiveNotification
                 object:nil];
    return self;
}

- (void)dealloc {
    [NSNotificationCenter.defaultCenter removeObserver:self];
}

- (CGFloat)resolvedPanelHeight {
    return _resolvedPanelHeight;
}

- (void)releaseContent {
    if (_released) return;
    _released = YES;

    [_timer invalidate];
    _timer = nil;
    _nativeAdView.nativeAd = nil;
    [_nativeAdView removeFromSuperview];
    _nativeAdView = nil;
    for (UIView *child in [self.subviews copy]) [child removeFromSuperview];
    _countdown = nil;
    _close = nil;
}

- (void)onPresented {
    [self onPresentedWithRemainingMs:_countDownRemainingMs];
}

- (void)onPresentedWithRemainingMs:(int64_t)remainingMs {
    if (_released) return;
    _countDownRemainingMs = MAX(0, remainingMs);
    [self ro_startCountdown];
}

- (void)onPaused {
    [_timer invalidate];
    _timer = nil;
}

- (int64_t)countDownRemainingMs {
    return _countDownRemainingMs;
}

- (void)commitAdClick {
    _clickCommitted = YES;
}

- (void)ro_applicationDidBecomeActive {
    // The window-focus release of the Android latch: the store sheet or the
    // browser the click opened has gone away.
    _clickCommitted = NO;
}

// The latch: after a committed click every touch inside the ad is consumed
// by this view instead of any child, so the SDK cannot register another.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    UIView *hit = [super hitTest:point withEvent:event];
    if (_clickCommitted && hit != nil && hit != _close) return self;
    return hit;
}

// ---------------------------------------------------------------------------
// Build
// ---------------------------------------------------------------------------

- (void)ro_buildWithRequestedPanelHeight:(CGFloat)requestedPanelHeight {
    GADMediaContent *mediaContent = _nativeAd.mediaContent;
    BOOL hasVideoContent = mediaContent.hasVideoContent;
    UIImage *mainMediaImage = mediaContent.mainImage;
    _fallbackMediaImage = (!hasVideoContent && mainMediaImage == nil)
            ? [self ro_findFallbackMediaImage]
            : nil;
    _hasDisplayableMedia = hasVideoContent
            || mainMediaImage != nil
            || _fallbackMediaImage != nil;
    _minimumMediaSize = hasVideoContent
            ? kROMinVideoMediaSize
            : kROMinImageMediaSize;
    _mediaAspectRatio = [self ro_mediaAspectRatio];
    CGSize hbScreen = UIScreen.mainScreen.bounds.size;
    CGFloat sidePanelHeight = _fullscreen
            ? hbScreen.height
            : requestedPanelHeight;
    _tickerLayout = !_fullscreen
            && requestedPanelHeight < kROTickerMaxPanelHeight;
    BOOL panelHostsMedia = _fullscreen
            || requestedPanelHeight
                    >= _minimumMediaSize + kROMinMediaLowerContent;
    // With the media dropped - or a creative that never had any - the
    // registered icon stands in for it, the way the in-feed slot works.
    _iconHero = !_fullscreen
            && !_tickerLayout
            && (!_hasDisplayableMedia || !panelHostsMedia)
            && _nativeAd.icon.image != nil;
    _hasDisplayableMedia = _hasDisplayableMedia
            && panelHostsMedia
            && !_tickerLayout;
    _sideMediaLayout = _hasDisplayableMedia
            && [self ro_shouldUseSideMediaForPanelHeight:sidePanelHeight];

    // One line per build: every layout decision and its inputs, so a
    // screenshot of a wrong layout always arrives with its numbers.
    NSLog(@"%@: Overlay layout: fullscreen=%d media=%d video=%d side=%d "
            "ticker=%d iconHero=%d aspect=%g reported=%d panel=%g"
          , kROTag, _fullscreen, _hasDisplayableMedia, hasVideoContent
          , _sideMediaLayout, _tickerLayout, _iconHero
          , _mediaAspectRatio, _mediaAspectReported, requestedPanelHeight);

    [self ro_configureBackground];

    _nativeAdView = [[GADNativeAdView alloc] init];
    _contentColumn = [[HBLinearLayoutView alloc] init];
    _contentColumn.ro_vertical = YES;
    _contentColumn.ro_gravity =
            HBGravityCenterVertical | HBGravityCenterHorizontal;
    _contentColumn.ro_padding = UIEdgeInsetsMake(
            kROControlStripHeight
          , kROHorizontalPadding
          , 0
          , kROHorizontalPadding);

    _mediaView = [[GADMediaView alloc] init];
    // Deliberately black: when a creative reports one ratio but renders
    // less inside it, the black ground makes the shortfall visible.
    _mediaView.backgroundColor = UIColor.blackColor;
    _mediaView.contentMode = UIViewContentModeScaleAspectFit;
    _mediaView.ro_minimumSize =
            CGSizeMake(_minimumMediaSize, _minimumMediaSize);
    _mediaView.ro_layoutWidth = ROLayoutMatchParent;
    _mediaView.ro_layoutHeight = _minimumMediaSize;
    // The media is the one child allowed to ignore the side padding: it
    // bleeds edge to edge through negative margins while every text stays
    // inset.
    _mediaView.ro_layoutMargins = UIEdgeInsetsMake(
            0, -kROHorizontalPadding, 0, -kROHorizontalPadding);
    if (hasVideoContent || mainMediaImage != nil) {
        _mediaView.mediaContent = mediaContent;
    } else if (_fallbackMediaImage != nil) {
        _fallbackMediaImageView =
                [[UIImageView alloc] initWithImage:_fallbackMediaImage];
        _fallbackMediaImageView.contentMode = UIViewContentModeScaleAspectFit;
        [_mediaView addSubview:_fallbackMediaImageView];
    }

    _identityRow = [[HBLinearLayoutView alloc] init];
    _identityRow.ro_vertical = NO;
    _identityRow.ro_gravity = HBGravityCenterVertical;
    _identityRow.ro_padding = UIEdgeInsetsMake(
            _fullscreen
                    ? kROMaxIdentityVerticalPadding
                    : kROMinIdentityVerticalPadding
          , 0
          , kROMinIdentityVerticalPadding
          , 0);
    _identityRow.ro_layoutWidth = ROLayoutMatchParent;

    _icon = [[UIImageView alloc] init];
    _icon.contentMode = UIViewContentModeScaleAspectFill;
    _icon.clipsToBounds = YES;
    _icon.ro_layoutWidth = kROMinIconSize;
    _icon.ro_layoutHeight = kROMinIconSize;
    _icon.ro_layoutMargins = UIEdgeInsetsMake(0, 0, 0, kROIconGap);
    if (_iconHero) {
        _icon.contentMode = UIViewContentModeScaleAspectFit;
        _icon.clipsToBounds = NO;
    } else if (!_sideMediaLayout) {
        [_identityRow addSubview:_icon];
    }

    _identityText = [[HBLinearLayoutView alloc] init];
    _identityText.ro_vertical = YES;
    _identityText.ro_gravity = HBGravityCenterVertical;
    _identityText.ro_layoutWidth = 0;
    _identityText.ro_layoutWeight = 1;

    _headline = [[ROAdTextLabel alloc] init];
    _headline.font = [UIFont boldSystemFontOfSize:
            _fullscreen
                    ? kROFullscreenHeadlineTextSize
                    : kROMinHeadlineTextSize];
    _headline.textColor = UIColor.whiteColor;
    _headline.maxLines = NSIntegerMax;
    _headline.ro_layoutWidth = ROLayoutMatchParent;

    _advertiser = [[ROAdTextLabel alloc] init];
    _advertiser.font = [UIFont systemFontOfSize:kROMinAdvertiserTextSize];
    _advertiser.textColor = [UIColor colorWithWhite:1 alpha:0.8];
    // The single line that does not fit scrolls rather than being cut.
    _advertiser.marquee = YES;
    _advertiser.ro_layoutWidth = ROLayoutMatchParent;

    _starRating = [[RONativeAdStarRatingView alloc] init];
    _starRating.ro_layoutGravity = HBGravityLeft;

    [_identityText addSubview:_headline];
    [_identityText addSubview:_advertiser];
    [_identityText addSubview:_starRating];
    [_identityRow addSubview:_identityText];

    _body = [[ROAdTextLabel alloc] init];
    _body.font = [UIFont systemFontOfSize:kROMinBodyTextSize];
    _body.textColor = [UIColor colorWithWhite:1 alpha:0.8];
    _body.maxLines = kROMaxBodyLineCount;
    _body.ro_layoutWidth = ROLayoutMatchParent;
    _body.ro_padding = UIEdgeInsetsMake(0, 0, kROMinBodyBottomPadding, 0);

    _callToAction = [UIButton buttonWithType:UIButtonTypeCustom];
    [self ro_styleCallToAction:_callToAction];
    _callToAction.titleLabel.font =
            [UIFont boldSystemFontOfSize:kROMinCallToActionTextSize];
    _callToAction.titleLabel.numberOfLines = 0;
    _callToAction.ro_minimumSize = CGSizeMake(0, kROMinCallToActionHeight);
    _callToAction.ro_layoutWidth = ROLayoutMatchParent;

    if (_sideMediaLayout) {
        CGFloat sideMediaWidth =
                [self ro_sideMediaWidthForPanelHeight:sidePanelHeight];
        _sideMediaWidthPx = sideMediaWidth;
        // The media column is exactly as tall as the creative can fill at
        // its own aspect, centred - never a band of dead backfill painted
        // to the panel's height.
        CGFloat sideMediaHeight = MIN(
                sidePanelHeight
              , round(sideMediaWidth / _mediaAspectRatio));
        NSLog(@"%@: Side-media layout: media %gx%g in panel height %g"
              , kROTag, sideMediaWidth, sideMediaHeight, sidePanelHeight);
        _contentColumn.ro_padding = UIEdgeInsetsZero;
        HBLinearLayoutView *sideRow = [[HBLinearLayoutView alloc] init];
        sideRow.ro_vertical = NO;
        _mediaView.ro_layoutWidth = sideMediaWidth;
        _mediaView.ro_layoutHeight = sideMediaHeight;
        _mediaView.ro_layoutGravity = HBGravityCenterVertical;
        _mediaView.ro_layoutMargins = UIEdgeInsetsZero;
        [sideRow addSubview:_mediaView];

        HBLinearLayoutView *rail = [[HBLinearLayoutView alloc] init];
        rail.ro_vertical = YES;
        rail.ro_gravity = HBGravityCenterVertical;
        // The corner controls and AdChoices sit over the rail's top, so only
        // the rail keeps the strip inset; the media needs none. Side padding
        // follows the rail's width - a narrow column keeps its ground for
        // content.
        CGFloat railOuterWidth = MAX(
                0
              , UIScreen.mainScreen.bounds.size.width - sideMediaWidth);
        CGFloat railPad = MAX(
                kRORailMinSidePadding
              , MIN(
                    kROHorizontalPadding
                  , railOuterWidth * kRORailSidePaddingRatio));
        rail.ro_padding = UIEdgeInsetsMake(
                kROControlStripHeight
              , railPad
              , 0
              , railPad);
        // The rail is narrow, so the icon never shares a line with text
        // here: it stands alone and the identity stack follows below at the
        // rail's full width.
        if (!_iconHero) {
            CGFloat railWidth = MAX(0, railOuterWidth - 2 * railPad);
            CGFloat railIconSize = MAX(
                    kROMinIconSize
                  , MIN(
                        kROMaxIconSize
                      , round(railWidth * kROSideRailIconWidthRatio)));
            _icon.ro_layoutWidth = railIconSize;
            _icon.ro_layoutHeight = railIconSize;
            _icon.ro_layoutGravity = HBGravityCenterHorizontal;
            _icon.ro_layoutMargins =
                    UIEdgeInsetsMake(0, 0, kRORailIconGap, 0);
            [rail addSubview:_icon];
        }
        [rail addSubview:_identityRow];
        [rail addSubview:_body];
        [rail addSubview:_callToAction];
        rail.ro_layoutWidth = 0;
        rail.ro_layoutHeight = ROLayoutMatchParent;
        rail.ro_layoutWeight = 1;
        [sideRow addSubview:rail];
        sideRow.ro_layoutWidth = ROLayoutMatchParent;
        sideRow.ro_layoutHeight = sidePanelHeight;
        [_contentColumn addSubview:sideRow];
        _sideRail = rail;
    } else if (_tickerLayout) {
        _contentColumn.ro_padding = UIEdgeInsetsZero;
        _body.hidden = YES;
        _advertiser.hidden = YES;
        _starRating.hidden = YES;
        _headline.maxLines = 1;
        _callToAction.ro_minimumSize =
                CGSizeMake(0, kROTickerCtaMinHeight);
        _callToAction.ro_layoutWidth = ROLayoutWrapContent;
        HBLinearLayoutView *ticker = [[HBLinearLayoutView alloc] init];
        ticker.ro_vertical = NO;
        ticker.ro_gravity = HBGravityCenterVertical;
        CGFloat tickerControlReserve =
                kROControlStripHeight + kRORightControlInset;
        ticker.ro_padding = UIEdgeInsetsMake(
                0, tickerControlReserve, 0, tickerControlReserve);
        _identityRow.ro_padding = UIEdgeInsetsZero;
        _identityRow.ro_layoutWidth = 0;
        _identityRow.ro_layoutWeight = 1;
        [ticker addSubview:_identityRow];
        [ticker addSubview:_callToAction];
        ticker.ro_layoutWidth = ROLayoutMatchParent;
        [_contentColumn addSubview:ticker];
    } else {
        if (_hasDisplayableMedia) {
            [_contentColumn addSubview:_mediaView];
        } else if (_iconHero) {
            _icon.ro_layoutWidth = ROLayoutMatchParent;
            _icon.ro_layoutHeight = 0;
            _icon.ro_layoutWeight = 1;
            _icon.ro_layoutMargins = UIEdgeInsetsZero;
            [_contentColumn addSubview:_icon];
        }
        [_contentColumn addSubview:_identityRow];
        [_contentColumn addSubview:_body];
        [_contentColumn addSubview:_callToAction];
    }
    [_nativeAdView addSubview:_contentColumn];

    _attribution = [self ro_createAttributionLabel];
    [_nativeAdView addSubview:_attribution];

    _adChoicesReserve = [[UIView alloc] init];
    _adChoicesReserve.userInteractionEnabled = NO;
    [_nativeAdView addSubview:_adChoicesReserve];

    if (_hasDisplayableMedia) _nativeAdView.mediaView = _mediaView;
    _nativeAdView.iconView = _icon;
    _nativeAdView.headlineView = _headline;
    _nativeAdView.advertiserView = _advertiser;
    _nativeAdView.starRatingView = _starRating;
    _nativeAdView.bodyView = _body;
    _nativeAdView.callToActionView = _callToAction;
    [self ro_bindAssets];
    if (!_fullscreen && !_tickerLayout) {
        if (_sideMediaLayout) {
            [self ro_configureResponsiveSideRailWithPanelHeight:
                    requestedPanelHeight];
        } else {
            [self ro_configureResponsiveCollapsibleContentWithPanelHeight:
                    requestedPanelHeight];
        }
    }
    if (_fullscreen && _hasDisplayableMedia && !_sideMediaLayout) {
        // The stack hugs the bottom and the media grows toward the corner
        // controls; the exact fit runs in the layout pass, where the true
        // frame is known.
        [self ro_enableControlAvoidanceWithPanelHeight:0];
    }
    _nativeAdView.nativeAd = _nativeAd;
    [self addSubview:_nativeAdView];

    _countdown = [self ro_createControlLabelWithText:
                    [NSString stringWithFormat:@"%lld"
                          , (long long)((_countDownRemainingMs + 999) / 1000)]
                                            textSize:15
                                     backgroundAlpha:0.4
                                     backgroundWhite:0];
    _close = [self ro_createControlLabelWithText:@"✕"
                                        textSize:16
                                 backgroundAlpha:0.66
                                 backgroundWhite:0];
    _close.hidden = YES;
    _close.userInteractionEnabled = YES;
    _close.isAccessibilityElement = YES;
    _close.accessibilityLabel = @"Close ad";
    [_close addGestureRecognizer:[[UITapGestureRecognizer alloc]
            initWithTarget:self
                    action:@selector(ro_closeTapped)]];
    [self addSubview:_countdown];
    [self addSubview:_close];

    [self ro_resolveContentHeightWithRequestedPanelHeight:requestedPanelHeight];
}

- (void)ro_closeTapped {
    if (_onClose != nil) _onClose();
}

- (void)ro_configureBackground {
    uint32_t backgroundRgb = _fullscreen
            ? kROOverlayBackgroundRgb
            : kROCollapsibleBackgroundRgb;
    float defaultAlpha = _fullscreen
            ? kROOverlayDefaultAlpha
            : kROCollapsibleDefaultAlpha;
    float alpha = _backgroundAlpha;
    if (isnan(alpha) || isinf(alpha)) {
        NSLog(@"%@: backgroundAlpha is not finite; using the mode default"
              , kROTag);
        alpha = defaultAlpha;
    } else if (alpha < 0) {
        alpha = defaultAlpha;
    } else {
        float clamped = MAX(0.0f, MIN(1.0f, alpha));
        if (clamped != alpha) {
            NSLog(@"%@: backgroundAlpha must be within [0,1]; clamping it"
                  , kROTag);
        }
        alpha = clamped;
    }
    self.backgroundColor = [HBArgb(0xFF000000 | backgroundRgb)
            colorWithAlphaComponent:alpha];
}

- (HBPaddedLabel *)ro_createAttributionLabel {
    HBPaddedLabel *attribution = [[HBPaddedLabel alloc] init];
    attribution.text = kROAttributionText;
    attribution.textColor = UIColor.blackColor;
    attribution.font = [UIFont boldSystemFontOfSize:10];
    attribution.textAlignment = NSTextAlignmentCenter;
    attribution.backgroundColor = HBArgb(0xFFFFC107);
    attribution.ro_contentInsets = UIEdgeInsetsMake(1, 5, 1, 5);
    attribution.ro_minimumSize = CGSizeMake(
            MAX(kROAttributionWidth, kROMinBadgeSize)
          , MAX(kROAttributionHeight, kROMinBadgeSize));
    attribution.userInteractionEnabled = NO;
    return attribution;
}

- (HBPaddedLabel *)ro_createControlLabelWithText:(NSString *)text
                                        textSize:(CGFloat)textSize
                                 backgroundAlpha:(CGFloat)backgroundAlpha
                                 backgroundWhite:(CGFloat)backgroundWhite {
    HBPaddedLabel *control = [[HBPaddedLabel alloc] init];
    control.text = text;
    control.textColor = UIColor.whiteColor;
    control.font = [UIFont systemFontOfSize:textSize];
    control.textAlignment = NSTextAlignmentCenter;
    control.backgroundColor = [UIColor colorWithWhite:backgroundWhite
                                                alpha:backgroundAlpha];
    return control;
}

// Same treatment as the in-feed button: its own filled and bordered
// background rather than the platform default.
- (void)ro_styleCallToAction:(UIButton *)callToAction {
    callToAction.backgroundColor = HBArgb(0xFF2196F3);
    callToAction.layer.borderColor = HBArgb(0xFF1565C0).CGColor;
    callToAction.layer.borderWidth = 1;
    [callToAction setTitleColor:UIColor.whiteColor
                       forState:UIControlStateNormal];
    callToAction.titleLabel.textAlignment = NSTextAlignmentCenter;
    // The SDK owns the tap; the button never runs its own action.
    callToAction.userInteractionEnabled = YES;
}

- (void)ro_bindAssets {
    // A headline the creative never sent is not a blank line to reserve:
    // an absent asset leaves no trace, exactly as the advertiser, body and
    // rating already do.
    NSString *headlineValue = [_nativeAd.headline
            stringByTrimmingCharactersInSet:
                    NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (headlineValue.length == 0) {
        _headline.ro_gone = YES;
        _headline.hidden = YES;
    } else {
        _headline.text = _nativeAd.headline;
    }

    UIImage *iconImage = _nativeAd.icon.image;
    if (iconImage == nil) {
        _icon.ro_gone = YES;
        _icon.hidden = YES;
    } else {
        _icon.image = iconImage;
    }

    NSString *advertiserValue = _nativeAd.advertiser;
    if (advertiserValue.length == 0) {
        _advertiser.ro_gone = YES;
        _advertiser.hidden = YES;
    } else {
        _advertiser.text = advertiserValue;
    }

    NSDecimalNumber *starRatingValue = _nativeAd.starRating;
    double stars = starRatingValue.doubleValue;
    if (starRatingValue == nil || stars <= 0 || isnan(stars) || isinf(stars)) {
        _starRating.ro_gone = YES;
        _starRating.hidden = YES;
    } else {
        _starRating.rating = MAX(0, MIN(5, stars));
    }

    NSString *bodyValue = _nativeAd.body;
    if (bodyValue.length == 0) {
        _body.ro_gone = YES;
        _body.hidden = YES;
    } else {
        _body.text = bodyValue;
    }

    NSString *callToActionValue = _nativeAd.callToAction;
    if (callToActionValue.length == 0) {
        _callToAction.ro_gone = YES;
        _callToAction.hidden = YES;
    } else {
        [_callToAction setTitle:callToActionValue
                       forState:UIControlStateNormal];
    }
}

- (UIImage *)ro_findFallbackMediaImage {
    for (GADNativeAdImage *image in _nativeAd.images) {
        if (image.image != nil) return image.image;
    }
    return nil;
}

- (CGFloat)ro_mediaAspectRatio {
    _mediaAspectReported = YES;
    if (_fallbackMediaImage != nil
            && _fallbackMediaImage.size.width > 0
            && _fallbackMediaImage.size.height > 0) {
        return _fallbackMediaImage.size.width
                / _fallbackMediaImage.size.height;
    }

    GADMediaContent *mediaContent = _nativeAd.mediaContent;
    if (mediaContent != nil) {
        if (!mediaContent.hasVideoContent) {
            UIImage *mainImage = mediaContent.mainImage;
            if (mainImage != nil
                    && mainImage.size.width > 0
                    && mainImage.size.height > 0) {
                return mainImage.size.width / mainImage.size.height;
            }
        }

        CGFloat aspectRatio = mediaContent.aspectRatio;
        if (aspectRatio > 0
                && !isnan(aspectRatio)
                && !isinf(aspectRatio)) {
            return aspectRatio;
        }
    }

    _mediaAspectReported = NO;
    return kRODefaultMediaAspectRatio;
}

// ---------------------------------------------------------------------------
// Responsive collapsible ladder - a direct transcription: shed body lines,
// then body, then rating, then advertiser until the minimum media fits, and
// afterwards binary-search the largest uniform scale the natural media
// still tolerates.
// ---------------------------------------------------------------------------

- (void)ro_configureResponsiveCollapsibleContentWithPanelHeight:
        (CGFloat)panelHeight {
    // The strip-avoiding attempt comes first: the media inset like every
    // other element and the control strip's band kept above it, so close and
    // timer overlay nothing. Shrinking spends the call to action, the icon
    // and the paddings before the attempt surrenders; only when even those
    // floors cannot host the content does the layout fall back to bleeding
    // the media edge to edge under the strip.
    if (_hasDisplayableMedia) {
        _mediaView.ro_layoutMargins = UIEdgeInsetsZero;
        _mediaAvoidsControlStrip = YES;
        BOOL contentFits =
                [self ro_runCollapsibleFitPipelineWithPanelHeight:panelHeight];
        if (!contentFits) {
            [self ro_applyStripAvoidingFloors];
            contentFits = [self
                    ro_contentFitsWithMinimumMediaForPanelHeight:panelHeight];
        }
        if (contentFits) {
            // The compact chrome is not a last resort but the standing
            // dress: the height it frees goes straight to the media. The
            // half panel then runs the same maximiser as the full screen.
            [self ro_applyStripAvoidingFloors];
            [self ro_enableControlAvoidanceWithPanelHeight:panelHeight];
            return;
        }

        _mediaAvoidsControlStrip = NO;
        _mediaView.ro_layoutMargins = UIEdgeInsetsMake(
                0, -kROHorizontalPadding, 0, -kROHorizontalPadding);
        [self ro_restoreOptionalRows];
    }
    [self ro_runCollapsibleFitPipelineWithPanelHeight:panelHeight];
}

- (BOOL)ro_runCollapsibleFitPipelineWithPanelHeight:(CGFloat)panelHeight {
    _headline.maxLines = kROCollapsibleHeadlineMaxLines;
    _body.maxLines = kROMaxBodyLineCount;
    [self ro_applyResponsiveContentScale:0];

    BOOL contentFits =
            [self ro_contentFitsWithMinimumMediaForPanelHeight:panelHeight];
    while (!_body.ro_gone
            && _body.maxLines > kROMinBodyLineCount
            && !contentFits) {
        _body.maxLines = _body.maxLines - 1;
        contentFits = [self
                ro_contentFitsWithMinimumMediaForPanelHeight:panelHeight];
    }

    if (!contentFits && !_body.ro_gone) {
        _body.ro_gone = YES;
        _body.hidden = YES;
        contentFits = [self
                ro_contentFitsWithMinimumMediaForPanelHeight:panelHeight];
    }
    if (!contentFits && !_starRating.ro_gone) {
        _starRating.ro_gone = YES;
        _starRating.hidden = YES;
        contentFits = [self
                ro_contentFitsWithMinimumMediaForPanelHeight:panelHeight];
    }
    if (!contentFits && !_advertiser.ro_gone) {
        _advertiser.ro_gone = YES;
        _advertiser.hidden = YES;
        contentFits = [self
                ro_contentFitsWithMinimumMediaForPanelHeight:panelHeight];
    }

    if (!contentFits) return NO;
    if (![self ro_contentFitsWithNaturalMediaForPanelHeight:panelHeight]) {
        return YES;
    }

    [self ro_applyResponsiveContentScale:1];
    if ([self ro_contentFitsWithNaturalMediaForPanelHeight:panelHeight]) {
        return YES;
    }

    CGFloat minimumScale = 0;
    CGFloat maximumScale = 1;
    for (NSInteger iteration = 0;
         iteration < kROResponsiveScaleSearchIterations;
         ++iteration) {
        CGFloat candidateScale = (minimumScale + maximumScale) / 2;
        [self ro_applyResponsiveContentScale:candidateScale];
        if ([self ro_contentFitsWithNaturalMediaForPanelHeight:panelHeight]) {
            minimumScale = candidateScale;
        } else {
            maximumScale = candidateScale;
        }
    }
    [self ro_applyResponsiveContentScale:minimumScale];
    return YES;
}

// The rail owns a fixed height beside the media, so the fit that matters is
// the rail's own stack against that height. The search mirrors the stacked
// panel's: the largest text that keeps every element inside the rail,
// shedding body lines and then optional rows when even the floor does not
// fit.
- (void)ro_configureResponsiveSideRailWithPanelHeight:(CGFloat)panelHeight {
    if (_sideRail == nil) return;

    _headline.maxLines = kROCollapsibleHeadlineMaxLines;
    _body.maxLines = kROMaxBodyLineCount;
    [self ro_applyRailContentScale:0];

    BOOL railFits = [self ro_sideRailFitsWithPanelHeight:panelHeight];
    while (!railFits
            && !_body.ro_gone
            && _body.maxLines > kROMinBodyLineCount) {
        _body.maxLines = _body.maxLines - 1;
        railFits = [self ro_sideRailFitsWithPanelHeight:panelHeight];
    }
    if (!railFits && !_body.ro_gone) {
        _body.ro_gone = YES;
        _body.hidden = YES;
        railFits = [self ro_sideRailFitsWithPanelHeight:panelHeight];
    }
    if (!railFits && !_starRating.ro_gone) {
        _starRating.ro_gone = YES;
        _starRating.hidden = YES;
        railFits = [self ro_sideRailFitsWithPanelHeight:panelHeight];
    }
    if (!railFits && !_advertiser.ro_gone) {
        _advertiser.ro_gone = YES;
        _advertiser.hidden = YES;
        railFits = [self ro_sideRailFitsWithPanelHeight:panelHeight];
    }
    if (!railFits) return;

    // Text no larger than the rail can wear: the ceiling follows the
    // rail's width, so a narrow column keeps modest sizes even with height
    // to spare.
    CGFloat railOuterWidth = MAX(
            0
          , UIScreen.mainScreen.bounds.size.width - _sideMediaWidthPx);
    CGFloat railScaleCap = MAX(
            0
          , MIN(
                1
              , (railOuterWidth - kRORailScaleCapMinWidth)
                        / kRORailScaleCapRange));
    CGFloat railScale = railScaleCap;
    [self ro_applyRailContentScale:railScale];
    if (![self ro_sideRailFitsWithPanelHeight:panelHeight]) {
        CGFloat minimumScale = 0;
        CGFloat maximumScale = railScaleCap;
        for (NSInteger iteration = 0;
             iteration < kROResponsiveScaleSearchIterations;
             ++iteration) {
            CGFloat candidateScale = (minimumScale + maximumScale) / 2;
            [self ro_applyRailContentScale:candidateScale];
            if ([self ro_sideRailFitsWithPanelHeight:panelHeight]) {
                minimumScale = candidateScale;
            } else {
                maximumScale = candidateScale;
            }
        }
        railScale = minimumScale;
        [self ro_applyRailContentScale:railScale];
    }

    // Whole text before size, size before scrolling: give lines while they
    // fit, and when a text still cannot show itself whole, trade the scale
    // down a step and try again. Only what survives this may ever scroll.
    CGFloat railContentWidth = MAX(
            0
          , railOuterWidth
                - 2 * MAX(
                    kRORailMinSidePadding
                  , MIN(
                        kROHorizontalPadding
                      , railOuterWidth * kRORailSidePaddingRatio)));
    for (NSInteger attempt = 0;
         attempt <= kRORailFullTextSteps;
         ++attempt) {
        [self ro_growTextLines:_headline
                       maximum:kRORailHeadlineMaxLineCount
                   panelHeight:panelHeight];
        [self ro_growTextLines:_body
                       maximum:kRORailBodyMaxLineCount
                   panelHeight:panelHeight];
        BOOL allWhole =
                [self ro_railTextWhole:_headline width:railContentWidth]
                && [self ro_railTextWhole:_body width:railContentWidth]
                && [self ro_railTextWhole:_advertiser
                                    width:railContentWidth];
        if (allWhole) break;
        if (railScale <= 0) break;

        railScale = MAX(0, railScale - kRORailFullTextScaleStep);
        [self ro_applyRailContentScale:railScale];
    }

    [self ro_growRailIconWithPanelHeight:panelHeight
                          railOuterWidth:railOuterWidth];
}

// The rail's own dress code on top of the shared scale: the button keeps
// the avoidance floor and the rows drop their optional padding - a narrow
// column spends its ground on text and media, not on chrome.
- (void)ro_applyRailContentScale:(CGFloat)scale {
    [self ro_applyResponsiveContentScale:scale];
    _callToAction.ro_minimumSize =
            CGSizeMake(0, kROAvoidCallToActionHeight);
    _identityRow.ro_padding = UIEdgeInsetsZero;
    _body.ro_padding = UIEdgeInsetsZero;
}

- (void)ro_growTextLines:(ROAdTextLabel *)text
                 maximum:(NSInteger)maximumLineCount
             panelHeight:(CGFloat)panelHeight {
    if (text == nil || text.ro_gone || text.hidden) return;

    while (text.maxLines < maximumLineCount) {
        text.maxLines = text.maxLines + 1;
        if (![self ro_sideRailFitsWithPanelHeight:panelHeight]) {
            text.maxLines = text.maxLines - 1;
            return;
        }
    }
}

- (BOOL)ro_railTextWhole:(ROAdTextLabel *)text
                   width:(CGFloat)width {
    if (text == nil || text.ro_gone || text.hidden || width <= 0) {
        return YES;
    }
    return [text ro_showsEntireTextForWidth:width];
}

// Height that remains after the text has everything belongs to the icon:
// it grows into the slack, never past the rail's proportion.
- (void)ro_growRailIconWithPanelHeight:(CGFloat)panelHeight
                        railOuterWidth:(CGFloat)railOuterWidth {
    if (_iconHero || _icon.ro_gone || _icon.superview != _sideRail) return;
    if (![self ro_sideRailFitsWithPanelHeight:panelHeight]) return;

    CGFloat maximumIconSize = railOuterWidth * kRORailIconMaxWidthRatio;
    while (_icon.ro_layoutWidth + kRORailIconGrowthStep
            <= maximumIconSize) {
        _icon.ro_layoutWidth += kRORailIconGrowthStep;
        _icon.ro_layoutHeight += kRORailIconGrowthStep;
        if (![self ro_sideRailFitsWithPanelHeight:panelHeight]) {
            _icon.ro_layoutWidth -= kRORailIconGrowthStep;
            _icon.ro_layoutHeight -= kRORailIconGrowthStep;
            break;
        }
    }
}

- (BOOL)ro_sideRailFitsWithPanelHeight:(CGFloat)panelHeight {
    [_sideRail ro_measureWithWidthSpec:
                    HBMeasureSpecMake(
                            HBMeasureSpecExactly
                          , MAX(
                                0
                              , UIScreen.mainScreen.bounds.size.width
                                        - _sideMediaWidthPx))
                            heightSpec:
                    HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    return _sideRail.ro_measuredSize.height <= panelHeight;
}

// The floors the strip-avoiding layout may fall to before it surrenders: a
// shorter call to action, a smaller icon and no optional padding, each a
// price worth paying to keep the corner controls off the media.
- (void)ro_applyStripAvoidingFloors {
    _callToAction.ro_minimumSize =
            CGSizeMake(0, kROAvoidCallToActionHeight);
    if (!_iconHero && !_sideMediaLayout) {
        _icon.ro_layoutWidth = kROAvoidIconSize;
        _icon.ro_layoutHeight = kROAvoidIconSize;
    }
}

- (void)ro_restoreOptionalRows {
    if (_nativeAd.advertiser.length > 0) {
        _advertiser.ro_gone = NO;
        _advertiser.hidden = NO;
    }
    NSDecimalNumber *starRatingValue = _nativeAd.starRating;
    double stars = starRatingValue.doubleValue;
    if (starRatingValue != nil && stars > 0 && !isnan(stars)
            && !isinf(stars)) {
        _starRating.ro_gone = NO;
        _starRating.hidden = NO;
    }
    if (_nativeAd.body.length > 0) {
        _body.ro_gone = NO;
        _body.hidden = NO;
    }
    _body.maxLines = kROMaxBodyLineCount;
}

static CGFloat HBInterpolate(CGFloat minimum, CGFloat maximum, CGFloat scale) {
    return minimum + (maximum - minimum) * scale;
}

- (void)ro_applyResponsiveContentScale:(CGFloat)scale {
    CGFloat resolvedScale = MAX(0, MIN(1, scale));
    _headline.font = [UIFont boldSystemFontOfSize:
            HBInterpolate(
                    kROMinHeadlineTextSize
                  , kROMaxHeadlineTextSize
                  , resolvedScale)];
    _advertiser.font = [UIFont systemFontOfSize:
            HBInterpolate(
                    kROMinAdvertiserTextSize
                  , kROMaxAdvertiserTextSize
                  , resolvedScale)];
    _body.font = [UIFont systemFontOfSize:
            HBInterpolate(
                    kROMinBodyTextSize
                  , kROMaxBodyTextSize
                  , resolvedScale)];
    _callToAction.titleLabel.font = [UIFont boldSystemFontOfSize:
            HBInterpolate(
                    kROMinCallToActionTextSize
                  , kROMaxCallToActionTextSize
                  , resolvedScale)];

    CGFloat identityVerticalPadding = round(
            HBInterpolate(
                    kROMinIdentityVerticalPadding
                  , kROMaxIdentityVerticalPadding
                  , resolvedScale));
    _identityRow.ro_padding = UIEdgeInsetsMake(
            identityVerticalPadding, 0, identityVerticalPadding, 0);
    _body.ro_padding = UIEdgeInsetsMake(
            0
          , 0
          , round(HBInterpolate(
                    kROMinBodyBottomPadding
                  , kROMaxBodyBottomPadding
                  , resolvedScale))
          , 0);

    if (!_iconHero && !_sideMediaLayout) {
        CGFloat iconSize = round(
                HBInterpolate(kROMinIconSize, kROMaxIconSize, resolvedScale));
        _icon.ro_layoutWidth = iconSize;
        _icon.ro_layoutHeight = iconSize;
        _icon.ro_layoutMargins = UIEdgeInsetsMake(0, 0, 0, kROIconGap);
    }

    _callToAction.ro_minimumSize = CGSizeMake(
            0
          , round(HBInterpolate(
                    kROMinCallToActionHeight
                  , kROMaxCallToActionHeight
                  , resolvedScale)));
}

- (BOOL)ro_shouldUseSideMediaForPanelHeight:(CGFloat)panelHeight {
    if (_mediaAspectRatio >= kROSideMediaMaxAspect) return NO;
    if (panelHeight > UIScreen.mainScreen.bounds.size.width
            * kROSideMediaMaxPanelHeightRatio) {
        return NO;
    }

    CGFloat mediaWidth = [self ro_sideMediaWidthForPanelHeight:panelHeight];
    CGFloat railWidth =
            UIScreen.mainScreen.bounds.size.width - mediaWidth;
    return mediaWidth >= _minimumMediaSize
            && railWidth >= kROSideMediaMinRail;
}

- (CGFloat)ro_sideMediaWidthForPanelHeight:(CGFloat)panelHeight {
    return MIN(
            round(panelHeight * _mediaAspectRatio)
          , round(UIScreen.mainScreen.bounds.size.width
                    * kROSideMediaMaxWidthShare));
}

- (CGFloat)ro_contentWidth {
    return MAX(
            0
          , UIScreen.mainScreen.bounds.size.width - 2 * kROHorizontalPadding);
}

- (BOOL)ro_contentFitsWithMinimumMediaForPanelHeight:(CGFloat)panelHeight {
    return [self ro_contentFitsWithMediaHeight:_minimumMediaSize
                                   panelHeight:panelHeight];
}

- (BOOL)ro_contentFitsWithNaturalMediaForPanelHeight:(CGFloat)panelHeight {
    CGFloat contentWidth = [self ro_contentWidth];
    CGFloat naturalMediaHeight = MAX(
            _minimumMediaSize
          , round(contentWidth / _mediaAspectRatio));
    return [self ro_contentFitsWithMediaHeight:naturalMediaHeight
                                   panelHeight:panelHeight];
}

- (BOOL)ro_contentFitsWithMediaHeight:(CGFloat)mediaHeight
                          panelHeight:(CGFloat)panelHeight {
    CGFloat contentWidth = [self ro_contentWidth];
    if (_hasDisplayableMedia && !_sideMediaLayout) {
        CGFloat resolvedMediaHeight = MAX(_minimumMediaSize, mediaHeight);
        CGFloat resolvedMediaWidth = MIN(
                contentWidth
              , MAX(
                    _minimumMediaSize
                  , round(resolvedMediaHeight * _mediaAspectRatio)));
        _mediaView.ro_layoutWidth = resolvedMediaWidth;
        _mediaView.ro_layoutHeight = resolvedMediaHeight;
        _mediaView.ro_layoutGravity = HBGravityCenterHorizontal;
    }

    [_contentColumn ro_measureWithWidthSpec:
                    HBMeasureSpecMake(
                            HBMeasureSpecExactly
                          , contentWidth + 2 * kROHorizontalPadding)
                                 heightSpec:
                    HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    return _contentColumn.ro_measuredSize.height <= panelHeight;
}

- (void)ro_resolveContentHeightWithRequestedPanelHeight:
        (CGFloat)requestedPanelHeight {
    CGSize screen = UIScreen.mainScreen.bounds.size;
    CGFloat contentWidth = [self ro_contentWidth];
    [_contentColumn ro_measureWithWidthSpec:
                    HBMeasureSpecMake(HBMeasureSpecExactly, screen.width)
                                 heightSpec:
                    HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    if (_hasDisplayableMedia && !_sideMediaLayout
            && !_controlAvoidanceActive) {
        // Controls and badges draw on top with their own opaque backgrounds,
        // and with media as the first child the strip can only ever cover
        // media, never text - so the inset's height belongs to the media.
        // The strip-avoiding layout keeps the inset instead: nothing of the
        // media sits under the controls there.
        if (!_mediaAvoidsControlStrip) {
            UIEdgeInsets columnInset = _contentColumn.ro_padding;
            columnInset.top = 0;
            _contentColumn.ro_padding = columnInset;
        }
        [_contentColumn ro_measureWithWidthSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, screen.width)
                                     heightSpec:
                        HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
        CGFloat lowerContentHeight = MAX(
                0
              , _contentColumn.ro_measuredSize.height - _minimumMediaSize);
        CGFloat panelHeight = _fullscreen
                ? screen.height
                : requestedPanelHeight;
        CGFloat availableMediaHeight = panelHeight - lowerContentHeight;
        // A strip-avoiding media is inset like every other element, so its
        // width basis is the content width, not the bleed's screen width.
        CGFloat mediaWidthBasis = _mediaAvoidsControlStrip
                ? contentWidth
                : screen.width;
        CGFloat naturalMediaHeight = MAX(
                _minimumMediaSize
              , round(mediaWidthBasis / _mediaAspectRatio));
        CGFloat resolvedMediaHeight = MAX(
                _minimumMediaSize
              , MIN(
                    naturalMediaHeight
                  , MAX(_minimumMediaSize, availableMediaHeight)));
        CGFloat resolvedMediaWidth = MIN(
                mediaWidthBasis
              , MAX(
                    _minimumMediaSize
                  , round(resolvedMediaHeight * _mediaAspectRatio)));
        _mediaView.ro_layoutWidth = resolvedMediaWidth;
        _mediaView.ro_layoutHeight = resolvedMediaHeight;
        _mediaView.ro_layoutGravity = HBGravityCenterHorizontal;
        [_contentColumn ro_measureWithWidthSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, screen.width)
                                     heightSpec:
                        HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    }

    CGFloat requiredPanelHeight = _contentColumn.ro_measuredSize.height;
    _resolvedPanelHeight = _fullscreen
            ? screen.height
            : MIN(
                    screen.height
                  , MAX(requestedPanelHeight, requiredPanelHeight));
}

// ---------------------------------------------------------------------------
// Layout - the explicit pass Android got from its view hierarchy. Runs the
// measure model over the content column, overlays the badges, aligns the
// controls to the attribution's edge, then applies the whole-or-scrolling
// rule to whatever the pass truncated.
// ---------------------------------------------------------------------------

- (void)layoutSubviews {
    [super layoutSubviews];
    if (_released || _nativeAdView == nil) return;

    CGRect bounds = self.bounds;
    CGFloat cutoutInset = _fullscreen ? self.safeAreaInsets.top : 0;
    CGRect adFrame = CGRectMake(
            0
          , cutoutInset
          , bounds.size.width
          , bounds.size.height - cutoutInset);
    _nativeAdView.frame = adFrame;

    if (_controlAvoidanceActive) {
        [self ro_recomputeMediaControlAvoidanceWithWidth:adFrame.size.width
                                                height:adFrame.size.height];
    }

    [_contentColumn ro_measureWithWidthSpec:
                    HBMeasureSpecMake(HBMeasureSpecExactly, adFrame.size.width)
                                 heightSpec:
                    HBMeasureSpecMake(HBMeasureSpecExactly, adFrame.size.height)];
    [_contentColumn ro_layoutWithFrame:
            CGRectMake(0, 0, adFrame.size.width, adFrame.size.height)];
    if (_fallbackMediaImageView != nil) {
        _fallbackMediaImageView.frame = _mediaView.bounds;
    }

    [_attribution ro_measureWithWidthSpec:
                    HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)
                               heightSpec:
                    HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    CGSize attributionSize = _attribution.ro_measuredSize;
    // In the side-media layout even the badge steps off the picture: it
    // hugs the media's right edge, and the left corner control then lines
    // up beside it.
    CGFloat attributionX = _sideMediaLayout
            ? _sideMediaWidthPx + kROControlGap
            : 0;
    _attribution.frame = CGRectMake(
            attributionX, 0, attributionSize.width, attributionSize.height);

    CGFloat adChoicesSize = MAX(kROAdChoicesReserveSize, kROMinBadgeSize);
    _adChoicesReserve.frame = CGRectMake(
            adFrame.size.width - adChoicesSize, 0, adChoicesSize, adChoicesSize);

    CGFloat controlSize = kROControlStripHeight;
    // The left control follows the attribution badge - which in the
    // side-media layout already stepped off the picture to the media's
    // edge. When the avoidance pass chose the edge placement, the controls
    // hug the panel's sides just below the badges instead of the top row.
    CGFloat leftControlInset;
    CGFloat rightControlInset;
    CGFloat controlY;
    if (_controlsAtEdgesBelowBadges) {
        leftControlInset = 0;
        rightControlInset = 0;
        controlY = cutoutInset
                + _attribution.frame.size.height
                + kROControlGap;
    } else {
        leftControlInset =
                CGRectGetMaxX(_attribution.frame) + kROControlGap;
        rightControlInset = kRORightControlInset;
        controlY = cutoutInset;
    }
    BOOL closeLeft = _closeOnLeft;
    BOOL numberLeft = _numberOpposite ? !closeLeft : closeLeft;
    _close.frame = CGRectMake(
            closeLeft
                    ? leftControlInset
                    : bounds.size.width - rightControlInset - controlSize
          , controlY
          , controlSize
          , controlSize);
    _countdown.frame = CGRectMake(
            numberLeft
                    ? leftControlInset
                    : bounds.size.width - rightControlInset - controlSize
          , controlY
          , controlSize
          , controlSize);

    [self ro_matchIconSizeToIdentityText];
    [self ro_keepTextWholeOrScrolling:_headline];
    [self ro_keepTextWholeOrScrolling:_body];
    [self ro_keepTextWholeOrScrolling:_advertiser];
}

// A probe: the column measured with the media collapsed, giving the
// height of everything below it. The media's layout values are restored
// before returning, and the caller owns re-measuring for real.
- (CGFloat)ro_measureLowerContentWithPanelWidth:(CGFloat)panelWidth {
    CGFloat previousWidth = _mediaView.ro_layoutWidth;
    CGFloat previousHeight = _mediaView.ro_layoutHeight;
    CGFloat previousWeight = _mediaView.ro_layoutWeight;
    _mediaView.ro_layoutWidth = 0;
    _mediaView.ro_layoutHeight = 0;
    _mediaView.ro_layoutWeight = 0;
    [_contentColumn ro_measureWithWidthSpec:
                    HBMeasureSpecMake(HBMeasureSpecExactly, panelWidth)
                                 heightSpec:
                    HBMeasureSpecMake(HBMeasureSpecUnspecified, 0)];
    CGFloat lowerContentHeight = _contentColumn.ro_measuredSize.height;
    _mediaView.ro_layoutWidth = previousWidth;
    _mediaView.ro_layoutHeight = previousHeight;
    _mediaView.ro_layoutWeight = previousWeight;
    return lowerContentHeight;
}

- (void)ro_enableControlAvoidanceWithPanelHeight:(CGFloat)panelHeight {
    _controlAvoidanceActive = YES;
    _avoidancePanelHeight = panelHeight;
    _contentColumn.ro_gravity =
            HBGravityBottom | HBGravityCenterHorizontal;
    _mediaView.ro_layoutMargins = UIEdgeInsetsZero;
}

// Control avoidance, the Android transcription: the stack hugs the bottom,
// four candidates compete - the media rising through the gap of the top
// control row, sitting below that row, rising between edge-hugging controls
// below the badges, or sitting below those - and the placement that leaves
// the media largest wins. The chosen band then becomes the media's box:
// the creative fits centred inside it and the ambient backdrop carries its
// colours to the edges. Badges may sit over media; the close and timer
// controls avoid it.
- (void)ro_recomputeMediaControlAvoidanceWithWidth:(CGFloat)panelWidth
                                          height:(CGFloat)panelHeight {
    if (panelWidth <= 0 || panelHeight <= 0) return;

    CGFloat controlSize = kROControlStripHeight;
    CGFloat badgeHeight = kROAttributionHeight;
    CGFloat controlGap = kROControlGap;

    // The lower stack's height, measured with the media collapsed.
    UIEdgeInsets columnPadding = _contentColumn.ro_padding;
    columnPadding.top = 0;
    columnPadding.bottom = 0;
    _contentColumn.ro_padding = columnPadding;
    CGFloat lowerContentHeight =
            [self ro_measureLowerContentWithPanelWidth:panelWidth];
    CGFloat availableHeight = MAX(0, panelHeight - lowerContentHeight);

    // Both corner controls share one spot until the countdown ends, so a
    // side is an obstacle when either of them lives there.
    BOOL numberLeft = _numberOpposite ? !_closeOnLeft : _closeOnLeft;
    BOOL leftOccupied = _closeOnLeft || numberLeft;
    BOOL rightOccupied = !_closeOnLeft || !numberLeft;
    // The media's field: its sides NEVER pass the panel's side padding -
    // the same 8pt every element below wears - its ceiling is the panel's
    // top edge, and rising into the control band adds the two control
    // columns as walls. Intervals run from wall to wall with no buffer:
    // touching without overlapping is the goal. Both control POSITIONS
    // count as walls whatever is currently visible, so the media never
    // re-anchors when the timer hands over to the close.
    CGFloat syncLeft = kROHorizontalPadding;
    CGFloat syncRight = panelWidth - kROHorizontalPadding;
    CGFloat topRowIntervalLeft = MAX(
            syncLeft
          , leftOccupied
                    ? kROAttributionWidth + controlGap + controlSize
                    : syncLeft);
    CGFloat topRowIntervalRight = MIN(
            syncRight
          , rightOccupied
                    ? panelWidth - kRORightControlInset - controlSize
                    : syncRight);
    CGFloat edgeIntervalLeft = MAX(
            syncLeft
          , leftOccupied ? controlSize : syncLeft);
    CGFloat edgeIntervalRight = MIN(
            syncRight
          , rightOccupied ? panelWidth - controlSize : syncRight);

    CGFloat candidateTops[4] = {
            0
          , controlSize
          , badgeHeight
          , badgeHeight + controlSize };
    CGFloat candidateLefts[4] = {
            topRowIntervalLeft
          , syncLeft
          , edgeIntervalLeft
          , syncLeft };
    CGFloat candidateRights[4] = {
            topRowIntervalRight
          , syncRight
          , edgeIntervalRight
          , syncRight };
    BOOL candidateEdges[4] = { NO, NO, YES, YES };
    // A reported ratio sizes the box to the creative itself: it grows
    // until it touches whichever limit binds, and the band's slack splits
    // so the block sits centred between the controls and the bottom edge.
    // Only a creative that never reported its proportions is handed the
    // whole band - no number exists to size or validate it - and renders
    // inside as it pleases on the black ground.
    // A box must touch a VISIBLE wall: the panel's top, the badge line,
    // the control columns or the sync padding edges. The line under the
    // control row is not a wall anyone can see, so a box whose only
    // contact is that line loses to a narrower one that visibly touches -
    // only when nothing touches at all does raw area decide.
    CGFloat bestScore = -1;
    CGFloat bestBoxWidth = MAX(panelWidth, _minimumMediaSize);
    CGFloat bestBoxHeight = MAX(
            _minimumMediaSize
          , availableHeight - kROMediaLowerSeam);
    CGFloat bestTop = 0;
    CGFloat bestIntervalLeft = 0;
    CGFloat bestIntervalWidth = panelWidth;
    BOOL bestAtEdges = NO;
    for (NSInteger pass = 0; pass < 2 && bestScore < 0; ++pass) {
        BOOL requireVisibleTouch = pass == 0;
        for (NSInteger index = 0; index < 4; ++index) {
            CGFloat top = candidateTops[index];
            CGFloat intervalLeft = MAX(0, candidateLefts[index]);
            CGFloat intervalRight =
                    MIN(panelWidth, candidateRights[index]);
            CGFloat intervalWidth = intervalRight - intervalLeft;
            if (intervalWidth < _minimumMediaSize) continue;

            CGFloat bandHeight =
                    availableHeight - top - kROMediaLowerSeam;
            if (bandHeight < _minimumMediaSize) continue;

            CGFloat boxWidth;
            CGFloat boxHeight;
            if (_mediaAspectReported) {
                boxHeight = MIN(
                        bandHeight
                      , round(intervalWidth / _mediaAspectRatio));
                boxWidth = MIN(
                        intervalWidth
                      , round(boxHeight * _mediaAspectRatio));
                if (boxWidth < _minimumMediaSize
                        || boxHeight < _minimumMediaSize) {
                    continue;
                }
                BOOL widthBound = boxWidth >= intervalWidth - 2;
                if (requireVisibleTouch
                        && !widthBound
                        && top > badgeHeight) {
                    continue;
                }
            } else {
                boxWidth = intervalWidth;
                boxHeight = bandHeight;
            }

            CGFloat score = boxWidth * boxHeight;
            if (score > bestScore) {
                bestScore = score;
                bestBoxWidth = boxWidth;
                bestBoxHeight = boxHeight;
                bestTop = top;
                bestIntervalLeft = intervalLeft;
                bestIntervalWidth = intervalWidth;
                bestAtEdges = candidateEdges[index];
            }
        }
    }

    CGFloat slack = _mediaAspectReported
            ? MAX(0, availableHeight
                    - bestTop
                    - kROMediaLowerSeam
                    - bestBoxHeight)
            : 0;
    // Slack feeds the text before it pads the void: the body takes more
    // whole lines while slack remains, so a clipped line never sits beside
    // empty space. A line already scrolling is left alone.
    if (!_body.ro_gone && !_body.hidden && !_body.marquee) {
        while (slack > 0
                && _body.maxLines < kRORailBodyMaxLineCount
                && ![_body ro_showsEntireTextForWidth:
                        [self ro_contentWidth]]) {
            _body.maxLines = _body.maxLines + 1;
            CGFloat grownLower =
                    [self ro_measureLowerContentWithPanelWidth:panelWidth];
            CGFloat grownAvailable = MAX(0, panelHeight - grownLower);
            CGFloat grownSlack =
                    grownAvailable - bestTop - bestBoxHeight;
            if (grownSlack < 0) {
                _body.maxLines = _body.maxLines - 1;
                [self ro_measureLowerContentWithPanelWidth:panelWidth];
                break;
            }
            lowerContentHeight = grownLower;
            availableHeight = grownAvailable;
            slack = grownSlack;
        }
    }
    NSLog(@"%@: Avoidance: panel=%gx%g lower=%g avail=%g box=%gx%g top=%g "
            "intervalLeft=%g slack=%g edges=%d"
          , kROTag, panelWidth, panelHeight, lowerContentHeight
          , availableHeight, bestBoxWidth, bestBoxHeight, bestTop
          , bestIntervalLeft, slack, bestAtEdges);
    _controlsAtEdgesBelowBadges = bestAtEdges;
    columnPadding.top = bestTop;
    columnPadding.bottom = slack / 2;
    _contentColumn.ro_padding = columnPadding;
    _mediaView.ro_layoutWidth = bestBoxWidth;
    _mediaView.ro_layoutHeight = bestBoxHeight;
    _mediaView.ro_layoutWeight = 0;
    // Centred within the free interval. The margin is measured from the
    // panel edge, so the column's own left padding is subtracted - negative
    // means the media bleeds through it, as it may.
    _mediaView.ro_layoutGravity = HBGravityLeft;
    UIEdgeInsets mediaMargins = UIEdgeInsetsZero;
    mediaMargins.left = bestIntervalLeft
            + (bestIntervalWidth - bestBoxWidth) / 2
            - _contentColumn.ro_padding.left;
    mediaMargins.bottom = kROMediaLowerSeam;
    _mediaView.ro_layoutMargins = mediaMargins;
}

- (void)ro_matchIconSizeToIdentityText {
    if (_icon.ro_gone || _sideMediaLayout) return;

    CGFloat textHeight = _identityText.ro_measuredSize.height;
    CGFloat rowWidth = _identityRow.ro_measuredSize.width;
    if (textHeight <= 0 || rowWidth <= 0) return;

    CGFloat minimumIconSize = MAX(
            kROMinIconSize
          , MAX(_icon.ro_layoutWidth, _icon.ro_layoutHeight));
    CGFloat maximumIconSize = MAX(
            minimumIconSize
          , MIN(kROMaxIconSize, round(rowWidth * kROMaxIconRowWidthRatio)));
    CGFloat resolvedIconSize = MAX(
            minimumIconSize
          , MIN(textHeight, maximumIconSize));
    if (_icon.ro_layoutWidth == resolvedIconSize
            && _icon.ro_layoutHeight == resolvedIconSize) {
        return;
    }

    _icon.ro_layoutWidth = resolvedIconSize;
    _icon.ro_layoutHeight = resolvedIconSize;
    [self setNeedsLayout];
}

// The same rule the in-feed layout follows: a value is shown whole, or on
// one line that scrolls at a smaller size - never cut.
- (void)ro_keepTextWholeOrScrolling:(ROAdTextLabel *)text {
    if (text == nil || text.ro_gone || text.hidden) return;

    NSValue *identity = [NSValue valueWithNonretainedObject:text];
    CGFloat width = text.bounds.size.width;
    if (width <= 0) return;

    if (text.marquee) {
        if ([_shrunkTexts containsObject:identity]) return;
        if ([text ro_showsEntireTextForWidth:width]
                && text.bounds.size.width > 0
                && [text sizeThatFits:
                        CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)].width <= width) {
            return;
        }
        [_shrunkTexts addObject:identity];
        text.font = [text.font fontWithSize:
                text.font.pointSize * kROMarqueeTextShrink];
        [self setNeedsLayout];
        return;
    }

    if ([text ro_showsEntireTextForWidth:width]) return;

    [_shrunkTexts addObject:identity];
    CGFloat shrunkSize = text.font.pointSize * kROMarqueeTextShrink;
    text.marquee = YES;
    text.font = [text.font fontWithSize:shrunkSize];
    [self setNeedsLayout];
}

// ---------------------------------------------------------------------------
// Countdown
// ---------------------------------------------------------------------------

- (void)ro_startCountdown {
    if (_countdown == nil || _close == nil) return;
    [_timer invalidate];
    _timer = nil;

    int64_t remainingMs = MAX(0, _countDownRemainingMs);
    _countdown.text = [NSString stringWithFormat:@"%lld"
          , (long long)((remainingMs + 999) / 1000)];
    _countdown.hidden = NO;
    _close.hidden = YES;
    if (remainingMs <= 0) {
        _countdown.hidden = YES;
        _close.hidden = NO;
        return;
    }

    NSDate *finishAt =
            [NSDate dateWithTimeIntervalSinceNow:remainingMs / 1000.0];
    __weak ROOverlayAdContentView *weakSelf = self;
    _timer = [NSTimer scheduledTimerWithTimeInterval:kROCountdownInterval
                                             repeats:YES
                                               block:^(NSTimer *timer) {
        ROOverlayAdContentView *strongSelf = weakSelf;
        if (strongSelf == nil) {
            [timer invalidate];
            return;
        }
        NSTimeInterval remaining = finishAt.timeIntervalSinceNow;
        strongSelf->_countDownRemainingMs =
                MAX(0, (int64_t)(remaining * 1000));
        if (remaining > 0) {
            strongSelf->_countdown.text =
                    [NSString stringWithFormat:@"%ld"
                          , (long)ceil(remaining)];
            return;
        }
        [timer invalidate];
        strongSelf->_timer = nil;
        strongSelf->_countDownRemainingMs = 0;
        strongSelf->_countdown.hidden = YES;
        strongSelf->_close.hidden = NO;
    }];
}

@end
