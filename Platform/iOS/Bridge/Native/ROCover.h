#import <UIKit/UIKit.h>

// What the ad surfaces need to know about the covers, and nothing else.

// The half-screen cover's container view, or nil when none is up. A
// half-screen ad inserts directly above this when it is non-nil, so the pair
// keeps its order whichever of the two was shown first.
UIView *ROHalfScreenCover_View(void);

// Told by the full-screen ad whether it needs the game stopped. One place in
// the pack calls UnityPause, and this is how the ad reaches it.
void ROCover_SetAdWantsPause(BOOL wantsPause);
