#import "ROInFeedAd.h"

#import "ROInFeedAdSlot.h"

static NSString *const kROTag = @"InFeed";

static const int32_t kROLoadSuccessCode = 0;
static const NSInteger kROMaxRetryExponent = 5;
static const NSTimeInterval kRORetryBaseDelay = 1.0;
static const NSInteger kROMaxSlotCount = 8;
static const NSInteger kROMaxCacheSize = 5;

NSTimeInterval ROInFeedBackoffDelay(
        NSInteger streak
      , NSInteger immediateAttempts) {
    if (streak <= immediateAttempts) return 0;
    NSInteger exponent = MIN(
            kROMaxRetryExponent, streak - immediateAttempts);
    return kRORetryBaseDelay * (NSTimeInterval)(1LL << exponent);
}

@implementation ROInFeedCachedAd
@end

static float ROResolveBackgroundAlpha(float value);

@interface ROInFeedAd () <GADNativeAdLoaderDelegate
                              , GADNativeAdDelegate>
@end

@implementation ROInFeedAd {
    NSString *_adUnitId;
    NSInteger _cacheSize;
    float _backgroundAlpha;
    NSArray<ROInFeedAdSlot *> *_slots;
    NSMutableArray<ROInFeedCachedAd *> *_cachedAds;
    // OwnsAd runs inside the SDK's paid-event callback, off whatever thread
    // the SDK chose; the guarded set keeps the membership test safe.
    NSHashTable<GADNativeAd *> *_ownedAds;
    GADAdLoader *_adLoader;
    BOOL _isAdLoading;
    BOOL _retryScheduled;
    NSInteger _noFillStreak;

    NSTimer *_retryTimer;
    NSTimer *_cacheExpiryTimer;

    // Read on SDK threads, written from Unity's thread; the struct swap is
    // guarded so a reader never sees half of an update.
    ROInFeedAdListenerCallbacks _inFeedCallbacks;
    NSObject *_inFeedCallbacksLock;
}

static NSTimeInterval RONow(void) {
    return [NSProcessInfo processInfo].systemUptime;
}

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                       slotCount:(NSInteger)slotCount
                       cacheSize:(NSInteger)cacheSize
                 backgroundAlpha:(float)backgroundAlpha
                      instanceId:(int32_t)instanceId {
    self = [super initWithInstanceId:instanceId];
    if (self == nil) return nil;

    _adUnitId = [adUnitId copy];
    NSInteger boundedSlotCount = MAX(1, MIN(kROMaxSlotCount, slotCount));
    _cacheSize = MIN(
            kROMaxCacheSize
          , cacheSize < 1 ? boundedSlotCount + 1 : cacheSize);
    _backgroundAlpha = ROResolveBackgroundAlpha(backgroundAlpha);
    _cachedAds = [NSMutableArray array];
    _ownedAds = [NSHashTable weakObjectsHashTable];
    _inFeedCallbacksLock = [NSObject new];
    NSMutableArray<ROInFeedAdSlot *> *slots =
            [NSMutableArray arrayWithCapacity:boundedSlotCount];
    for (NSInteger i = 0; i < boundedSlotCount; ++i) {
        [slots addObject:[[ROInFeedAdSlot alloc] initWithOwner:self index:i]];
    }
    _slots = slots;
    [RONativeAd runOnMainThread:^{
        if (self.released) return;
        if (![RONativeAd isViewControllerUsable:
                [RONativeAd unityViewController]]) {
            NSLog(@"%@: created without a usable host; cache loading will "
                   "start when Configure or Show receives one", kROTag);
            return;
        }
        [self ro_startLoad];
    }];
    return self;
}

static float ROResolveBackgroundAlpha(float value) {
    if (isnan(value) || isinf(value)) {
        NSLog(@"%@: backgroundAlpha is not finite; using 1", kROTag);
        return 1;
    }
    return MAX(0.f, MIN(1.f, value));
}

- (void)setInFeedListenerCallbacks:
        (ROInFeedAdListenerCallbacks)callbacks {
    if (self.released) return;
    @synchronized (_inFeedCallbacksLock) {
        _inFeedCallbacks = callbacks;
    }
}

- (ROInFeedAdListenerCallbacks)ro_currentInFeedCallbacks {
    @synchronized (_inFeedCallbacksLock) {
        return _inFeedCallbacks;
    }
}

- (nullable ROInFeedAdSlot *)ro_slotAt:(NSInteger)slotIndex
                           operation:(NSString *)operation {
    if (slotIndex >= 0 && slotIndex < (NSInteger)_slots.count) {
        return _slots[slotIndex];
    }
    NSLog(@"%@: %@ ignored: slot %ld is outside [0, %ld]"
          , kROTag
          , operation
          , (long)slotIndex
          , (long)(_slots.count - 1));
    return nil;
}

