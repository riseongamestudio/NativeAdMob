// Internal to the pack: one display slot of an in-feed unit - a rect on
// screen, the presentation pair being shown and warmed for it, and every
// trigger that decides when the rect earns a fresh ad. It consumes raw ads
// from its owner's shared cache and never talks to the network itself.

#import <Foundation/Foundation.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "ROInFeedAd.h"

NS_ASSUME_NONNULL_BEGIN

static const NSTimeInterval kROInFeedNoRetryScheduled = -1;
static const NSTimeInterval kROInFeedMaxCachedAdAge = 3600;

// Shared backoff curve; defined beside the no-fill retry in the unit.
NSTimeInterval ROInFeedBackoffDelay(
        NSInteger streak
      , NSInteger immediateAttempts);

@interface ROInFeedCachedAd : NSObject
@property (nonatomic, strong) GADNativeAd *ad;
@property (nonatomic) NSTimeInterval loadedAt;
@end

@interface ROInFeedAdSlot : NSObject

- (instancetype)initWithOwner:(ROInFeedAd *)owner
                        index:(NSInteger)index;

- (void)configureWithX:(CGFloat)xPt
                     y:(CGFloat)yPt
                 width:(CGFloat)widthPt
                height:(CGFloat)heightPt;
- (void)show;
- (void)hide;
- (void)setPositionX:(CGFloat)xPt y:(CGFloat)yPt;
- (void)releaseOnMain;
- (void)commitAdClick;

// A freshly loaded ad goes to the first configured slot with nothing on
// screen and nothing being built; rotation of a filled slot pulls from the
// cache on its own schedule instead.
- (BOOL)wantsCachedAd;
- (void)presentCachedAd;

@end

// The owner surface the slots consume.
@interface ROInFeedAd (SlotSupport)
- (BOOL)hasCachedAd;
- (nullable ROInFeedCachedAd *)takeCachedAd;
- (NSInteger)cachedCount;
- (void)requestLoad;
- (void)destroyAd:(nullable GADNativeAd *)ad;
- (float)slotBackgroundColor;
- (NSString *)unitAdUnitId;
- (NSInteger)noFillStreak;
- (void)notifySlotDisplayed:(NSInteger)slotIndex;
- (void)notifySlotShowNotReady:(NSInteger)slotIndex;
- (void)notifySlotPresentationFailed:(NSInteger)slotIndex
                                code:(int32_t)errorCode
                             message:(NSString *_Nullable)errorMessage;
@end

NS_ASSUME_NONNULL_END
