#import "RONativeAdMobInFeed.h"

#import "RONativeAdMobInFeedPresentation.h"

static NSString *const kROTag = @"InFeed";

static const int32_t kROLoadSuccessCode = 0;
static const NSInteger kROMaxRetryExponent = 5;
static const NSTimeInterval kRORetryBaseDelay = 1.0;
static const NSTimeInterval kRONoRetryScheduled = -1;
static const NSInteger kRORetryImmediateLayoutAttempts = 2;
static const NSInteger kROMaxUnprovenLayoutFailures = 20;
static const NSTimeInterval kROUnprovenLayoutRetryDelay = 300;
static const NSInteger kROInFeedAdBufferMax = 2;
// Rotation follows appearances rather than a clock: a slot that keeps being
// shown and hidden earns a fresh ad every time it comes back, and the dwell
// interval only covers a slot that never goes away.
static const NSTimeInterval kRODwellRefreshInterval = 30;
static const NSTimeInterval kROMinDwell = 1;
static const NSTimeInterval kROMinSwapInterval = 3;
static const NSTimeInterval kROMaxCachedAdAge = 3600;
static const NSTimeInterval kROSlotWatchdogInterval = 1;
static const NSTimeInterval kROForegroundRecheckDelay = 1;
static const NSTimeInterval kROActiveSlotVisibilityTimeout = 3;
// Last-resort net only; the presentation bounds its own waits and ends as a
// dismissal, which is the path that normally clears a slot.
static const NSTimeInterval kROMaterializingSlotTimeout = 20;
static const NSTimeInterval kRONoTimestamp = -1;

@interface ROInFeedStyle : NSObject
@property (nonatomic) CGFloat x;
@property (nonatomic) CGFloat y;
@property (nonatomic) CGFloat width;
@property (nonatomic) CGFloat height;
@property (nonatomic) float backgroundAlpha;
@end

@implementation ROInFeedStyle
@end

@interface HBCachedInFeedAd : NSObject
@property (nonatomic, strong) GADNativeAd *ad;
@property (nonatomic) NSTimeInterval loadedAt;
@end

@implementation HBCachedInFeedAd
@end

@interface ROInFeedDisplaySlot : NSObject
@property (nonatomic, strong) GADNativeAd *ad;
@property (nonatomic, strong) RONativeAdMobInFeedPresentation *presentation;
@property (nonatomic) NSTimeInterval loadedAt;
@property (nonatomic) NSTimeInterval accumulatedVisible;
@property (nonatomic) NSTimeInterval visibleStartedAt;
@property (nonatomic) NSTimeInterval visibleWaitStartedAt;
@property (nonatomic) NSTimeInterval displayedAt;
@property (nonatomic) BOOL ready;
@property (nonatomic) BOOL actuallyVisible;
@end

@implementation ROInFeedDisplaySlot

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _visibleStartedAt = kRONoTimestamp;
        _visibleWaitStartedAt = kRONoTimestamp;
        _displayedAt = kRONoTimestamp;
    }
    return self;
}

@end

@interface RONativeAdMobInFeed () <GADNativeAdLoaderDelegate
                              , GADNativeAdDelegate
                              , ROInFeedPresentationListener>
@end

@implementation RONativeAdMobInFeed {
    NSString *_adUnitId;
    ROInFeedStyle *_style;
    ROInFeedDisplaySlot *_activeSlot;
    ROInFeedDisplaySlot *_materializingSlot;
    NSMutableArray<HBCachedInFeedAd *> *_cachedAds;
    // OwnsAd runs inside the SDK's paid-event callback, off whatever thread
    // the SDK chose; the guarded set keeps the membership test safe.
    NSHashTable<GADNativeAd *> *_ownedAds;
    GADAdLoader *_adLoader;
    NSTimeInterval _lastSwapAt;
    BOOL _configured;
    BOOL _visibleRequested;
    BOOL _isAdLoading;
    BOOL _retryScheduled;
    NSInteger _noFillStreak;
    NSInteger _layoutFailStreak;
    BOOL _layoutProven;

    NSTimer *_refreshTimer;
    NSTimer *_retryTimer;
    NSTimer *_hiddenExpiryTimer;
    NSTimer *_slotWatchdogTimer;
    NSTimer *_foregroundRecheckTimer;
    // Bumping the generation orphans a pending deferred swap, the way the
    // Android side removed its frame callback.
    NSInteger _hiddenSwapGeneration;
}

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                      instanceId:(int32_t)instanceId {
    self = [super initWithInstanceId:instanceId];
    if (self == nil) return nil;

    _adUnitId = [adUnitId copy];
    _cachedAds = [NSMutableArray array];
    _ownedAds = [NSHashTable weakObjectsHashTable];
    _lastSwapAt = kRONoTimestamp;
    [RONativeAdMob runOnMainThread:^{
        if (self.released) return;
        if (![RONativeAdMob isViewControllerUsable:
                [RONativeAdMob unityViewController]]) {
            NSLog(@"%@: created without a usable host; cache loading will "
                   "start when Configure or Show receives one", kROTag);
            return;
        }
        [self ro_startLoad];
    }];
    return self;
}

