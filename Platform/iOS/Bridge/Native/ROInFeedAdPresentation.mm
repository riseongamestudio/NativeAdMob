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

// Elapsed time is measured on the clock that only goes forward, never on
// NSDate: a wall clock is a statement about what time it is, not about how
// long something has taken, and an NTP correction landing mid-wait would
// move a deadline that has nothing to do with the calendar. The same clock
// the rest of this port already keeps - and the exact counterpart of the
// SystemClock.uptimeMillis the Java side measures these three windows with,
// deep sleep excluded on both.
static NSTimeInterval RONow(void) {
    return [NSProcessInfo processInfo].systemUptime;
}

// Live in-feed views in the order they were created, oldest first. Weak, so
// a view that goes away drops out on its own rather than being kept alive by
// the bookkeeping meant to order it.
static NSPointerArray *ROLiveInFeedViews = nil;

static void *kRORootBoundsContext = &kRORootBoundsContext;


@implementation ROInFeedAdPresentation {
    __weak UIViewController *_hostViewController;
    GADNativeAd *_nativeAd;
    CGFloat _requestedX;
    CGFloat _requestedY;
    CGFloat _requestedWidth;
    CGFloat _requestedHeight;
    int32_t _backgroundColor;
    CGFloat _roundCorner;
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
    __weak CALayer *_observedRootLayer;
    CGSize _observedRootSize;
    BOOL _appActive;
    NSTimeInterval _finalObservationStartedAt;
    NSTimeInterval _candidateObservationStartedAt;
    NSInteger _stablePasses;
    NSInteger _invalidStablePasses;
    int64_t _lastGeometrySignature;
    int64_t _lastInvalidGeometrySignature;

    BOOL _waitingForRoot;
    NSTimeInterval _rootWaitStartedAt;
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

// The array only ever grows by one per in-feed shown, and shrinks as they
// are dismissed, so the walk is over a handful of entries. Entries emptied
// by ARC are stepped over rather than trusted to -compact, which does
// nothing on an array that has not been mutated since.
+ (UIView *)ro_frontmostInFeedView {
    for (id view in ROLiveInFeedViews) {
        if (view != nil) return (UIView *)view;
    }
    return nil;
}

- (instancetype)initWithHostViewController:(UIViewController *)hostViewController
                                  nativeAd:(GADNativeAd *)nativeAd
                                         x:(CGFloat)xPt
                                         y:(CGFloat)yPt
                                     width:(CGFloat)widthPt
                                    height:(CGFloat)heightPt
                           backgroundColor:(int32_t)backgroundColor
                               roundCorner:(CGFloat)roundCornerPt
                                  listener:(id<ROInFeedPresentationListener>)listener {
    self = [super initWithFrame:CGRectZero];
    if (self == nil) return nil;

    _hostViewController = hostViewController;
    _nativeAd = nativeAd;
    _requestedX = xPt;
    _requestedY = yPt;
    _requestedWidth = MAX(1, widthPt);
    _requestedHeight = MAX(1, heightPt);
    _backgroundColor = backgroundColor;
    // A radius past half the short side is a pill that no longer has
    // straight edges to inset from; the cell caps it there.
    _roundCorner = MAX(
            0
          , MIN(roundCornerPt, MIN(_requestedWidth, _requestedHeight) / 2));
    _listener = listener;
    _visibleRequested = YES;
    _layoutPlans = [NSMutableArray array];

    _viewFactory = [[ROInFeedAdViewFactory alloc]
            initWithNativeAd:nativeAd
             slotShortSidePt:MIN(_requestedWidth, _requestedHeight)
               roundCornerPt:_roundCorner];
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

    // Whole from the caller. A fully transparent colour is a real answer
    // here, not an unset value - a feed cell that wants no backdrop of its
    // own asks for exactly that.
    self.backgroundColor = [UIColor
            colorWithRed:(((uint32_t)_backgroundColor >> 16) & 0xFF) / 255.0
                   green:(((uint32_t)_backgroundColor >> 8) & 0xFF) / 255.0
                    blue:((uint32_t)_backgroundColor & 0xFF) / 255.0
                   alpha:(((uint32_t)_backgroundColor >> 24) & 0xFF) / 255.0];
    // The rounded backdrop doubles as the clip (clipsToBounds follows the
    // layer's corner radius): everything inside the cell is cut to the
    // same curve, which is what lets the scrim layout's background picture
    // fill the cell edge to edge and still end at the corner. The assets
    // in front keep clear of the curve on their own (the view factory's
    // corner inset). A transparent colour still clips - the shape is the
    // layer's, not the paint's.
    self.layer.cornerRadius = _roundCorner;
    self.clipsToBounds = YES;

    // Read once rather than assumed: a slot built while the app is coming
    // back from the background would otherwise start out believing it is on
    // screen, and start charging dwell for it.
    _appActive = UIApplication.sharedApplication.applicationState
            == UIApplicationStateActive;
    [NSNotificationCenter.defaultCenter
            addObserver:self
               selector:@selector(ro_applicationDidBecomeActive)
                   name:UIApplicationDidBecomeActiveNotification
                 object:nil];
    [NSNotificationCenter.defaultCenter
            addObserver:self
               selector:@selector(ro_applicationWillResignActive)
                   name:UIApplicationWillResignActiveNotification
                 object:nil];
    return self;
}

- (void)ro_leaveLiveRegister {
    for (NSUInteger index = ROLiveInFeedViews.count; index > 0; index--) {
        id view = (__bridge id)[ROLiveInFeedViews pointerAtIndex:index - 1];
        if (view == nil || view == self) {
            [ROLiveInFeedViews removePointerAtIndex:index - 1];
        }
    }
}

- (void)dealloc {
    [NSNotificationCenter.defaultCenter removeObserver:self];
    [self ro_stopObservingRootBounds];
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
    _appActive = YES;
    [self ro_notifyActualVisibilityIfChanged];
}

// The counterpart of the Activity losing window focus on Android. Read from
// the notification rather than from applicationState, because at the moment
// this fires the application is still reported as active.
- (void)ro_applicationWillResignActive {
    _appActive = NO;
    [self ro_notifyActualVisibilityIfChanged];
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
    // The counterpart of Activity.isFinishing: the reference is weak, so a
    // host that has gone reads as nil and there is nothing left to show into.
    if (_hostViewController == nil) {
        return [self ro_fail:@"In-feed Show rejected because the host "
                              "controller is gone"];
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
    // Index 0: still above the game, because the game is this view's
    // PARENT (rootVC.view is UnityView itself), never a sibling. Below every
    // other layer the pack adds, which is exactly where in-feed belongs.
    [contentRoot insertSubview:self atIndex:0];
    if (ROLiveInFeedViews == nil) {
        ROLiveInFeedViews = [NSPointerArray weakObjectsPointerArray];
    }
    [ROLiveInFeedViews addPointer:(__bridge void *)self];
    if (![self ro_fitHostInsideContentRoot]) {
        return [self ro_fail:@"In-feed could not place its presentation view"];
    }
    _finalObservationStartedAt = RONow();
    [self ro_beginObservingFinalAssetGeometry];
    return YES;
}

- (void)ro_waitForContentRootReady {
    if (_waitingForRoot) return;
    _waitingForRoot = YES;
    _rootWaitStartedAt = RONow();
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
    // A host that has been torn down is never going to become ready, so it
    // ends here rather than after the full wait - the ten seconds are for a
    // root that is slow, not for one that no longer exists.
    if (_hostViewController == nil) {
        [self ro_recordFailure:@"In-feed content root never became ready "
                                "because the host controller is gone"];
        [self ro_dismissWithNotify:YES removeFromParent:YES];
        return;
    }
    if (![self ro_isContentRootReady]) {
        // Bounded so a root that never settles ends as a normal dismissal
        // instead of parking the slot and blocking every later load.
        if (RONow() - _rootWaitStartedAt >= kROMaxRootWait) {
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
        // Views placed by Auto Layout are the SDK's - its AdChoices
        // container and overlays; everything this pack builds is placed by
        // frame. A frame from this pass would only fight their constraints:
        // it stretched the AdChoices container over the whole cell until
        // Auto Layout next ran, and would undo the pins moved just below.
        if (!child.translatesAutoresizingMaskIntoConstraints) continue;

        [child ro_measureWithWidthSpec:HBMeasureSpecMake(
                        HBMeasureSpecExactly, _activePlan.width)
                            heightSpec:HBMeasureSpecMake(
                        HBMeasureSpecExactly, _activePlan.height)];
        [child ro_layoutWithFrame:CGRectMake(
                0, 0, _activePlan.width, _activePlan.height)];
    }
    [_viewFactory insetSdkAdChoicesInNativeAdView:_nativeAdView
                                        logResult:NO];
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
    _candidateObservationStartedAt = RONow();
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

// Rotation, split-screen and a resized game view all change the root out
// from under a layout that was validated against the old size, and the
// display link above has already stopped by then - it only runs while a plan
// is being judged. Android watches the same thing for the whole life of the
// presentation with addOnLayoutChangeListener; this is that listener.
//
// The observation is on the LAYER, not on the view. CALayer documents its
// properties as KVC and KVO compliant; UIView promises nothing of the sort
// for its own frame and bounds, and the layer changes on exactly the same
// occasions.
- (void)ro_startObservingRootBounds {
    if (_observedRootLayer != nil) return;

    UIView *contentRoot = [self ro_contentRoot];
    if (contentRoot == nil) return;

    _observedRootSize = contentRoot.bounds.size;
    _observedRootLayer = contentRoot.layer;
    [contentRoot.layer addObserver:self
                        forKeyPath:@"bounds"
                           options:0
                           context:kRORootBoundsContext];
}

- (void)ro_stopObservingRootBounds {
    CALayer *observed = _observedRootLayer;
    _observedRootLayer = nil;
    if (observed == nil) return;

    [observed removeObserver:self
                  forKeyPath:@"bounds"
                     context:kRORootBoundsContext];
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary *)change
                       context:(void *)context {
    if (context != kRORootBoundsContext) {
        [super observeValueForKeyPath:keyPath
                             ofObject:object
                               change:change
                              context:context];
        return;
    }
    [self ro_handleRootBoundsChanged];
}

// A rotation animates, so this arrives several times for one turn. Only a
// size that actually differs from the one the layout was judged against is
// worth a re-fit, which leaves the intermediate frames costing a comparison.
- (void)ro_handleRootBoundsChanged {
    if (_dismissed || !_layoutReady) return;

    UIView *contentRoot = [self ro_contentRoot];
    if (contentRoot == nil) return;

    CGSize size = contentRoot.bounds.size;
    if (size.width <= 0 || size.height <= 0) return;
    if (CGSizeEqualToSize(size, _observedRootSize)) return;

    _observedRootSize = size;
    if ([self ro_fitHostInsideContentRoot]) return;

    [self ro_recordFailure:[NSString stringWithFormat:
            @"In-feed active layout no longer fits after content surface "
             "resize; %@"
          , [self ro_describeRequestedRect]]];
    [self ro_dismissWithNotify:YES removeFromParent:YES];
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
            RONow() - _candidateObservationStartedAt;
    NSTimeInterval totalObservation =
            RONow() - _finalObservationStartedAt;
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
    // The layout has settled, so the SDK has built whatever it was going to:
    // what the inset finds now is the verdict worth reporting.
    [_viewFactory insetSdkAdChoicesInNativeAdView:_nativeAdView
                                        logResult:YES];
    [self ro_startObservingRootBounds];
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

// Being in a window is not the same as being looked at. Backgrounded, or
// under the share sheet a click opened, the view is still in its window and
// still unhidden - and without this term the dwell clock would keep running
// through it and rotate the creative where nobody could see either one.
//
// Not an exact twin of the Android test, which reads getWindowVisibility and
// isShown on the panel: those go false when the Activity STOPS, so Android
// keeps counting through a notification shade or a Control Center pull where
// this stops. The case both cover is the one this was written for - the app
// actually in the background - and erring towards not charging dwell for
// time the player did not spend looking is the safe direction.
- (BOOL)ro_isActuallyVisible {
    return !_dismissed
            && _layoutReady
            && _visibleRequested
            && _appActive
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
    [self ro_stopObservingRootBounds];

    if (removeFromParent) [self removeFromSuperview];
    // Outside the branch above on purpose. Two dismissal paths pass NO here -
    // the observation tick that finds the superview gone, and didMoveToWindow
    // - and a presentation left in the register after either of those still
    // answers ro_frontmostInFeedView. It is the OLDEST entry, so it answers
    // first, and its superview is nil, so both half-screen callers fail the
    // `anchor.superview == hostView` test and fall back to index 0 - putting
    // the half-screen layer UNDER the in-feed ads it must sit above.
    [self ro_leaveLiveRegister];
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


@end
