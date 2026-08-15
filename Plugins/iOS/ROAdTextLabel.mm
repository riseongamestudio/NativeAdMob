#import "ROAdTextLabel.h"

static const NSTimeInterval kROMarqueeInitialDelaySeconds = 1.2;
static const NSTimeInterval kROMarqueeRepeatDelaySeconds = 1.2;
static const CGFloat kROMarqueePointsPerSecond = 30;
// Android separates the ghost from the tail by a third of the view width.
static const CGFloat kROMarqueeGhostGapRatio = 1.0f / 3.0f;

@implementation ROAdTextLabel {
    UILabel *_label;
    UILabel *_ghost;
    // Identity of the current animation cycle; bumping it orphans every
    // pending delayed block, the way invalidating a generation does.
    NSInteger _marqueeGeneration;
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self == nil) return nil;

    self.clipsToBounds = YES;
    _font = [UIFont systemFontOfSize:UIFont.labelFontSize];
    _textColor = UIColor.whiteColor;
    _maxLines = 1;
    _textAlignment = NSTextAlignmentLeft;
    _label = [[UILabel alloc] init];
    _ghost = [[UILabel alloc] init];
    _ghost.hidden = YES;
    [self addSubview:_label];
    [self addSubview:_ghost];
    [self ro_applyTextConfiguration];
    return self;
}

- (void)setText:(NSString *)text {
    _text = [text copy];
    [self ro_applyTextConfiguration];
}

- (void)setFont:(UIFont *)font {
    _font = font;
    [self ro_applyTextConfiguration];
}

- (void)setTextColor:(UIColor *)textColor {
    _textColor = textColor;
    [self ro_applyTextConfiguration];
}

- (void)setMaxLines:(NSInteger)maxLines {
    _maxLines = MAX(1, maxLines);
    [self ro_applyTextConfiguration];
}

- (void)setMarquee:(BOOL)marquee {
    if (_marquee == marquee) return;
    _marquee = marquee;
    [self ro_applyTextConfiguration];
}

- (void)setTextAlignment:(NSTextAlignment)textAlignment {
    _textAlignment = textAlignment;
    [self ro_applyTextConfiguration];
}

// One attributed rebuild for every configuration change: full hyphenation in
// wrapped mode matches the Android factory's HYPHENATION_FREQUENCY_FULL, so
// line counts - and with them which candidates fit - agree across platforms.
- (void)ro_applyTextConfiguration {
    ++_marqueeGeneration;
    NSMutableParagraphStyle *paragraph = [[NSMutableParagraphStyle alloc] init];
    paragraph.alignment = _textAlignment;
    if (self.marquee) {
        paragraph.lineBreakMode = NSLineBreakByClipping;
    } else {
        paragraph.lineBreakMode = NSLineBreakByWordWrapping;
        paragraph.hyphenationFactor = 1;
    }

    NSAttributedString *value = [[NSAttributedString alloc]
            initWithString:self.text ?: @""
                attributes:@{
                    NSFontAttributeName: self.font
                  , NSForegroundColorAttributeName: self.textColor
                  , NSParagraphStyleAttributeName: paragraph
                }];
    _label.attributedText = value;
    _ghost.attributedText = value;
    _label.numberOfLines = self.marquee ? 1 : self.maxLines;
    _ghost.numberOfLines = 1;
    _ghost.hidden = YES;
    [self setNeedsLayout];
    [self ro_layoutContentAndRestartMarquee];
}

- (CGFloat)ro_textNaturalWidth {
    return ceil([_label sizeThatFits:
            CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)].width);
}

- (CGFloat)ro_singleLineHeight {
    return ceil(self.font.lineHeight);
}

// Wrapped: the value over its allowed lines, bounded by the width offered.
// Marquee: a single line whose desired width is the whole text, the same
// desired size Android's horizontally scrolling TextView reports; the
// container's EXACTLY spec then clamps it to the slot.
- (CGSize)sizeThatFits:(CGSize)size {
    if (self.marquee) {
        return CGSizeMake(
                [self ro_textNaturalWidth]
              , [self ro_singleLineHeight]);
    }
    CGSize bounded = [_label sizeThatFits:
            CGSizeMake(size.width, CGFLOAT_MAX)];
    return CGSizeMake(ceil(bounded.width), ceil(bounded.height));
}

