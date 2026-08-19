package com.riseon.nativeadmob;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

// The full-screen cover, and the reason it is an Activity rather than a
// window: an Activity on top pauses the one below, so Unity stops rendering
// and stops its audio without the game asking. The half-screen cover in
// NativeCover does not and cannot - same process, same Activity, nothing for
// the system to pause.
//
// THE PRICE, AND THE RULE THAT PAYS IT. Stopping Unity stops C#, so whatever
// takes this cover down must not need C# on Unity's player loop to run. Hide
// is reachable from any thread - it is a JNI call that posts to the Android
// main looper, both of which are alive while Unity is stopped - but the CALL
// has to come from somewhere still running. In this game that is
// AdMaxProvider's onCompletedAnyThread callbacks, which fire on the SDK's own
// thread rather than through UniTask.Post. Route a hide through the player
// loop instead and the screen stays black forever: the cover stops the loop
// that was going to take the cover away. Every path that ends a show has to
// carry the any-thread hide, the display-FAILED ones included.
//
// The other cost is that Android gives no way to hold one of these and toggle
// it. There is no setActive, no show, no hide: start puts it on top, finish
// takes it away, and every appearance is a task transition.
public final class NativeCoverActivity extends Activity {
    private static final String EXTRA_COLOR =
            "com.riseon.nativeadmob.COVER_COLOR";

    private static NativeCoverActivity current;

    private FrameLayout coverView;
    private Object backCallback;

    static void Start(Activity host, int color) {
        NativeCoverActivity showing = current;
        if (showing != null) {
            // Already up: repaint rather than stack a second one.
            showing.ApplyColor(color);
            return;
        }

        android.content.Intent intent =
                new android.content.Intent(host, NativeCoverActivity.class);
        intent.putExtra(EXTRA_COLOR, color);
        // No animation either way: the cover exists to hide a seam, and a
        // fade of its own would be one more seam to hide.
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
        host.startActivity(intent);
        host.overridePendingTransition(0, 0);
    }

    static void Finish() {
        NativeCoverActivity showing = current;
        if (showing == null) return;

        showing.finish();
        showing.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;

        Window window = getWindow();
        if (window != null) {
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.setWindowAnimations(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(attributes);
            }
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        coverView = new FrameLayout(this);
        coverView.setClickable(true);
        coverView.setFocusable(false);
        coverView.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ApplyColor(getIntent().getIntExtra(EXTRA_COLOR, Color.BLACK));
        setContentView(coverView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = Api33Impl.RegisterBackCallback(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backCallback != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33Impl.UnregisterBackCallback(this, backCallback);
        }
        backCallback = null;
        if (current == this) current = null;
    }

    // The cover is not a screen the player navigated to, so back must not
    // dismiss it - only the caller decides when it goes.
    //
    // Two mechanisms because there are two eras. onBackPressed is the legacy
    // one and is NOT called once predictive back is in force, which is where
    // the registered no-op callback takes over; the ad Activity already
    // carries the same pair.
    @Override
    public void onBackPressed() {}

    private void ApplyColor(int color) {
        if (coverView != null) coverView.setBackgroundColor(color);
    }

    private static final class Api33Impl {
        private Api33Impl() {}

        static Object RegisterBackCallback(Activity activity) {
            OnBackInvokedCallback callback = () -> {};
            activity.getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_DEFAULT
                          , callback);
            return callback;
        }

        static void UnregisterBackCallback(
                Activity activity
              , Object callback) {
            activity.getOnBackInvokedDispatcher()
                    .unregisterOnBackInvokedCallback(
                            (OnBackInvokedCallback)callback);
        }
    }
}
