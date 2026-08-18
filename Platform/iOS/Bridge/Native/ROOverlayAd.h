// Port of com.riseon.nativeadmob.Overlay: the cached-ad state machine.
// One loaded ad at a time; Show consumes it, completion hands the result
// back through the bridge callback, and a collapsible presentation is
// prepared ahead of the show exactly as the Java side warms its dialog.

#import <Foundation/Foundation.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "ROBaseAd.h"

NS_ASSUME_NONNULL_BEGIN

@interface ROOverlayAd : ROBaseAd

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                      instanceId:(int32_t)instanceId;

- (void)configureWithFullscreen:(BOOL)fullscreen
                   countdownSec:(int32_t)countdownSec
                    xRandomSide:(BOOL)xRandomSide
             numberOppositeSide:(BOOL)numberOppositeSide
                    heightRatio:(float)heightRatio
                backgroundAlpha:(float)backgroundAlpha;
- (void)setCountdownSec:(int32_t)countdownSec;
- (void)load;
- (void)showWithShowId:(int32_t)showId
             onCompleted:(RONativeAdShowCompletedCallback _Nullable)onCompleted;
- (void)hide;
- (void)releaseAd;

@end

NS_ASSUME_NONNULL_END
