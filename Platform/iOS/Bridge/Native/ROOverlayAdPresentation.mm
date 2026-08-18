#import "ROOverlayAdPresentation.h"

#import "ROOverlayAdContentView.h"

static NSString *const kROTag = @"Overlay";

// The Activity: a translucent full-screen controller whose whole content is
// the ad face. Status bar and home indicator step back the way the Android
// immersive flags pushed the system bars away.
@interface ROOverlayAdViewController : UIViewController
@property (nonatomic, strong, nullable) ROOverlayAdContentView *contentView;
@end

@implementation ROOverlayAdViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.clearColor;
    if (self.contentView != nil) {
        self.contentView.frame = self.view.bounds;
        self.contentView.autoresizingMask =
                UIViewAutoresizingFlexibleWidth
                        | UIViewAutoresizingFlexibleHeight;
        [self.view addSubview:self.contentView];
    }
}

- (BOOL)prefersStatusBarHidden {
    return YES;
}

- (BOOL)prefersHomeIndicatorAutoHidden {
    return YES;
}

@end

@implementation ROOverlayAdPresentation {
    __weak UIViewController *_hostViewController;
    GADNativeAd *_nativeAd;
    int32_t _countdownSec;
    BOOL _closeOnLeft;
    BOOL _timerOnLeft;
    BOOL _fullscreen;
    float _heightRatio;
    float _backgroundAlpha;

    ROOverlayAdContentView *_contentView;
    ROOverlayAdViewController *_presentedController;
    BOOL _showing;
    BOOL _dismissed;
    BOOL _observingLifecycle;
}

- (instancetype)initWithViewController:(UIViewController *)viewController
                              nativeAd:(GADNativeAd *)nativeAd
                          countdownSec:(int32_t)countdownSec
                           closeOnLeft:(BOOL)closeOnLeft
                           timerOnLeft:(BOOL)timerOnLeft
                            fullscreen:(BOOL)fullscreen
                           heightRatio:(float)heightRatio
                       backgroundAlpha:(float)backgroundAlpha
                  fakeCloseAutoDismiss:(BOOL)fakeCloseAutoDismiss {
    self = [super init];
    if (self == nil) return nil;

    _hostViewController = viewController;
    _nativeAd = nativeAd;
    _countdownSec = MAX(0, countdownSec);
    _closeOnLeft = closeOnLeft;
    _timerOnLeft = timerOnLeft;
    _fullscreen = fullscreen;
    _heightRatio = heightRatio;
    _backgroundAlpha = backgroundAlpha;
    _fakeCloseAutoDismiss = fakeCloseAutoDismiss;
    return self;
}

- (void)dealloc {
    [self ro_stopObservingLifecycle];
}

- (BOOL)prepare {
    if (_dismissed) return NO;
    if (_contentView != nil) return YES;

    BOOL hasVideoContent = _nativeAd.mediaContent.hasVideoContent;
    CGFloat requestedPanelHeight = [ROOverlayAdContentView
            resolveInitialPanelHeightForFullscreen:_fullscreen
                                       heightRatio:_heightRatio
                                   hasVideoContent:hasVideoContent];
    __weak ROOverlayAdPresentation *weakSelf = self;
    _contentView = [[ROOverlayAdContentView alloc]
            initWithNativeAd:_nativeAd
        countDownRemainingMs:(int64_t)_countdownSec * 1000
                 closeOnLeft:_closeOnLeft
                 timerOnLeft:_timerOnLeft
                  fullscreen:_fullscreen
             backgroundAlpha:_backgroundAlpha
        fakeCloseAutoDismiss:_fakeCloseAutoDismiss
        requestedPanelHeight:requestedPanelHeight
                     onClose:^{ [weakSelf dismiss]; }];
    return _contentView != nil;
}

- (BOOL)show {
    if (_dismissed || _showing) return NO;
    if (![self prepare]) return NO;

    UIViewController *host = _hostViewController;
    if (host == nil || host.view.window == nil) {
        NSLog(@"%@: Show rejected because the host controller is gone", kROTag);
        return NO;
    }

    if (_fullscreen) {
        _presentedController =
                [[ROOverlayAdViewController alloc] init];
        _presentedController.contentView = _contentView;
        _presentedController.modalPresentationStyle =
                UIModalPresentationOverFullScreen;
        _presentedController.modalTransitionStyle =
                UIModalTransitionStyleCrossDissolve;
        UIViewController *presenter = host;
        while (presenter.presentedViewController != nil) {
            presenter = presenter.presentedViewController;
        }
        [presenter presentViewController:_presentedController
                                animated:NO
                              completion:nil];
    } else {
        UIView *hostView = host.view;
        CGFloat panelHeight = _contentView.resolvedPanelHeight;
        _contentView.frame = CGRectMake(
                0
              , hostView.bounds.size.height - panelHeight
              , hostView.bounds.size.width
              , panelHeight);
        _contentView.autoresizingMask =
                UIViewAutoresizingFlexibleWidth
                        | UIViewAutoresizingFlexibleTopMargin;
        [hostView addSubview:_contentView];
    }

    _showing = YES;
    [self ro_startObservingLifecycle];
    [_contentView onPresented];
    if (self.onShow != nil) self.onShow();
    return YES;
}

- (BOOL)isShowing {
    return _showing && !_dismissed;
}

- (void)dismiss {
    [self ro_dismissWithNotify:YES];
}

- (void)releasePresentation {
    [self ro_dismissWithNotify:NO];
}

- (void)onAdClicked {
    [_contentView commitAdClick];
}

- (void)ro_dismissWithNotify:(BOOL)notify {
    if (_dismissed) return;
    _dismissed = YES;
    _showing = NO;
    [self ro_stopObservingLifecycle];

    [_contentView onPaused];
    [_contentView releaseContent];
    [_contentView removeFromSuperview];
    _contentView = nil;

    ROOverlayAdViewController *presented = _presentedController;
    _presentedController = nil;
    if (presented != nil && presented.presentingViewController != nil) {
        [presented dismissViewControllerAnimated:NO completion:nil];
    }

    if (notify && self.onDismiss != nil) self.onDismiss();
}

// The Activity's onPause/onResume pair: the countdown holds its remaining
// time across backgrounding instead of burning through it unseen.
- (void)ro_startObservingLifecycle {
    if (_observingLifecycle) return;
    _observingLifecycle = YES;
    [NSNotificationCenter.defaultCenter
            addObserver:self
               selector:@selector(ro_applicationWillResignActive)
                   name:UIApplicationWillResignActiveNotification
                 object:nil];
    [NSNotificationCenter.defaultCenter
            addObserver:self
               selector:@selector(ro_applicationDidBecomeActive)
                   name:UIApplicationDidBecomeActiveNotification
                 object:nil];
}

- (void)ro_stopObservingLifecycle {
    if (!_observingLifecycle) return;
    _observingLifecycle = NO;
    [NSNotificationCenter.defaultCenter removeObserver:self];
}

- (void)ro_applicationWillResignActive {
    if (_showing) [_contentView onPaused];
}

- (void)ro_applicationDidBecomeActive {
    if (_showing) [_contentView onPresented];
}

@end
