// Port of com.riseon.nativeadmob.NativeAdMobStarRatingView.

#import <UIKit/UIKit.h>

#import "ROMeasureLayout.h"

NS_ASSUME_NONNULL_BEGIN

@interface RONativeAdMobStarRatingView : UIView

// Clamped to [0, 5], fractional fills allowed.
@property (nonatomic) CGFloat rating;

@end

NS_ASSUME_NONNULL_END
