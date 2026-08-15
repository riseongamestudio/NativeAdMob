#import "ROInFeedAdPresentation.h"

#import "ROMeasureLayout.h"
#import "ROInFeedAdLayoutEngine.h"
#import "ROInFeedAdLayoutTypes.h"
#import "ROInFeedAdLayoutValidator.h"
#import "ROInFeedAdViewFactory.h"

static NSString *const kROTag = @"InFeed";

static const NSInteger kROMinStableLayoutPasses = 3;
static const NSTimeInterval kROMinFinalLayoutObservation = 0.08;
static const NSTimeInterval kROMaxFinalLayoutObservation = 0.5;
static const NSTimeInterval kRORootReadyRecheckDelay = 0.05;
static const NSTimeInterval kROMaxRootWait = 10.0;
static const uint32_t kROBackgroundRgb = 0x1B2029;

@implementation ROInFeedAdPresentation {
    __weak UIViewController *_hostViewController;
    GADNativeAd *_nativeAd;
    CGFloat _requestedX;
    CGFloat _requestedY;
    CGFloat _requestedWidth;
    CGFloat _requestedHeight;
    float _backgroundAlpha;
    __weak id<ROInFeedPresentationListener> _listener;

    ROInFeedAdViewFactory *_viewFactory;
    ROInFeedAdLayoutValidator *_validator;
    ROInFeedAdLayoutEngine *_layoutEngine;
    NSMutableArray<ROInFeedLayoutPlan *> *_layoutPlans;

    GADNativeAdView *_nativeAdView;
    ROInFeedAssetViews *_boundViews;
    ROInFeedLayoutPlan *_activePlan;
    UIImage *_mainImage;
    NSUInteger _nextLayoutPlanIndex;
    NSString *_lastLayoutFailure;

    CADisplayLink *_observationLink;
    NSDate *_finalObservationStartedAt;
    NSDate *_candidateObservationStartedAt;
    NSInteger _stablePasses;
    NSInteger _invalidStablePasses;
    int64_t _lastGeometrySignature;
    int64_t _lastInvalidGeometrySignature;

    BOOL _waitingForRoot;
    NSDate *_rootWaitStartedAt;
    BOOL _visibleRequested;
    BOOL _layoutReady;
    BOOL _readyNotified;
    BOOL _displayedNotified;
    BOOL _displayNotificationPending;
    BOOL _actualVisibilityKnown;
    BOOL _lastActualVisibility;
    BOOL _dismissed;
    BOOL _clickCommitted;
    NSString *_failureMessage;
}

- (instancetype)initWithHostViewController:(UIViewController *)hostViewController
                                  nativeAd:(GADNativeAd *)nativeAd
                                         x:(CGFloat)xPt
                                         y:(CGFloat)yPt
                                     width:(CGFloat)widthPt
                                    height:(CGFloat)heightPt
                           backgroundAlpha:(float)backgroundAlpha
                                  listener:(id<ROInFeedPresentationListener>)listener {
    self = [super initWithFrame:CGRectZero];
    if (self == nil) return nil;

    _hostViewController = hostViewController;
    _nativeAd = nativeAd;
    _requestedX = xPt;
    _requestedY = yPt;
    _requestedWidth = MAX(1, widthPt);
    _requestedHeight = MAX(1, heightPt);
    _backgroundAlpha = [ROInFeedAdPresentation
            ro_resolveBackgroundAlpha:backgroundAlpha];
    _listener = listener;
    _visibleRequested = YES;
    _layoutPlans = [NSMutableArray array];

    _viewFactory = [[ROInFeedAdViewFactory alloc]
            initWithNativeAd:nativeAd
             slotShortSidePt:MIN(_requestedWidth, _requestedHeight)];
    _validator = [[ROInFeedAdLayoutValidator alloc]
            initWithNativeAd:nativeAd
                 viewFactory:_viewFactory];
    _layoutEngine = [[ROInFeedAdLayoutEngine alloc]
            initWithNativeAd:nativeAd
                  requestedX:xPt
                  requestedY:yPt
              requestedWidth:_requestedWidth
             requestedHeight:_requestedHeight
                 viewFactory:_viewFactory
                   validator:_validator];

    self.backgroundColor = [[UIColor
            colorWithRed:((kROBackgroundRgb >> 16) & 0xFF) / 255.0
                   green:((kROBackgroundRgb >> 8) & 0xFF) / 255.0
                    blue:(kROBackgroundRgb & 0xFF) / 255.0
                   alpha:1]
            colorWithAlphaComponent:_backgroundAlpha];
    self.clipsToBounds = YES;

    [NSNotificationCenter.defaultCenter
            addObserver:self
               selector:@selector(ro_applicationDidBecomeActive)
                   name:UIApplicationDidBecomeActiveNotification
                 object:nil];
    return self;
}

