// Port of com.riseon.nativeadmob.InFeed: one instance is one ad unit id for
// the life of the app. It owns the shared supply - the cache of raw loaded
// ads, the load requests, the no-fill backoff - and a fixed array of display
// slots that consume from it. Everything rect-shaped (layout, dwell,
// rotation, watchdogs) lives in ROInFeedAdSlot, same as InFeedSlot on Android.

#import <Foundation/Foundation.h>

#import "ROBaseAd.h"

NS_ASSUME_NONNULL_BEGIN

// The in-feed listener surface is slot-indexed where the full-screen one is
// flat; the loading and paid callbacks describe the shared supply.
typedef struct {
    RONativeAdLoadingStartedCallback _Nullable loadingStarted;
    RONativeAdLoadingCompletedCallback _Nullable loadingCompleted;
    RONativeAdPaidCallback _Nullable adPaid;
    ROInFeedAdSlotDisplayedCallback _Nullable slotDisplayed;
    ROInFeedAdSlotShowNotReadyCallback _Nullable slotShowNotReady;
    ROInFeedAdSlotPresentationFailedCallback
            _Nullable slotPresentationFailed;
} ROInFeedAdListenerCallbacks;

@interface ROInFeedAd : ROBaseAd

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                       slotCount:(NSInteger)slotCount
                       cacheSize:(NSInteger)cacheSize
                 backgroundAlpha:(float)backgroundAlpha
                      instanceId:(int32_t)instanceId;

- (void)setInFeedListenerCallbacks:
        (ROInFeedAdListenerCallbacks)callbacks;

// Points, converted from Unity's pixels at the bridge boundary.
- (void)configureSlot:(NSInteger)slotIndex
                    x:(CGFloat)xPt
                    y:(CGFloat)yPt
                width:(CGFloat)widthPt
               height:(CGFloat)heightPt;
- (void)showSlot:(NSInteger)slotIndex;
- (void)hideSlot:(NSInteger)slotIndex;
- (void)setSlot:(NSInteger)slotIndex positionX:(CGFloat)xPt y:(CGFloat)yPt;
- (void)releaseAd;

@end

NS_ASSUME_NONNULL_END
