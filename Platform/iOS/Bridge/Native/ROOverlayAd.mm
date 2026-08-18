#import "ROOverlayAd.h"

#import "ROOverlayAdPresentation.h"

static NSString *const kROTag = @"Overlay";
static const int32_t kROLoadSuccessCode = 0;

// The style snapshot Configure publishes and every show reads - the same
// immutable OverlayStyle the Java side passes around.
@interface HBOverlayStyle : NSObject
@property (nonatomic, readonly) BOOL fullscreen;
@property (nonatomic, readonly) int32_t countdownSec;
@property (nonatomic, readonly) BOOL xRandomSide;
@property (nonatomic, readonly) BOOL numberOppositeSide;
@property (nonatomic, readonly) float heightRatio;
@property (nonatomic, readonly) float backgroundAlpha;
@end

@implementation HBOverlayStyle

- (instancetype)initWithFullscreen:(BOOL)fullscreen
                      countdownSec:(int32_t)countdownSec
                       xRandomSide:(BOOL)xRandomSide
                numberOppositeSide:(BOOL)numberOppositeSide
                       heightRatio:(float)heightRatio
                   backgroundAlpha:(float)backgroundAlpha {
    self = [super init];
    if (self == nil) return nil;
    _fullscreen = fullscreen;
    _countdownSec = MAX(0, countdownSec);
    _xRandomSide = xRandomSide;
    _numberOppositeSide = numberOppositeSide;
    _heightRatio = heightRatio;
    _backgroundAlpha = backgroundAlpha;
    return self;
}

- (HBOverlayStyle *)styleWithCountdownSec:(int32_t)countdownSec {
    return [[HBOverlayStyle alloc]
            initWithFullscreen:self.fullscreen
                  countdownSec:countdownSec
                   xRandomSide:self.xRandomSide
            numberOppositeSide:self.numberOppositeSide
                   heightRatio:self.heightRatio
               backgroundAlpha:self.backgroundAlpha];
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
    GADNativeAd *_nativeAd;
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
    return self;
}

- (void)configureWithFullscreen:(BOOL)fullscreen
                   countdownSec:(int32_t)countdownSec
                    xRandomSide:(BOOL)xRandomSide
             numberOppositeSide:(BOOL)numberOppositeSide
                    heightRatio:(float)heightRatio
                backgroundAlpha:(float)backgroundAlpha {
    [ROBaseAd runOnMainThread:^{
        if (self.released) {
            NSLog(@"%@: Configure ignored after Release", kROTag);
            return;
        }
        if (self->_configured) {
            NSLog(@"%@: Configure may only be called once per instance", kROTag);
            return;
        }

        self->_configuredStyle = [[HBOverlayStyle alloc]
                initWithFullscreen:fullscreen
                      countdownSec:countdownSec
                       xRandomSide:xRandomSide
                numberOppositeSide:numberOppositeSide
                       heightRatio:heightRatio
                   backgroundAlpha:backgroundAlpha];
        self->_configured = YES;
    }];
}

- (void)setCountdownSec:(int32_t)countdownSec {
    [ROBaseAd runOnMainThread:^{
        HBOverlayStyle *currentStyle = self->_configuredStyle;
        if (!self->_configured || self.released || currentStyle == nil) {
            NSLog(@"%@: SetCountdownSec ignored before Configure or after "
                    "Release", kROTag);
            return;
        }
        HBOverlayStyle *updatedStyle =
                [currentStyle styleWithCountdownSec:countdownSec];
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
        if (self->_isAdLoading) return;

        // A cached ad is the whole point of loading, so one is never thrown
        // away to fetch another - that is what made the replacement fetched
        // during the show die at dismissal and the player wait for a fresh
        // load anyway. An empty cache is the only reason to load, whoever
        // is on screen.
        if (self->_nativeAd != nil) return;
        UIViewController *host = [ROBaseAd unityViewController];
        if (![ROBaseAd isViewControllerUsable:host]) {
            NSLog(@"%@: Load ignored because the host controller is not "
                    "usable", kROTag);
            return;
        }
        [self ro_doLoadAdWithHost:host style:self->_configuredStyle];
    }];
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

        self->_nativeAd = nativeAd;
        nativeAd.delegate = self;
        __weak ROOverlayAd *weakSelf = self;
        __weak GADNativeAd *weakAd = nativeAd;
        [self bindPaidEventForAd:nativeAd
                    paidAdUnitId:self->_adUnitId
                     isCurrentAd:^BOOL{
            ROOverlayAd *strongSelf = weakSelf;
            GADNativeAd *strongAd = weakAd;
            return strongSelf != nil
                    && strongAd != nil
                    && (strongSelf->_nativeAd == strongAd
                            || strongSelf->_activeNativeAd == strongAd);
        }];

        // Both formats prepay their face at load time - the Android side
        // prebuilds the Activity's content view, this side the whole
        // presentation - so the show itself has nothing slow left to do.
        HBOverlayStyle *presentationStyle = self->_configuredStyle;
        if (presentationStyle != nil) {
            [self ro_preparePresentationForAd:nativeAd
                                        style:presentationStyle];
        } else {
            [self ro_releasePreparedPresentation];
        }

        self->_isAdLoading = NO;
        [self ro_notifyCurrentState];
        [self notifyLoadingCompletedWithCode:kROLoadSuccessCode message:@""];
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
        if (self->_nativeAd == nil
                || requestedStyle == nil
                || ![ROBaseAd isViewControllerUsable:host]) {
            [self ro_invokeCompleted:onCompleted
                              showId:showId
                             message:@"Ad not ready"
                          adConsumed:NO];
            return;
        }

        GADNativeAd *shownAd = self->_nativeAd;
        self->_nativeAd = nil;
        self->_activeNativeAd = shownAd;
        self->_activeShowCompleted = onCompleted;
        self->_activeShowId = showId;
        [self ro_notifyCurrentState];

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

        self->_nativeAd = nil;
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
    ROOverlayAdPresentation *createdPresentation =
            [[ROOverlayAdPresentation alloc]
                    initWithViewController:host
                                  nativeAd:ad
                              countdownSec:style.countdownSec
                                   xRandom:style.xRandomSide
                            numberOpposite:style.numberOppositeSide
                                fullscreen:style.fullscreen
                               heightRatio:style.heightRatio
                           backgroundAlpha:style.backgroundAlpha];
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

- (void)ro_preparePresentationForAd:(GADNativeAd *)ad
                              style:(HBOverlayStyle *)style {
    [self ro_releasePreparedPresentation];
    UIViewController *host = [ROBaseAd unityViewController];
    if (self.released
            || ad == nil
            || style == nil
            || _nativeAd != ad
            || ![ROBaseAd isViewControllerUsable:host]) {
        return;
    }

    ROOverlayAdPresentation *createdPresentation =
            [self ro_createPresentationWithHost:host ad:ad style:style];
    if (![createdPresentation prepare]
            || self.released
            || _nativeAd != ad
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
    // touches the prepared face of the CACHED ad, never the one on screen.
    if (self.released
            || requestedStyle == nil
            || _configuredStyle != requestedStyle
            || _nativeAd == nil) {
        return;
    }

    GADNativeAd *ad = _nativeAd;
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
            && _nativeAd != nil;
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