- (void)dealloc {
    [NSNotificationCenter.defaultCenter removeObserver:self];
    [_observationLink invalidate];
}

- (NSString *)failureMessage {
    return _failureMessage;
}

- (void)commitAdClick {
    _clickCommitted = YES;
}

- (void)ro_applicationDidBecomeActive {
    // The Android latch released on window visibility because the panel
    // never takes focus; here the overlay the click opened has gone away.
    _clickCommitted = NO;
}

// The same latch the full-screen container uses: once a click is committed
// every further touch is swallowed until the slot comes back.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    UIView *hit = [super hitTest:point withEvent:event];
    if (_clickCommitted && hit != nil) return self;
    return hit;
}

- (BOOL)ro_fail:(NSString *)message {
    [self ro_recordFailure:message];
    return NO;
}

- (void)ro_recordFailure:(NSString *)message {
    if (_failureMessage != nil) return;
    _failureMessage = message;
    NSLog(@"%@: %@", kROTag, message);
}

- (UIView *)ro_contentRoot {
    return _hostViewController.view;
}

- (BOOL)ro_isContentRootReady {
    UIView *contentRoot = [self ro_contentRoot];
    return contentRoot != nil
            && contentRoot.window != nil
            && contentRoot.bounds.size.width > 0
            && contentRoot.bounds.size.height > 0;
}

- (BOOL)show {
    if (_dismissed) {
        return [self ro_fail:@"In-feed Show rejected because presentation "
                              "is dismissed"];
    }
    if (_nativeAd == nil) {
        return [self ro_fail:@"In-feed Show rejected because nativeAd is nil"];
    }
    if (_waitingForRoot) return YES;

    if (![self ro_isContentRootReady]) {
        [self ro_waitForContentRootReady];
        return YES;
    }

    UIView *contentRoot = [self ro_contentRoot];
    CGFloat screenWidth = contentRoot.bounds.size.width;
    CGFloat screenHeight = contentRoot.bounds.size.height;
    if (_nativeAd.headline.length == 0
            || _nativeAd.callToAction.length == 0) {
        return [self ro_fail:@"In-feed creative is missing required headline "
                              "or CTA"];
    }
    if ([_viewFactory hasUnrenderableIcon]) {
        return [self ro_fail:@"In-feed creative supplies an icon without a "
                              "drawable; the required icon cannot be "
                              "rendered safely"];
    }

    _mainImage = [_viewFactory findMainImage];
    BOOL hasVideo = [_viewFactory hasVideoContent];
    BOOL hasMainImage = [_viewFactory canRenderMainImage:_mainImage];
    [_layoutPlans removeAllObjects];
    [_layoutPlans addObjectsFromArray:
            [_layoutEngine choosePlansForScreenWidth:screenWidth
                                        screenHeight:screenHeight
                                            hasVideo:hasVideo
                                        hasMainImage:hasMainImage]];
    _nextLayoutPlanIndex = 0;
    _lastLayoutFailure = nil;
    if (_layoutPlans.count == 0) {
        NSString *rejectionSummary = [_layoutEngine describeLastRejections];
        return [self ro_fail:[NSString stringWithFormat:
                @"In-feed cannot build a policy-safe layout for %@; "
                 "hasVideo=%d; hasMainImage=%d%@"
              , [self ro_describeRequestedRect]
              , hasVideo
              , hasMainImage
              , rejectionSummary.length == 0
                        ? @""
                        : [NSString stringWithFormat:@"; rejected={%@}"
                                  , rejectionSummary]]];
    }

    if (![self ro_activateNextLayoutPlan]) {
        return [self ro_fail:[NSString stringWithFormat:
                @"In-feed could not bind any policy-safe layout for %@%@"
              , [self ro_describeRequestedRect]
              , [self ro_describeLastLayoutFailure]]];
    }

    self.hidden = YES;
    [contentRoot addSubview:self];
    if (![self ro_fitHostInsideContentRoot]) {
        return [self ro_fail:@"In-feed could not place its presentation view"];
    }
    _finalObservationStartedAt = [NSDate date];
    [self ro_beginObservingFinalAssetGeometry];
    return YES;
}

