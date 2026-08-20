package com.riseon.nativeadmob;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

// The half-screen cover: a WINDOW on the Unity Activity, not an Activity of
// its own. It changes nothing about the Activity's lifecycle, so the game
// keeps running behind it - which is what a cover over part of the screen has
// to do anyway - and Hide is always reachable.
public final class HalfScreenCover {
    private static CoverDialog dialog;
    private static int color = Color.BLACK;
    private static float heightRatio = 1f;

    private HalfScreenCover() {}

    public static void Show(
            Activity currentActivity
          , int coverColor
          , float ratio) {
        float resolvedRatio = ResolveHeightRatio(ratio);
        BaseCover.RunOnMainThread(() -> {
            color = coverColor;
            // A cover already up at this height only needs repainting.
            // Tearing the window down and building another is a visible
            // flicker, in the one surface whose whole job is to hide those.
            if (dialog != null && heightRatio == resolvedRatio) {
                dialog.SetColor(coverColor);
                return;
            }

            heightRatio = resolvedRatio;
            Dismiss();
            if (!BaseCover.IsActivityUsable(currentActivity)) return;

            CoverDialog createdDialog = new CoverDialog(
                    currentActivity
                  , color
                  , heightRatio);
            dialog = createdDialog;
            try {
                createdDialog.show();
            } catch (RuntimeException ignored) {
                if (dialog == createdDialog) Dismiss();
            }
        });
    }

    public static void Hide() {
        BaseCover.RunOnMainThread(HalfScreenCover::Dismiss);
    }

    // Anything outside (0,1] means the whole screen: a caller that names no
    // share is asking for the cover it has always had.
    private static float ResolveHeightRatio(float value) {
        if (Float.isNaN(value) || value <= 0f || value >= 1f) return 1f;
        return value;
    }

    private static void Dismiss() {
        CoverDialog currentDialog = dialog;
        dialog = null;
        if (currentDialog == null) return;

        try {
            currentDialog.dismiss();
        } catch (RuntimeException ignored) {
        }
    }

    private static final class CoverDialog extends Dialog {
        private final Activity hostActivity;
        private final int initialColor;
        private final float coverHeightRatio;
        private FrameLayout overlayView;

        CoverDialog(Activity activity, int dialogColor, float ratio) {
            super(
                    activity
                  , android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            hostActivity = activity;
            initialColor = dialogColor;
            coverHeightRatio = ratio;
        }

        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setCancelable(false);

            Window window = getWindow();
            ConfigureWindow(window, hostActivity, coverHeightRatio);

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
            ConfigureWindow(getWindow(), hostActivity, coverHeightRatio);
        }

        void SetColor(int newColor) {
            if (overlayView != null) overlayView.setBackgroundColor(newColor);
        }

        private static void ConfigureWindow(
                Window window
              , Activity activity
              , float ratio) {
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
            //
            // The height comes from the ad's own function, not from a second
            // formula of our own. This used to read decorView.getHeight(),
            // which matches displayMetrics.heightPixels on a plain emulator
            // and does NOT on a device with a cutout or a gesture bar - and
            // the difference showed as a black band above the ad, since both
            // windows sit at Gravity.BOTTOM.
            int coverHeight = ratio >= 1f
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : Math.max(
                            1
                          , OverlayAdContentView.ResolveHalfScreenPanelHeight(
                                activity
                              , ratio));

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