static NSTimeInterval HBNow(void) {
    return [NSProcessInfo processInfo].systemUptime;
}

- (void)configureWithX:(CGFloat)xPt
                     y:(CGFloat)yPt
                 width:(CGFloat)widthPt
                height:(CGFloat)heightPt
       backgroundAlpha:(float)backgroundAlpha {
    [RONativeAdMob runOnMainThread:^{
        if (self.released) {
            NSLog(@"%@: Configure ignored after Release", kROTag);
            return;
        }
        if (![RONativeAdMob isViewControllerUsable:
                [RONativeAdMob unityViewController]]) {
            NSLog(@"%@: Configure requires a usable host", kROTag);
            return;
        }

        ROInFeedStyle *newStyle = [[ROInFeedStyle alloc] init];
        newStyle.x = xPt;
        newStyle.y = yPt;
        newStyle.width = MAX(1, widthPt);
        newStyle.height = MAX(1, heightPt);
        newStyle.backgroundAlpha = backgroundAlpha;
        ROInFeedStyle *oldStyle = self->_style;
        BOOL styleChanged = self->_configured
                && (oldStyle == nil
                        || oldStyle.x != newStyle.x
                        || oldStyle.y != newStyle.y
                        || oldStyle.width != newStyle.width
                        || oldStyle.height != newStyle.height
                        || oldStyle.backgroundAlpha
                                != newStyle.backgroundAlpha);

        self->_visibleRequested = NO;
        [self->_refreshTimer invalidate];
        self->_refreshTimer = nil;
        [self ro_pauseVisibleTimer:self->_activeSlot];
        [self->_activeSlot.presentation setVisible:NO];
        [self->_materializingSlot.presentation setVisible:NO];

        if (styleChanged) {
            // A different rect has to prove itself again.
            self->_layoutFailStreak = 0;
            self->_layoutProven = NO;
            [self ro_destroySlot:self->_materializingSlot];
            self->_materializingSlot = nil;
            [self ro_destroySlot:self->_activeSlot];
            self->_activeSlot = nil;
        }

        self->_style = newStyle;
        self->_configured = YES;
        [self ro_removeExpiredCachedAds];
        [self ro_removeExpiredSlots];
        if ([self ro_shouldMaterializeCachedAd]) {
            [self ro_presentCachedAd];
        } else {
            [self ro_startLoad];
        }
        [self ro_scheduleHiddenExpiry];
    }];
}

- (void)show {
    if (self.released) return;
    [RONativeAdMob runOnMainThread:^{
        [self ro_showInternal];
        [self ro_scheduleSlotWatchdog];
    }];
}

- (void)hide {
    if (self.released) return;
    [RONativeAdMob runOnMainThread:^{ [self ro_hideInternal]; }];
}

- (void)setPositionX:(CGFloat)xPt y:(CGFloat)yPt {
    if (self.released) return;
    [RONativeAdMob runOnMainThread:^{
        if (self.released || !self->_configured || self->_style == nil) {
            NSLog(@"%@: SetPosition ignored before Configure or after "
                   "Release", kROTag);
            return;
        }
        self->_style.x = xPt;
        self->_style.y = yPt;
        [self->_materializingSlot.presentation setPositionX:xPt y:yPt];
        [self->_activeSlot.presentation setPositionX:xPt y:yPt];
    }];
}

- (void)releaseAd {
    if (self.released) return;
    [self markReleased];
    [self invalidateLoadGeneration];
    [RONativeAdMob runOnMainThread:^{
        self->_isAdLoading = NO;
        self->_retryScheduled = NO;
        self->_visibleRequested = NO;
        ++self->_hiddenSwapGeneration;
        [self->_refreshTimer invalidate];
        [self->_retryTimer invalidate];
        [self->_hiddenExpiryTimer invalidate];
        [self->_slotWatchdogTimer invalidate];
        [self->_foregroundRecheckTimer invalidate];
        [self ro_pauseVisibleTimer:self->_activeSlot];
        [self ro_destroySlot:self->_materializingSlot];
        self->_materializingSlot = nil;
        [self ro_destroySlot:self->_activeSlot];
        self->_activeSlot = nil;
        for (HBCachedInFeedAd *cached in self->_cachedAds) {
            [self ro_destroyAd:cached.ad];
        }
        [self->_cachedAds removeAllObjects];
        self->_style = nil;
        self->_adLoader = nil;
        [self clearListenerCallbacks];
    }];
}

// ---------------------------------------------------------------------------
// Show / hide
// ---------------------------------------------------------------------------

- (NSString *)ro_describeSlot:(ROInFeedDisplaySlot *)slot {
    if (slot == nil) return @"none";
    return [NSString stringWithFormat:
            @"{ready=%@,visible=%@,displayed=%@}"
          , slot.ready ? @"true" : @"false"
          , slot.actuallyVisible ? @"true" : @"false"
          , slot.displayedAt != kRONoTimestamp ? @"true" : @"false"];
}

