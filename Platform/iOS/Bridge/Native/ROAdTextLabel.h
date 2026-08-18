// The counterpart of a TextView run through ApplyTextMode: two modes and no
// middle ground. Wrapped lays the whole value over at most maxLines lines
// and cuts without an ellipsis when it does not fit - the state validators
// detect and reject. Marquee is one line that scrolls the entire value.
// iOS has no built-in marquee, so this recreates Android's: 1200ms still,
// 30pt per second, a ghost copy a third of the view apart, 1200ms before it
// repeats - the same cadence every Android app shows.

#import <UIKit/UIKit.h>

#import "ROMeasureLayout.h"

NS_ASSUME_NONNULL_BEGIN

@interface ROAdTextLabel : ROLayoutContainerView

@property (nonatomic, copy, nullable) NSString *text;
@property (nonatomic, strong) UIFont *font;
@property (nonatomic, strong) UIColor *textColor;
// Wrapped mode only; ignored while marquee is on.
@property (nonatomic) NSInteger maxLines;
@property (nonatomic, getter=isMarquee) BOOL marquee;
@property (nonatomic) NSTextAlignment textAlignment;

// Whether the whole value is readable: always true for marquee (the scroll
// will bring the rest around), and for wrapped only when the allotted lines
// hold everything - the whole-or-scrolling policy check.
- (BOOL)ro_showsEntireTextForWidth:(CGFloat)width;

// The height one rendered line takes at the current font.
- (CGFloat)ro_singleLineHeight;

@end

NS_ASSUME_NONNULL_END
