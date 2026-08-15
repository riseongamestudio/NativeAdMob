// Port of com.riseon.nativeadmob.NativeAdMob - the shared bridge core: main-thread
// dispatch, listener fan-out, load generation and request options. The
// show/hide lifecycle of each format lives in its subclass, same as Java.

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "RONativeAdMobBridge.h"

NS_ASSUME_NONNULL_BEGIN

// One struct instead of Java's listener object: the function pointers arrive
// together from C# and are swapped together, so a torn listener can never be
// observed.
typedef struct {
    RONativeAdMobLoadingStartedCallback _Nullable loadingStarted;
    RONativeAdMobLoadingCompletedCallback _Nullable loadingCompleted;
    RONativeAdMobPaidCallback _Nullable adPaid;
    RONativeAdMobDisplayedCallback _Nullable displayed;
    RONativeAdMobPresentationFailedCallback _Nullable presentationFailed;
    RONativeAdMobStateChangedCallback _Nullable stateChanged;
    RONativeAdMobShowNotReadyCallback _Nullable showNotReady;
} RONativeAdMobListenerCallbacks;

extern const int32_t RONativeAdMobInternalLoadError;         // -1, same as Java
extern const int32_t RONativeAdMobInternalPresentationError; // -2, same as Java

@interface RONativeAdMob : NSObject

@property (atomic, readonly) BOOL released;
@property (nonatomic, readonly) int32_t instanceId;

- (instancetype)initWithInstanceId:(int32_t)instanceId;

// Marks the instance released. Subclasses do their teardown on main after
// calling this, same shape as Java's Release() posting to the handler.
- (void)markReleased;

- (void)setListenerCallbacks:(RONativeAdMobListenerCallbacks)callbacks;
- (void)clearListenerCallbacks;

// Load generation - AtomicInteger semantics from Java.
- (int32_t)nextLoadGeneration;
- (void)invalidateLoadGeneration;
- (BOOL)isCurrentLoadGeneration:(int32_t)generation;

// Runs on main immediately when already there, else dispatches - the same
// behaviour as Java's RunOnMainThread.
+ (void)runOnMainThread:(dispatch_block_t)action;

// The request options CreateNativeAdOptions builds on Android: any media
// aspect ratio, AdChoices in the top-right corner, and the given mute state.
+ (NSArray<GADAdLoaderOptions *> *)adLoaderOptionsWithStartMuted:(BOOL)startMuted;

// Attaches the paid-event handler, reporting through the listener exactly as
// BindPaidEvent does. The isCurrentAd block runs inside the SDK callback, off
// whatever thread the SDK chose - it must be safe there, same as Java.
- (void)bindPaidEventForAd:(GADNativeAd *)ad
              paidAdUnitId:(NSString *)paidAdUnitId
               isCurrentAd:(BOOL (^)(void))isCurrentAd;

// Listener notifications, each a no-op when the callback is absent.
- (void)notifyLoadingStarted;
- (void)notifyAdPaidWithSource:(NSString *)source
                      adUnitId:(NSString *)adUnitId
                         value:(double)value
                  currencyCode:(NSString *_Nullable)currencyCode
                     precision:(int32_t)precision;
- (void)notifyLoadingCompletedWithCode:(int32_t)errorCode
                               message:(NSString *_Nullable)errorMessage;
- (void)notifyStateChangedWithReady:(BOOL)isReady loading:(BOOL)isLoading;
- (void)notifyShowNotReady;
- (void)notifyDisplayed;
- (void)notifyPresentationFailedWithCode:(int32_t)errorCode
                                 message:(NSString *_Nullable)errorMessage;

// The presenter every window and view controller hangs off - Unity's root
// view controller, the counterpart of the Android Activity parameter.
+ (UIViewController *_Nullable)unityViewController;
+ (BOOL)isViewControllerUsable:(UIViewController *_Nullable)viewController;

@end

NS_ASSUME_NONNULL_END
