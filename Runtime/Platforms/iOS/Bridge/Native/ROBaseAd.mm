#import "ROBaseAd.h"

#include <stdatomic.h>

// Provided by Unity's iOS trampoline at link time.
extern "C" UIViewController* UnityGetGLViewController(void);

const int32_t RONativeAdInternalLoadError = -1;
const int32_t RONativeAdInternalPresentationError = -2;

@implementation ROBaseAd {
    atomic_bool _released;
    // Callbacks are read on SDK threads and written from Unity's thread; the
    // struct swap is guarded so a reader never sees half of an update.
    RONativeAdListenerCallbacks _callbacks;
    NSObject *_callbacksLock;
}

- (instancetype)initWithInstanceId:(int32_t)instanceId {
    self = [super init];
    if (self == nil) return nil;

    _instanceId = instanceId;
    _callbacksLock = [NSObject new];
    atomic_init(&_released, false);
    return self;
}

- (BOOL)released {
    return atomic_load(&_released);
}

- (void)markReleased {
    atomic_store(&_released, true);
}

- (void)setListenerCallbacks:(RONativeAdListenerCallbacks)callbacks {
    if (self.released) return;
    @synchronized (_callbacksLock) {
        _callbacks = callbacks;
    }
}

- (void)clearListenerCallbacks {
    RONativeAdListenerCallbacks empty = {0};
    @synchronized (_callbacksLock) {
        _callbacks = empty;
    }
}

- (RONativeAdListenerCallbacks)currentCallbacks {
    @synchronized (_callbacksLock) {
        return _callbacks;
    }
}

+ (void)runOnMainThread:(dispatch_block_t)action {
    if (action == nil) return;

    if (NSThread.isMainThread) {
        action();
    } else {
        dispatch_async(dispatch_get_main_queue(), action);
    }
}

+ (NSArray<GADAdLoaderOptions *> *)adLoaderOptionsWithStartMuted:(BOOL)startMuted {
    GADVideoOptions *videoOptions = [[GADVideoOptions alloc] init];
    videoOptions.startMuted = startMuted;

    GADNativeAdMediaAdLoaderOptions *mediaOptions =
            [[GADNativeAdMediaAdLoaderOptions alloc] init];
    mediaOptions.mediaAspectRatio = GADMediaAspectRatioAny;

    GADNativeAdViewAdOptions *viewOptions =
            [[GADNativeAdViewAdOptions alloc] init];
    viewOptions.preferredAdChoicesPosition =
            GADAdChoicesPositionTopRightCorner;

    return @[ videoOptions, mediaOptions, viewOptions ];
}

- (void)bindPaidEventForAd:(GADNativeAd *)ad
              paidAdUnitId:(NSString *)paidAdUnitId
               isCurrentAd:(BOOL (^)(void))isCurrentAd {
    __weak ROBaseAd *weakSelf = self;
    __weak GADNativeAd *weakAd = ad;
    ad.paidEventHandler = ^(GADAdValue *_Nonnull adValue) {
        ROBaseAd *strongSelf = weakSelf;
        if (strongSelf == nil
                || strongSelf.released
                || isCurrentAd == nil
                || !isCurrentAd()) {
            return;
        }

        NSString *source = @"";
        GADNativeAd *strongAd = weakAd;
        GADAdNetworkResponseInfo *loadedResponse =
                strongAd.responseInfo.loadedAdNetworkResponseInfo;
        if (loadedResponse.adNetworkClassName != nil) {
            source = loadedResponse.adNetworkClassName;
        }
        // GADAdValue is already denominated in currency units, unlike the
        // Android micros - no division here or the value shrinks 10^6-fold.
        [strongSelf notifyAdPaidWithSource:source
                                  adUnitId:paidAdUnitId
                                     value:adValue.value.doubleValue
                              currencyCode:adValue.currencyCode
                                 precision:(int32_t)adValue.precision];
    };
}

- (void)notifyLoadingStarted {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.loadingStarted == NULL) return;
    callbacks.loadingStarted(self.instanceId);
}

- (void)notifyLoadingCompletedWithCode:(int32_t)errorCode
                               message:(NSString *)errorMessage
                           cachedCount:(int32_t)cachedCount
                             cacheSize:(int32_t)cacheSize {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.loadingCompleted == NULL) return;
    callbacks.loadingCompleted(
            self.instanceId
          , errorCode
          , (errorMessage ?: @"").UTF8String
          , cachedCount
          , cacheSize);
}

- (void)notifyAdPaidWithSource:(NSString *)source
                      adUnitId:(NSString *)adUnitId
                         value:(double)value
                  currencyCode:(NSString *)currencyCode
                     precision:(int32_t)precision {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.adPaid == NULL) return;
    callbacks.adPaid(
            self.instanceId
          , (source ?: @"").UTF8String
          , (adUnitId ?: @"").UTF8String
          , value
          , (currencyCode ?: @"").UTF8String
          , precision);
}

- (void)notifyStateChangedWithReady:(BOOL)isReady loading:(BOOL)isLoading {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.stateChanged == NULL) return;
    callbacks.stateChanged(self.instanceId, isReady, isLoading);
}

- (void)notifyShowNotReady {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.showNotReady == NULL) return;
    callbacks.showNotReady(self.instanceId);
}

- (void)notifyDisplayed {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.displayed == NULL) return;
    callbacks.displayed(self.instanceId);
}

- (void)notifyPresentationFailedWithCode:(int32_t)errorCode
                                 message:(NSString *)errorMessage {
    RONativeAdListenerCallbacks callbacks = [self currentCallbacks];
    if (callbacks.presentationFailed == NULL) return;
    callbacks.presentationFailed(
            self.instanceId
          , errorCode
          , (errorMessage ?: @"").UTF8String);
}

+ (UIViewController *)unityViewController {
    return UnityGetGLViewController();
}

+ (BOOL)isViewControllerUsable:(UIViewController *)viewController {
    return viewController != nil && viewController.view.window != nil;
}

@end
