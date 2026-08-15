// Port of com.riseon.nativeadmob.NativeAdMobStarRatingView: five drawn stars with a
// fractional fill, 74x16 dp desired, 2dp gaps - dp read as points here.

#import "RONativeAdStarRatingView.h"

static NSString *const kROEmptyStarColor = @"#66FFFFFF";
static NSString *const kROFilledStarColor = @"#FFFFC107";
static const NSInteger kROStarCount = 5;
static const CGFloat kRODesiredWidth = 74;
static const CGFloat kRODesiredHeight = 16;
static const CGFloat kROStarGap = 2;
static const NSInteger kROStarPathPointCount = 10;
static const CGFloat kROInnerRadiusRatio = 0.45f;

// #AARRGGBB, the Android color-string format the Java file uses.
static UIColor *HBColorFromARGBString(NSString *value) {
    unsigned long long parsed = 0;
    NSScanner *scanner = [NSScanner scannerWithString:
            [value stringByReplacingOccurrencesOfString:@"#" withString:@""]];
    [scanner scanHexLongLong:&parsed];
    CGFloat alpha = ((parsed >> 24) & 0xFF) / 255.0;
    CGFloat red = ((parsed >> 16) & 0xFF) / 255.0;
    CGFloat green = ((parsed >> 8) & 0xFF) / 255.0;
    CGFloat blue = (parsed & 0xFF) / 255.0;
    return [UIColor colorWithRed:red green:green blue:blue alpha:alpha];
}

@implementation RONativeAdStarRatingView {
    CGFloat _rating;
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self == nil) return nil;

    self.backgroundColor = UIColor.clearColor;
    self.opaque = NO;
    self.ro_minimumSize = CGSizeMake(kRODesiredWidth, kRODesiredHeight);
    return self;
}

- (void)setRating:(CGFloat)rating {
    _rating = MAX(0, MIN(kROStarCount, rating));
    [self setNeedsDisplay];
}

- (CGFloat)rating {
    return _rating;
}

- (CGSize)sizeThatFits:(CGSize)size {
    return CGSizeMake(kRODesiredWidth, kRODesiredHeight);
}

- (void)drawRect:(CGRect)rect {
    CGFloat width = self.bounds.size.width;
    CGFloat height = self.bounds.size.height;
    CGFloat starSize = MIN(
            height
          , (width - kROStarGap * (kROStarCount - 1)) / kROStarCount);
    if (starSize <= 0) return;

    CGFloat totalWidth = starSize * kROStarCount
            + kROStarGap * (kROStarCount - 1);
    CGFloat startX = (width - totalWidth) / 2;
    CGFloat centerY = height / 2;
    CGFloat outerRadius = starSize / 2;
    CGFloat innerRadius = outerRadius * kROInnerRadiusRatio;

    UIColor *emptyColor = HBColorFromARGBString(kROEmptyStarColor);
    UIColor *filledColor = HBColorFromARGBString(kROFilledStarColor);
    CGContextRef context = UIGraphicsGetCurrentContext();
    for (NSInteger index = 0; index < kROStarCount; ++index) {
        CGFloat left = startX + index * (starSize + kROStarGap);
        UIBezierPath *path = [RONativeAdStarRatingView
                ro_starPathWithCenterX:left + outerRadius
                               centerY:centerY
                           outerRadius:outerRadius
                           innerRadius:innerRadius];
        [emptyColor setFill];
        [path fill];

        CGFloat filledFraction = MAX(0, MIN(1, _rating - index));
        if (filledFraction <= 0) continue;

        CGContextSaveGState(context);
        [path addClip];
        CGContextClipToRect(
                context
              , CGRectMake(left, 0, starSize * filledFraction, height));
        [filledColor setFill];
        [path fill];
        CGContextRestoreGState(context);
    }
}

+ (UIBezierPath *)ro_starPathWithCenterX:(CGFloat)centerX
                                 centerY:(CGFloat)centerY
                             outerRadius:(CGFloat)outerRadius
                             innerRadius:(CGFloat)innerRadius {
    UIBezierPath *path = [UIBezierPath bezierPath];
    for (NSInteger point = 0; point < kROStarPathPointCount; ++point) {
        double angle = -M_PI / 2
                + point * M_PI / kROStarCount;
        CGFloat radius = point % 2 == 0 ? outerRadius : innerRadius;
        CGPoint vertex = CGPointMake(
                centerX + cos(angle) * radius
              , centerY + sin(angle) * radius);
        if (point == 0) [path moveToPoint:vertex];
        else [path addLineToPoint:vertex];
    }
    [path closePath];
    return path;
}

@end
