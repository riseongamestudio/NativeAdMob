#import "ROInFeedAdSlot.h"

#import "ROInFeedAdPresentation.h"

static NSString *const kROTag = @"InFeed";

static const NSInteger kRORetryImmediateLayoutAttempts = 2;
static const NSInteger kROMaxUnprovenLayoutFailures = 20;
static const NSTimeInterval kROUnprovenLayoutRetryDelay = 300;
// Rotation follows appearances rather than a clock: a slot that keeps being
// shown and hidden earns a fresh ad every time it comes back, and the dwell
// interval only covers a slot that never goes away.
static const NSTimeInterval kRODwellRefreshInterval = 30;
// How long an ad has to have been on screen before a hide is allowed to
// rotate it away. A second is a glance; the ad is gone before it has been
// read, and the one that replaces it burns an impression on a slot the player
// is leaving.
static const NSTimeInterval kROMinDwell = 4;
static const NSTimeInterval kROMinSwapInterval = 3;
static const NSTimeInterval kROWatchdogInterval = 1;
static const NSTimeInterval kROForegroundRecheckDelay = 1;
static const NSTimeInterval kROActiveEntryVisibilityTimeout = 3;
// Last-resort net only; the presentation bounds its own waits and ends as a
// dismissal, which is the path that normally clears a slot.
static const NSTimeInterval kROMaterializingEntryTimeout = 20;
static const NSTimeInterval kRONoTimestamp = -1;

@interface ROInFeedAdSlotRect : NSObject
@property (nonatomic) CGFloat x;
@property (nonatomic) CGFloat y;
@property (nonatomic) CGFloat width;
@property (nonatomic) CGFloat height;
@end

@implementation ROInFeedAdSlotRect
@end

@interface ROInFeedDisplayEntry : NSObject
@property (nonatomic, strong) GADNativeAd *ad;
@property (nonatomic, strong) ROInFeedAdPresentation *presentation;
@property (nonatomic) NSTimeInterval loadedAt;
@property (nonatomic) NSTimeInterval accumulatedVisible;
@property (nonatomic) NSTimeInterval visibleStartedAt;
@property (nonatomic) NSTimeInterval visibleWaitStartedAt;
@property (nonatomic) NSTimeInterval displayedAt;
@property (nonatomic) BOOL ready;
@property (nonatomic) BOOL actuallyVisible;
@end

@implementation ROInFeedDisplayEntry

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

@interface ROInFeedAdSlot () <ROInFeedPresentationListener>
@end

@implementation ROInFeedAdSlot {
    // The owner holds the slots for the life of the unit; weak back-reference
    // so the pair cannot keep each other alive after Release.
    __weak ROInFeedAd *_owner;
    NSInteger _index;
    ROInFeedAdSlotRect *_rect;
    ROInFeedDisplayEntry *_activeEntry;
    ROInFeedDisplayEntry *_materializingEntry;
    NSTimeInterval _lastSwapAt;
    BOOL _configured;
    BOOL _visibleRequested;
    BOOL _layoutRetryScheduled;
    NSInteger _layoutFailStreak;
    BOOL _layoutProven;

    NSTimer *_refreshTimer;
    NSTimer *_layoutRetryTimer;
    NSTimer *_entryExpiryTimer;
    NSTimer *_watchdogTimer;
    NSTimer *_foregroundRecheckTimer;
    // Bumping the generation orphans a pending deferred swap, the way the
    // Android side removed its frame callback.
    NSInteger _hiddenSwapGeneration;
}

static NSTimeInterval RONow(void) {
    return [NSProcessInfo processInfo].systemUptime;
}

- (instancetype)initWithOwner:(ROInFeedAd *)owner
                        index:(NSInteger)index {
    self = [super init];
    if (self == nil) return nil;

    _owner = owner;
    _index = index;
    _lastSwapAt = kRONoTimestamp;
    return self;
}

- (BOOL)ro_isReleased {
    ROInFeedAd *owner = _owner;
    return owner == nil || owner.released;
}

