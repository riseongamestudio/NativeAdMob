// Port of com.riseon.nativeadmob.Overlay: the cached-ad state machine.
// Cache size ads are kept warm; Show consumes the head, completion hands
// the result back through the bridge callback, and the head's collapsible
// presentation is prepared ahead of the show exactly as the Java side
// warms its dialog.

#import <Foundation/Foundation.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "ROBaseAd.h"

NS_ASSUME_NONNULL_BEGIN

@interface ROOverlayAd : ROBaseAd

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                      instanceId:(int32_t)instanceId;

- (void)configureWithFullscreen:(BOOL)fullscreen
                    heightRatio:(float)heightRatio
                backgroundColor:(int32_t)backgroundColor
                      cacheSize:(int32_t)cacheSize
                       cooldown:(int32_t)cooldown
                      closeSide:(int32_t)closeSide
                      timerSide:(int32_t)timerSide
                redirectOnClose:(BOOL)redirectOnClose;
- (void)setCloseWithCooldown:(int32_t)cooldown
                   closeSide:(int32_t)closeSide
                   timerSide:(int32_t)timerSide
             redirectOnClose:(BOOL)redirectOnClose;
- (void)load;
- (void)showWithShowId:(int32_t)showId
             onCompleted:(RONativeAdShowCompletedCallback _Nullable)onCompleted;
- (void)hide;
- (void)releaseAd;

@end

NS_ASSUME_NONNULL_END