- (void)ro_showInternal {
    if (self.released || !_configured) {
        NSLog(@"%@: Show ignored before Configure or after Release", kROTag);
        return;
    }
    if (![RONativeAdMob isViewControllerUsable:[RONativeAdMob unityViewController]]) {
        NSLog(@"%@: Show ignored because the host is not usable", kROTag);
        return;
    }

    NSLog(@"%@: Show: active=%@ materializing=%@ cached=%lu"
          , kROTag
          , [self ro_describeSlot:_activeSlot]
          , [self ro_describeSlot:_materializingSlot]
          , (unsigned long)_cachedAds.count);
    _visibleRequested = YES;
    [_hiddenExpiryTimer invalidate];
    _hiddenExpiryTimer = nil;

    [self ro_removeExpiredCachedAds];
    [self ro_removeExpiredSlots];
    [_activeSlot.presentation requestDisplayNotification];
    [_materializingSlot.presentation requestDisplayNotification];
    BOOL readyForShow = _activeSlot != nil
            || (_materializingSlot != nil && _materializingSlot.ready);
    if (!readyForShow) [self notifyShowNotReady];
    if (_materializingSlot != nil) {
        if (_materializingSlot.ready) {
            // Already laid out, so open straight onto it. Showing the
            // outgoing ad first is what made one appear and get replaced a
            // moment later.
            [_materializingSlot.presentation setVisible:YES];
        } else {
            [self ro_showActiveSlot];
            if (_activeSlot == nil) {
                [_materializingSlot.presentation setVisible:YES];
            }
        }
        return;
    }
    if (_activeSlot != nil) {
        if ([self ro_showActiveSlot]) [self ro_scheduleRefresh];
        return;
    }
    if (_cachedAds.count > 0) {
        [self ro_presentCachedAd];
        return;
    }

    [self ro_startLoad];
}

- (void)ro_hideInternal {
    if (self.released) return;

    NSLog(@"%@: Hide: active=%@ materializing=%@ cached=%lu"
          , kROTag
          , [self ro_describeSlot:_activeSlot]
          , [self ro_describeSlot:_materializingSlot]
          , (unsigned long)_cachedAds.count);
    _visibleRequested = NO;
    [_refreshTimer invalidate];
    _refreshTimer = nil;
    [self ro_pauseVisibleTimer:_activeSlot];
    [_activeSlot.presentation setVisible:NO];
    [_materializingSlot.presentation setVisible:NO];
    // Rotate during the hidden stretch rather than on the way back in - but
    // not in this turn of the runloop: laying the replacement out would sit
    // in front of the commit that draws the hide. The completion block runs
    // after this transaction commits, then the swap gets its own turn.
    NSInteger generation = ++_hiddenSwapGeneration;
    __weak RONativeAdMobInFeed *weakSelf = self;
    [CATransaction setCompletionBlock:^{
        dispatch_async(dispatch_get_main_queue(), ^{
            RONativeAdMobInFeed *strongSelf = weakSelf;
            if (strongSelf == nil
                    || strongSelf.released
                    || generation != strongSelf->_hiddenSwapGeneration
                    || strongSelf->_visibleRequested) {
                return;
            }
            [strongSelf ro_trySwapActiveSlot];
        });
    }];
    if (![self ro_shouldRetry]) [self ro_cancelRetry];
    _activeSlot.visibleWaitStartedAt = kRONoTimestamp;
    _materializingSlot.visibleWaitStartedAt = kRONoTimestamp;
    [_slotWatchdogTimer invalidate];
    _slotWatchdogTimer = nil;
    [self ro_scheduleHiddenExpiry];
}

// ---------------------------------------------------------------------------
// Loading
// ---------------------------------------------------------------------------

- (BOOL)ro_shouldStartLoad {
    // The buffer stays topped up regardless of what is on screen; the swap
    // triggers decide when a warm ad is spent.
    return (NSInteger)_cachedAds.count < kROInFeedAdBufferMax;
}

- (BOOL)ro_shouldMaterializeCachedAd {
    return _configured && _cachedAds.count > 0;
}

- (BOOL)ro_shouldRetry {
    if (self.released) return NO;
    if ([self ro_shouldMaterializeCachedAd]) return YES;
    return !_isAdLoading && [self ro_shouldStartLoad];
}

- (BOOL)ro_startLoad {
    UIViewController *host = [RONativeAdMob unityViewController];
    if (self.released
            || _isAdLoading
            || _retryScheduled
            || ![self ro_shouldStartLoad]
            || ![RONativeAdMob isViewControllerUsable:host]) {
        return NO;
    }

    _isAdLoading = YES;
    [self nextLoadGeneration];
    [self notifyLoadingStarted];

    _adLoader = [[GADAdLoader alloc]
            initWithAdUnitID:_adUnitId
          rootViewController:host
                     adTypes:@[ GADAdLoaderAdTypeNative ]
                     options:[RONativeAdMob adLoaderOptionsWithStartMuted:YES]];
    _adLoader.delegate = self;
    [_adLoader loadRequest:[GADRequest request]];
    return YES;
}