- (void)ro_waitForContentRootReady {
    if (_waitingForRoot) return;
    _waitingForRoot = YES;
    _rootWaitStartedAt = [NSDate date];
    [self ro_scheduleRootReadyRecheck];
}

- (void)ro_scheduleRootReadyRecheck {
    __weak ROInFeedAdPresentation *weakSelf = self;
    dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW
                  , (int64_t)(kRORootReadyRecheckDelay * NSEC_PER_SEC))
          , dispatch_get_main_queue()
          , ^{ [weakSelf ro_checkContentRootReady]; });
}

- (void)ro_checkContentRootReady {
    if (_dismissed || !_waitingForRoot) return;
    if (![self ro_isContentRootReady]) {
        // Bounded so a root that never settles ends as a normal dismissal
        // instead of parking the slot and blocking every later load.
        if (-[_rootWaitStartedAt timeIntervalSinceNow] >= kROMaxRootWait) {
            [self ro_recordFailure:[NSString stringWithFormat:
                    @"In-feed content root did not become ready within %gms"
                  , kROMaxRootWait * 1000]];
            [self ro_dismissWithNotify:YES removeFromParent:YES];
            return;
        }
        [self ro_scheduleRootReadyRecheck];
        return;
    }

    _waitingForRoot = NO;
    if (![self show]) [self ro_dismissWithNotify:YES removeFromParent:YES];
}

- (BOOL)ro_activateNextLayoutPlan {
    [self ro_destroyNativeAdView];
    while (_nextLayoutPlanIndex < _layoutPlans.count) {
        ROInFeedLayoutPlan *plan = _layoutPlans[_nextLayoutPlanIndex];
        ++_nextLayoutPlanIndex;
        @try {
            ROInFeedNativeAdViewResult *viewResult =
                    [_viewFactory buildNativeAdViewForPlan:plan
                                                 mainImage:_mainImage];
            _nativeAdView = viewResult.nativeAdView;
            _boundViews = viewResult.assetViews;
            _activePlan = plan;
            _nativeAdView.frame = CGRectMake(0, 0, plan.width, plan.height);
            [self addSubview:_nativeAdView];
            if (self.superview != nil
                    && ![self ro_fitHostInsideContentRoot]) {
                _lastLayoutFailure = [NSString stringWithFormat:
                        @"layout no longer fits content root: %@"
                      , ROInFeedDescribePlan(plan)];
                [self ro_destroyNativeAdView];
                continue;
            }
            [self ro_layoutBoundContent];
            return YES;
        } @catch (NSException *exception) {
            _lastLayoutFailure = [NSString stringWithFormat:
                    @"failed to bind %@: %@"
                  , ROInFeedDescribePlan(plan)
                  , exception.reason];
            NSLog(@"%@: %@", kROTag, _lastLayoutFailure);
            [self ro_destroyNativeAdView];
        }
    }
    return NO;
}

- (void)ro_destroyNativeAdView {
    if (_nativeAdView != nil) {
        [_nativeAdView removeFromSuperview];
        _nativeAdView.nativeAd = nil;
    }
    _nativeAdView = nil;
    _boundViews = nil;
    _activePlan = nil;
    _layoutReady = NO;
}

// Runs the measure model over the bound tree - the pass Android's view
// hierarchy ran on its own.
- (void)ro_layoutBoundContent {
    if (_nativeAdView == nil || _activePlan == nil) return;
    _nativeAdView.frame =
            CGRectMake(0, 0, _activePlan.width, _activePlan.height);
    for (UIView *child in _nativeAdView.subviews) {
        [child ro_measureWithWidthSpec:HBMeasureSpecMake(
                        HBMeasureSpecExactly, _activePlan.width)
                            heightSpec:HBMeasureSpecMake(
                        HBMeasureSpecExactly, _activePlan.height)];
        [child ro_layoutWithFrame:CGRectMake(
                0, 0, _activePlan.width, _activePlan.height)];
    }
}

