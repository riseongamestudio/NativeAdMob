// Port of com.riseon.nativeadmob.NativeAdMobOverlayContentView: the full-screen and
// collapsible ad face - media, identity row, body, call to action, the Ad
// badge with an AdChoices reserve, countdown and close controls, the
// responsive collapsible ladder, and the whole-or-scrolling text rule.
// Sizes are points; the requested panel height arrives already converted.

#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "ROMeasureLayout.h"

NS_ASSUME_NONNULL_BEGIN

@interface ROOverlayAdContentView : UIView

- (instancetype)initWithNativeAd:(GADNativeAd *)nativeAd
             countDownRemainingMs:(int64_t)countDownRemainingMs
                      closeOnLeft:(BOOL)closeOnLeft
                   numberOpposite:(BOOL)numberOpposite
                       fullscreen:(BOOL)fullscreen
                  backgroundAlpha:(float)backgroundAlpha
             fakeCloseAutoDismiss:(BOOL)fakeCloseAutoDismiss
             requestedPanelHeight:(CGFloat)requestedPanelHeight
                          onClose:(dispatch_block_t)onClose;

// The counterpart of ResolveInitialPanelHeight: fullscreen takes the screen,
// otherwise the height ratio bounded below by the control strip plus the
// media minimum.
+ (CGFloat)resolveInitialPanelHeightForFullscreen:(BOOL)fullscreen
                                      heightRatio:(float)heightRatio
                                  hasVideoContent:(BOOL)hasVideoContent;

@property (nonatomic, readonly) CGFloat resolvedPanelHeight;

// GADNativeAdView needs to outlive nothing: releasing detaches and drops it.
- (void)releaseContent;

- (void)onPresented;
- (void)onPresentedWithRemainingMs:(int64_t)remainingMs;
- (void)onPaused;
- (int64_t)countDownRemainingMs;

// The click latch: every further touch is swallowed until the ad's overlay
// goes away or the app comes back to the foreground.
- (void)commitAdClick;

@end

NS_ASSUME_NONNULL_END