- (void)configureSlot:(NSInteger)slotIndex
                    x:(CGFloat)xPt
                    y:(CGFloat)yPt
                width:(CGFloat)widthPt
               height:(CGFloat)heightPt {
    [RONativeAd runOnMainThread:^{
        if (self.released) {
            NSLog(@"%@: Configure ignored after Release", kROTag);
            return;
        }
        if (![RONativeAd isViewControllerUsable:
                [RONativeAd unityViewController]]) {
            NSLog(@"%@: Configure requires a usable host", kROTag);
            return;
        }
        ROInFeedAdSlot *slot = [self ro_slotAt:slotIndex
                                   operation:@"Configure"];
        if (slot == nil) return;

        [slot configureWithX:xPt y:yPt width:widthPt height:heightPt];
    }];
}

- (void)showSlot:(NSInteger)slotIndex {
    if (self.released) return;
    [RONativeAd runOnMainThread:^{
        if (self.released) return;
        if (![RONativeAd isViewControllerUsable:
                [RONativeAd unityViewController]]) {
            NSLog(@"%@: Show ignored because the host is not usable", kROTag);
            return;
        }
        ROInFeedAdSlot *slot = [self ro_slotAt:slotIndex operation:@"Show"];
        if (slot != nil) [slot show];
    }];
}

- (void)hideSlot:(NSInteger)slotIndex {
    if (self.released) return;
    [RONativeAd runOnMainThread:^{
        if (self.released) return;
        ROInFeedAdSlot *slot = [self ro_slotAt:slotIndex operation:@"Hide"];
        if (slot != nil) [slot hide];
    }];
}

- (void)setSlot:(NSInteger)slotIndex positionX:(CGFloat)xPt y:(CGFloat)yPt {
    if (self.released) return;
    [RONativeAd runOnMainThread:^{
        if (self.released) return;
        ROInFeedAdSlot *slot = [self ro_slotAt:slotIndex
                                   operation:@"SetPosition"];
        if (slot != nil) [slot setPositionX:xPt y:yPt];
    }];
}

- (void)releaseAd {
    if (self.released) return;
    [self markReleased];
    [self invalidateLoadGeneration];
    [RONativeAd runOnMainThread:^{
        self->_isAdLoading = NO;
        self->_retryScheduled = NO;
        [self->_retryTimer invalidate];
        [self->_cacheExpiryTimer invalidate];
        for (ROInFeedAdSlot *slot in self->_slots) [slot releaseOnMain];
        for (ROInFeedCachedAd *cached in self->_cachedAds) {
            [self destroyAd:cached.ad];
        }
        [self->_cachedAds removeAllObjects];
        self->_adLoader = nil;
        ROInFeedAdListenerCallbacks empty = {0};
        @synchronized (self->_inFeedCallbacksLock) {
            self->_inFeedCallbacks = empty;
        }
    }];
}

// ---------------------------------------------------------------------------
// Shared supply
// ---------------------------------------------------------------------------

// Keep the cache topped up regardless of what is on screen; the slots decide
// when a warm ad is spent, this only decides when to fetch another.
- (BOOL)ro_shouldStartLoad {
    return (NSInteger)_cachedAds.count < _cacheSize;
}

- (BOOL)ro_startLoad {
    UIViewController *host = [RONativeAd unityViewController];
    if (self.released
            || _isAdLoading
            || _retryScheduled
            || ![self ro_shouldStartLoad]
            || ![RONativeAd isViewControllerUsable:host]) {
        return NO;
    }

    _isAdLoading = YES;
    [self nextLoadGeneration];
    [self ro_notifyLoadingStarted];

    _adLoader = [[GADAdLoader alloc]
            initWithAdUnitID:_adUnitId
          rootViewController:host
                     adTypes:@[ GADAdLoaderAdTypeNative ]
                     options:[RONativeAd adLoaderOptionsWithStartMuted:YES]];
    _adLoader.delegate = self;
    [_adLoader loadRequest:[GADRequest request]];
    return YES;
}

// From a slot that has nothing to materialize and found the cache empty:
// start a load, or if one is already in flight or backed off, leave the
// existing schedule in charge.
- (void)requestLoad {
    if (self.released) return;
    if ([self ro_startLoad]) return;
    if (!_isAdLoading && !_retryScheduled && [self ro_shouldStartLoad]) {
        [self ro_postRetry:ROInFeedBackoffDelay(MAX(1, _noFillStreak), 0)];
    }
}