// ---------------------------------------------------------------------------
// Operations; the owner already hopped to main and vetted the host
// ---------------------------------------------------------------------------

- (void)configureWithX:(CGFloat)xPt
                     y:(CGFloat)yPt
                 width:(CGFloat)widthPt
                height:(CGFloat)heightPt {
    ROInFeedAdSlotRect *newRect = [[ROInFeedAdSlotRect alloc] init];
    newRect.x = xPt;
    newRect.y = yPt;
    newRect.width = MAX(1, widthPt);
    newRect.height = MAX(1, heightPt);
    ROInFeedAdSlotRect *oldRect = _rect;
    BOOL rectChanged = _configured
            && (oldRect == nil
                    || oldRect.x != newRect.x
                    || oldRect.y != newRect.y
                    || oldRect.width != newRect.width
                    || oldRect.height != newRect.height);

    _visibleRequested = NO;
    [_refreshTimer invalidate];
    _refreshTimer = nil;
    [self ro_pauseVisibleTimer:_activeEntry];
    [_activeEntry.presentation setVisible:NO];
    [_materializingEntry.presentation setVisible:NO];

    if (rectChanged) {
        // A different rect has to prove itself again.
        _layoutFailStreak = 0;
        _layoutProven = NO;
        [self ro_destroyEntry:_materializingEntry];
        _materializingEntry = nil;
        [self ro_destroyEntry:_activeEntry];
        _activeEntry = nil;
    }

    _rect = newRect;
    _configured = YES;
    [self ro_removeExpiredEntries];
    ROInFeedAd *owner = _owner;
    if (owner == nil) return;
    if ([owner hasCachedAd]) {
        [self presentCachedAd];
    } else {
        [owner requestLoad];
    }
    [self ro_scheduleEntryExpiry];
}

- (void)show {
    [self ro_showCore];
    [self ro_scheduleWatchdog];
}

- (void)ro_showCore {
    ROInFeedAd *owner = _owner;
    if (owner == nil || owner.released || !_configured) {
        NSLog(@"%@: Show ignored before Configure or after Release", kROTag);
        return;
    }

    NSLog(@"%@: Show[%ld]: active=%@ materializing=%@ cached=%ld"
          , kROTag
          , (long)_index
          , [self ro_describeEntry:_activeEntry]
          , [self ro_describeEntry:_materializingEntry]
          , (long)[owner cachedCount]);
    _visibleRequested = YES;
    [_entryExpiryTimer invalidate];
    _entryExpiryTimer = nil;

    [self ro_removeExpiredEntries];
    [_activeEntry.presentation requestDisplayNotification];
    [_materializingEntry.presentation requestDisplayNotification];
    BOOL readyForShow = _activeEntry != nil
            || (_materializingEntry != nil && _materializingEntry.ready);
    if (!readyForShow) [owner notifySlotShowNotReady:_index];
    if (_materializingEntry != nil) {
        if (_materializingEntry.ready) {
            // Already laid out, so open straight onto it. Showing the
            // outgoing ad first is what made one appear and get replaced a
            // moment later.
            [_materializingEntry.presentation setVisible:YES];
        } else {
            [self ro_showActiveEntry];
            if (_activeEntry == nil) {
                [_materializingEntry.presentation setVisible:YES];
            }
        }
        return;
    }
    if (_activeEntry != nil) {
        if ([self ro_showActiveEntry]) [self ro_scheduleRefresh];
        return;
    }
    if ([owner hasCachedAd]) {
        [self presentCachedAd];
        return;
    }

    [owner requestLoad];
}

