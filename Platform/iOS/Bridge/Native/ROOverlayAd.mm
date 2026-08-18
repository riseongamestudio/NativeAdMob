#import "ROOverlayAd.h"

#import "ROOverlayAdPresentation.h"

static NSString *const kROTag = @"Overlay";
static const int32_t kROLoadSuccessCode = 0;
// The ceiling the in-feed unit already keeps: past a handful, warm ads
// expire unseen and the impressions are simply burnt.
static const NSInteger kROMaxCacheSize = 5;
// developers.google.com/admob/ios/native/start, Request ads: "Since ads
// expire after an hour, you should clear this cache and reload with new ads
// every hour." The in-feed unit keeps the same number, from the same
// sentence.
static const NSTimeInterval kROMaxCachedAdAge = 3600;

static NSTimeInterval RONow(void) {
    return [NSProcessInfo processInfo].systemUptime;
}

// An ad and the moment it arrived - the only two things needed to know
// whether it may still be shown.
@interface ROOverlayCachedAd : NSObject
@property (nonatomic, strong) GADNativeAd *ad;
@property (nonatomic, assign) NSTimeInterval loadedAt;
@end

@implementation ROOverlayCachedAd
@end

// The style snapshot Configure publishes and every show reads - the same
// immutable OverlayStyle the Java side passes around.
@interface HBOverlayStyle : NSObject
@property (nonatomic, readonly) BOOL fullscreen;
@property (nonatomic, readonly) int32_t cooldown;
// Ordinals shared with the C# enums: Left 0, Right 1, Random 2, and for the
// timer OppositeOfClose 3, SameAsClose 4.
@property (nonatomic, readonly) int32_t closeSide;
@property (nonatomic, readonly) int32_t timerSide;
@property (nonatomic, readonly) float heightRatio;
@property (nonatomic, readonly) float backgroundAlpha;
// The close button commits the ad's click on its way out.
@property (nonatomic, readonly) BOOL fakeCloseAutoDismiss;
@end

@implementation HBOverlayStyle

- (instancetype)initWithFullscreen:(BOOL)fullscreen
                          cooldown:(int32_t)cooldown
                         closeSide:(int32_t)closeSide
                         timerSide:(int32_t)timerSide
                       heightRatio:(float)heightRatio
                   backgroundAlpha:(float)backgroundAlpha
              fakeCloseAutoDismiss:(BOOL)fakeCloseAutoDismiss {
    self = [super init];
    if (self == nil) return nil;
    _fullscreen = fullscreen;
    _cooldown = MAX(0, cooldown);
    _closeSide = closeSide;
    _timerSide = timerSide;
    _heightRatio = heightRatio;
    _backgroundAlpha = backgroundAlpha;
    _fakeCloseAutoDismiss = fakeCloseAutoDismiss;
    return self;
}

- (HBOverlayStyle *)styleWithCooldown:(int32_t)cooldown
                            closeSide:(int32_t)closeSide
                            timerSide:(int32_t)timerSide
                      redirectOnClose:(BOOL)redirectOnClose {
    return [[HBOverlayStyle alloc]
            initWithFullscreen:self.fullscreen
                      cooldown:cooldown
                     closeSide:closeSide
                     timerSide:timerSide
                   heightRatio:self.heightRatio
               backgroundAlpha:self.backgroundAlpha
          fakeCloseAutoDismiss:redirectOnClose];
}

// Random is answered once per presentation; the relative timer modes then
// read that answer rather than rolling again.
- (BOOL)resolveCloseOnLeft {
    if (self.closeSide == 0) return YES;
    if (self.closeSide == 1) return NO;
    return arc4random_uniform(2) == 0;
}

- (BOOL)resolveTimerOnLeft:(BOOL)closeOnLeft {
    switch (self.timerSide) {
        case 0:  return YES;
        case 1:  return NO;
        case 3:  return !closeOnLeft;
        case 4:  return closeOnLeft;
        default: return arc4random_uniform(2) == 0;
    }
}

- (BOOL)pausesGame {
    return self.fullscreen;
}

@end

@interface ROOverlayAd () <GADNativeAdLoaderDelegate, GADNativeAdDelegate>
@end