- (BOOL)ro_showsEntireTextForWidth:(CGFloat)width {
    if (self.marquee) return YES;
    if ((self.text ?: @"").length == 0) return YES;
    if (width <= 0) return NO;

    // The height the whole value would need against the height the allowed
    // line count grants; anything beyond the cap is cut, not shown.
    CGRect unbounded = [_label.attributedText
            boundingRectWithSize:CGSizeMake(width, CGFLOAT_MAX)
                         options:NSStringDrawingUsesLineFragmentOrigin
                         context:nil];
    NSInteger cappedLines = _label.numberOfLines;
    CGFloat allowedHeight = cappedLines <= 0
            ? CGFLOAT_MAX
            : cappedLines * self.font.lineHeight;
    return ceil(unbounded.size.height) <= ceil(allowedHeight) + 1;
}

- (void)ro_layoutWithFrame:(CGRect)frame {
    [super ro_layoutWithFrame:frame];
    [self ro_layoutContentAndRestartMarquee];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    [self ro_layoutContentAndRestartMarquee];
}

- (void)ro_layoutContentAndRestartMarquee {
    ++_marqueeGeneration;
    _label.transform = CGAffineTransformIdentity;
    _ghost.transform = CGAffineTransformIdentity;

    CGFloat viewWidth = self.bounds.size.width;
    CGFloat viewHeight = self.bounds.size.height;
    if (!self.marquee) {
        _ghost.hidden = YES;
        _label.frame = CGRectMake(0, 0, viewWidth, viewHeight);
        return;
    }

    CGFloat textWidth = [self ro_textNaturalWidth];
    CGFloat lineHeight = [self ro_singleLineHeight];
    CGFloat textTop = MAX(0, (viewHeight - lineHeight) / 2);
    _label.frame = CGRectMake(
            0, textTop, MAX(textWidth, viewWidth), lineHeight);
    if (textWidth <= viewWidth || viewWidth <= 0) {
        // Fits still; Android's marquee does not move either.
        _ghost.hidden = YES;
        return;
    }

    CGFloat ghostOffset = textWidth + viewWidth * kROMarqueeGhostGapRatio;
    _ghost.hidden = NO;
    _ghost.frame = CGRectMake(ghostOffset, textTop, textWidth, lineHeight);
    [self ro_scheduleMarqueeCycleWithOffset:ghostOffset
                                      delay:kROMarqueeInitialDelaySeconds
                                 generation:_marqueeGeneration];
}

- (void)ro_scheduleMarqueeCycleWithOffset:(CGFloat)ghostOffset
                                    delay:(NSTimeInterval)delay
                               generation:(NSInteger)generation {
    __weak ROAdTextLabel *weakSelf = self;
    dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay * NSEC_PER_SEC))
          , dispatch_get_main_queue()
          , ^{
        ROAdTextLabel *strongSelf = weakSelf;
        if (strongSelf == nil
                || generation != strongSelf->_marqueeGeneration) {
            return;
        }

        NSTimeInterval duration = ghostOffset / kROMarqueePointsPerSecond;
        [UIView animateWithDuration:duration
                              delay:0
                            options:UIViewAnimationOptionCurveLinear
                         animations:^{
            CGAffineTransform scrolled =
                    CGAffineTransformMakeTranslation(-ghostOffset, 0);
            strongSelf->_label.transform = scrolled;
            strongSelf->_ghost.transform = scrolled;
        } completion:^(BOOL finished) {
            ROAdTextLabel *completedSelf = weakSelf;
            if (completedSelf == nil
                    || generation != completedSelf->_marqueeGeneration) {
                return;
            }
            completedSelf->_label.transform = CGAffineTransformIdentity;
            completedSelf->_ghost.transform = CGAffineTransformIdentity;
            if (!finished) return;
            [completedSelf
                    ro_scheduleMarqueeCycleWithOffset:ghostOffset
                                                delay:kROMarqueeRepeatDelaySeconds
                                           generation:generation];
        }];
    });
}

- (void)willMoveToWindow:(UIWindow *)newWindow {
    [super willMoveToWindow:newWindow];
    if (newWindow == nil) {
        // Orphan pending cycles; layout restarts them on return.
        ++_marqueeGeneration;
    } else {
        [self ro_layoutContentAndRestartMarquee];
    }
}

@end