- (void)hide {
    if ([self ro_isReleased]) return;

    ROInFeedAd *owner = _owner;
    NSLog(@"%@: Hide[%ld]: active=%@ materializing=%@ cached=%ld"
          , kROTag
          , (long)_index
          , [self ro_describeEntry:_activeEntry]
          , [self ro_describeEntry:_materializingEntry]
          , (long)[owner cachedCount]);
    _visibleRequested = NO;
    [_refreshTimer invalidate];
    _refreshTimer = nil;
    [self ro_pauseVisibleTimer:_activeEntry];
    [_activeEntry.presentation setVisible:NO];
    [_materializingEntry.presentation setVisible:NO];
    // Rotate during the hidden stretch rather than on the way back in - but
    // not in this turn of the runloop: laying the replacement out would sit
    // in front of the commit that draws the hide. The completion block runs
    // after this transaction commits, then the swap gets its own turn.
    NSInteger generation = ++_hiddenSwapGeneration;
    __weak ROInFeedAdSlot *weakSelf = self;
    [CATransaction setCompletionBlock:^{
        dispatch_async(dispatch_get_main_queue(), ^{
            ROInFeedAdSlot *strongSelf = weakSelf;
            if (strongSelf == nil
                    || [strongSelf ro_isReleased]
                    || generation != strongSelf->_hiddenSwapGeneration
                    || strongSelf->_visibleRequested) {
                return;
            }
            [strongSelf ro_trySwapActiveEntry];
        });
    }];
    _activeEntry.visibleWaitStartedAt = kRONoTimestamp;
    _materializingEntry.visibleWaitStartedAt = kRONoTimestamp;
    [_watchdogTimer invalidate];
    _watchdogTimer = nil;
    [self ro_scheduleEntryExpiry];
}

- (void)setPositionX:(CGFloat)xPt y:(CGFloat)yPt {
    if ([self ro_isReleased] || !_configured || _rect == nil) {
        NSLog(@"%@: SetPosition ignored before Configure or after Release"
              , kROTag);
        return;
    }
    _rect.x = xPt;
    _rect.y = yPt;
    [_materializingEntry.presentation setPositionX:xPt y:yPt];
    [_activeEntry.presentation setPositionX:xPt y:yPt];
}

- (void)releaseOnMain {
    ++_hiddenSwapGeneration;
    _layoutRetryScheduled = NO;
    _visibleRequested = NO;
    [_refreshTimer invalidate];
    [_layoutRetryTimer invalidate];
    [_entryExpiryTimer invalidate];
    [_watchdogTimer invalidate];
    [_foregroundRecheckTimer invalidate];
    [self ro_pauseVisibleTimer:_activeEntry];
    [self ro_destroyEntry:_materializingEntry];
    _materializingEntry = nil;
    [self ro_destroyEntry:_activeEntry];
    _activeEntry = nil;
    _rect = nil;
}

- (void)commitAdClick {
    [_activeEntry.presentation commitAdClick];
    [_materializingEntry.presentation commitAdClick];
}

- (BOOL)wantsCachedAd {
    return _configured && _activeEntry == nil && _materializingEntry == nil;
}

// ---------------------------------------------------------------------------
// Materialization
// ---------------------------------------------------------------------------

- (BOOL)ro_isHostVisible {
    // A backgrounded host is still usable but does not draw; everything that
    // depends on drawing waits for the foreground instead of failing.
    UIViewController *host = [RONativeAd unityViewController];
    return [RONativeAd isViewControllerUsable:host]
            && UIApplication.sharedApplication.applicationState
                    == UIApplicationStateActive;
}