- (void)ro_rejectActivePlanAndTryNext:(NSString *)reason {
    NSString *rejectedPlan = ROInFeedDescribePlan(_activePlan);
    _lastLayoutFailure = [NSString stringWithFormat:@"%@; rejected=%@"
          , reason, rejectedPlan];
    NSLog(@"%@: %@", kROTag, _lastLayoutFailure);
    _layoutReady = NO;
    self.hidden = YES;

    if ([self ro_activateNextLayoutPlan]) {
        NSLog(@"%@: In-feed trying fallback layout %@ after %@"
              , kROTag, ROInFeedDescribePlan(_activePlan), rejectedPlan);
        [self ro_beginObservingFinalAssetGeometry];
        return;
    }

    [self ro_recordFailure:[NSString stringWithFormat:
            @"In-feed exhausted %lu policy-safe layout candidate(s) for %@%@"
          , (unsigned long)_layoutPlans.count
          , [self ro_describeRequestedRect]
          , [self ro_describeLastLayoutFailure]]];
    [self ro_dismissWithNotify:YES removeFromParent:YES];
}

// ---------------------------------------------------------------------------
// Final geometry observation - the pre-draw loop, on a display link.
// ---------------------------------------------------------------------------

- (void)ro_beginObservingFinalAssetGeometry {
    [_observationLink invalidate];
    _candidateObservationStartedAt = [NSDate date];
    _stablePasses = 0;
    _invalidStablePasses = 0;
    _lastGeometrySignature = INT64_MIN;
    _lastInvalidGeometrySignature = INT64_MIN;
    _observationLink = [CADisplayLink
            displayLinkWithTarget:self
                         selector:@selector(ro_observationTick)];
    [_observationLink addToRunLoop:NSRunLoop.mainRunLoop
                           forMode:NSRunLoopCommonModes];
}

- (void)ro_stopObserving {
    [_observationLink invalidate];
    _observationLink = nil;
}

- (void)ro_observationTick {
    if (_dismissed || self.superview == nil) {
        [self ro_stopObserving];
        if (!_dismissed) [self ro_dismissWithNotify:YES removeFromParent:NO];
        return;
    }

    [self ro_layoutBoundContent];
    NSTimeInterval candidateObservation =
            -[_candidateObservationStartedAt timeIntervalSinceNow];
    NSTimeInterval totalObservation =
            -[_finalObservationStartedAt timeIntervalSinceNow];
    BOOL validationDeadlineReached =
            totalObservation >= kROMaxFinalLayoutObservation;
    BOOL hostFits = [self ro_fitHostInsideContentRoot];
    BOOL valid = hostFits
            && [_validator validateAssetGeometryForRoot:_nativeAdView
                                                  views:_boundViews
                                                   plan:_activePlan
                                             logFailure:validationDeadlineReached];
    if (!valid) {
        int64_t invalidSignature =
                [_validator geometrySignatureForHost:self
                                                root:_nativeAdView
                                               views:_boundViews];
        if (invalidSignature == _lastInvalidGeometrySignature) {
            ++_invalidStablePasses;
        } else {
            _lastInvalidGeometrySignature = invalidSignature;
            _invalidStablePasses = 1;
        }
        BOOL deterministicFailure =
                _invalidStablePasses >= kROMinStableLayoutPasses;
        BOOL invalidObservationComplete = deterministicFailure
                || candidateObservation >= kROMinFinalLayoutObservation;
        if (!invalidObservationComplete) {
            _stablePasses = 0;
            _lastGeometrySignature = INT64_MIN;
            return;
        }
        [self ro_stopObserving];
        NSString *validationReason = hostFits
                ? _validator.lastFailureReason
                : @"layout host is outside content root";
        [self ro_rejectActivePlanAndTryNext:[NSString stringWithFormat:
                @"In-feed rejected after final layout; required asset is "
                 "clipped, empty, or overlapping%@"
              , validationReason.length == 0
                        ? @""
                        : [NSString stringWithFormat:@": %@"
                                  , validationReason]]];
        return;
    }
    _invalidStablePasses = 0;
    _lastInvalidGeometrySignature = INT64_MIN;

    int64_t signature = [_validator geometrySignatureForHost:self
                                                        root:_nativeAdView
                                                       views:_boundViews];
    if (signature == _lastGeometrySignature) {
        ++_stablePasses;
    } else {
        _lastGeometrySignature = signature;
        _stablePasses = 1;
    }
    BOOL observedLongEnough =
            candidateObservation >= kROMinFinalLayoutObservation;
    if (_stablePasses < kROMinStableLayoutPasses || !observedLongEnough) {
        BOOL stabilizationDeadlineReached =
                candidateObservation >= kROMaxFinalLayoutObservation
                || (totalObservation >= kROMaxFinalLayoutObservation
                        && observedLongEnough);
        if (stabilizationDeadlineReached) {
            [self ro_stopObserving];
            [self ro_rejectActivePlanAndTryNext:
                    @"In-feed rejected after final layout; asset geometry "
                     "did not become stable"];
        }
        return;
    }

    [self ro_stopObserving];
    _layoutReady = YES;
    NSLog(@"%@: In-feed layout ready %@ for %@"
          , kROTag
          , ROInFeedDescribePlan(_activePlan)
          , [self ro_describeRequestedRect]);
    if (!_readyNotified && _listener != nil) {
        _readyNotified = YES;
        [_listener inFeedPresentationReady];
    }
    [self ro_applyRequestedVisibility];
}

