// Port of com.riseon.nativeadmob.NativeAdMobInFeedLayoutValidator: every accepted
// layout shows its registered assets unclipped, non-overlapping and at
// policy sizes, and every text is on screen whole or on a line that
// scrolls - never partially.

#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "RONativeInFeedAdMobViewFactory.h"

NS_ASSUME_NONNULL_BEGIN

@interface RONativeInFeedAdMobLayoutValidator : NSObject

- (instancetype)initWithNativeAd:(GADNativeAd *)nativeAd
                     viewFactory:(RONativeInFeedAdMobViewFactory *)viewFactory;

- (BOOL)validateAssetGeometryForRoot:(UIView *)rootView
                               views:(ROInFeedAssetViews *_Nullable)views
                                plan:(ROInFeedLayoutPlan *_Nullable)plan
                          logFailure:(BOOL)logFailure;
@property (nonatomic, readonly, nullable) NSString *lastFailureReason;

- (int64_t)geometrySignatureForHost:(UIView *)host
                               root:(UIView *)root
                              views:(ROInFeedAssetViews *)views;

- (CGFloat)requiredVideoWidthForAspect:(CGFloat)creativeAspect;
- (CGFloat)requiredVideoHeightForAspect:(CGFloat)creativeAspect;
- (BOOL)isPolicySafeVideoSizeWithWidth:(CGFloat)width height:(CGFloat)height;

@end

NS_ASSUME_NONNULL_END
