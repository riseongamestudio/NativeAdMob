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
// and stops its audio without the game asking. The window-backed cover in
// NativeOverlay does not and cannot - same process, same Activity, nothing
// for the system to pause.
//
// The cost is that Android gives no way to hold one of these and toggle it.
// There is no setActive, no show, no hide: start puts it on top, finish takes
// it away, and every appearance is a task transition. That is the trade the
// caller is making when it asks for the full-screen cover.
public final class NativeOverlayActivity extends Activity {
    private static final String EXTRA_COLOR =
            "com.riseon.nativeadmob.OVERLAY_COLOR";

    private static NativeOverlayActivity current;

    private FrameLayout coverView;
    private Object backCallback;

    static void Start(Activity host, int color) {
        NativeOverlayActivity showing = current;
        if (showing != null) {
            // Already up: repaint rather than stack a second one.
            showing.ApplyColor(color);
            return;
        }

        android.content.Intent intent =
                new android.content.Intent(host, NativeOverlayActivity.class);
        intent.putExtra(EXTRA_COLOR, color);
        // No animation either way: the cover exists to hide a seam, and a
        // fade of its own would be one more seam to hide.
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
        host.startActivity(intent);
        host.overridePendingTransition(0, 0);
    }

    static void Finish() {
        NativeOverlayActivity showing = current;
        if (showing == null) return;

        showing.finish();
        showing.overridePendingTransition(0, 0);
    }

    static void SetColor(int color) {
        NativeOverlayActivity showing = current;
        if (showing != null) showing.ApplyColor(color);
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