- (void)presentCachedAd {
    ROInFeedAd *owner = _owner;
    UIViewController *host = [RONativeAd unityViewController];
    if (owner == nil
            || owner.released
            || !_configured
            || _materializingEntry != nil
            || ![owner hasCachedAd]
            || ![RONativeAd isViewControllerUsable:host]) {
        return;
    }
    if (![self ro_isHostVisible]) {
        // The geometry observation only advances on draw passes; presenting
        // into a window that is not drawing parks the slot.
        [self ro_scheduleForegroundRecheck];
        return;
    }

    ROInFeedCachedAd *next = [owner takeCachedAd];
    if (next == nil) {
        [owner requestLoad];
        return;
    }

    ROInFeedDisplayEntry *entry = [[ROInFeedDisplayEntry alloc] init];
    entry.ad = next.ad;
    entry.loadedAt = next.loadedAt;
    ROInFeedAdPresentation *presentation =
            [[ROInFeedAdPresentation alloc]
                    initWithHostViewController:host
                                      nativeAd:next.ad
                                             x:_rect.x
                                             y:_rect.y
                                         width:_rect.width
                                        height:_rect.height
                               backgroundAlpha:[owner slotBackgroundAlpha]
                                      listener:self];
    entry.presentation = presentation;
    _materializingEntry = entry;

    [presentation setVisible:(_visibleRequested && _activeEntry == nil)];
    if (![presentation show]) {
        NSString *failureMessage = presentation.failureMessage;
        _materializingEntry = nil;
        [self ro_destroyEntry:entry];
        [self ro_showActiveEntry];
        NSTimeInterval retryDelay = [self ro_scheduleLayoutRetry];
        [owner notifySlotPresentationFailed:_index
                                       code:RONativeAdInternalPresentationError
                                    message:[self ro_failureMessage:
                                           failureMessage
                                                   ?: @"In-feed presentation "
                                                       "failed before display"
                                                         retryDelay:retryDelay]];
    }
    [self ro_scheduleWatchdog];
}

- (void)inFeedPresentationReady {
    ROInFeedDisplayEntry *entry = _materializingEntry;
    if ([self ro_isReleased] || entry == nil) return;
    entry.ready = YES;
    if (_visibleRequested) [entry.presentation setVisible:YES];
}

- (void)inFeedPresentationDisplayed {
    ROInFeedAd *owner = _owner;
    ROInFeedDisplayEntry *entry = _materializingEntry ?: _activeEntry;
    if (owner == nil || owner.released || entry == nil) return;
    if (!_visibleRequested) {
        [entry.presentation setVisible:NO];
        return;
    }

    if (_materializingEntry == entry) {
        ROInFeedDisplayEntry *previous = _activeEntry;
        _activeEntry = entry;
        _materializingEntry = nil;
        _layoutFailStreak = 0;
        _layoutProven = YES;
        _lastSwapAt = RONow();
        [self ro_destroyEntry:previous];
    }
    if (entry.displayedAt == kRONoTimestamp) entry.displayedAt = RONow();

    if (entry.actuallyVisible) {
        [self ro_resumeVisibleTimer:entry];
    } else {
        [self ro_pauseVisibleTimer:entry];
    }
    [self ro_scheduleWatchdog];
    [owner notifySlotDisplayed:_index];
    [self ro_scheduleRefresh];
}

- (void)inFeedPresentationActualVisibilityChanged:(BOOL)isActuallyVisible {
    // Both entries report through here; refresh only follows the active one.
    ROInFeedDisplayEntry *materializing = _materializingEntry;
    ROInFeedDisplayEntry *active = _activeEntry;
    ROInFeedDisplayEntry *entry = nil;
    if (materializing != nil
            && materializing.actuallyVisible != isActuallyVisible) {
        entry = materializing;
    }
    if (active != nil && active.actuallyVisible != isActuallyVisible) {
        entry = entry ?: active;
    }
    if (entry == nil) return;

    entry.actuallyVisible = isActuallyVisible;
    [self ro_markVisibilityWait:entry];
    if (entry != _activeEntry) return;

    if (_visibleRequested && isActuallyVisible) {
        [self ro_resumeVisibleTimer:entry];
        [self ro_scheduleRefresh];
    } else {
        [_refreshTimer invalidate];
        _refreshTimer = nil;
        [self ro_pauseVisibleTimer:entry];
    }
    [self ro_scheduleWatchdog];
}