- (void)adLoader:(GADAdLoader *)adLoader
        didReceiveNativeAd:(GADNativeAd *)nativeAd {
    [RONativeAd runOnMainThread:^{
        if (self.released) return;

        self->_noFillStreak = 0;
        nativeAd.delegate = self;
        @synchronized (self->_ownedAds) {
            [self->_ownedAds addObject:nativeAd];
        }
        ROInFeedCachedAd *cached = [[ROInFeedCachedAd alloc] init];
        cached.ad = nativeAd;
        cached.loadedAt = RONow();
        [self->_cachedAds addObject:cached];
        [self ro_scheduleCacheExpiry];
        __weak ROInFeedAd *weakSelf = self;
        __weak GADNativeAd *weakAd = nativeAd;
        [self bindPaidEventForAd:nativeAd
                    paidAdUnitId:self->_adUnitId
                     isCurrentAd:^BOOL{
            ROInFeedAd *strongSelf = weakSelf;
            GADNativeAd *strongAd = weakAd;
            if (strongSelf == nil || strongAd == nil) return NO;
            @synchronized (strongSelf->_ownedAds) {
                return [strongSelf->_ownedAds containsObject:strongAd];
            }
        }];

        self->_isAdLoading = NO;
        [self ro_notifyLoadingCompletedWithCode:kROLoadSuccessCode
                                        message:@""];
        [self ro_offerCacheToSlots];
        [self ro_startLoad];
    }];
}

- (void)adLoader:(GADAdLoader *)adLoader
        didFailToReceiveAdWithError:(NSError *)error {
    [RONativeAd runOnMainThread:^{
        if (self.released) return;

        self->_isAdLoading = NO;
        NSTimeInterval retryDelay = [self ro_scheduleNoFillRetry];
        [self ro_notifyLoadingCompletedWithCode:(int32_t)error.code
                                        message:[self ro_failureMessage:
                                               error.localizedDescription
                                                             retryDelay:retryDelay]];
    }];
}

// Not gated on the load generation: the ad that was clicked may well have
// come from an earlier load.
- (void)nativeAdDidRecordClick:(GADNativeAd *)nativeAd {
    [RONativeAd runOnMainThread:^{
        for (ROInFeedAdSlot *slot in self->_slots) [slot commitAdClick];
    }];
}

- (void)ro_offerCacheToSlots {
    for (ROInFeedAdSlot *slot in _slots) {
        if (_cachedAds.count == 0) return;
        if ([slot wantsCachedAd]) [slot presentCachedAd];
    }
}

- (BOOL)hasCachedAd {
    [self ro_removeExpiredCachedAds];
    return _cachedAds.count > 0;
}

- (NSInteger)cachedCount {
    return (NSInteger)_cachedAds.count;
}

// Pops for one slot and immediately starts replacing what it took.
- (nullable ROInFeedCachedAd *)takeCachedAd {
    [self ro_removeExpiredCachedAds];
    ROInFeedCachedAd *next = _cachedAds.firstObject;
    if (next != nil) [_cachedAds removeObjectAtIndex:0];
    [self ro_startLoad];
    return next;
}

// ---------------------------------------------------------------------------
// Retry and expiry
// ---------------------------------------------------------------------------

// No fill is a property of inventory: waiting is the only thing that can
// help, so back off.
- (NSTimeInterval)ro_scheduleNoFillRetry {
    [self ro_cancelRetry];
    if (self.released) return kROInFeedNoRetryScheduled;

    ++_noFillStreak;
    return [self ro_postRetry:ROInFeedBackoffDelay(_noFillStreak, 0)];
}

- (NSTimeInterval)ro_postRetry:(NSTimeInterval)delay {
    [self ro_cancelRetry];
    if (self.released) return kROInFeedNoRetryScheduled;

    _retryScheduled = YES;
    __weak ROInFeedAd *weakSelf = self;
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
    if (self.released) return;

    [self ro_offerCacheToSlots];
    if ([self ro_startLoad]) return;
    if (!_isAdLoading && [self ro_shouldStartLoad] && !_retryScheduled) {
        // Nothing failed here, the state simply was not ready; re-arm at the
        // current delay rather than counting another failure.
        [self ro_postRetry:ROInFeedBackoffDelay(MAX(1, _noFillStreak), 0)];
    }
}

- (void)ro_scheduleCacheExpiry {
    [_cacheExpiryTimer invalidate];
    _cacheExpiryTimer = nil;
    if (self.released) return;

    NSTimeInterval nextExpiryAt = DBL_MAX;
    for (ROInFeedCachedAd *cached in _cachedAds) {
        nextExpiryAt = MIN(
                nextExpiryAt, cached.loadedAt + kROInFeedMaxCachedAdAge);
    }
    if (nextExpiryAt == DBL_MAX) return;

    __weak ROInFeedAd *weakSelf = self;
    _cacheExpiryTimer = [NSTimer
            scheduledTimerWithTimeInterval:MAX(0.01, nextExpiryAt - RONow())
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleCacheExpiry];
    }];
}

