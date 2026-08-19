package com.riseon.nativeadmob;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

// Two covers, two mechanisms, and the difference is what they stop.
//
// The full-screen cover is an ACTIVITY (NativeCoverActivity), so the system
// pauses Unity behind it. That is the point of it - and the reason it is
// dangerous: a paused Unity runs no C#, so whatever takes the cover down must
// reach Hide from a thread that is still moving. See NativeCoverActivity for
// the rule that pays for it.
//
// The half-screen cover is a WINDOW on the Unity Activity. It changes nothing
// about the Activity's lifecycle, so the game keeps running behind it - which
// is what a cover over half the screen has to do anyway - and Hide is always
// reachable.
public final class NativeCover {
    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    // Only the half-screen cover keeps state here; the full-screen one lives
    // as an Activity and holds its own.
    private static final Cover HALF_SCREEN = new Cover();

    private NativeCover() {}

    public static void ShowFullScreen(Activity currentActivity, int color) {
        RunOnMainThread(() -> {
            if (!IsActivityUsable(currentActivity)) return;

            NativeCoverActivity.Start(currentActivity, color);
        });
    }

    public static void HideFullScreen() {
        RunOnMainThread(NativeCoverActivity::Finish);
    }

    public static void ShowHalfScreen(
            Activity currentActivity
          , int color
          , float heightRatio) {
        Show(HALF_SCREEN, currentActivity, color, heightRatio);
    }

    public static void HideHalfScreen() {
        RunOnMainThread(HALF_SCREEN::Dismiss);
    }

    private static void Show(
            Cover cover
          , Activity currentActivity
          , int color
          , float heightRatio) {
        float resolvedRatio = ResolveHeightRatio(heightRatio);
        RunOnMainThread(() -> {
            cover.color = color;
            // A cover already up at this height only needs repainting.
            // Tearing the window down and building another is a visible
            // flicker, in the one surface whose whole job is to hide those.
            if (cover.dialog != null && cover.heightRatio == resolvedRatio) {
                cover.dialog.SetColor(color);
                return;
            }

            cover.heightRatio = resolvedRatio;
            cover.Dismiss();
            if (!IsActivityUsable(currentActivity)) return;

            CoverDialog createdDialog = new CoverDialog(
                    currentActivity
                  , cover.color
                  , cover.heightRatio);
            cover.dialog = createdDialog;
            try {
                createdDialog.show();
            } catch (RuntimeException ignored) {
                if (cover.dialog == createdDialog) cover.Dismiss();
            }
        });
    }

    // Anything outside (0,1] means the whole screen: a caller that names no
    // share is asking for the cover it has always had.
    private static float ResolveHeightRatio(float value) {
        if (Float.isNaN(value) || value <= 0f || value >= 1f) return 1f;
        return value;
    }

    // One hop for the whole class, and it does not need an Activity to make
    // it. Activity.runOnUiThread is the same logic - run inline when already
    // on the UI thread, post otherwise - but it has nothing to post to when
    // the Activity is null, and the version that stood here ran the action
    // on whatever thread called it. Window work on Unity's thread is a crash
    // waiting for the one call that arrives with no Activity in hand.
    private static void RunOnMainThread(Runnable action) {
        if (action == null) return;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        MAIN.post(action);
    }

    private static boolean IsActivityUsable(Activity activity) {
        return activity != null
                && !activity.isFinishing()
                && !activity.isDestroyed();
    }

    private static final class Cover {
        private CoverDialog dialog;
        private int color = Color.BLACK;
        private float heightRatio = 1f;

        private void Dismiss() {
            CoverDialog currentDialog = dialog;
            dialog = null;
            if (currentDialog == null) return;

            try {
                currentDialog.dismiss();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static final class CoverDialog extends Dialog {
        private final Activity hostActivity;
        private final int initialColor;
        private final float heightRatio;
        private FrameLayout overlayView;

        CoverDialog(Activity activity, int color, float ratio) {
            super(
                    activity
                  , android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            hostActivity = activity;
            initialColor = color;
            heightRatio = ratio;
        }

        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setCancelable(false);

            Window window = getWindow();
            ConfigureWindow(window, hostActivity, heightRatio);

            overlayView = new FrameLayout(hostActivity);
            overlayView.setBackgroundColor(initialColor);
            overlayView.setClickable(true);
            overlayView.setFocusable(false);
            overlayView.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setContentView(
                    overlayView
                  , new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT
                          , ViewGroup.LayoutParams.MATCH_PARENT));
        }

        @Override
        protected void onStart() {
            super.onStart();
            ConfigureWindow(getWindow(), hostActivity, heightRatio);
        }

        void SetColor(int color) {
            if (overlayView != null) overlayView.setBackgroundColor(color);
        }

        private static void ConfigureWindow(
                Window window
              , Activity activity
              , float heightRatio) {
            if (window == null
                    || activity == null
                    || activity.getWindow() == null) {
                return;
            }

            View hostDecorView = activity.getWindow().getDecorView();
            android.os.IBinder hostWindowToken =
                    hostDecorView.getWindowToken();
            if (hostWindowToken == null) {
                hostWindowToken =
                        hostDecorView.getApplicationWindowToken();
            }
            if (hostWindowToken == null) return;

            // A partial cover is a SHORT WINDOW, not a full one with a
            // transparent top: the strip above it never enters this window's
            // input region, so the game keeps receiving touches there.
            int coverHeight = heightRatio >= 1f
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : Math.max(
                            1
                          , Math.round(
                                hostDecorView.getHeight() * heightRatio));

            WindowManager.LayoutParams attributes =
                    window.getAttributes();
            attributes.token = hostWindowToken;
            attributes.type =
                    WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
            attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
            attributes.height = coverHeight;
            attributes.gravity = android.view.Gravity.BOTTOM;
            window.setAttributes(attributes);

            window.setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                  , WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
            window.setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT));
            window.setWindowAnimations(0);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , coverHeight);

            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = window.getAttributes();
                attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(attributes);
            }
        }
    }
}