// The face of the creative's assets at one moment. A presentation built
// at load time is only valid while this stays the same: assets that finish
// arriving later change the layout the ad needs, and a stale face must be
// rebuilt rather than shown.
static NSString *ROMediaSignature(GADNativeAd *nativeAd) {
    GADMediaContent *mediaContent = nativeAd.mediaContent;
    BOOL hasVideo = mediaContent.hasVideoContent;
    BOOL hasMainImage = mediaContent.mainImage != nil;
    CGFloat aspectRatio = mediaContent != nil ? mediaContent.aspectRatio : 0;
    BOOL hasAnyImage = NO;
    for (GADNativeAdImage *image in nativeAd.images) {
        if (image.image != nil) {
            hasAnyImage = YES;
            break;
        }
    }
    return [NSString stringWithFormat:@"%d|%d|%d|%g"
          , hasVideo, hasMainImage, hasAnyImage, aspectRatio];
}

@implementation ROOverlayAd {
    NSString *_adUnitId;
    HBOverlayStyle *_configuredStyle;
    BOOL _configured;
    BOOL _isAdLoading;

    GADAdLoader *_adLoader;
    NSInteger _cacheSize;
    // The warm ads, oldest first. The head is the one the next show takes,
    // and the only one worth a prepared face. Oldest first also means the
    // head is always the first to go stale.
    NSMutableArray<ROOverlayCachedAd *> *_cachedAds;
    NSTimer *_cacheExpiryTimer;
    // The paid event fires on whatever thread the SDK picked, while the
    // array is a main-thread structure. Membership answers "is this still
    // ours" without walking one that may be changing underneath.
    NSHashTable<GADNativeAd *> *_ownedAds;
    GADNativeAd *_activeNativeAd;
    ROOverlayAdPresentation *_presentation;
    ROOverlayAdPresentation *_preparedPresentation;
    GADNativeAd *_preparedNativeAd;
    HBOverlayStyle *_preparedStyle;
    NSString *_preparedMediaSignature;
    RONativeAdShowCompletedCallback _activeShowCompleted;
    int32_t _activeShowId;
}

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                      instanceId:(int32_t)instanceId {
    self = [super initWithInstanceId:instanceId];
    if (self == nil) return nil;
    _adUnitId = [adUnitId copy];
    _cacheSize = 1;
    _cachedAds = [NSMutableArray array];
    _ownedAds = [NSHashTable weakObjectsHashTable];
    return self;
}

- (void)configureWithFullscreen:(BOOL)fullscreen
                    heightRatio:(float)heightRatio
                backgroundAlpha:(float)backgroundAlpha
                      cacheSize:(int32_t)cacheSize
                       cooldown:(int32_t)cooldown
                      closeSide:(int32_t)closeSide
                      timerSide:(int32_t)timerSide
                redirectOnClose:(BOOL)redirectOnClose {
    [ROBaseAd runOnMainThread:^{
        if (self.released) {
            NSLog(@"%@: Configure ignored after Release", kROTag);
            return;
        }
        if (self->_configured) {
            NSLog(@"%@: Configure may only be called once per instance", kROTag);
            return;
        }

        // How many ads stay warm at once. One - always hold a spare -
        // is the placement that never asked; a chained placement raises it
        // so the follow-up is already in hand when the first ad closes.
        self->_cacheSize = MAX(1, MIN(kROMaxCacheSize, (NSInteger)cacheSize));
        self->_configuredStyle = [[HBOverlayStyle alloc]
                initWithFullscreen:fullscreen
                          cooldown:cooldown
                         closeSide:closeSide
                         timerSide:timerSide
                       heightRatio:heightRatio
                   backgroundAlpha:backgroundAlpha
              fakeCloseAutoDismiss:redirectOnClose];
        self->_configured = YES;
    }];
}

- (void)setCloseWithCooldown:(int32_t)cooldown
                   closeSide:(int32_t)closeSide
                   timerSide:(int32_t)timerSide
             redirectOnClose:(BOOL)redirectOnClose {
    [ROBaseAd runOnMainThread:^{
        HBOverlayStyle *currentStyle = self->_configuredStyle;
        if (!self->_configured || self.released || currentStyle == nil) {
            NSLog(@"%@: SetClose ignored before Configure or after "
                    "Release", kROTag);
            return;
        }
        HBOverlayStyle *updatedStyle =
                [currentStyle styleWithCooldown:cooldown
                                             closeSide:closeSide
                                             timerSide:timerSide
                                       redirectOnClose:redirectOnClose];
        self->_configuredStyle = updatedStyle;
        [self ro_rebuildPreparedPresentationForStyle:updatedStyle];
    }];
}

