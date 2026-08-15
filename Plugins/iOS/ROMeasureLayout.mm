#import "ROMeasureLayout.h"

#import <objc/runtime.h>

const CGFloat ROLayoutMatchParent = -1;
const CGFloat ROLayoutWrapContent = -2;

static const void *kROLayoutWidthKey = &kROLayoutWidthKey;
static const void *kROLayoutHeightKey = &kROLayoutHeightKey;
static const void *kROLayoutWeightKey = &kROLayoutWeightKey;
static const void *kROLayoutGravityKey = &kROLayoutGravityKey;
static const void *kROLayoutMarginsKey = &kROLayoutMarginsKey;
static const void *kROMeasuredSizeKey = &kROMeasuredSizeKey;
static const void *kROGoneKey = &kROGoneKey;
static const void *kROMinimumSizeKey = &kROMinimumSizeKey;

static CGFloat HBResolveSize(CGFloat desired, HBMeasureSpec spec) {
    switch (spec.mode) {
        case HBMeasureSpecExactly: return spec.size;
        case HBMeasureSpecAtMost: return MIN(desired, spec.size);
        case HBMeasureSpecUnspecified:
        default: return desired;
    }
}

@implementation UIView (ROMeasureLayout)

- (CGFloat)ro_layoutWidth {
    NSNumber *value = objc_getAssociatedObject(self, kROLayoutWidthKey);
    return value == nil ? ROLayoutWrapContent : value.doubleValue;
}

