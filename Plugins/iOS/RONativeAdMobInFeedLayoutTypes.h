// Shared vocabulary of the in-feed layout system: templates, tiers, and the
// LayoutPlan record the engine scores and the factory builds from. Values
// mirror NativeAdMobInFeedLayoutEngine's nested types exactly.

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, ROInFeedTemplate) {
    ROInFeedTemplateCompactRow = 0
  , ROInFeedTemplateCompactColumn = 1
  , ROInFeedTemplateMediaLeft = 2
  , ROInFeedTemplateMediaTop = 3
  , ROInFeedTemplateMediaBackground = 4
  , ROInFeedTemplateMediaRight = 5
};

static inline BOOL ROInFeedTemplateIsMediaSide(
        ROInFeedTemplate layoutTemplate) {
    return layoutTemplate == ROInFeedTemplateMediaLeft
            || layoutTemplate == ROInFeedTemplateMediaRight;
}

typedef NS_ENUM(NSInteger, ROInFeedTier) {
    ROInFeedTierCompact = 0
  , ROInFeedTierRegular = 1
  , ROInFeedTierRoomy = 2
};

@interface ROInFeedLayoutPlan : NSObject

@property (nonatomic) ROInFeedTemplate layoutTemplate;
@property (nonatomic) ROInFeedTier tier;
@property (nonatomic) BOOL showMedia;
@property (nonatomic) BOOL renderVideo;
@property (nonatomic) BOOL showBody;
@property (nonatomic) BOOL showAdvertiser;
@property (nonatomic) BOOL showRating;
@property (nonatomic) BOOL showIcon;
@property (nonatomic) CGFloat width;
@property (nonatomic) CGFloat height;
@property (nonatomic) CGFloat mediaWidth;
@property (nonatomic) CGFloat mediaHeight;
@property (nonatomic) CGFloat measuredContentHeight;
@property (nonatomic) CGFloat textScale;
// Marquee is decided per text: the headline only ever scrolls after
// scrolling just the secondary lines was not enough.
@property (nonatomic) BOOL marqueeHeadline;
@property (nonatomic) BOOL marqueeSecondary;
@property (nonatomic) int64_t score;

@end

NSString *ROInFeedTemplateName(ROInFeedTemplate layoutTemplate);
NSString *ROInFeedTierName(ROInFeedTier tier);
NSString *ROInFeedMediaName(ROInFeedLayoutPlan *plan);
NSString *ROInFeedDescribePlan(ROInFeedLayoutPlan *_Nullable plan);

NS_ASSUME_NONNULL_END