- (void)load {
    [ROBaseAd runOnMainThread:^{
        if (!self->_configured || self.released) {
            NSLog(@"%@: Load ignored before Configure or after Release"
                  , kROTag);
            return;
        }
        UIViewController *host = [ROBaseAd unityViewController];
        if (![ROBaseAd isViewControllerUsable:host]) {
            NSLog(@"%@: Load ignored because the host controller is not "
                    "usable", kROTag);
            return;
        }
        [self ro_startLoadWithHost:host];
    }];
}

// The one gate every load passes. A warm ad is never thrown away to fetch
// another - that is what made the replacement fetched during a show die at
// dismissal and the player wait for a fresh load anyway - so a free seat in
// the cache is the only reason to load, whoever is on screen. Says whether
// a request actually left.
- (BOOL)ro_startLoadWithHost:(UIViewController *)host {
    HBOverlayStyle *loadStyle = _configuredStyle;
    if (self.released
            || !_configured
            || _isAdLoading
            || loadStyle == nil
            || (NSInteger)_cachedAds.count >= _cacheSize
            || ![ROBaseAd isViewControllerUsable:host]) {
        return NO;
    }

    [self ro_doLoadAdWithHost:host style:loadStyle];
    return YES;
}

- (void)ro_doLoadAdWithHost:(UIViewController *)host
                      style:(HBOverlayStyle *)loadStyle {
    _isAdLoading = YES;
    [self nextLoadGeneration];
    [self ro_notifyCurrentState];
    [self notifyLoadingStarted];

    _adLoader = [[GADAdLoader alloc]
            initWithAdUnitID:_adUnitId
          rootViewController:host
                     adTypes:@[ GADAdLoaderAdTypeNative ]
                     options:[ROBaseAd adLoaderOptionsWithStartMuted:
                                     !loadStyle.pausesGame]];
    _adLoader.delegate = self;
    [_adLoader loadRequest:[GADRequest request]];
}

- (void)adLoader:(GADAdLoader *)adLoader
        didReceiveNativeAd:(GADNativeAd *)nativeAd {
    [ROBaseAd runOnMainThread:^{
        if (self.released) return;

        @synchronized (self->_ownedAds) {
            [self->_ownedAds addObject:nativeAd];
        }
        ROOverlayCachedAd *cached = [[ROOverlayCachedAd alloc] init];
        cached.ad = nativeAd;
        cached.loadedAt = RONow();
        [self->_cachedAds addObject:cached];
        [self ro_scheduleCacheExpiry];
        nativeAd.delegate = self;
        __weak ROOverlayAd *weakSelf = self;
        __weak GADNativeAd *weakAd = nativeAd;
        [self bindPaidEventForAd:nativeAd
                    paidAdUnitId:self->_adUnitId
                     isCurrentAd:^BOOL{
            ROOverlayAd *strongSelf = weakSelf;
            GADNativeAd *strongAd = weakAd;
            if (strongSelf == nil || strongAd == nil) return NO;
            @synchronized (strongSelf->_ownedAds) {
                return [strongSelf->_ownedAds containsObject:strongAd];
            }
        }];

        // Both formats prepay their face at load time - the Android side
        // prebuilds the Activity's content view, this side the whole
        // presentation - so the show itself has nothing slow left to do.
        // Only the head earns one: it is the ad the next show takes, and
        // those behind it get theirs on reaching the front.
        if ([self ro_isHeadAd:nativeAd]) {
            [self ro_prepareHeadFace];
        }

        self->_isAdLoading = NO;
        [self ro_notifyCurrentState];
        [self notifyLoadingCompletedWithCode:kROLoadSuccessCode message:@""];
        // One request per ad: this chains on until the cache is full, and
        // the last one simply finds no seat left. A turn later, never here:
        // starting the next load swaps _adLoader, and this ad's own loader
        // is still walking its callbacks on the stack above us.
        dispatch_async(dispatch_get_main_queue(), ^{
            if (self.released) return;

            [self ro_startLoadWithHost:[ROBaseAd unityViewController]];
        });
    }];
}

- (void)adLoader:(GADAdLoader *)adLoader
        didFailToReceiveAdWithError:(NSError *)error {
    [ROBaseAd runOnMainThread:^{
        if (self.released) return;

        self->_isAdLoading = NO;
        [self ro_notifyCurrentState];
        [self notifyLoadingCompletedWithCode:(int32_t)error.code
                                     message:error.localizedDescription];
    }];
}