- (void)setHb_layoutWidth:(CGFloat)width {
    objc_setAssociatedObject(
            self, kROLayoutWidthKey, @(width)
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (CGFloat)ro_layoutHeight {
    NSNumber *value = objc_getAssociatedObject(self, kROLayoutHeightKey);
    return value == nil ? ROLayoutWrapContent : value.doubleValue;
}

- (void)setHb_layoutHeight:(CGFloat)height {
    objc_setAssociatedObject(
            self, kROLayoutHeightKey, @(height)
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (CGFloat)ro_layoutWeight {
    NSNumber *value = objc_getAssociatedObject(self, kROLayoutWeightKey);
    return value == nil ? 0 : value.doubleValue;
}

- (void)setHb_layoutWeight:(CGFloat)weight {
    objc_setAssociatedObject(
            self, kROLayoutWeightKey, @(weight)
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (HBGravity)ro_layoutGravity {
    NSNumber *value = objc_getAssociatedObject(self, kROLayoutGravityKey);
    return value == nil ? 0 : (HBGravity)value.unsignedIntegerValue;
}

- (void)setHb_layoutGravity:(HBGravity)gravity {
    objc_setAssociatedObject(
            self, kROLayoutGravityKey, @(gravity)
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (UIEdgeInsets)ro_layoutMargins {
    NSValue *value = objc_getAssociatedObject(self, kROLayoutMarginsKey);
    return value == nil ? UIEdgeInsetsZero : value.UIEdgeInsetsValue;
}

- (void)setHb_layoutMargins:(UIEdgeInsets)margins {
    objc_setAssociatedObject(
            self, kROLayoutMarginsKey
          , [NSValue valueWithUIEdgeInsets:margins]
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (CGSize)ro_measuredSize {
    NSValue *value = objc_getAssociatedObject(self, kROMeasuredSizeKey);
    return value == nil ? CGSizeZero : value.CGSizeValue;
}

- (void)setHb_measuredSize:(CGSize)size {
    objc_setAssociatedObject(
            self, kROMeasuredSizeKey
          , [NSValue valueWithCGSize:size]
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (BOOL)ro_gone {
    NSNumber *value = objc_getAssociatedObject(self, kROGoneKey);
    return value.boolValue;
}

- (void)setHb_gone:(BOOL)gone {
    objc_setAssociatedObject(
            self, kROGoneKey, @(gone)
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (CGSize)ro_minimumSize {
    NSValue *value = objc_getAssociatedObject(self, kROMinimumSizeKey);
    return value == nil ? CGSizeZero : value.CGSizeValue;
}

- (void)setHb_minimumSize:(CGSize)size {
    objc_setAssociatedObject(
            self, kROMinimumSizeKey
          , [NSValue valueWithCGSize:size]
          , OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

// Leaf measurement. Labels, images and buttons size through sizeThatFits,
// which honours numberOfLines and image dimensions the way Android's
// onMeasure honours maxLines and drawable bounds. A plain UIView reports
// zero content - sizeThatFits on it would echo its current bounds, and a
// spacer that remembers its old size breaks every weighted pass.
- (void)ro_measureWithWidthSpec:(HBMeasureSpec)widthSpec
                     heightSpec:(HBMeasureSpec)heightSpec {
    CGSize content = CGSizeZero;
    if (![self isMemberOfClass:UIView.class]) {
        CGFloat availableWidth = widthSpec.mode == HBMeasureSpecUnspecified
                ? CGFLOAT_MAX
                : widthSpec.size;
        CGFloat availableHeight = heightSpec.mode == HBMeasureSpecUnspecified
                ? CGFLOAT_MAX
                : heightSpec.size;
        content = [self sizeThatFits:
                CGSizeMake(availableWidth, availableHeight)];
    }
    CGSize minimum = self.ro_minimumSize;
    content.width = MAX(content.width, minimum.width);
    content.height = MAX(content.height, minimum.height);
    self.ro_measuredSize = CGSizeMake(
            HBResolveSize(ceil(content.width), widthSpec)
          , HBResolveSize(ceil(content.height), heightSpec));
}

- (void)ro_layoutWithFrame:(CGRect)frame {
    self.frame = frame;
}

+ (HBMeasureSpec)ro_childMeasureSpecForParentSpec:(HBMeasureSpec)parentSpec
                                          padding:(CGFloat)padding
                                   childDimension:(CGFloat)childDimension {
    CGFloat available = MAX(0, parentSpec.size - padding);
    if (childDimension >= 0) {
        return HBMeasureSpecMake(HBMeasureSpecExactly, childDimension);
    }

    switch (parentSpec.mode) {
        case HBMeasureSpecExactly:
            return childDimension == ROLayoutMatchParent
                    ? HBMeasureSpecMake(HBMeasureSpecExactly, available)
                    : HBMeasureSpecMake(HBMeasureSpecAtMost, available);
        case HBMeasureSpecAtMost:
            return HBMeasureSpecMake(HBMeasureSpecAtMost, available);
        case HBMeasureSpecUnspecified:
        default:
            return HBMeasureSpecMake(HBMeasureSpecUnspecified, 0);
    }
}

@end

@implementation ROLayoutContainerView

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self != nil) _ro_padding = UIEdgeInsetsZero;
    return self;
}

@end

// Axis-neutral accessors keep one implementation honest for both
// orientations instead of two long near-copies drifting apart.
static CGFloat HBAxisSize(CGSize size, BOOL mainIsVertical) {
    return mainIsVertical ? size.height : size.width;
}

static CGFloat HBCrossSize(CGSize size, BOOL mainIsVertical) {
    return mainIsVertical ? size.width : size.height;
}

static CGFloat HBAxisMargins(UIEdgeInsets margins, BOOL mainIsVertical) {
    return mainIsVertical
            ? margins.top + margins.bottom
            : margins.left + margins.right;
}

static CGFloat HBCrossMargins(UIEdgeInsets margins, BOOL mainIsVertical) {
    return mainIsVertical
            ? margins.left + margins.right
            : margins.top + margins.bottom;
}

@implementation HBLinearLayoutView

- (CGFloat)ro_mainDimensionOf:(UIView *)child {
    return self.ro_vertical ? child.ro_layoutHeight : child.ro_layoutWidth;
}

- (CGFloat)ro_crossDimensionOf:(UIView *)child {
    return self.ro_vertical ? child.ro_layoutWidth : child.ro_layoutHeight;
}

- (void)ro_measureChild:(UIView *)child
           mainAxisSpec:(HBMeasureSpec)mainSpec
          crossAxisSpec:(HBMeasureSpec)crossSpec
               mainUsed:(CGFloat)mainUsed
          mainDimension:(CGFloat)mainDimension {
    UIEdgeInsets padding = self.ro_padding;
    UIEdgeInsets margins = child.ro_layoutMargins;
    BOOL vertical = self.ro_vertical;
    CGFloat mainPadding = vertical
            ? padding.top + padding.bottom
            : padding.left + padding.right;
    CGFloat crossPadding = vertical
            ? padding.left + padding.right
            : padding.top + padding.bottom;

    HBMeasureSpec childMainSpec = [UIView
            ro_childMeasureSpecForParentSpec:mainSpec
                                     padding:mainPadding
                                             + HBAxisMargins(margins, vertical)
                                             + mainUsed
                              childDimension:mainDimension];
    HBMeasureSpec childCrossSpec = [UIView
            ro_childMeasureSpecForParentSpec:crossSpec
                                     padding:crossPadding
                                             + HBCrossMargins(margins, vertical)
                              childDimension:[self ro_crossDimensionOf:child]];
    if (vertical) {
        [child ro_measureWithWidthSpec:childCrossSpec
                            heightSpec:childMainSpec];
    } else {
        [child ro_measureWithWidthSpec:childMainSpec
                            heightSpec:childCrossSpec];
    }
}

- (void)ro_measureWithWidthSpec:(HBMeasureSpec)widthSpec
                     heightSpec:(HBMeasureSpec)heightSpec {
    BOOL vertical = self.ro_vertical;
    HBMeasureSpec mainSpec = vertical ? heightSpec : widthSpec;
    HBMeasureSpec crossSpec = vertical ? widthSpec : heightSpec;
    UIEdgeInsets padding = self.ro_padding;
    CGFloat mainPadding = vertical
            ? padding.top + padding.bottom
            : padding.left + padding.right;
    CGFloat crossPadding = vertical
            ? padding.left + padding.right
            : padding.top + padding.bottom;

    CGFloat totalMain = 0;
    CGFloat maxCross = 0;
    CGFloat totalWeight = 0;
    for (UIView *child in self.subviews) {
        if (child.ro_gone) continue;

        CGFloat weight = child.ro_layoutWeight;
        totalWeight += weight;
        UIEdgeInsets margins = child.ro_layoutMargins;
        CGFloat mainDimension = [self ro_mainDimensionOf:child];
        if (mainSpec.mode == HBMeasureSpecExactly
                && mainDimension == 0
                && weight > 0) {
            // Uses only leftover space; measured in the weight pass.
            totalMain += HBAxisMargins(margins, vertical);
            continue;
        }
        if (mainDimension == 0 && weight > 0) {
            // Not EXACTLY, so there is no leftover to claim: the child
            // wanted to stretch, translate that to WRAP_CONTENT so it does
            // not end up with a size of zero. This is the Android rule the
            // in-feed engine's natural-height pass depends on.
            mainDimension = ROLayoutWrapContent;
        }

        [self ro_measureChild:child
                 mainAxisSpec:mainSpec
                crossAxisSpec:crossSpec
                     mainUsed:totalMain
                mainDimension:mainDimension];
        CGSize measured = child.ro_measuredSize;
        totalMain += HBAxisSize(measured, vertical)
                + HBAxisMargins(margins, vertical);
        maxCross = MAX(
                maxCross
              , HBCrossSize(measured, vertical)
                        + HBCrossMargins(margins, vertical));
    }
    totalMain += mainPadding;

    CGFloat resolvedMain = HBResolveSize(totalMain, mainSpec);
    CGFloat delta = resolvedMain - totalMain;
    if (totalWeight > 0 && delta != 0) {
        CGFloat remainingWeight = totalWeight;
        CGFloat remainingDelta = delta;
        totalMain = mainPadding;
        maxCross = 0;
        for (UIView *child in self.subviews) {
            if (child.ro_gone) continue;

            UIEdgeInsets margins = child.ro_layoutMargins;
            CGFloat weight = child.ro_layoutWeight;
            if (weight > 0) {
                CGFloat share = remainingDelta * weight / remainingWeight;
                remainingWeight -= weight;
                remainingDelta -= share;
                CGFloat mainDimension = [self ro_mainDimensionOf:child];
                CGFloat base = mainDimension == 0
                        ? 0
                        : HBAxisSize(child.ro_measuredSize, vertical);
                [self ro_measureChild:child
                         mainAxisSpec:HBMeasureSpecMake(
                                HBMeasureSpecExactly
                              , MAX(0, base + share))
                        crossAxisSpec:crossSpec
                             mainUsed:0
                        mainDimension:ROLayoutMatchParent];
            }
            CGSize measured = child.ro_measuredSize;
            totalMain += HBAxisSize(measured, vertical)
                    + HBAxisMargins(margins, vertical);
            maxCross = MAX(
                    maxCross
                  , HBCrossSize(measured, vertical)
                            + HBCrossMargins(margins, vertical));
        }
        resolvedMain = HBResolveSize(totalMain, mainSpec);
    }

    CGFloat resolvedCross = HBResolveSize(maxCross + crossPadding, crossSpec);
    CGSize minimum = self.ro_minimumSize;
    CGSize resolved = vertical
            ? CGSizeMake(resolvedCross, resolvedMain)
            : CGSizeMake(resolvedMain, resolvedCross);
    resolved.width = MAX(resolved.width, minimum.width);
    resolved.height = MAX(resolved.height, minimum.height);
    self.ro_measuredSize = resolved;
}

- (void)ro_layoutWithFrame:(CGRect)frame {
    self.frame = frame;
    BOOL vertical = self.ro_vertical;
    UIEdgeInsets padding = self.ro_padding;
    CGRect content = UIEdgeInsetsInsetRect(
            CGRectMake(0, 0, frame.size.width, frame.size.height)
          , padding);

    CGFloat totalMain = 0;
    for (UIView *child in self.subviews) {
        if (child.ro_gone) continue;
        totalMain += HBAxisSize(child.ro_measuredSize, vertical)
                + HBAxisMargins(child.ro_layoutMargins, vertical);
    }

    CGFloat leftover = HBAxisSize(content.size, vertical) - totalMain;
    HBGravity gravity = self.ro_gravity;
    CGFloat mainCursor = vertical ? content.origin.y : content.origin.x;
    if (vertical) {
        if (gravity & HBGravityCenterVertical) mainCursor += leftover / 2;
        else if (gravity & HBGravityBottom) mainCursor += leftover;
    } else {
        if (gravity & HBGravityCenterHorizontal) mainCursor += leftover / 2;
        else if (gravity & HBGravityRight) mainCursor += leftover;
    }

    for (UIView *child in self.subviews) {
        if (child.ro_gone) continue;

        UIEdgeInsets margins = child.ro_layoutMargins;
        CGSize measured = child.ro_measuredSize;
        HBGravity childGravity = child.ro_layoutGravity;
        if (childGravity == 0) childGravity = gravity;

        CGFloat crossStart = vertical ? content.origin.x : content.origin.y;
        CGFloat crossAvailable = HBCrossSize(content.size, vertical)
                - HBCrossSize(measured, vertical)
                - HBCrossMargins(margins, vertical);
        CGFloat crossOffset = 0;
        if (vertical) {
            if (childGravity & HBGravityCenterHorizontal) {
                crossOffset = crossAvailable / 2;
            } else if (childGravity & HBGravityRight) {
                crossOffset = crossAvailable;
            }
        } else {
            if (childGravity & HBGravityCenterVertical) {
                crossOffset = crossAvailable / 2;
            } else if (childGravity & HBGravityBottom) {
                crossOffset = crossAvailable;
            }
        }

        CGRect childFrame;
        if (vertical) {
            childFrame = CGRectMake(
                    crossStart + margins.left + crossOffset
                  , mainCursor + margins.top
                  , measured.width
                  , measured.height);
            mainCursor += measured.height + margins.top + margins.bottom;
        } else {
            childFrame = CGRectMake(
                    mainCursor + margins.left
                  , crossStart + margins.top + crossOffset
                  , measured.width
                  , measured.height);
            mainCursor += measured.width + margins.left + margins.right;
        }
        [child ro_layoutWithFrame:childFrame];
    }
}

@end

@implementation HBFrameLayoutView

- (void)ro_measureWithWidthSpec:(HBMeasureSpec)widthSpec
                     heightSpec:(HBMeasureSpec)heightSpec {
    UIEdgeInsets padding = self.ro_padding;
    CGFloat horizontalPadding = padding.left + padding.right;
    CGFloat verticalPadding = padding.top + padding.bottom;

    CGFloat maxWidth = 0;
    CGFloat maxHeight = 0;
    NSMutableArray<UIView *> *matchParentChildren = [NSMutableArray array];
    for (UIView *child in self.subviews) {
        if (child.ro_gone) continue;

        UIEdgeInsets margins = child.ro_layoutMargins;
        [child ro_measureWithWidthSpec:
                        [UIView ro_childMeasureSpecForParentSpec:widthSpec
                                padding:horizontalPadding
                                        + margins.left + margins.right
                                 childDimension:child.ro_layoutWidth]
                            heightSpec:
                        [UIView ro_childMeasureSpecForParentSpec:heightSpec
                                padding:verticalPadding
                                        + margins.top + margins.bottom
                                 childDimension:child.ro_layoutHeight]];
        CGSize measured = child.ro_measuredSize;
        maxWidth = MAX(
                maxWidth
              , measured.width + margins.left + margins.right);
        maxHeight = MAX(
                maxHeight
              , measured.height + margins.top + margins.bottom);
        if (child.ro_layoutWidth == ROLayoutMatchParent
                || child.ro_layoutHeight == ROLayoutMatchParent) {
            [matchParentChildren addObject:child];
        }
    }

    CGSize minimum = self.ro_minimumSize;
    CGFloat resolvedWidth = MAX(
            HBResolveSize(maxWidth + horizontalPadding, widthSpec)
          , minimum.width);
    CGFloat resolvedHeight = MAX(
            HBResolveSize(maxHeight + verticalPadding, heightSpec)
          , minimum.height);
    self.ro_measuredSize = CGSizeMake(resolvedWidth, resolvedHeight);

    // Same second pass FrameLayout runs: once the frame's own size is known,
    // MATCH_PARENT children are remeasured to fill it exactly.
    for (UIView *child in matchParentChildren) {
        UIEdgeInsets margins = child.ro_layoutMargins;
        CGFloat childWidth = child.ro_layoutWidth == ROLayoutMatchParent
                ? MAX(0, resolvedWidth - horizontalPadding
                        - margins.left - margins.right)
                : child.ro_measuredSize.width;
        CGFloat childHeight = child.ro_layoutHeight == ROLayoutMatchParent
                ? MAX(0, resolvedHeight - verticalPadding
                        - margins.top - margins.bottom)
                : child.ro_measuredSize.height;
        [child ro_measureWithWidthSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, childWidth)
                            heightSpec:
                        HBMeasureSpecMake(HBMeasureSpecExactly, childHeight)];
    }
}

- (void)ro_layoutWithFrame:(CGRect)frame {
    self.frame = frame;
    UIEdgeInsets padding = self.ro_padding;
    CGRect content = UIEdgeInsetsInsetRect(
            CGRectMake(0, 0, frame.size.width, frame.size.height)
          , padding);

    for (UIView *child in self.subviews) {
        if (child.ro_gone) continue;

        UIEdgeInsets margins = child.ro_layoutMargins;
        CGSize measured = child.ro_measuredSize;
        HBGravity gravity = child.ro_layoutGravity;

        CGFloat x = content.origin.x + margins.left;
        if (gravity & HBGravityCenterHorizontal) {
            x = content.origin.x
                    + (content.size.width - measured.width) / 2;
        } else if (gravity & HBGravityRight) {
            x = CGRectGetMaxX(content) - measured.width - margins.right;
        }

        CGFloat y = content.origin.y + margins.top;
        if (gravity & HBGravityCenterVertical) {
            y = content.origin.y
                    + (content.size.height - measured.height) / 2;
        } else if (gravity & HBGravityBottom) {
            y = CGRectGetMaxY(content) - measured.height - margins.bottom;
        }

        [child ro_layoutWithFrame:
                CGRectMake(x, y, measured.width, measured.height)];
    }
}

@end