// ---------------------------------------------------------------------------
// Visibility and geometry
// ---------------------------------------------------------------------------

- (BOOL)setVisible:(BOOL)visible {
    if (_dismissed) return NO;
    _visibleRequested = visible;
    [self ro_applyRequestedVisibility];
    return _layoutReady;
}

- (void)requestDisplayNotification {
    if (_dismissed) return;
    _displayedNotified = NO;
    [self ro_applyRequestedVisibility];
}

- (BOOL)setPositionX:(CGFloat)xPt y:(CGFloat)yPt {
    if (_dismissed) return NO;
    _requestedX = xPt;
    _requestedY = yPt;
    [_layoutEngine setPositionX:xPt y:yPt];
    return [self ro_fitHostInsideContentRoot];
}

- (void)ro_applyRequestedVisibility {
    if (_dismissed || !_layoutReady || self.superview == nil) return;

    if (!_visibleRequested) {
        self.hidden = YES;
        self.userInteractionEnabled = NO;
        [self ro_notifyActualVisibilityIfChanged];
        return;
    }

    self.userInteractionEnabled = YES;
    self.hidden = NO;
    // The slot came back into sight: the click latch releases here the way
    // the Android one released on window visibility.
    _clickCommitted = NO;
    [self ro_notifyActualVisibilityIfChanged];
    if (_displayedNotified || _displayNotificationPending) return;

    _displayNotificationPending = YES;
    __weak ROInFeedAdPresentation *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        ROInFeedAdPresentation *strongSelf = weakSelf;
        if (strongSelf == nil) return;
        strongSelf->_displayNotificationPending = NO;
        if (!strongSelf->_dismissed
                && strongSelf->_visibleRequested
                && strongSelf.window != nil
                && !strongSelf.hidden
                && strongSelf.superview != nil
                && strongSelf->_listener != nil) {
            strongSelf->_displayedNotified = YES;
            [strongSelf->_listener inFeedPresentationDisplayed];
        }
    });
}

- (BOOL)ro_isActuallyVisible {
    return !_dismissed
            && _layoutReady
            && _visibleRequested
            && self.superview != nil
            && self.window != nil
            && !self.hidden;
}

- (void)ro_notifyActualVisibilityIfChanged {
    BOOL isActuallyVisible = [self ro_isActuallyVisible];
    if (_actualVisibilityKnown
            && _lastActualVisibility == isActuallyVisible) {
        return;
    }
    _actualVisibilityKnown = YES;
    _lastActualVisibility = isActuallyVisible;
    [_listener inFeedPresentationActualVisibilityChanged:isActuallyVisible];
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    [self ro_notifyActualVisibilityIfChanged];
    if (self.window == nil && !_dismissed && self.superview == nil) {
        [self ro_dismissWithNotify:YES removeFromParent:NO];
    }
}