// Not gated on generation: the ad that was clicked may well be the one on
// screen from an earlier load, same as the Android AdListener comment.
- (void)nativeAdDidRecordClick:(GADNativeAd *)nativeAd {
    [ROBaseAd runOnMainThread:^{
        [self->_presentation onAdClicked];
    }];
}

- (void)showWithShowId:(int32_t)showId
             onCompleted:(RONativeAdShowCompletedCallback)onCompleted {
    [ROBaseAd runOnMainThread:^{
        if (self.released) {
            [self ro_invokeCompleted:onCompleted
                              showId:showId
                             message:@"Ad released"
                          adConsumed:NO];
            return;
        }
        if (!self->_configured
                || [self ro_isShowing]
                || self->_activeNativeAd != nil) {
            [self ro_invokeCompleted:onCompleted
                              showId:showId
                             message:!self->_configured
                                    ? @"Ad not configured"
                                    : @"Ad already showing"
                          adConsumed:NO];
            return;
        }
        UIViewController *host = [ROBaseAd unityViewController];
        HBOverlayStyle *requestedStyle = self->_configuredStyle;
        [self ro_sweepExpiredCachedAds];
        if (self->_cachedAds.count == 0
                || requestedStyle == nil
                || ![ROBaseAd isViewControllerUsable:host]) {
            [self ro_invokeCompleted:onCompleted
                              showId:showId
                             message:@"Ad not ready"
                          adConsumed:NO];
            return;
        }

        GADNativeAd *shownAd = self->_cachedAds.firstObject.ad;
        [self->_cachedAds removeObjectAtIndex:0];
        [self ro_scheduleCacheExpiry];
        self->_activeNativeAd = shownAd;
        self->_activeShowCompleted = onCompleted;
        self->_activeShowId = showId;
        [self ro_notifyCurrentState];
        // The seat this show emptied is refilled at once, and whoever is at
        // the head now earns a face. Both wait a turn so this show takes
        // the face it was promised first.
        dispatch_async(dispatch_get_main_queue(), ^{
            if (self.released) return;

            [self ro_startLoadWithHost:[ROBaseAd unityViewController]];
            [self ro_prepareHeadFace];
        });

        ROOverlayAdPresentation *createdPresentation =
                [self ro_takePreparedPresentationForAd:shownAd
                                                 style:requestedStyle];
        if (createdPresentation == nil) {
            createdPresentation = [self ro_createPresentationWithHost:host
                                                                   ad:shownAd
                                                                style:requestedStyle];
            if (![createdPresentation prepare]) {
                [createdPresentation releasePresentation];
                [self ro_completePresentationForAd:shownAd
                                           message:@"Failed to prepare ad "
                                                    "presentation"];
                return;
            }
        }
        self->_presentation = createdPresentation;

        if (![createdPresentation show]) {
            [createdPresentation releasePresentation];
            if (self->_presentation == createdPresentation) {
                self->_presentation = nil;
            }
            [self ro_completePresentationForAd:shownAd message:@""];
        }
    }];
}

- (void)hide {
    [ROBaseAd runOnMainThread:^{
        ROOverlayAdPresentation *currentPresentation =
                self->_presentation;
        if (currentPresentation != nil && currentPresentation.isShowing) {
            [currentPresentation dismiss];
        }
    }];
}

- (void)releaseAd {
    if (self.released) return;
    [self markReleased];
    [self invalidateLoadGeneration];
    [ROBaseAd runOnMainThread:^{
        self->_isAdLoading = NO;
        [self ro_releasePreparedPresentation];

        ROOverlayAdPresentation *currentPresentation =
                self->_presentation;
        self->_presentation = nil;
        [currentPresentation releasePresentation];

        [self->_cacheExpiryTimer invalidate];
        self->_cacheExpiryTimer = nil;
        [self->_cachedAds removeAllObjects];
        self->_activeNativeAd = nil;
        RONativeAdShowCompletedCallback completed =
                self->_activeShowCompleted;
        int32_t completedShowId = self->_activeShowId;
        self->_activeShowCompleted = NULL;

        self->_configuredStyle = nil;
        self->_adLoader = nil;
        [self clearListenerCallbacks];
        [self ro_invokeCompleted:completed
                          showId:completedShowId
                         message:@"Ad released"
                      adConsumed:NO];
    }];
}