- (void)adLoader:(GADAdLoader *)adLoader
        didReceiveNativeAd:(GADNativeAd *)nativeAd {
    [RONativeAdMob runOnMainThread:^{
        if (self.released) return;

        self->_noFillStreak = 0;
        nativeAd.delegate = self;
        @synchronized (self->_ownedAds) {
            [self->_ownedAds addObject:nativeAd];
        }
        HBCachedInFeedAd *cached = [[HBCachedInFeedAd alloc] init];
        cached.ad = nativeAd;
        cached.loadedAt = HBNow();
        [self->_cachedAds addObject:cached];
        [self ro_scheduleHiddenExpiry];
        __weak RONativeAdMobInFeed *weakSelf = self;
        __weak GADNativeAd *weakAd = nativeAd;
        [self bindPaidEventForAd:nativeAd
                    paidAdUnitId:self->_adUnitId
                     isCurrentAd:^BOOL{
            RONativeAdMobInFeed *strongSelf = weakSelf;
            GADNativeAd *strongAd = weakAd;
            if (strongSelf == nil || strongAd == nil) return NO;
            @synchronized (strongSelf->_ownedAds) {
                return [strongSelf->_ownedAds containsObject:strongAd];
            }
        }];

        self->_isAdLoading = NO;
        [self notifyLoadingCompletedWithCode:kROLoadSuccessCode message:@""];
        if (self->_activeSlot == nil
                && [self ro_shouldMaterializeCachedAd]) {
            [self ro_presentCachedAd];
        }
        [self ro_startLoad];
    }];
}

- (void)adLoader:(GADAdLoader *)adLoader
        didFailToReceiveAdWithError:(NSError *)error {
    [RONativeAdMob runOnMainThread:^{
        if (self.released) return;

        self->_isAdLoading = NO;
        NSTimeInterval retryDelay = [self ro_scheduleNoFillRetry];
        [self notifyLoadingCompletedWithCode:(int32_t)error.code
                                     message:[self ro_failureMessage:
                                            error.localizedDescription
                                                          retryDelay:retryDelay]];
    }];
}

// Not gated on the load generation: the ad that was clicked may well have
// come from an earlier load.
- (void)nativeAdDidRecordClick:(GADNativeAd *)nativeAd {
    [RONativeAdMob runOnMainThread:^{
        [self->_activeSlot.presentation commitAdClick];
        [self->_materializingSlot.presentation commitAdClick];
    }];
}

// ---------------------------------------------------------------------------
// Materialization
// ---------------------------------------------------------------------------

- (BOOL)ro_isHostVisible {
    // A backgrounded host is still usable but does not draw; everything that
    // depends on drawing waits for the foreground instead of failing.
    UIViewController *host = [RONativeAdMob unityViewController];
    return [RONativeAdMob isViewControllerUsable:host]
            && UIApplication.sharedApplication.applicationState
                    == UIApplicationStateActive;
}

- (void)ro_presentCachedAd {
    UIViewController *host = [RONativeAdMob unityViewController];
    if (self.released
            || !_configured
            || _materializingSlot != nil
            || ![self ro_shouldMaterializeCachedAd]
            || ![RONativeAdMob isViewControllerUsable:host]) {
        return;
    }
    if (![self ro_isHostVisible]) {
        // The geometry observation only advances on draw passes; presenting
        // into a window that is not drawing parks the slot.
        [self ro_scheduleForegroundRecheck];
        return;
    }

    [self ro_removeExpiredCachedAds];
    HBCachedInFeedAd *next = _cachedAds.firstObject;
    if (next == nil) {
        [self ro_startLoad];
        return;
    }
    [_cachedAds removeObjectAtIndex:0];
    [self ro_startLoad];

    ROInFeedDisplaySlot *slot = [[ROInFeedDisplaySlot alloc] init];
    slot.ad = next.ad;
    slot.loadedAt = next.loadedAt;
    RONativeAdMobInFeedPresentation *presentation =
            [[RONativeAdMobInFeedPresentation alloc]
                    initWithHostViewController:host
                                      nativeAd:next.ad
                                             x:_style.x
                                             y:_style.y
                                         width:_style.width
                                        height:_style.height
                               backgroundAlpha:_style.backgroundAlpha
                                      listener:self];
    slot.presentation = presentation;
    _materializingSlot = slot;

    [presentation setVisible:(_visibleRequested && _activeSlot == nil)];
    if (![presentation show]) {
        NSString *failureMessage = presentation.failureMessage;
        _materializingSlot = nil;
        [self ro_destroySlot:slot];
        [self ro_showActiveSlot];
        NSTimeInterval retryDelay = [self ro_scheduleLayoutRetry];
        [self notifyPresentationFailedWithCode:RONativeAdMobInternalPresentationError
                                       message:[self ro_failureMessage:
                                              failureMessage
                                                      ?: @"In-feed presentation "
                                                          "failed before display"
                                                            retryDelay:retryDelay]];
    }
    [self ro_scheduleSlotWatchdog];
}