- (void)ro_handleCacheExpiry {
    if (self.released) return;

    [self ro_removeExpiredCachedAds];
    [self ro_offerCacheToSlots];
    [self ro_startLoad];
    [self ro_scheduleCacheExpiry];
}

- (void)ro_removeExpiredCachedAds {
    NSTimeInterval now = RONow();
    NSMutableArray<ROInFeedCachedAd *> *expired = [NSMutableArray array];
    for (ROInFeedCachedAd *cached in _cachedAds) {
        if (now - cached.loadedAt >= kROInFeedMaxCachedAdAge) {
            [expired addObject:cached];
        }
    }
    for (ROInFeedCachedAd *cached in expired) {
        [self destroyAd:cached.ad];
        [_cachedAds removeObject:cached];
    }
}

- (void)destroyAd:(GADNativeAd *)ad {
    if (ad == nil) return;
    @synchronized (_ownedAds) {
        [_ownedAds removeObject:ad];
    }
    ad.paidEventHandler = nil;
    ad.delegate = nil;
}

// ---------------------------------------------------------------------------
// Slot-facing accessors and notifications
// ---------------------------------------------------------------------------

- (float)slotBackgroundAlpha {
    return _backgroundAlpha;
}

- (NSString *)unitAdUnitId {
    return _adUnitId;
}

- (NSInteger)noFillStreak {
    return _noFillStreak;
}

- (void)ro_notifyLoadingStarted {
    ROInFeedAdListenerCallbacks callbacks =
            [self ro_currentInFeedCallbacks];
    if (callbacks.loadingStarted == NULL) return;
    callbacks.loadingStarted(self.instanceId);
}

- (void)ro_notifyLoadingCompletedWithCode:(int32_t)errorCode
                                  message:(NSString *)errorMessage {
    ROInFeedAdListenerCallbacks callbacks =
            [self ro_currentInFeedCallbacks];
    if (callbacks.loadingCompleted == NULL) return;
    callbacks.loadingCompleted(
            self.instanceId
          , errorCode
          , (errorMessage ?: @"").UTF8String);
}

// The base's paid binding delivers through this hook.
- (void)notifyAdPaidWithSource:(NSString *)source
                      adUnitId:(NSString *)adUnitId
                         value:(double)value
                  currencyCode:(NSString *)currencyCode
                     precision:(int32_t)precision {
    ROInFeedAdListenerCallbacks callbacks =
            [self ro_currentInFeedCallbacks];
    if (callbacks.adPaid == NULL) return;
    callbacks.adPaid(
            self.instanceId
          , (source ?: @"").UTF8String
          , (adUnitId ?: @"").UTF8String
          , value
          , (currencyCode ?: @"").UTF8String
          , precision);
}

- (void)notifySlotDisplayed:(NSInteger)slotIndex {
    ROInFeedAdListenerCallbacks callbacks =
            [self ro_currentInFeedCallbacks];
    if (callbacks.slotDisplayed == NULL) return;
    callbacks.slotDisplayed(self.instanceId, (int32_t)slotIndex);
}

- (void)notifySlotShowNotReady:(NSInteger)slotIndex {
    ROInFeedAdListenerCallbacks callbacks =
            [self ro_currentInFeedCallbacks];
    if (callbacks.slotShowNotReady == NULL) return;
    callbacks.slotShowNotReady(self.instanceId, (int32_t)slotIndex);
}

- (void)notifySlotPresentationFailed:(NSInteger)slotIndex
                                code:(int32_t)errorCode
                             message:(NSString *)errorMessage {
    ROInFeedAdListenerCallbacks callbacks =
            [self ro_currentInFeedCallbacks];
    if (callbacks.slotPresentationFailed == NULL) return;
    callbacks.slotPresentationFailed(
            self.instanceId
          , (int32_t)slotIndex
          , errorCode
          , (errorMessage ?: @"").UTF8String);
}

- (NSString *)ro_failureMessage:(NSString *)reason
                     retryDelay:(NSTimeInterval)retryDelay {
    NSString *retryDescription = retryDelay == kROInFeedNoRetryScheduled
            ? @"not scheduled"
            : [NSString stringWithFormat:@"%lldms"
                      , (long long)llround(retryDelay * 1000)];
    return [NSString stringWithFormat:
            @"%@ | adUnitId=%@ | cached=%lu | noFill=%ld | retry=%@"
          , reason ?: @""
          , _adUnitId
          , (unsigned long)_cachedAds.count
          , (long)_noFillStreak
          , retryDescription];
}

@end