- (void)inFeedPresentationDismissed {
    // Failure paths funnel through dismissal; whichever entry lost its
    // presentation is cleared and the retry ladder decides what follows.
    ROInFeedAd *owner = _owner;
    if (owner == nil) return;

    ROInFeedDisplayEntry *dismissed = nil;
    if (_materializingEntry != nil
            && ![_materializingEntry.presentation isShowingPresentation]) {
        dismissed = _materializingEntry;
        _materializingEntry = nil;
    } else if (_activeEntry != nil
            && ![_activeEntry.presentation isShowingPresentation]) {
        dismissed = _activeEntry;
        [self ro_pauseVisibleTimer:dismissed];
        _activeEntry = nil;
    }
    if (dismissed == nil) return;

    NSString *failureMessage = dismissed.presentation.failureMessage;
    [owner destroyAd:dismissed.ad];

    if (owner.released) return;
    [self ro_showActiveEntry];
    NSTimeInterval retryDelay = [self ro_scheduleLayoutRetry];
    [self ro_scheduleWatchdog];
    if (failureMessage != nil) {
        [owner notifySlotPresentationFailed:_index
                                       code:RONativeAdInternalPresentationError
                                    message:[self ro_failureMessage:failureMessage
                                                         retryDelay:retryDelay]];
    }
}

// ---------------------------------------------------------------------------
// Rotation
// ---------------------------------------------------------------------------

- (BOOL)ro_showActiveEntry {
    if (!_visibleRequested || _activeEntry == nil) return NO;
    if (![_activeEntry.presentation setVisible:YES]) return NO;
    if (_activeEntry.actuallyVisible) {
        [self ro_resumeVisibleTimer:_activeEntry];
    }
    return YES;
}

- (BOOL)ro_isActiveEntryConsumed {
    return _activeEntry != nil
            && _activeEntry.displayedAt != kRONoTimestamp
            && [self ro_currentVisibleDuration:_activeEntry] >= kROMinDwell;
}

// Rotate to a warm ad once the one on screen has had its turn; swaps are
// spaced so a burst of show and hide cannot drain the cache.
- (void)ro_trySwapActiveEntry {
    ROInFeedAd *owner = _owner;
    if (owner == nil
            || owner.released
            || _materializingEntry != nil
            || _activeEntry == nil
            || ![owner hasCachedAd]
            || ![self ro_isActiveEntryConsumed]) {
        return;
    }
    NSTimeInterval now = RONow();
    if (_lastSwapAt != kRONoTimestamp
            && now - _lastSwapAt < kROMinSwapInterval) {
        return;
    }
    [self presentCachedAd];
}

- (void)ro_scheduleRefresh {
    [_refreshTimer invalidate];
    _refreshTimer = nil;
    if ([self ro_isReleased]
            || !_visibleRequested
            || _activeEntry == nil
            || !_activeEntry.actuallyVisible) {
        return;
    }

    NSTimeInterval remaining = MAX(
            0
          , kRODwellRefreshInterval
                    - [self ro_currentVisibleDuration:_activeEntry]);
    __weak ROInFeedAdSlot *weakSelf = self;
    _refreshTimer = [NSTimer scheduledTimerWithTimeInterval:MAX(0.01, remaining)
                                                    repeats:NO
                                                      block:^(NSTimer *timer) {
        [weakSelf ro_handleRefresh];
    }];
}

- (void)ro_handleRefresh {
    if ([self ro_isReleased]) return;
    if (!_visibleRequested
            || _activeEntry == nil
            || !_activeEntry.actuallyVisible) {
        [self ro_pauseVisibleTimer:_activeEntry];
        return;
    }

    [self ro_resumeVisibleTimer:_activeEntry];
    NSTimeInterval remaining = kRODwellRefreshInterval
            - [self ro_currentVisibleDuration:_activeEntry];
    if (remaining > 0) {
        [self ro_scheduleRefresh];
        return;
    }

    [self ro_trySwapActiveEntry];
    // Start the next dwell window whether or not a swap happened, or the
    // trigger re-arms every tick.
    _activeEntry.accumulatedVisible = 0;
    _activeEntry.visibleStartedAt =
            (_visibleRequested && _activeEntry.actuallyVisible)
                    ? RONow()
                    : kRONoTimestamp;
    [self ro_scheduleRefresh];
}

// ---------------------------------------------------------------------------
// Layout retry ladder
// ---------------------------------------------------------------------------

