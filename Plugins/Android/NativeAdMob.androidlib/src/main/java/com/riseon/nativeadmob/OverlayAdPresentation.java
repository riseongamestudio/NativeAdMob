package com.riseon.nativeadmob;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

public final class OverlayAdPresentation
        extends Dialog
        implements NativeAdPresentation {
    private static final int NON_FULLSCREEN_WINDOW_TYPE =
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
    private static final int FULLSCREEN_WINDOW_TYPE =
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;

    private final Activity hostActivity;
    private final com.google.android.gms.ads.nativead.NativeAd nativeAd;
    private final int countDownSec;
    private final boolean xRandom;
    private final boolean numberOpposite;
    private final boolean fullscreen;
    private final float heightRatio;
    private final float backgroundAlpha;
    private OverlayAdContentView contentView;

    public OverlayAdPresentation(
            Context context
          , com.google.android.gms.ads.nativead.NativeAd nativeAd
          , int countDownSec
          , boolean xRandom
          , boolean numberOpposite
          , boolean fullscreen
          , float heightRatio
          , float backgroundAlpha) {
        super(
                context
              , android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.hostActivity =
                context instanceof Activity
                        ? (Activity) context
                        : null;
        this.nativeAd = nativeAd;
        this.countDownSec = countDownSec;
        this.xRandom = xRandom;
        this.numberOpposite = numberOpposite;
        this.fullscreen = fullscreen;
        this.heightRatio = heightRatio;
        this.backgroundAlpha = backgroundAlpha;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);

        Context context = getContext();
        boolean hasVideoContent =
                nativeAd.getMediaContent() != null
                        && nativeAd.getMediaContent().hasVideoContent();
        int requestedPanelHeight =
                OverlayAdContentView.ResolveInitialPanelHeight(
                        context
                      , fullscreen
                      , heightRatio
                      , hasVideoContent);
        Window window = getWindow();
        ConfigureWindow(window, requestedPanelHeight);

        contentView = new OverlayAdContentView(
                context
              , nativeAd
              , countDownSec
              , xRandom
              , numberOpposite
              , fullscreen
              , backgroundAlpha
              , requestedPanelHeight
              , this::dismiss);
        setContentView(contentView);

        if (!fullscreen && window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , contentView.GetResolvedPanelHeight());
        }
    }

    @Override
    public void dismiss() {
        if (contentView != null) contentView.Release();
        super.dismiss();
    }

    @Override
    public boolean Show() {
        show();
        return isShowing();
    }

    public boolean Prepare() {
        create();
        return contentView != null;
    }

    @Override
    public boolean IsShowing() {
        return isShowing();
    }

    @Override
    public void OnAdClicked() {
        if (contentView != null) contentView.CommitAdClick();
    }

    @Override
    public void Dismiss() {
        dismiss();
    }

    @Override
    public void Release() {
        setOnDismissListener(null);
        dismiss();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ConfigureWindowOrder(getWindow());
        if (contentView != null) contentView.OnPresented();
    }

    private void ConfigureWindow(
            Window window
          , int requestedPanelHeight) {
        if (window == null) return;

        window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
              , WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        ConfigureWindowOrder(window);
        window.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT));
        window.setWindowAnimations(0);
        if (fullscreen) {
            ConfigureFullscreenCutout(window);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT
                  , ViewGroup.LayoutParams.MATCH_PARENT);
            return;
        }

        window.setGravity(Gravity.BOTTOM);
        window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT
              , requestedPanelHeight);
        window.setDimAmount(0f);
        window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    private void ConfigureWindowOrder(Window window) {
        if (hostActivity == null
                || hostActivity.getWindow() == null) {
            return;
        }

        View hostDecorView =
                hostActivity.getWindow().getDecorView();
        IBinder hostWindowToken = hostDecorView.getWindowToken();
        if (hostWindowToken == null) {
            hostWindowToken = hostDecorView.getApplicationWindowToken();
        }
        if (hostWindowToken == null) return;

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.token = hostWindowToken;
        attributes.type = fullscreen
                ? FULLSCREEN_WINDOW_TYPE
                : NON_FULLSCREEN_WINDOW_TYPE;
        window.setAttributes(attributes);
    }

    private void ConfigureFullscreenCutout(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30Impl.ConfigureFullscreenInsets(window);
        } else {
            ConfigureLegacyFullscreenInsets(window);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);
    }

    @SuppressWarnings("deprecation")
    private static void ConfigureLegacyFullscreenInsets(Window window) {
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private static final class Api30Impl {
        private Api30Impl() {}

        static void ConfigureFullscreenInsets(Window window) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            int fitInsetTypes =
                    WindowInsets.Type.systemBars()
                            & ~WindowInsets.Type.statusBars();
            attributes.setFitInsetsTypes(fitInsetTypes);
            window.setAttributes(attributes);
        }
    }
}