// The listener callbacks arrive from whichever slot's presentation raised
// them; identity is resolved by pointer the way the Java holder was.
- (ROInFeedDisplaySlot *)ro_slotForPresentation:(id)presentation {
    if (_materializingSlot.presentation == presentation) {
        return _materializingSlot;
    }
    if (_activeSlot.presentation == presentation) return _activeSlot;
    return nil;
}

- (void)inFeedPresentationReady {
    ROInFeedDisplaySlot *slot = _materializingSlot;
    if (self.released || slot == nil) return;
    slot.ready = YES;
    if (_visibleRequested) [slot.presentation setVisible:YES];
}

- (void)inFeedPresentationDisplayed {
    ROInFeedDisplaySlot *slot = _materializingSlot ?: _activeSlot;
    if (self.released || slot == nil) return;
    if (!_visibleRequested) {
        [slot.presentation setVisible:NO];
        return;
    }

    if (_materializingSlot == slot) {
        ROInFeedDisplaySlot *previous = _activeSlot;
        _activeSlot = slot;
        _materializingSlot = nil;
        _layoutFailStreak = 0;
        _layoutProven = YES;
        _lastSwapAt = HBNow();
        [self ro_destroySlot:previous];
    }
    if (slot.displayedAt == kRONoTimestamp) slot.displayedAt = HBNow();

    if (slot.actuallyVisible) {
        [self ro_resumeVisibleTimer:slot];
    } else {
        [self ro_pauseVisibleTimer:slot];
    }
    [self ro_scheduleSlotWatchdog];
    [self notifyDisplayed];
    [self ro_scheduleRefresh];
}

- (void)inFeedPresentationActualVisibilityChanged:(BOOL)isActuallyVisible {
    // Both slots report through here; refresh only follows the active one.
    ROInFeedDisplaySlot *materializing = _materializingSlot;
    ROInFeedDisplaySlot *active = _activeSlot;
    ROInFeedDisplaySlot *slot = nil;
    if (materializing != nil
            && materializing.actuallyVisible != isActuallyVisible) {
        slot = materializing;
    }
    if (active != nil && active.actuallyVisible != isActuallyVisible) {
        slot = slot ?: active;
    }
    if (slot == nil) return;

    slot.actuallyVisible = isActuallyVisible;
    [self ro_markVisibilityWait:slot];
    if (slot != _activeSlot) return;

    if (_visibleRequested && isActuallyVisible) {
        [self ro_resumeVisibleTimer:slot];
        [self ro_scheduleRefresh];
    } else {
        [_refreshTimer invalidate];
        _refreshTimer = nil;
        [self ro_pauseVisibleTimer:slot];
    }
    [self ro_scheduleSlotWatchdog];
}

- (void)inFeedPresentationDismissed {
    // Failure paths funnel through dismissal; whichever slot lost its
    // presentation is cleared and the retry ladder decides what follows.
    ROInFeedDisplaySlot *dismissed = nil;
    if (_materializingSlot != nil
            && ![_materializingSlot.presentation isShowingPresentation]) {
        dismissed = _materializingSlot;
        _materializingSlot = nil;
    } else if (_activeSlot != nil
            && ![_activeSlot.presentation isShowingPresentation]) {
        dismissed = _activeSlot;
        [self ro_pauseVisibleTimer:dismissed];
        _activeSlot = nil;
    }
    if (dismissed == nil) return;

    NSString *failureMessage = dismissed.presentation.failureMessage;
    [self ro_destroyAd:dismissed.ad];

    if (self.released) return;
    [self ro_showActiveSlot];
    NSTimeInterval retryDelay = [self ro_scheduleLayoutRetry];
    [self ro_scheduleSlotWatchdog];
    if (failureMessage != nil) {
        [self notifyPresentationFailedWithCode:RONativeAdMobInternalPresentationError
                                       message:[self ro_failureMessage:failureMessage
                                                            retryDelay:retryDelay]];
    }
}

// ---------------------------------------------------------------------------
// Rotation
// ---------------------------------------------------------------------------

- (BOOL)ro_showActiveSlot {
    if (!_visibleRequested || _activeSlot == nil) return NO;
    if (![_activeSlot.presentation setVisible:YES]) return NO;
    if (_activeSlot.actuallyVisible) [self ro_resumeVisibleTimer:_activeSlot];
    return YES;
}

- (BOOL)ro_isActiveSlotConsumed {
    return _activeSlot != nil
            && _activeSlot.displayedAt != kRONoTimestamp
            && [self ro_currentVisibleDuration:_activeSlot] >= kROMinDwell;
}

// Rotate to a warm ad once the one on screen has had its turn; swaps are
// spaced so a burst of show and hide cannot drain the buffer.
- (void)ro_trySwapActiveSlot {
    if (self.released
            || _materializingSlot != nil
            || _activeSlot == nil
            || _cachedAds.count == 0
            || ![self ro_isActiveSlotConsumed]) {
        return;
    }
    NSTimeInterval now = HBNow();
    if (_lastSwapAt != kRONoTimestamp
            && now - _lastSwapAt < kROMinSwapInterval) {
        return;
    }
    [self ro_presentCachedAd];
}

