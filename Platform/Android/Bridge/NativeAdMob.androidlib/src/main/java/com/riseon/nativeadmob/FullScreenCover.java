package com.riseon.nativeadmob;

import android.app.Activity;

// The full-screen cover: an ACTIVITY, so the system pauses Unity behind it.
// That is the whole difference from HalfScreenCover, which is a window and
// stops nothing.
//
// The price of the pause, and the rule that pays it, are spelled out in
// FullScreenCoverActivity: a stopped Unity runs no C#, so whatever takes this
// cover down must not be scheduled on the player loop.
public final class FullScreenCover {
    private FullScreenCover() {}

    public static void Show(Activity currentActivity, int color) {
        BaseCover.RunOnMainThread(() -> {
            if (!BaseCover.IsActivityUsable(currentActivity)) return;

            FullScreenCoverActivity.Start(currentActivity, color);
        });
    }

    public static void Hide() {
        BaseCover.RunOnMainThread(FullScreenCoverActivity::Finish);
    }
}