// A layout failure is a property of this creative, not of inventory; the
// first attempts fetch a replacement straight away and only a long run of
// failures treats the rect itself as unusable.
- (NSTimeInterval)ro_scheduleLayoutRetry {
    [self ro_cancelLayoutRetry];
    if ([self ro_isReleased] || !_configured) return kROInFeedNoRetryScheduled;

    ++_layoutFailStreak;
    if (!_layoutProven
            && _layoutFailStreak >= kROMaxUnprovenLayoutFailures) {
        NSLog(@"%@: %ld creatives in a row failed to lay out in %@ and none "
               "has ever rendered there; falling back to a slow poll"
              , kROTag
              , (long)_layoutFailStreak
              , [self ro_describeRequestedRect]);
        return [self ro_postLayoutRetry:kROUnprovenLayoutRetryDelay];
    }
    return [self ro_postLayoutRetry:ROInFeedBackoffDelay(
            _layoutFailStreak, kRORetryImmediateLayoutAttempts)];
}

- (NSTimeInterval)ro_postLayoutRetry:(NSTimeInterval)delay {
    [self ro_cancelLayoutRetry];
    if ([self ro_isReleased] || !_configured) return kROInFeedNoRetryScheduled;

    _layoutRetryScheduled = YES;
    __weak ROInFeedAdSlot *weakSelf = self;
    _layoutRetryTimer = [NSTimer scheduledTimerWithTimeInterval:MAX(0.01, delay)
                                                        repeats:NO
                                                          block:^(NSTimer *timer) {
        [weakSelf ro_handleLayoutRetry];
    }];
    return delay;
}

- (void)ro_cancelLayoutRetry {
    [_layoutRetryTimer invalidate];
    _layoutRetryTimer = nil;
    _layoutRetryScheduled = NO;
}

- (void)ro_handleLayoutRetry {
    _layoutRetryScheduled = NO;
    ROInFeedAd *owner = _owner;
    if (owner == nil || owner.released || !_configured) return;
    if ([owner hasCachedAd]) {
        [self presentCachedAd];
        return;
    }

    [owner requestLoad];
}

// ---------------------------------------------------------------------------
// Watchdogs and expiry
// ---------------------------------------------------------------------------

- (void)ro_markVisibilityWait:(ROInFeedDisplayEntry *)entry {
    if (entry == nil) return;
    if (!_visibleRequested || entry.actuallyVisible) {
        entry.visibleWaitStartedAt = kRONoTimestamp;
        return;
    }
    if (entry.visibleWaitStartedAt == kRONoTimestamp) {
        entry.visibleWaitStartedAt = RONow();
    }
}

// Only armed while something is actually waiting to be seen; a
// materializing entry with no pending Show is just a warm ad.
- (void)ro_scheduleWatchdog {
    [_watchdogTimer invalidate];
    _watchdogTimer = nil;
    if ([self ro_isReleased] || !_visibleRequested) return;
    if (![self ro_isHostVisible]) {
        _activeEntry.visibleWaitStartedAt = kRONoTimestamp;
        _materializingEntry.visibleWaitStartedAt = kRONoTimestamp;
        [self ro_scheduleForegroundRecheck];
        return;
    }

    [self ro_markVisibilityWait:_activeEntry];
    [self ro_markVisibilityWait:_materializingEntry];
    if (_materializingEntry == nil && _activeEntry == nil) return;

    __weak ROInFeedAdSlot *weakSelf = self;
    _watchdogTimer = [NSTimer
            scheduledTimerWithTimeInterval:kROWatchdogInterval
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleWatchdog];
    }];
}

