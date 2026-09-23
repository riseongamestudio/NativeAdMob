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

// The in-feed view standing nearest the top of the host's subviews, or nil
// when no in-feed is up. Every in-feed goes in at index 0, so the OLDEST one
// still alive is the highest of them - and the half-screen ad inserts just
// above it to land in its own layer without walking the array.
+ (UIView *)ro_frontmostInFeedView;

- (instancetype)initWithHostViewController:(UIViewController *)hostViewController
                                  nativeAd:(GADNativeAd *)nativeAd
                                         x:(CGFloat)xPt
                                         y:(CGFloat)yPt
                                     width:(CGFloat)widthPt
                                    height:(CGFloat)heightPt
                           backgroundColor:(int32_t)backgroundColor
                               roundCorner:(CGFloat)roundCornerPt
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
