// Port of com.riseon.nativeadmob.NativeAdMobInFeedViewFactory: builds probe trees for
// the engine's candidate search and the final GADNativeAdView for the plan
// that wins. All sizes are points (1pt here stands where 1dp stood); the
// Java Dp() conversions collapse to identity and only the log lines keep
// reporting pixels through the screen scale.

#import <UIKit/UIKit.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

#import "ROAdTextLabel.h"
#import "ROMeasureLayout.h"
#import "RONativeAdMobInFeedLayoutTypes.h"

NS_ASSUME_NONNULL_BEGIN

@interface ROInFeedAssetViews : NSObject

@property (nonatomic, strong, nullable) ROAdTextLabel *headline;
@property (nonatomic, strong, nullable) ROAdTextLabel *body;
@property (nonatomic, strong, nullable) ROAdTextLabel *advertiser;
@property (nonatomic, strong, nullable) ROAdTextLabel *rating;
@property (nonatomic, strong, nullable) UIImageView *icon;
@property (nonatomic, strong, nullable) UIButton *callToAction;
@property (nonatomic, strong, nullable) UIView *media;
@property (nonatomic, strong, nullable) UILabel *attribution;
@property (nonatomic, strong, nullable) UIView *adChoicesReserve;
@property (nonatomic, strong, nullable) HBLinearLayoutView *outer;
@property (nonatomic, strong, nullable) UIView *mediaSlot;
@property (nonatomic, strong, nullable) UIView *videoRail;
@property (nonatomic, strong, nullable) UIView *scrim;
@property (nonatomic, strong, nullable) UIView *insetContent;
@property (nonatomic) BOOL insetContentAvoidsBadges;

@end

@interface ROInFeedProbeLayout : NSObject

@property (nonatomic) ROInFeedTemplate layoutTemplate;
@property (nonatomic) ROInFeedTier tier;
@property (nonatomic) BOOL showMedia;
@property (nonatomic) BOOL renderVideo;
@property (nonatomic, strong) UIView *root;
@property (nonatomic, strong) ROInFeedAssetViews *views;

@end

@interface ROInFeedNativeAdViewResult : NSObject

@property (nonatomic, strong) GADNativeAdView *nativeAdView;
@property (nonatomic, strong) ROInFeedAssetViews *assetViews;

@end

@interface RONativeAdMobInFeedViewFactory : NSObject

- (instancetype)initWithNativeAd:(GADNativeAd *)nativeAd
                 slotShortSidePt:(CGFloat)slotShortSidePt;

- (ROInFeedProbeLayout *)createProbeForPlan:(ROInFeedLayoutPlan *)plan;
- (void)configureProbeLayout:(ROInFeedProbeLayout *)probe
                     forPlan:(ROInFeedLayoutPlan *)plan;
// Throws (NSException) on bind problems the Java side throws for; the
// presentation catches and walks to the next plan.
- (ROInFeedNativeAdViewResult *)buildNativeAdViewForPlan:(ROInFeedLayoutPlan *)plan
                                               mainImage:(UIImage *_Nullable)mainImage;

- (BOOL)hasVideoContent;
- (BOOL)hasValidStarRating;
- (double)resolveStarRating;
- (BOOL)canRenderMainImage:(UIImage *_Nullable)mainImage;
- (BOOL)hasUnrenderableIcon;
- (BOOL)hasRenderableIcon;
- (UIImage *_Nullable)findMainImage;

- (CGFloat)minimumWidthForTier:(ROInFeedTier)tier;
// The smallest a registered MediaView may end up on either side.
- (CGFloat)mediaPolicyFloor;
- (CGFloat)mediaSizeForTier:(ROInFeedTier)tier;
- (CGFloat)paddingForTier:(ROInFeedTier)tier;
- (CGFloat)gapForTier:(ROInFeedTier)tier;
- (ROInFeedTier)preferredTierForWidth:(CGFloat)width height:(CGFloat)height;
- (CGFloat)minimumMediaLeftTextSlotWidthForPlan:(ROInFeedLayoutPlan *)plan;
- (CGFloat)callToActionHeightForPlan:(ROInFeedLayoutPlan *)plan;

@end

NS_ASSUME_NONNULL_END