- (ROOverlayAdPresentation *)
        ro_createPresentationWithHost:(UIViewController *)host
                                   ad:(GADNativeAd *)ad
                                style:(HBOverlayStyle *)style {
    BOOL closeOnLeftForPresentation = [style resolveCloseOnLeft];
    ROOverlayAdPresentation *createdPresentation =
            [[ROOverlayAdPresentation alloc]
                    initWithViewController:host
                                  nativeAd:ad
                              countdownSec:style.cooldown
                               closeOnLeft:closeOnLeftForPresentation
                               timerOnLeft:[style resolveTimerOnLeft:
                                            closeOnLeftForPresentation]
                                fullscreen:style.fullscreen
                               heightRatio:style.heightRatio
                           backgroundAlpha:style.backgroundAlpha
                      fakeCloseAutoDismiss:style.fakeCloseAutoDismiss];
    __weak ROOverlayAd *weakSelf = self;
    __weak GADNativeAd *weakAd = ad;
    createdPresentation.onShow = ^{
        ROOverlayAd *strongSelf = weakSelf;
        if (strongSelf != nil
                && !strongSelf.released
                && strongSelf->_activeNativeAd == weakAd) {
            [strongSelf notifyDisplayed];
        }
    };
    createdPresentation.onDismiss = ^{
        ROOverlayAd *strongSelf = weakSelf;
        GADNativeAd *strongAd = weakAd;
        if (strongSelf != nil && strongAd != nil) {
            [strongSelf ro_completePresentationForAd:strongAd message:@""];
        }
    };
    return createdPresentation;
}

- (BOOL)ro_isHeadAd:(GADNativeAd *)ad {
    return ad != nil && _cachedAds.firstObject.ad == ad;
}

// An hour after it arrived a warm ad may no longer be shown, so it goes and
// a fresh one is fetched. The head ages out first, which is also the one
// holding a prepared face - hence the rebuild.
- (void)ro_handleCacheExpiry {
    if (self.released) return;

    if ([self ro_removeExpiredCachedAds]) {
        [self ro_prepareHeadFace];
        [self ro_notifyCurrentState];
    }
    [self ro_startLoadWithHost:[ROBaseAd unityViewController]];
    [self ro_scheduleCacheExpiry];
}

- (void)ro_scheduleCacheExpiry {
    [_cacheExpiryTimer invalidate];
    _cacheExpiryTimer = nil;
    if (self.released) return;

    NSTimeInterval nextExpiryAt = DBL_MAX;
    for (ROOverlayCachedAd *cached in _cachedAds) {
        nextExpiryAt = MIN(nextExpiryAt, cached.loadedAt + kROMaxCachedAdAge);
    }
    if (nextExpiryAt == DBL_MAX) return;

    __weak ROOverlayAd *weakSelf = self;
    _cacheExpiryTimer = [NSTimer
            scheduledTimerWithTimeInterval:MAX(0.01, nextExpiryAt - RONow())
                                   repeats:NO
                                     block:^(NSTimer *timer) {
        [weakSelf ro_handleCacheExpiry];
    }];
}

// The show's own sweep: systemUptime keeps counting while the device sleeps
// but the timer does not fire, so a phone woken after a long night can hold
// ads older than the schedule believes.
- (void)ro_sweepExpiredCachedAds {
    if (![self ro_removeExpiredCachedAds]) return;

    [self ro_prepareHeadFace];
    [self ro_notifyCurrentState];
    [self ro_startLoadWithHost:[ROBaseAd unityViewController]];
    [self ro_scheduleCacheExpiry];
}

- (BOOL)ro_removeExpiredCachedAds {
    NSTimeInterval now = RONow();
    ROOverlayCachedAd *head = _cachedAds.firstObject;
    // Oldest first, so nothing behind the head can be stale while the head
    // is not: one look answers for the whole queue.
    if (head == nil || now - head.loadedAt < kROMaxCachedAdAge) return NO;

    // The prepared face is built on the ad about to go, so it goes first.
    [self ro_releasePreparedPresentation];
    NSMutableArray<ROOverlayCachedAd *> *expired = [NSMutableArray array];
    for (ROOverlayCachedAd *cached in _cachedAds) {
        if (now - cached.loadedAt >= kROMaxCachedAdAge) {
            [expired addObject:cached];
        }
    }
    for (ROOverlayCachedAd *cached in expired) {
        @synchronized (_ownedAds) {
            [_ownedAds removeObject:cached.ad];
        }
        [_cachedAds removeObject:cached];
    }
    return YES;
}

