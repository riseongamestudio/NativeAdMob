// A deliberately small re-creation of Android's measure and layout model -
// MeasureSpec, LinearLayout, FrameLayout, gravity, margins, GONE - just deep
// enough for the in-feed layout engine and the two view factories to port
// line for line. The engine's whole candidate search is phrased in terms of
// measure(UNSPECIFIED) versus measure(EXACTLY) passes; recreating that model
// is what keeps the iOS port a transcription instead of a redesign.
//
// Everything here works in points; one point stands where Android's px
// stood, and the factories convert dp thresholds through the screen scale
// at their boundary the way the Android factories convert through density.

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, HBMeasureSpecMode) {
    HBMeasureSpecUnspecified = 0
  , HBMeasureSpecExactly
  , HBMeasureSpecAtMost
};

typedef struct {
    HBMeasureSpecMode mode;
    CGFloat size;
} HBMeasureSpec;

static inline HBMeasureSpec HBMeasureSpecMake(
        HBMeasureSpecMode mode
      , CGFloat size) {
    HBMeasureSpec spec;
    spec.mode = mode;
    spec.size = size;
    return spec;
}

// Android's LayoutParams dimension sentinels.
extern const CGFloat ROLayoutMatchParent; // -1
extern const CGFloat ROLayoutWrapContent; // -2

// Android's Gravity subset, combinable.
typedef NS_OPTIONS(NSUInteger, HBGravity) {
    HBGravityLeft = 1 << 0
  , HBGravityTop = 1 << 1
  , HBGravityRight = 1 << 2
  , HBGravityBottom = 1 << 3
  , HBGravityCenterHorizontal = 1 << 4
  , HBGravityCenterVertical = 1 << 5
};

// Layout state carried per child, the counterpart of LayoutParams plus the
// measured size a measure pass leaves behind.
@interface UIView (ROMeasureLayout)

@property (nonatomic) CGFloat ro_layoutWidth;   // pt, MatchParent or WrapContent
@property (nonatomic) CGFloat ro_layoutHeight;  // pt, MatchParent or WrapContent
@property (nonatomic) CGFloat ro_layoutWeight;
@property (nonatomic) HBGravity ro_layoutGravity; // 0 = container decides
@property (nonatomic) UIEdgeInsets ro_layoutMargins;
@property (nonatomic) CGSize ro_measuredSize;
// Android GONE: skipped by measure and layout entirely. UIView.hidden alone
// is Android INVISIBLE - it still takes its space.
@property (nonatomic) BOOL ro_gone;
// Minimum content size honoured by the default leaf measurement, the
// counterpart of setMinimumWidth/Height.
@property (nonatomic) CGSize ro_minimumSize;

// Measures the view against the given specs and stores ro_measuredSize.
// Containers override; the default resolves a leaf through sizeThatFits.
- (void)ro_measureWithWidthSpec:(HBMeasureSpec)widthSpec
                     heightSpec:(HBMeasureSpec)heightSpec;

// Positions the view at the frame a container computed from measured sizes.
- (void)ro_layoutWithFrame:(CGRect)frame;

// Android's getChildMeasureSpec, verbatim.
+ (HBMeasureSpec)ro_childMeasureSpecForParentSpec:(HBMeasureSpec)parentSpec
                                          padding:(CGFloat)padding
                                   childDimension:(CGFloat)childDimension;

@end

// Container base holding padding and the shared child enumeration.
@interface ROLayoutContainerView : UIView

@property (nonatomic) UIEdgeInsets ro_padding;

@end

// Android LinearLayout: sequential main-axis placement, weights absorbing
// the leftover of an EXACTLY pass, and - load-bearing for the engine - a
// weighted zero-height child measured under UNSPECIFIED is treated as
// WRAP_CONTENT rather than collapsing to nothing.
@interface HBLinearLayoutView : ROLayoutContainerView

@property (nonatomic) BOOL ro_vertical;
@property (nonatomic) HBGravity ro_gravity;

@end

// Android FrameLayout: children stacked, sized to the frame minus margins,
// positioned by their own gravity.
@interface HBFrameLayoutView : ROLayoutContainerView

@end

NS_ASSUME_NONNULL_END
