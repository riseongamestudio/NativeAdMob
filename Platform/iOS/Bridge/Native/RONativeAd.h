// Port of com.riseon.nativeadmob.NativeAdMob - the shared bridge core: main-thread
// dispatch, listener fan-out, load generation and request options. The
// show/hide lifecycle of each format lives in its subclass, same as Java.

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "RONativeAdBridge.h"

NS_ASSUME_NONNULL_BEGIN

// One struct instead of Java's listener object: the function pointers arrive
// together from C# and are swapped together, so a torn listener can never be
// observed.
typedef struct {
    RONativeAdLoadingStartedCallback _Nullable loadingStarted;
    RONativeAdLoadingCompletedCallback _Nullable loadingCompleted;
    RONativeAdPaidCallback _Nullable adPaid;
    RONativeAdDisplayedCallback _Nullable displayed;
    RONativeAdPresentationFailedCallback _Nullable presentationFailed;
    RONativeAdStateChangedCallback _Nullable stateChanged;
    RONativeAdShowNotReadyCallback _Nullable showNotReady;
} RONativeAdListenerCallbacks;

extern const int32_t RONativeAdInternalLoadError;         // -1, same as Java
extern const int32_t RONativeAdInternalPresentationError; // -2, same as Java

@interface RONativeAd : NSObject

@property (atomic, readonly) BOOL released;
@property (nonatomic, readonly) int32_t instanceId;

- (instancetype)initWithInstanceId:(int32_t)instanceId;

// Marks the instance released. Subclasses do their teardown on main after
// calling this, same shape as Java's Release() posting to the handler.
- (void)markReleased;

- (void)setListenerCallbacks:(RONativeAdListenerCallbacks)callbacks;
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