- (void)ro_scheduleRefresh {
    [_refreshTimer invalidate];
    _refreshTimer = nil;
    if (self.released
            || !_visibleRequested
            || _activeSlot == nil
            || !_activeSlot.actuallyVisible) {
        return;
    }

    NSTimeInterval remaining = MAX(
            0
          , kRODwellRefreshInterval
                    - [self ro_currentVisibleDuration:_activeSlot]);
    __weak RONativeAdMobInFeed *weakSelf = self;
    _refreshTimer = [NSTimer scheduledTimerWithTimeInterval:MAX(0.01, remaining)
                                                    repeats:NO
                                                      block:^(NSTimer *timer) {
        [weakSelf ro_handleRefresh];
    }];
}

- (void)ro_handleRefresh {
    if (self.released) return;
    if (!_visibleRequested
            || _activeSlot == nil
            || !_activeSlot.actuallyVisible) {
        [self ro_pauseVisibleTimer:_activeSlot];
        return;
    }

    [self ro_resumeVisibleTimer:_activeSlot];
    NSTimeInterval remaining = kRODwellRefreshInterval
            - [self ro_currentVisibleDuration:_activeSlot];
    if (remaining > 0) {
        [self ro_scheduleRefresh];
        return;
    }

    [self ro_trySwapActiveSlot];
    // Start the next dwell window whether or not a swap happened, or the
    // trigger re-arms every tick.
    _activeSlot.accumulatedVisible = 0;
    _activeSlot.visibleStartedAt =
            (_visibleRequested && _activeSlot.actuallyVisible)
                    ? HBNow()
                    : kRONoTimestamp;
    [self ro_scheduleRefresh];
}

// ---------------------------------------------------------------------------
// Retry ladder
// ---------------------------------------------------------------------------

static NSTimeInterval HBBackoffDelay(NSInteger streak
                                   , NSInteger immediateAttempts) {
    if (streak <= immediateAttempts) return 0;
    NSInteger exponent = MIN(
            kROMaxRetryExponent, streak - immediateAttempts);
    return kRORetryBaseDelay * (NSTimeInterval)(1LL << exponent);
}

// No fill is a property of inventory: waiting is the only thing that can
// help, so back off.
- (NSTimeInterval)ro_scheduleNoFillRetry {
    [self ro_cancelRetry];
    if (self.released || ![self ro_shouldRetry]) return kRONoRetryScheduled;
    ++_noFillStreak;
    return [self ro_postRetry:HBBackoffDelay(_noFillStreak, 0)];
}

// A layout failure is a property of this creative, not of inventory; the
// first attempts fetch a replacement straight away and only a long run of
// failures treats the rect itself as unusable.
- (NSTimeInterval)ro_scheduleLayoutRetry {
    [self ro_cancelRetry];
    if (self.released || ![self ro_shouldRetry]) return kRONoRetryScheduled;

    ++_layoutFailStreak;
    if (!_layoutProven
            && _layoutFailStreak >= kROMaxUnprovenLayoutFailures) {
        NSLog(@"%@: %ld creatives in a row failed to lay out in %@ and none "
               "has ever rendered there; falling back to a slow poll"
              , kROTag
              , (long)_layoutFailStreak
              , [self ro_describeRequestedRect]);
        return [self ro_postRetry:kROUnprovenLayoutRetryDelay];
    }
    return [self ro_postRetry:HBBackoffDelay(
            _layoutFailStreak, kRORetryImmediateLayoutAttempts)];
}

- (NSTimeInterval)ro_postRetry:(NSTimeInterval)delay {
    [self ro_cancelRetry];
    if (self.released || ![self ro_shouldRetry]) return kRONoRetryScheduled;

    _retryScheduled = YES;
    __weak RONativeAdMobInFeed *weakSelf = self;
    _retryTimer = [NSTimer scheduledTimerWithTimeInterval:MAX(0.01, delay)
                                                  repeats:NO
                                                    block:^(NSTimer *timer) {
        [weakSelf ro_handleRetry];
    }];
    return delay;
}

- (void)ro_cancelRetry {
    [_retryTimer invalidate];
    _retryTimer = nil;
    _retryScheduled = NO;
}

- (void)ro_handleRetry {
    _retryScheduled = NO;
    if (self.released || ![self ro_shouldRetry]) return;
    if ([self ro_shouldMaterializeCachedAd]) {
        [self ro_presentCachedAd];
        return;
    }

    BOOL started = [self ro_startLoad];
    if (!started && [self ro_shouldRetry] && !_retryScheduled) {
        // Nothing failed here, the state simply was not ready; re-arm at the
        // current delay rather than counting another failure.
        [self ro_postRetry:HBBackoffDelay(
                MAX(1, MAX(_noFillStreak, _layoutFailStreak)), 0)];
    }
}

// ---------------------------------------------------------------------------
// Watchdogs and expiry
// ---------------------------------------------------------------------------

