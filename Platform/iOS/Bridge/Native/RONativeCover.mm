#import "RONativeCover.h"

#import "ROInFeedAdPresentation.h"

// Unity's own entry points. Declared here rather than pulled in from
// UnityInterface.h for the same reason the Google SDK declares them: the
// trampoline headers are not on a plugin's include path.
extern "C" UIViewController *UnityGetGLViewController(void);
extern "C" void UnityPause(int pause);
extern "C" int UnityIsPaused(void);

static NSString *const kROTag = @"Cover";

// Both covers are SUBVIEWS of the Unity view. Neither is a window of its own
// and neither is presented, and that is the whole design.
//
// A UIWindow with its own windowLevel orders things absolutely, which is
// tempting - but it orders them above EVERYTHING, the ad SDKs' own surfaces
// included, and it leaves the cover a stranger to Unity. Presenting the cover
// from the Unity controller is worse still: a controller can present exactly
// one thing, so a cover sitting in that slot stops the mediation SDK from
// presenting its ad at all - and a cover is typically raised for the length
// of an ad, which is precisely when that slot is wanted.
//
// As subviews they cost nothing to anyone. A presented controller always
// draws above every subview of the controller that presented it, so every
// full-screen ad - the pack's own, the SDK's - lands above these covers
// without being told to, which is the order that was wanted anyway.
//
// The full-screen cover pauses the game; the half-screen one never does,
// because the game is still visible above it.
//
// The pause is the whole point of the full-screen cover, and it is also what
// makes it sharp: a paused Unity runs no C#, and Hide IS a C# call. Whatever
// ends a show has to reach Hide from a thread that is still moving - in this
// game, AdMaxProvider's onCompletedAnyThread callbacks, which fire on the
// SDK's own thread instead of going through the player loop. Route a hide
// through the player loop while this cover is up and the screen stays black
// for good. The Android side carries the same rule and the same warning.
//
// Where they sit is separate from what they stop: the half-screen cover goes
// in under the half-screen ad, the full-screen one is appended last so it
// lands above every layer the pack owns.

#pragma mark - Pause

// Two participants, one call site. The full-screen cover pauses because it
// hides the game completely; the full-screen ad pauses for the same reason,
// and only matters when no cover is already doing it. Two named flags rather
// than a counter says exactly that: these two and no one else, and no arrival
// order can leave the game stopped with nothing on top of it.
static BOOL ROCoverWantsPause;
static BOOL ROAdWantsPause;

static BOOL ROWantsPause(void) {
    return ROCoverWantsPause || ROAdWantsPause;
}

// A third party can clear the flag the pack set.
//
// UnityPause is a FLAG, not a counter, and the mediation SDK writes it too:
// it pauses when its ad opens and RESUMES when its ad closes. The game's own
// flow raises a cover, shows a mediation ad inside it, and lowers the cover
// afterwards - so the SDK's resume lands in the middle, leaving an opaque
// cover on screen with the game free to render and play audio behind it.
//
// Nothing notifies us of that write, so the only way to hold the invariant is
// to look. While the pack wants the game stopped, this checks once a frame
// that it still is, and sets it back when it is not. It costs a comparison
// per frame, only for as long as a cover or a full-screen ad of ours is up,
// and it can strand nothing: with both flags down it asserts nothing and
// stops running.
//
// Android needs none of this: its full-screen ad is an Activity, and the
// system decides what a stacked Activity does to the one below - there is no
// flag for anyone to clear.
@interface ROPauseGuard : NSObject
@end

static CADisplayLink *ROPauseGuardLink;
static BOOL ROPauseCorrectionLogged;

@implementation ROPauseGuard

- (void)ro_tick {
    if (!ROWantsPause() || UnityIsPaused() != 0) return;

    // Once per ad, not once per frame: the correction repeats every frame
    // until whoever cleared the flag stops doing so.
    if (!ROPauseCorrectionLogged) {
        ROPauseCorrectionLogged = YES;
        NSLog(@"%@: the game was resumed from outside the pack while a cover "
               "or full-screen ad was up; pausing it again", kROTag);
    }
    UnityPause(1);
}

@end

static void ROSetPauseGuardRunning(BOOL running) {
    if (running == (ROPauseGuardLink != nil)) return;

    if (!running) {
        [ROPauseGuardLink invalidate];
        ROPauseGuardLink = nil;
        ROPauseCorrectionLogged = NO;
        return;
    }

    ROPauseGuardLink =
            [CADisplayLink displayLinkWithTarget:[[ROPauseGuard alloc] init]
                                        selector:@selector(ro_tick)];
    // Common modes, so a tracking loop anywhere else in the app cannot
    // silence the check for as long as it runs.
    [ROPauseGuardLink addToRunLoop:NSRunLoop.mainRunLoop
                           forMode:NSRunLoopCommonModes];
}

static void ROApplyPause(void) {
    BOOL wantsPause = ROWantsPause();
    UnityPause(wantsPause ? 1 : 0);
    ROSetPauseGuardRunning(wantsPause);
}

void RONativeCover_SetAdWantsPause(BOOL wantsPause) {
    if (ROAdWantsPause == wantsPause) return;

    ROAdWantsPause = wantsPause;
    ROApplyPause();
}

#pragma mark - Colour

static UIColor *ROColorFromArgb(int32_t argb) {
    uint32_t value = (uint32_t)argb;
    return [UIColor colorWithRed:((value >> 16) & 0xFF) / 255.0
                           green:((value >> 8) & 0xFF) / 255.0
                            blue:(value & 0xFF) / 255.0
                           alpha:((value >> 24) & 0xFF) / 255.0];
}

static UIView *ROHostView(void) {
    return UnityGetGLViewController().view;
}

