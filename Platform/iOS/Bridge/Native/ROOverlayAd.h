// Port of com.riseon.nativeadmob.Overlay: the cached-ad state machine.
// One loaded ad at a time; ShowAd consumes it, completion hands the result
// back through the bridge callback, and a collapsible presentation is
// prepared ahead of the show exactly as the Java side warms its dialog.

#import <Foundation/Foundation.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "RONativeAd.h"

NS_ASSUME_NONNULL_BEGIN

@interface ROOverlayAd : RONativeAd

- (instancetype)initWithAdUnitId:(NSString *)adUnitId
                      instanceId:(int32_t)instanceId;

- (void)configureWithFullscreen:(BOOL)fullscreen
                   countdownSec:(int32_t)countdownSec
                    xRandomSide:(BOOL)xRandomSide
             numberOppositeSide:(BOOL)numberOppositeSide
                    heightRatio:(float)heightRatio
                backgroundAlpha:(float)backgroundAlpha;
- (void)setCountdownSec:(int32_t)countdownSec;
- (void)loadAd;
- (void)showAdWithShowId:(int32_t)showId
             onCompleted:(RONativeAdShowCompletedCallback _Nullable)onCompleted;
- (void)hideAd;
- (void)releaseAd;

@end

NS_ASSUME_NONNULL_END
