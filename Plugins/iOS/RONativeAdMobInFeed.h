// Port of com.riseon.nativeadmob.InFeed: the slot lifecycle. A buffer of
// warm ads, one active slot and one materializing, rotation on appearances
// with a dwell interval for slots that never go away, watchdogs so nothing
// parks forever, and retry backoff split between no-fill and layout
// failures - all transcribed with the same constants.

#import <Foundation/Foundation.h>

#import "RONativeAdMob.h"

NS_ASSUME_NONNULL_BEGIN

@interface RONativeAdMobInFeed : RONativeAdMob

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                      instanceId:(int32_t)instanceId;

// Points, converted from Unity's pixels at the bridge boundary.
- (void)configureWithX:(CGFloat)xPt
                     y:(CGFloat)yPt
                 width:(CGFloat)widthPt
                height:(CGFloat)heightPt
       backgroundAlpha:(float)backgroundAlpha;
- (void)show;
- (void)hide;
- (void)setPositionX:(CGFloat)xPt y:(CGFloat)yPt;
- (void)releaseAd;

@end

NS_ASSUME_NONNULL_END