- (void)ro_markVisibilityWait:(ROInFeedDisplaySlot *)slot {
    if (slot == nil) return;
    if (!_visibleRequested || slot.actuallyVisible) {
        slot.visibleWaitStartedAt = kRONoTimestamp;
        return;
    }
    if (slot.visibleWaitStartedAt == kRONoTimestamp) {
        slot.visibleWaitStartedAt = HBNow();
    }
}

// Only armed while something is actually waiting to be seen; a
// materializing slot with no pending Show is just a warm ad.
- (void)ro_scheduleSlotWatchdog {
    [_slotWatchdogTimer invalidate];
    _slotWatchdogTimer = nil;
    if (self.released || !_visibleRequested) return;
    if (![self ro_isHostVisible]) {
        _activeSlot.visibleWaitStartedAt = kRONoTimestamp;
        _materializingSlot.visibleWaitStartedAt = kRONoTimestamp;
        [self ro_scheduleForegroundRecheck];
        return;
    }

    [self ro_markVisibilityWait:_activeSlot];
    [self ro_markVisibilityWait:_materializingSlot];
    if (_materializingSlot == nil && _activeSlot == nil) return;

    __weak RONativeAdMobInFeed *weakSelf = self;
    _slotWatchdogTimer = [NSTimer
            scheduledTimerWithTimeInterval:kROSlotWatchdogInterval
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleSlotWatchdog];
    }];
}

// Without this both slots can park forever; either state would otherwise
// only clear after the cache age limit or an app restart.
- (void)ro_handleSlotWatchdog {
    if (self.released) return;
    if (![self ro_isHostVisible]) {
        [self ro_scheduleSlotWatchdog];
        return;
    }

    NSTimeInterval now = HBNow();
    if (_materializingSlot != nil) {
        if (_materializingSlot.visibleWaitStartedAt == kRONoTimestamp
                || now - _materializingSlot.visibleWaitStartedAt
                        < kROMaterializingSlotTimeout) {
            [self ro_scheduleSlotWatchdog];
            return;
        }

        NSLog(@"%@: In-feed materializing slot was never displayed within "
               "%gms; dropping it and reloading"
              , kROTag, kROMaterializingSlotTimeout * 1000);
        ROInFeedDisplaySlot *stuck = _materializingSlot;
        _materializingSlot = nil;
        [self ro_destroySlot:stuck];
        [self ro_showActiveSlot];
        [self ro_requestReplacementAd];
        [self ro_scheduleSlotWatchdog];
        return;
    }

    if (_visibleRequested
            && _activeSlot != nil
            && !_activeSlot.actuallyVisible
            && _activeSlot.visibleWaitStartedAt != kRONoTimestamp
            && now - _activeSlot.visibleWaitStartedAt
                    >= kROActiveSlotVisibilityTimeout) {
        NSLog(@"%@: In-feed active slot never became visible within %gms; "
               "dropping it and reloading"
              , kROTag, kROActiveSlotVisibilityTimeout * 1000);
        ROInFeedDisplaySlot *stuck = _activeSlot;
        [self ro_pauseVisibleTimer:stuck];
        _activeSlot = nil;
        [self ro_destroySlot:stuck];
        [self ro_requestReplacementAd];
    }

    [self ro_scheduleSlotWatchdog];
}

- (void)ro_requestReplacementAd {
    if ([self ro_shouldMaterializeCachedAd]) {
        [self ro_presentCachedAd];
        return;
    }
    // Through the retry backoff so a slot that keeps failing to display
    // cannot churn ad requests.
    [self ro_scheduleLayoutRetry];
}

- (void)ro_scheduleForegroundRecheck {
    [_foregroundRecheckTimer invalidate];
    _foregroundRecheckTimer = nil;
    if (self.released || [self ro_isHostVisible]) return;
    if (!_visibleRequested && ![self ro_shouldMaterializeCachedAd]) return;

    __weak RONativeAdMobInFeed *weakSelf = self;
    _foregroundRecheckTimer = [NSTimer
            scheduledTimerWithTimeInterval:kROForegroundRecheckDelay
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleForegroundRecheck];
    }];
}

- (void)ro_handleForegroundRecheck {
    if (self.released) return;
    if (![self ro_isHostVisible]) {
        [self ro_scheduleForegroundRecheck];
        return;
    }

    if ([self ro_shouldMaterializeCachedAd]) [self ro_presentCachedAd];
    [self ro_showActiveSlot];
    [self ro_scheduleSlotWatchdog];
    [self ro_scheduleRefresh];
}