// Without this both entries can park forever; either state would otherwise
// only clear after the cache age limit or an app restart.
- (void)ro_handleWatchdog {
    if ([self ro_isReleased]) return;
    if (![self ro_isHostVisible]) {
        [self ro_scheduleWatchdog];
        return;
    }

    NSTimeInterval now = RONow();
    if (_materializingEntry != nil) {
        if (_materializingEntry.visibleWaitStartedAt == kRONoTimestamp
                || now - _materializingEntry.visibleWaitStartedAt
                        < kROMaterializingEntryTimeout) {
            [self ro_scheduleWatchdog];
            return;
        }

        NSLog(@"%@: In-feed materializing slot was never displayed within "
               "%gms; dropping it and reloading"
              , kROTag, kROMaterializingEntryTimeout * 1000);
        ROInFeedDisplayEntry *stuck = _materializingEntry;
        _materializingEntry = nil;
        [self ro_destroyEntry:stuck];
        [self ro_showActiveEntry];
        [self ro_requestReplacementAd];
        [self ro_scheduleWatchdog];
        return;
    }

    if (_visibleRequested
            && _activeEntry != nil
            && !_activeEntry.actuallyVisible
            && _activeEntry.visibleWaitStartedAt != kRONoTimestamp
            && now - _activeEntry.visibleWaitStartedAt
                    >= kROActiveEntryVisibilityTimeout) {
        NSLog(@"%@: In-feed active slot never became visible within %gms; "
               "dropping it and reloading"
              , kROTag, kROActiveEntryVisibilityTimeout * 1000);
        ROInFeedDisplayEntry *stuck = _activeEntry;
        [self ro_pauseVisibleTimer:stuck];
        _activeEntry = nil;
        [self ro_destroyEntry:stuck];
        [self ro_requestReplacementAd];
    }

    [self ro_scheduleWatchdog];
}

- (void)ro_requestReplacementAd {
    ROInFeedAd *owner = _owner;
    if (owner != nil && [owner hasCachedAd]) {
        [self presentCachedAd];
        return;
    }
    // Through the retry backoff so a slot that keeps failing to display
    // cannot churn ad requests.
    [self ro_scheduleLayoutRetry];
}

- (void)ro_scheduleForegroundRecheck {
    [_foregroundRecheckTimer invalidate];
    _foregroundRecheckTimer = nil;
    ROInFeedAd *owner = _owner;
    if (owner == nil || owner.released || [self ro_isHostVisible]) return;
    if (!_visibleRequested && !(_configured && [owner hasCachedAd])) return;

    __weak ROInFeedAdSlot *weakSelf = self;
    _foregroundRecheckTimer = [NSTimer
            scheduledTimerWithTimeInterval:kROForegroundRecheckDelay
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleForegroundRecheck];
    }];
}

- (void)ro_handleForegroundRecheck {
    ROInFeedAd *owner = _owner;
    if (owner == nil || owner.released) return;
    if (![self ro_isHostVisible]) {
        [self ro_scheduleForegroundRecheck];
        return;
    }

    // Coming back to the foreground is a resume, not a rotation. Present only
    // what could not be presented while the window was dark - an empty slot -
    // and leave whatever survived the background on screen. presentCachedAd
    // is also the swap, so calling it here for a slot that already has an ad
    // turns every unfocus and focus into a rotation, with none of the dwell
    // the two rotation triggers apply.
    if ([self wantsCachedAd] && [owner hasCachedAd]) [self presentCachedAd];
    [self ro_showActiveEntry];
    [self ro_scheduleWatchdog];
    [self ro_scheduleRefresh];
}

- (void)ro_scheduleEntryExpiry {
    [_entryExpiryTimer invalidate];
    _entryExpiryTimer = nil;
    if ([self ro_isReleased] || _visibleRequested) return;

    NSTimeInterval nextExpiryAt = DBL_MAX;
    if (_materializingEntry != nil) {
        nextExpiryAt = MIN(
                nextExpiryAt
              , _materializingEntry.loadedAt + kROInFeedMaxCachedAdAge);
    }
    if (_activeEntry != nil) {
        nextExpiryAt = MIN(
                nextExpiryAt, _activeEntry.loadedAt + kROInFeedMaxCachedAdAge);
    }
    if (nextExpiryAt == DBL_MAX) return;

    __weak ROInFeedAdSlot *weakSelf = self;
    _entryExpiryTimer = [NSTimer
            scheduledTimerWithTimeInterval:MAX(0.01, nextExpiryAt - RONow())
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleEntryExpiry];
    }];
}

- (void)ro_handleEntryExpiry {
    ROInFeedAd *owner = _owner;
    if (owner == nil || owner.released || _visibleRequested) return;

    [self ro_removeExpiredEntries];
    if ([owner hasCachedAd]) {
        [self presentCachedAd];
    } else {
        [owner requestLoad];
    }
    [self ro_scheduleEntryExpiry];
}

