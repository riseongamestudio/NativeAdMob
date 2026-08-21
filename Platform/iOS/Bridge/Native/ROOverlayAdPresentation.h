// Port of OverlayAdPresentation and the presenting half of
// OverlayAdActivity in one object: fullscreen rides a translucent
// view controller presented over Unity (the Activity's role), collapsible
// pins the content view to the bottom of Unity's view (the panel dialog's
// role). iOS never tears a presented controller down behind the app's back,
// so the Android session-restore machinery has no counterpart here; the
// countdown still pauses across backgrounding the way it paused across
// onPause.

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

NS_ASSUME_NONNULL_BEGIN

@interface ROOverlayAdPresentation : NSObject

- (instancetype)initWithViewController:(UIViewController *)viewController
                              nativeAd:(GADNativeAd *)nativeAd
                          countdownSec:(int32_t)countdownSec
                           closeOnLeft:(BOOL)closeOnLeft
                           timerOnLeft:(BOOL)timerOnLeft
                            fullscreen:(BOOL)fullscreen
                           heightRatio:(float)heightRatio
                       backgroundColor:(int32_t)backgroundColor
                  fakeCloseAutoDismiss:(BOOL)fakeCloseAutoDismiss;

@property (nonatomic, copy, nullable) dispatch_block_t onShow;
@property (nonatomic, copy, nullable) dispatch_block_t onDismiss;

// Builds the content without showing it - Dialog.create() on Android, and
// what PreparePresentation warms up while the ad waits.
- (BOOL)prepare;
// Prepare builds the whole content view, so the verdict is already in by
// the time it returns - the mirror of Android's IsLayoutUnrenderable.
- (BOOL)isLayoutUnrenderable;
- (BOOL)show;
- (BOOL)isShowing;
- (void)dismiss;
// Dismiss without the onDismiss notification - Release() semantics.
- (void)releasePresentation;
- (void)onAdClicked;

@end

NS_ASSUME_NONNULL_END