#pragma mark - Full-screen cover

static UIView *ROFullScreenCover = nil;

static void ROShowFullScreen(int32_t color) {
    UIColor *coverColor = ROColorFromArgb(color);
    if (ROFullScreenCover != nil) {
        // Already up: repaint rather than stack a second one. The pause is
        // re-applied rather than assumed - showing the cover again is the
        // caller saying it wants the game stopped, and the flag it set the
        // first time may have been cleared by someone else since.
        ROFullScreenCover.backgroundColor = coverColor;
        ROCoverWantsPause = YES;
        ROApplyPause();
        return;
    }

    UIView *hostView = ROHostView();
    if (hostView == nil) return;

    UIView *cover = [[UIView alloc] initWithFrame:hostView.bounds];
    cover.backgroundColor = coverColor;
    // Rotation and split-screen resize the host without anyone calling in, so
    // the cover follows its bounds instead of a size captured at show time.
    cover.autoresizingMask =
            UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    // It swallows touches on purpose: nothing behind a full cover is meant to
    // be reachable, the game least of all.
    cover.userInteractionEnabled = YES;
    cover.isAccessibilityElement = NO;
    ROFullScreenCover = cover;

    ROCoverWantsPause = YES;
    ROApplyPause();

    // The end of the array is the top of the pack's own layers, above the
    // in-feed and half-screen surfaces both - which is the whole point of a
    // cover. Nothing is presented and nothing animates: the cover exists to
    // hide a seam, and a fade of its own would be one more seam to hide.
    [hostView addSubview:cover];
}

static void ROHideFullScreen(void) {
    UIView *cover = ROFullScreenCover;
    ROFullScreenCover = nil;

    ROCoverWantsPause = NO;
    ROApplyPause();
    [cover removeFromSuperview];
}

#pragma mark - Half-screen cover

@interface ROHalfScreenCoverView : UIView
@property (nonatomic) CGFloat coverHeightRatio;
@property (nonatomic, strong) UIView *cover;
@end

@implementation ROHalfScreenCoverView

- (instancetype)initWithColor:(UIColor *)color ratio:(CGFloat)ratio {
    self = [super initWithFrame:CGRectZero];
    if (self == nil) return nil;

    _coverHeightRatio = ratio;
    _cover = [[UIView alloc] initWithFrame:CGRectZero];
    _cover.backgroundColor = color;
    _cover.userInteractionEnabled = YES;
    _cover.isAccessibilityElement = NO;
    [self addSubview:_cover];
    return self;
}

// The container fills the host, so rotation and split-screen resize it for
// free; the ratio is applied to the inner view on every layout pass, which is
// how a turned phone gets a correctly sized cover without being told.
- (void)layoutSubviews {
    [super layoutSubviews];

    CGFloat ratio = self.coverHeightRatio;
    if (isnan(ratio) || ratio <= 0 || ratio >= 1) ratio = 1;

    CGFloat height = MAX(1, self.bounds.size.height * ratio);
    self.cover.frame = CGRectMake(
            0
          , self.bounds.size.height - height
          , self.bounds.size.width
          , height);
}

// The strip above the cover has to reach the game, exactly as it did when
// this was a short window. The container spans the whole host, so it is asked
// about the whole screen - and everywhere the inner cover is not, it answers
// "not me", which passes the touch to the view behind.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    UIView *hit = [super hitTest:point withEvent:event];
    return hit == self ? nil : hit;
}

@end

static ROHalfScreenCoverView *ROHalfScreenCover = nil;

UIView *RONativeCover_HalfScreenView(void) {
    return ROHalfScreenCover;
}

static void ROShowHalfScreen(int32_t color, float heightRatio) {
    UIColor *coverColor = ROColorFromArgb(color);
    if (ROHalfScreenCover != nil) {
        ROHalfScreenCover.cover.backgroundColor = coverColor;
        ROHalfScreenCover.coverHeightRatio = heightRatio;
        [ROHalfScreenCover setNeedsLayout];
        return;
    }

    UIView *hostView = ROHostView();
    if (hostView == nil) return;

    ROHalfScreenCoverView *cover =
            [[ROHalfScreenCoverView alloc] initWithColor:coverColor
                                                   ratio:heightRatio];
    cover.frame = hostView.bounds;
    cover.autoresizingMask =
            UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    ROHalfScreenCover = cover;

    // Above the in-feed layer, which is the same spot the half-screen ad
    // claims - and the ad moves above this one when it opens. Deliberately
    // NOT the end of the array: that place belongs to the full-screen cover,
    // which has to be able to hide this one.
    UIView *anchor = [ROInFeedAdPresentation ro_frontmostInFeedView];
    if (anchor != nil && anchor.superview == hostView) {
        [hostView insertSubview:cover aboveSubview:anchor];
    } else {
        [hostView insertSubview:cover atIndex:0];
    }
}

static void ROHideHalfScreen(void) {
    ROHalfScreenCoverView *cover = ROHalfScreenCover;
    ROHalfScreenCover = nil;
    [cover removeFromSuperview];
}

#pragma mark - C surface

extern "C" {

void RONativeCover_ShowFull(int32_t color) {
    dispatch_async(dispatch_get_main_queue(), ^{ ROShowFullScreen(color); });
}

void RONativeCover_HideFull(void) {
    dispatch_async(dispatch_get_main_queue(), ^{ ROHideFullScreen(); });
}

void RONativeCover_ShowHalf(int32_t color, float heightRatio) {
    dispatch_async(dispatch_get_main_queue(), ^{
        ROShowHalfScreen(color, heightRatio);
    });
}

void RONativeCover_HideHalf(void) {
    dispatch_async(dispatch_get_main_queue(), ^{ ROHideHalfScreen(); });
}

}
