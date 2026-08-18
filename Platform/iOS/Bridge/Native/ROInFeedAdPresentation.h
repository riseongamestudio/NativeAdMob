// Port of com.riseon.nativeadmob.NativeAdMobInFeedPresentation. The Android panel
// dialog collapses to this view itself, added to Unity's root view at the
// slot rect; a CADisplayLink stands where the pre-draw listener stood,
// driving the same stability observation over the same deadlines. Sizes
// and positions are points.

#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

NS_ASSUME_NONNULL_BEGIN

@protocol ROInFeedPresentationListener <NSObject>
- (void)inFeedPresentationReady;
- (void)inFeedPresentationDisplayed;
- (void)inFeedPresentationDismissed;
- (void)inFeedPresentationActualVisibilityChanged:(BOOL)isActuallyVisible;
@end

@interface ROInFeedAdPresentation : UIView

- (instancetype)initWithHostViewController:(UIViewController *)hostViewController
                                  nativeAd:(GADNativeAd *)nativeAd
                                         x:(CGFloat)xPt
                                         y:(CGFloat)yPt
                                     width:(CGFloat)widthPt
                                    height:(CGFloat)heightPt
                           backgroundAlpha:(float)backgroundAlpha
                                  listener:(id<ROInFeedPresentationListener>)listener;

- (BOOL)show;
- (BOOL)isShowingPresentation;
- (void)dismissPresentation;
- (void)releasePresentation;
- (BOOL)setVisible:(BOOL)visible;
- (void)requestDisplayNotification;
- (BOOL)setPositionX:(CGFloat)xPt y:(CGFloat)yPt;
- (void)commitAdClick;
@property (nonatomic, readonly, nullable) NSString *failureMessage;

@end

NS_ASSUME_NONNULL_END
