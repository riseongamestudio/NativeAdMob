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
import com.google.android.gms.ads.nativead.NativeAd;

public final class OverlayAdPresentation
        extends Dialog
        implements NativeAdPresentation {
    // Both the same layer, and it has to stay that way.
    //
    // Android orders windows by TYPE first and by the order they were added
    // second; there is no z-order API and no insert-at-index. The half-screen
    // cover is TYPE_APPLICATION_SUB_PANEL too, so raising it while this ad is
    // already up puts the cover ON TOP of the ad, where iOS keeps the ad on
    // top. The order between those two is a CALLER CONTRACT here: raise the
    // cover first, open the ad second. That order is correct on both
    // platforms, and it is the order the game uses.
    //
    // TYPE_APPLICATION_ATTACHED_DIALOG (1003) was tried as a way to make the
    // ladder hold by itself, on the assumption that a bigger type number
    // draws higher. IT DOES NOT. Measured with `adb shell dumpsys window
    // windows`, which lists top first:
    //
    //     ty=APPLICATION_SUB_PANEL          <- cover
    //     ty=APPLICATION_ATTACHED_DIALOG    <- ad, buried
    //     ty=APPLICATION_PANEL              <- in-feed
    //     ty=BASE_APPLICATION               <- Unity
    //
    // The collapsible showed as a black band with nothing in it. Sub-window
    // types are mapped to layers inside WindowManagerService and the mapping
    // is not the numeric order; the only type that sorts above SUB_PANEL is
    // ABOVE_SUB_PANEL (1005), which is @hide and cannot be named. So this is
    // the top rung available, and the contract carries the rest.
    private static final int NON_FULLSCREEN_WINDOW_TYPE =
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
    private static final int FULLSCREEN_WINDOW_TYPE =
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;

    private final Activity hostActivity;
    private final NativeAd nativeAd;
    private final int countDownSec;
    private final boolean closeOnLeft;
    private final boolean timerOnLeft;
    private final boolean fakeCloseAutoDismiss;
    private final boolean fullscreen;
    private final float heightRatio;
    private final int backgroundColor;
    private OverlayAdContentView contentView;

    public OverlayAdPresentation(
            Context context
          , NativeAd nativeAd
          , int countDownSec
          , boolean closeOnLeft
          , boolean timerOnLeft
          , boolean fullscreen
          , float heightRatio
          , int backgroundColor
          , boolean fakeCloseAutoDismiss) {
        super(
                context
              , android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.hostActivity =
                context instanceof Activity
                        ? (Activity) context
                        : null;
        this.nativeAd = nativeAd;
        this.countDownSec = countDownSec;
        this.closeOnLeft = closeOnLeft;
        this.timerOnLeft = timerOnLeft;
        this.fakeCloseAutoDismiss = fakeCloseAutoDismiss;
        this.fullscreen = fullscreen;
        this.heightRatio = heightRatio;
        this.backgroundColor = backgroundColor;
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
              , Math.max(0, countDownSec) * 1000L
              , closeOnLeft
              , timerOnLeft
              , fullscreen
              , backgroundColor
              , fakeCloseAutoDismiss
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