- (void)ro_removeExpiredEntries {
    NSTimeInterval now = RONow();
    if (_materializingEntry != nil
            && now - _materializingEntry.loadedAt >= kROInFeedMaxCachedAdAge) {
        [self ro_destroyEntry:_materializingEntry];
        _materializingEntry = nil;
    }
    if (_activeEntry != nil
            && now - _activeEntry.loadedAt >= kROInFeedMaxCachedAdAge) {
        [self ro_pauseVisibleTimer:_activeEntry];
        [self ro_destroyEntry:_activeEntry];
        _activeEntry = nil;
    }
}

// ---------------------------------------------------------------------------
// Visible-time accounting and teardown
// ---------------------------------------------------------------------------

- (void)ro_resumeVisibleTimer:(ROInFeedDisplayEntry *)entry {
    if (entry == nil
            || entry.visibleStartedAt != kRONoTimestamp
            || !_visibleRequested
            || !entry.actuallyVisible) {
        return;
    }
    entry.visibleStartedAt = RONow();
}

- (void)ro_pauseVisibleTimer:(ROInFeedDisplayEntry *)entry {
    if (entry == nil || entry.visibleStartedAt == kRONoTimestamp) return;
    entry.accumulatedVisible += MAX(0, RONow() - entry.visibleStartedAt);
    entry.visibleStartedAt = kRONoTimestamp;
}

- (NSTimeInterval)ro_currentVisibleDuration:(ROInFeedDisplayEntry *)entry {
    if (entry == nil) return 0;
    NSTimeInterval duration = entry.accumulatedVisible;
    if (entry.visibleStartedAt != kRONoTimestamp) {
        duration += MAX(0, RONow() - entry.visibleStartedAt);
    }
    return duration;
}

- (void)ro_destroyEntry:(ROInFeedDisplayEntry *)entry {
    if (entry == nil) return;
    [entry.presentation releasePresentation];
    [_owner destroyAd:entry.ad];
}

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

- (NSString *)ro_describeEntry:(ROInFeedDisplayEntry *)entry {
    if (entry == nil) return @"none";
    return [NSString stringWithFormat:
            @"{ready=%@,visible=%@,displayed=%@}"
          , entry.ready ? @"true" : @"false"
          , entry.actuallyVisible ? @"true" : @"false"
          , entry.displayedAt != kRONoTimestamp ? @"true" : @"false"];
}

- (NSString *)ro_describeRequestedRect {
    if (_rect == nil) return @"unavailable";
    CGFloat scale = MAX(1, UIScreen.mainScreen.nativeScale);
    return [NSString stringWithFormat:
            @"[%lld,%lld,%lld,%lld]px = [%lld,%lld]dp (density=%g)"
          , (long long)llround(_rect.x * scale)
          , (long long)llround(_rect.y * scale)
          , (long long)llround(_rect.width * scale)
          , (long long)llround(_rect.height * scale)
          , (long long)llround(_rect.width)
          , (long long)llround(_rect.height)
          , scale];
}

- (NSString *)ro_failureMessage:(NSString *)reason
                     retryDelay:(NSTimeInterval)retryDelay {
    ROInFeedAd *owner = _owner;
    NSString *retryDescription = retryDelay == kROInFeedNoRetryScheduled
            ? @"not scheduled"
            : [NSString stringWithFormat:@"%lldms"
                      , (long long)llround(retryDelay * 1000)];
    return [NSString stringWithFormat:
            @"%@ | adUnitId=%@ | slot=%ld | rect=%@ | noFill=%ld | "
             "layoutFail=%ld | retry=%@"
          , reason ?: @""
          , owner == nil ? @"" : [owner unitAdUnitId]
          , (long)_index
          , [self ro_describeRequestedRect]
          , owner == nil ? 0L : (long)[owner noFillStreak]
          , (long)_layoutFailStreak
          , retryDescription];
}

@end