- (BOOL)ro_fitHostInsideContentRoot {
    UIView *contentRoot = [self ro_contentRoot];
    if (contentRoot == nil || _activePlan == nil) return NO;
    CGFloat rootWidth = contentRoot.bounds.size.width;
    CGFloat rootHeight = contentRoot.bounds.size.height;
    if (rootWidth <= 0
            || rootHeight <= 0
            || _activePlan.width > rootWidth
            || _activePlan.height > rootHeight) {
        return NO;
    }

    CGFloat actualX = HBClamp(_requestedX, 0, rootWidth - _activePlan.width);
    CGFloat actualY = HBClamp(_requestedY, 0, rootHeight - _activePlan.height);
    CGRect targetFrame = CGRectMake(
            actualX, actualY, _activePlan.width, _activePlan.height);
    if (!CGRectEqualToRect(self.frame, targetFrame)) {
        self.frame = targetFrame;
        [self ro_logAdjustmentIfNeededForX:actualX y:actualY];
    }
    return YES;
}

- (BOOL)isShowingPresentation {
    return !_dismissed && (_waitingForRoot || self.superview != nil);
}

- (void)dismissPresentation {
    [self ro_dismissWithNotify:YES removeFromParent:YES];
}

- (void)releasePresentation {
    [self ro_dismissWithNotify:NO removeFromParent:YES];
}

- (void)ro_dismissWithNotify:(BOOL)notify
            removeFromParent:(BOOL)removeFromParent {
    if (_dismissed) return;
    _dismissed = YES;
    [self ro_notifyActualVisibilityIfChanged];
    _waitingForRoot = NO;
    _layoutReady = NO;
    _visibleRequested = NO;
    _displayNotificationPending = NO;
    [self ro_stopObserving];

    if (removeFromParent) [self removeFromSuperview];
    [self ro_destroyNativeAdView];
    [_layoutPlans removeAllObjects];
    [_layoutEngine clear];

    if (notify) [_listener inFeedPresentationDismissed];
}

// ---------------------------------------------------------------------------
// Descriptions
// ---------------------------------------------------------------------------

// Unity lays the slot out in pixels and every policy threshold here is
// points; report both so a log line answers the dp question outright.
- (NSString *)ro_describeRequestedRect {
    CGFloat scale = MAX(1, UIScreen.mainScreen.nativeScale);
    return [NSString stringWithFormat:
            @"requested rect [%lld,%lld,%lld,%lld]px = [%lld,%lld]dp "
             "(density=%g)"
          , (long long)llround(_requestedX * scale)
          , (long long)llround(_requestedY * scale)
          , (long long)llround(_requestedWidth * scale)
          , (long long)llround(_requestedHeight * scale)
          , (long long)llround(_requestedWidth)
          , (long long)llround(_requestedHeight)
          , scale];
}

- (NSString *)ro_describeLastLayoutFailure {
    return _lastLayoutFailure.length == 0
            ? @""
            : [NSString stringWithFormat:@"; lastFailure=%@"
                      , _lastLayoutFailure];
}

- (void)ro_logAdjustmentIfNeededForX:(CGFloat)actualX y:(CGFloat)actualY {
    if (actualX == _requestedX
            && actualY == _requestedY
            && _activePlan.width == _requestedWidth
            && _activePlan.height == _requestedHeight) {
        return;
    }
    NSLog(@"%@: In-feed adjusted: requested=[%g,%g,%g,%g] "
           "actual=[%g,%g,%g,%g] template=%@ tier=%@ media=%@"
          , kROTag
          , _requestedX, _requestedY, _requestedWidth, _requestedHeight
          , actualX, actualY, _activePlan.width, _activePlan.height
          , ROInFeedTemplateName(_activePlan.layoutTemplate)
          , ROInFeedTierName(_activePlan.tier)
          , ROInFeedMediaName(_activePlan));
}

+ (float)ro_resolveBackgroundAlpha:(float)value {
    if (isnan(value) || isinf(value)) {
        NSLog(@"%@: In-feed backgroundAlpha is not finite; using 1", kROTag);
        return 1;
    }
    float clamped = MAX(0.0f, MIN(1.0f, value));
    if (clamped != value) {
        NSLog(@"%@: In-feed backgroundAlpha must be within [0,1]; "
               "clamped %g to %g", kROTag, value, clamped);
    }
    return clamped;
}

@end