- (void)ro_scheduleHiddenExpiry {
    [_hiddenExpiryTimer invalidate];
    _hiddenExpiryTimer = nil;
    if (self.released || _visibleRequested) return;

    NSTimeInterval nextExpiryAt = DBL_MAX;
    for (HBCachedInFeedAd *cached in _cachedAds) {
        nextExpiryAt = MIN(nextExpiryAt, cached.loadedAt + kROMaxCachedAdAge);
    }
    if (_materializingSlot != nil) {
        nextExpiryAt = MIN(
                nextExpiryAt, _materializingSlot.loadedAt + kROMaxCachedAdAge);
    }
    if (_activeSlot != nil) {
        nextExpiryAt = MIN(
                nextExpiryAt, _activeSlot.loadedAt + kROMaxCachedAdAge);
    }
    if (nextExpiryAt == DBL_MAX) return;

    __weak RONativeAdMobInFeed *weakSelf = self;
    _hiddenExpiryTimer = [NSTimer
            scheduledTimerWithTimeInterval:MAX(0.01, nextExpiryAt - HBNow())
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleHiddenExpiry];
    }];
}

- (void)ro_handleHiddenExpiry {
    if (self.released || _visibleRequested) return;

    [self ro_removeExpiredCachedAds];
    [self ro_removeExpiredSlots];
    if ([self ro_shouldMaterializeCachedAd]) {
        [self ro_presentCachedAd];
    } else {
        [self ro_startLoad];
    }
    [self ro_scheduleHiddenExpiry];
}

- (void)ro_removeExpiredCachedAds {
    NSTimeInterval now = HBNow();
    NSMutableArray<HBCachedInFeedAd *> *expired = [NSMutableArray array];
    for (HBCachedInFeedAd *cached in _cachedAds) {
        if (now - cached.loadedAt >= kROMaxCachedAdAge) {
            [expired addObject:cached];
        }
    }
    for (HBCachedInFeedAd *cached in expired) {
        [self ro_destroyAd:cached.ad];
        [_cachedAds removeObject:cached];
    }
}

- (void)ro_removeExpiredSlots {
    NSTimeInterval now = HBNow();
    if (_materializingSlot != nil
            && now - _materializingSlot.loadedAt >= kROMaxCachedAdAge) {
        [self ro_destroySlot:_materializingSlot];
        _materializingSlot = nil;
    }
    if (_activeSlot != nil
            && now - _activeSlot.loadedAt >= kROMaxCachedAdAge) {
        [self ro_pauseVisibleTimer:_activeSlot];
        [self ro_destroySlot:_activeSlot];
        _activeSlot = nil;
    }
}

// ---------------------------------------------------------------------------
// Visible-time accounting and teardown
// ---------------------------------------------------------------------------

- (void)ro_resumeVisibleTimer:(ROInFeedDisplaySlot *)slot {
    if (slot == nil
            || slot.visibleStartedAt != kRONoTimestamp
            || !_visibleRequested
            || !slot.actuallyVisible) {
        return;
    }
    slot.visibleStartedAt = HBNow();
}

- (void)ro_pauseVisibleTimer:(ROInFeedDisplaySlot *)slot {
    if (slot == nil || slot.visibleStartedAt == kRONoTimestamp) return;
    slot.accumulatedVisible += MAX(0, HBNow() - slot.visibleStartedAt);
    slot.visibleStartedAt = kRONoTimestamp;
}

- (NSTimeInterval)ro_currentVisibleDuration:(ROInFeedDisplaySlot *)slot {
    if (slot == nil) return 0;
    NSTimeInterval duration = slot.accumulatedVisible;
    if (slot.visibleStartedAt != kRONoTimestamp) {
        duration += MAX(0, HBNow() - slot.visibleStartedAt);
    }
    return duration;
}

- (void)ro_destroySlot:(ROInFeedDisplaySlot *)slot {
    if (slot == nil) return;
    [slot.presentation releasePresentation];
    [self ro_destroyAd:slot.ad];
}

- (void)ro_destroyAd:(GADNativeAd *)ad {
    if (ad == nil) return;
    @synchronized (_ownedAds) {
        [_ownedAds removeObject:ad];
    }
    ad.paidEventHandler = nil;
    ad.delegate = nil;
}

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

- (NSString *)ro_describeRequestedRect {
    if (_style == nil) return @"unavailable";
    CGFloat scale = MAX(1, UIScreen.mainScreen.nativeScale);
    return [NSString stringWithFormat:
            @"[%lld,%lld,%lld,%lld]px = [%lld,%lld]dp (density=%g)"
          , (long long)llround(_style.x * scale)
          , (long long)llround(_style.y * scale)
          , (long long)llround(_style.width * scale)
          , (long long)llround(_style.height * scale)
          , (long long)llround(_style.width)
          , (long long)llround(_style.height)
          , scale];
}

- (NSString *)ro_failureMessage:(NSString *)reason
                     retryDelay:(NSTimeInterval)retryDelay {
    NSString *retryDescription = retryDelay == kRONoRetryScheduled
            ? @"not scheduled"
            : [NSString stringWithFormat:@"%lldms"
                      , (long long)llround(retryDelay * 1000)];
    return [NSString stringWithFormat:
            @"%@ | adUnitId=%@ | rect=%@ | noFill=%ld | layoutFail=%ld | "
             "retry=%@"
          , reason ?: @""
          , _adUnitId
          , [self ro_describeRequestedRect]
          , (long)_noFillStreak
          , (long)_layoutFailStreak
          , retryDescription];
}

@end