// The face of the ad at the head, prepaid before any show asks for it.
- (void)ro_prepareHeadFace {
    GADNativeAd *head = _cachedAds.firstObject.ad;
    HBOverlayStyle *style = _configuredStyle;
    if (head == nil || style == nil) {
        [self ro_releasePreparedPresentation];
        return;
    }

    [self ro_preparePresentationForAd:head style:style];
}

- (void)ro_preparePresentationForAd:(GADNativeAd *)ad
                              style:(HBOverlayStyle *)style {
    [self ro_releasePreparedPresentation];
    UIViewController *host = [ROBaseAd unityViewController];
    if (self.released
            || ad == nil
            || style == nil
            || ![self ro_isHeadAd:ad]
            || ![ROBaseAd isViewControllerUsable:host]) {
        return;
    }

    ROOverlayAdPresentation *createdPresentation =
            [self ro_createPresentationWithHost:host ad:ad style:style];
    if (![createdPresentation prepare]
            || self.released
            || ![self ro_isHeadAd:ad]
            || _configuredStyle != style) {
        [createdPresentation releasePresentation];
        return;
    }

    _preparedPresentation = createdPresentation;
    _preparedNativeAd = ad;
    _preparedStyle = style;
    _preparedMediaSignature = ROMediaSignature(ad);
}

- (void)ro_rebuildPreparedPresentationForStyle:(HBOverlayStyle *)requestedStyle {
    // Every runtime style setter publishes a new snapshot and passes through
    // here so a prepared face never keeps stale config.
    // A show in progress is no reason to skip: the rebuild only ever
    // touches the prepared face of the ad at the HEAD of the cache, never
    // the one on screen.
    if (self.released
            || requestedStyle == nil
            || _configuredStyle != requestedStyle
            || _cachedAds.count == 0) {
        return;
    }

    GADNativeAd *ad = _cachedAds.firstObject.ad;
    [self ro_releasePreparedPresentation];
    [self ro_preparePresentationForAd:ad style:requestedStyle];
}

- (ROOverlayAdPresentation *)
        ro_takePreparedPresentationForAd:(GADNativeAd *)ad
                                   style:(HBOverlayStyle *)style {
    if (_preparedPresentation == nil
            || _preparedNativeAd != ad
            || _preparedStyle != style
            || ![ROMediaSignature(ad)
                    isEqualToString:_preparedMediaSignature]) {
        [self ro_releasePreparedPresentation];
        return nil;
    }

    ROOverlayAdPresentation *result = _preparedPresentation;
    _preparedPresentation = nil;
    _preparedNativeAd = nil;
    _preparedStyle = nil;
    return result;
}

- (void)ro_releasePreparedPresentation {
    ROOverlayAdPresentation *prepared = _preparedPresentation;
    _preparedPresentation = nil;
    _preparedNativeAd = nil;
    _preparedStyle = nil;
    [prepared releasePresentation];
}

// Identity guard so completion and teardown run once per shown ad.
- (void)ro_completePresentationForAd:(GADNativeAd *)shownAd
                             message:(NSString *)errorMessage {
    if (_activeNativeAd != shownAd) return;

    RONativeAdShowCompletedCallback onCompleted = _activeShowCompleted;
    int32_t completedShowId = _activeShowId;
    _activeShowCompleted = NULL;
    _activeNativeAd = nil;
    _presentation = nil;

    [self ro_invokeCompleted:onCompleted
                      showId:completedShowId
                     message:errorMessage ?: @""
                  adConsumed:YES];
    // The wrapper assumes a consumed show leaves nothing cached, which is
    // no longer true once a replacement has been fetched during the show:
    // the truth follows the completion so readiness is right.
    [self ro_notifyCurrentState];
}

- (BOOL)ro_isShowing {
    ROOverlayAdPresentation *currentPresentation = _presentation;
    return currentPresentation != nil && currentPresentation.isShowing;
}

- (void)ro_notifyCurrentState {
    BOOL isReady = _configured
            && !self.released
            && _activeNativeAd == nil
            && _cachedAds.count > 0;
    BOOL isLoading = _configured && !self.released && _isAdLoading;
    [self notifyStateChangedWithReady:isReady loading:isLoading];
}

- (void)ro_invokeCompleted:(RONativeAdShowCompletedCallback)onCompleted
                    showId:(int32_t)showId
                   message:(NSString *)errorMessage
                adConsumed:(BOOL)adConsumed {
    if (onCompleted == NULL) return;
    onCompleted(
            self.instanceId
          , showId
          , (errorMessage ?: @"").UTF8String
          , adConsumed);
}

@end
