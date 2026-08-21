package com.riseon.nativeadmob;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.NativeAd;

import java.util.ArrayList;

final class InFeedAdPresentation extends FrameLayout
        implements NativeAdPresentation {
    private static final String TAG = "InFeedAd";
    private static final int MIN_STABLE_LAYOUT_PASSES = 3;
    private static final long MIN_FINAL_LAYOUT_OBSERVATION_MS = 80L;
    private static final long MAX_FINAL_LAYOUT_OBSERVATION_MS = 500L;
    private static final long ROOT_READY_RECHECK_DELAY_MS = 50L;
    private static final long MAX_ROOT_WAIT_MS = 10_000L;


    private final Activity activity;
    private final NativeAd nativeAd;
    private int requestedX;
    private int requestedY;
    private final int requestedWidth;
    private final int requestedHeight;
    private final int backgroundColor;
    private final NativeAdPresentation.Listener listener;
    private final InFeedAdViewFactory viewFactory;
    private final InFeedAdLayoutValidator validator;
    private final InFeedAdLayoutEngine layoutEngine;
    private final ArrayList<InFeedAdLayoutEngine.LayoutPlan>
            layoutPlans = new ArrayList<>();

    private NativeAdView nativeAdView;
    private InFeedAdViewFactory.AssetViews boundViews;
    private InFeedAdLayoutEngine.LayoutPlan activePlan;
    private Drawable mainImage;
    private int nextLayoutPlanIndex;
    private String lastLayoutFailure;
    private long finalLayoutObservationStartedAtMs;
    private ViewGroup contentRoot;
    private Dialog hostDialog;
    private View.OnLayoutChangeListener rootLayoutListener;
    private View.OnLayoutChangeListener rootReadyLayoutListener;
    private View.OnAttachStateChangeListener rootAttachStateListener;
    private ViewTreeObserver.OnPreDrawListener rootReadyPreDrawListener;
    // The observer the listener above went onto, kept because it cannot be
    // asked for again: getViewTreeObserver() hands back the window's while
    // the view is attached and a fresh floating one after it detaches, and
    // removing from the wrong instance is a silent no-op that leaves this
    // presentation - and its ad, view tree and Activity - alive on the
    // window's callback list for the rest of its life.
    private ViewTreeObserver rootReadyObserver;
    private final Runnable rootReadyRecheckRunnable =
            this::CheckContentRootReady;
    private boolean waitingForRoot;
    private long rootWaitStartedAtMs;
    private boolean visibleRequested = true;
    private boolean layoutReady;
    private boolean readyNotified;
    private boolean displayedNotified;
    private boolean displayNotificationPending;
    private boolean actualVisibilityKnown;
    private boolean lastActualVisibility;
    private boolean dismissed;
    private boolean clickCommitted;
    private String failureMessage;

    InFeedAdPresentation(
            Activity activity
          , NativeAd nativeAd
          , int xPx
          , int yPx
          , int widthPx
          , int heightPx
          , int backgroundColor
          , NativeAdPresentation.Listener listener) {
        super(activity);
        this.activity = activity;
        this.nativeAd = nativeAd;
        this.requestedX = xPx;
        this.requestedY = yPx;
        this.requestedWidth = widthPx;
        this.requestedHeight = heightPx;
        this.backgroundColor = backgroundColor;
        this.listener = listener;

        float density =
                activity.getResources().getDisplayMetrics().density;
        viewFactory = new InFeedAdViewFactory(
                activity
              , nativeAd
              , density
              , Math.min(widthPx, heightPx));
        validator = new InFeedAdLayoutValidator(
                nativeAd
              , density
              , viewFactory);
        layoutEngine = new InFeedAdLayoutEngine(
                nativeAd
              , xPx
              , yPx
              , widthPx
              , heightPx
              , viewFactory
              , validator);

        setClickable(false);
        setFocusable(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    // The same latch SingleClickNativeAdContainer uses on the full-screen ad:
    // once a click is committed every further touch is swallowed until the ad
    // comes back into view. It releases on window visibility rather than window
    // focus, because this presentation lives in a FLAG_NOT_FOCUSABLE dialog that
    // never takes focus and so would never be released by the full-screen test.
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (clickCommitted) return true;

        return super.dispatchTouchEvent(event);
    }

    void CommitAdClick() {
        clickCommitted = true;
    }

    @Override
    public boolean Show() {
        if (dismissed) {
            return Fail("In-feed Show rejected because presentation is dismissed");
        }
        if (nativeAd == null) {
            return Fail("In-feed Show rejected because nativeAd is null");
        }
        if (activity.isFinishing()) {
            return Fail("In-feed Show rejected because Activity is finishing");
        }
        if (waitingForRoot) return true;

        contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot == null) {
            return Fail("In-feed cannot find android.R.id.content");
        }
        ObserveContentRootDetach();
        if (!IsContentRootReady()) {
            WaitForContentRootReady();
            return true;
        }

        int screenWidth = contentRoot.getWidth();
        int screenHeight = contentRoot.getHeight();
        if (TextUtils.isEmpty(nativeAd.getHeadline())
                || TextUtils.isEmpty(nativeAd.getCallToAction())) {
            return Fail(
                    "In-feed creative is missing required headline or CTA");
        }

        if (viewFactory.HasUnrenderableIcon()) {
            return Fail(
                    "In-feed creative supplies an icon without a drawable; "
                            + "the required icon cannot be rendered safely");
        }
        mainImage = viewFactory.FindMainImage();
        boolean hasVideo = viewFactory.HasVideoContent();
        boolean hasMainImage = viewFactory.CanRenderMainImage(mainImage);
        layoutPlans.clear();
        layoutPlans.addAll(
                layoutEngine.ChoosePlans(
                        screenWidth
                      , screenHeight
                      , hasVideo
                      , hasMainImage));
        nextLayoutPlanIndex = 0;
        lastLayoutFailure = null;
        if (layoutPlans.isEmpty()) {
            String rejectionSummary =
                    layoutEngine.DescribeLastRejections();
            return Fail(
                    "In-feed cannot build a policy-safe layout for "
                            + DescribeRequestedRect()
                            + "; hasVideo=" + hasVideo
                            + "; hasMainImage=" + hasMainImage
                            + (TextUtils.isEmpty(rejectionSummary)
                                    ? ""
                                    : "; rejected={"
                                            + rejectionSummary + "}"));
        }

        if (!ActivateNextLayoutPlan()) {
            return Fail(
                    "In-feed could not bind any policy-safe layout for "
                            + DescribeRequestedRect()
                            + DescribeLastLayoutFailure());
        }

        setVisibility(INVISIBLE);
        if (!CreateAndShowHostDialog()) {
            return Fail("In-feed could not create its presentation window");
        }
        finalLayoutObservationStartedAtMs =
                SystemClock.uptimeMillis();
        ObserveContentRootResize();
        ObserveFinalAssetGeometry();
        return true;
    }

    @Override
    public boolean IsShowing() {
        return !dismissed
                && (waitingForRoot
                        || hostDialog != null && hostDialog.isShowing());
    }

    @Override
    public void Dismiss() {
        Dismiss(true);
    }

    @Override
    public void Release() {
        Dismiss(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotifyActualVisibilityIfChanged();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) clickCommitted = false;
        NotifyActualVisibilityIfChanged();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        NotifyActualVisibilityIfChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotifyActualVisibilityIfChanged();
        if (!dismissed) Dismiss(true, false);
    }

    private void WaitForContentRootReady() {
        if (waitingForRoot || contentRoot == null) return;

        waitingForRoot = true;
        rootWaitStartedAtMs = SystemClock.uptimeMillis();
        rootReadyPreDrawListener = () -> {
            CheckContentRootReady();
            return true;
        };
        rootReadyLayoutListener =
                (view
               , left
               , top
               , right
               , bottom
               , oldLeft
               , oldTop
               , oldRight
               , oldBottom) -> CheckContentRootReady();
        contentRoot.addOnLayoutChangeListener(rootReadyLayoutListener);

        ViewTreeObserver observer = contentRoot.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(rootReadyPreDrawListener);
            rootReadyObserver = observer;
        }
        ScheduleRootReadyRecheck(0L);
    }

    private void CheckContentRootReady() {
        if (dismissed || !waitingForRoot) {
            RemoveRootReadyObserver();
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            RecordFailure(
                    "In-feed content root never became ready because "
                            + "Activity is finishing or destroyed");
            Dismiss(true);
            return;
        }
        if (!IsContentRootReady()) {
            // Bounded so a root that never settles ends as a normal dismissal
            // instead of parking the slot and blocking every later load.
            if (SystemClock.uptimeMillis() - rootWaitStartedAtMs
                    >= MAX_ROOT_WAIT_MS) {
                RecordFailure(
                        "In-feed content root did not become ready within "
                                + MAX_ROOT_WAIT_MS + "ms");
                Dismiss(true);
                return;
            }
            ScheduleRootReadyRecheck(ROOT_READY_RECHECK_DELAY_MS);
            return;
        }

        waitingForRoot = false;
        RemoveRootReadyObserver();
        if (!Show()) Dismiss(true);
    }

    private void ScheduleRootReadyRecheck(long delayMs) {
        if (dismissed || !waitingForRoot || contentRoot == null) return;

        contentRoot.removeCallbacks(rootReadyRecheckRunnable);
        contentRoot.postDelayed(
                rootReadyRecheckRunnable
              , Math.max(0L, delayMs));
    }

    private boolean IsContentRootReady() {
        return contentRoot != null
                && contentRoot.isAttachedToWindow()
                && contentRoot.getWidth() > 0
                && contentRoot.getHeight() > 0
                && ResolveHostWindowToken() != null;
    }

    private void RemoveRootReadyObserver() {
        if (contentRoot != null) {
            contentRoot.removeCallbacks(rootReadyRecheckRunnable);
            if (rootReadyLayoutListener != null) {
                contentRoot.removeOnLayoutChangeListener(
                        rootReadyLayoutListener);
            }
        }
        if (rootReadyObserver != null
                && rootReadyPreDrawListener != null
                && rootReadyObserver.isAlive()) {
            rootReadyObserver.removeOnPreDrawListener(
                    rootReadyPreDrawListener);
        }
        rootReadyObserver = null;
        rootReadyLayoutListener = null;
        rootReadyPreDrawListener = null;
    }

    private void ObserveContentRootResize() {
        rootLayoutListener =
                (view
               , left
               , top
               , right
               , bottom
               , oldLeft
               , oldTop
               , oldRight
               , oldBottom) -> {
                    if (dismissed
                            || right - left <= 0
                            || bottom - top <= 0) {
                        return;
                    }
                    if (!FitHostInsideContentRoot()) {
                        RecordFailure(
                                "In-feed active layout no longer fits after "
                                        + "content surface resize; "
                                        + DescribeRequestedRect());
                        Dismiss(true);
                    }
                };
        contentRoot.addOnLayoutChangeListener(rootLayoutListener);
    }

    private boolean ActivateNextLayoutPlan() {
        DestroyNativeAdView();
        while (nextLayoutPlanIndex < layoutPlans.size()) {
            InFeedAdLayoutEngine.LayoutPlan plan =
                    layoutPlans.get(nextLayoutPlanIndex);
            ++nextLayoutPlanIndex;
            try {
                InFeedAdViewFactory.NativeAdViewResult viewResult =
                        viewFactory.BuildNativeAdView(plan, mainImage);
                nativeAdView = viewResult.nativeAdView;
                boundViews = viewResult.assetViews;
                activePlan = plan;
                ConfigureBackground();
                addView(
                        nativeAdView
                      , new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT
                          , ViewGroup.LayoutParams.MATCH_PARENT));
                if (getParent() != null
                        && !UpdateHostLayoutForActivePlan()) {
                    lastLayoutFailure =
                            "layout no longer fits content root: "
                                    + DescribePlan(plan);
                    DestroyNativeAdView();
                    continue;
                }
                return true;
            } catch (RuntimeException exception) {
                lastLayoutFailure =
                        "failed to bind " + DescribePlan(plan)
                                + ": " + exception.getMessage();
                Log.w(TAG, lastLayoutFailure, exception);
                DestroyNativeAdView();
            }
        }
        return false;
    }

    private void RejectActivePlanAndTryNext(String reason) {
        String rejectedPlan = DescribePlan(activePlan);
        lastLayoutFailure = reason + "; rejected=" + rejectedPlan;
        Log.w(TAG, lastLayoutFailure);
        layoutReady = false;
        setVisibility(INVISIBLE);

        if (ActivateNextLayoutPlan()) {
            Log.w(
                    TAG
                  , "In-feed trying fallback layout "
                            + DescribePlan(activePlan)
                            + " after " + rejectedPlan);
            ObserveFinalAssetGeometry();
            RequestObservationPass();
            return;
        }

        RecordFailure(
                "In-feed exhausted " + layoutPlans.size()
                        + " policy-safe layout candidate(s) for "
                        + DescribeRequestedRect()
                        + DescribeLastLayoutFailure());
        Dismiss(true);
    }

    private boolean CreateAndShowHostDialog() {
        if (hostDialog != null || activePlan == null) return false;

        Dialog createdDialog = new Dialog(
                activity
              , android.R.style.Theme_Translucent_NoTitleBar);
        createdDialog.setCancelable(false);
        createdDialog.setCanceledOnTouchOutside(false);
        hostDialog = createdDialog;

        Window window = createdDialog.getWindow();
        if (!ConfigureHostWindow(window)) {
            hostDialog = null;
            createdDialog.dismiss();
            return false;
        }

        createdDialog.setContentView(
                this
              , new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT
                      , ViewGroup.LayoutParams.MATCH_PARENT));
        createdDialog.setOnDismissListener(ignored -> {
            if (!dismissed) Dismiss(true, false);
        });
        createdDialog.show();
        return UpdateHostLayoutForActivePlan();
    }

    private boolean ConfigureHostWindow(Window window) {
        if (window == null
                || activity.getWindow() == null) {
            return false;
        }

        IBinder hostWindowToken = ResolveHostWindowToken();
        if (hostWindowToken == null) return false;

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.token = hostWindowToken;
        attributes.type =
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        window.setAttributes(attributes);
        window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
              , WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT));
        window.setWindowAnimations(0);
        return true;
    }

    private IBinder ResolveHostWindowToken() {
        if (activity.getWindow() == null) return null;

        View hostDecorView = activity.getWindow().getDecorView();
        IBinder hostWindowToken = hostDecorView.getWindowToken();
        return hostWindowToken != null
                ? hostWindowToken
                : hostDecorView.getApplicationWindowToken();
    }

    private boolean UpdateHostLayoutForActivePlan() {
        if (contentRoot == null
                || activePlan == null
                || hostDialog == null) {
            return false;
        }
        int rootWidth = contentRoot.getWidth();
        int rootHeight = contentRoot.getHeight();
        if (rootWidth <= 0
                || rootHeight <= 0
                || activePlan.width > rootWidth
                || activePlan.height > rootHeight) {
            return false;
        }

        int actualX = InFeedAdLayoutEngine.Clamp(
                requestedX
              , 0
              , rootWidth - activePlan.width);
        int actualY = InFeedAdLayoutEngine.Clamp(
                requestedY
              , 0
              , rootHeight - activePlan.height);

        Window window = hostDialog.getWindow();
        if (window == null) return false;
        int[] rootLocation = new int[2];
        contentRoot.getLocationInWindow(rootLocation);
        WindowManager.LayoutParams attributes = window.getAttributes();
        int targetGravity = Gravity.TOP | Gravity.LEFT;
        int targetX = rootLocation[0] + actualX;
        int targetY = rootLocation[1] + actualY;
        boolean layoutChanged =
                attributes.gravity != targetGravity
                        || attributes.x != targetX
                        || attributes.y != targetY
                        || attributes.width != activePlan.width
                        || attributes.height != activePlan.height;
        if (!layoutChanged) return true;

        attributes.gravity = targetGravity;
        attributes.x = targetX;
        attributes.y = targetY;
        attributes.width = activePlan.width;
        attributes.height = activePlan.height;
        window.setAttributes(attributes);
        LogAdjustmentIfNeeded(activePlan, actualX, actualY);
        return true;
    }

    private void DestroyNativeAdView() {
        if (nativeAdView != null) {
            removeView(nativeAdView);
            nativeAdView.destroy();
        }
        nativeAdView = null;
        boundViews = null;
        activePlan = null;
        layoutReady = false;
    }

    private void ObserveFinalAssetGeometry() {
        getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    private int stablePasses;
                    private int invalidStablePasses;
                    private long lastGeometrySignature = Long.MIN_VALUE;
                    private long lastInvalidGeometrySignature =
                            Long.MIN_VALUE;
                    private final long candidateObservationStartedAtMs =
                            SystemClock.uptimeMillis();

                    @Override
                    public boolean onPreDraw() {
                        ViewTreeObserver observer = getViewTreeObserver();
                        if (dismissed || getParent() == null) {
                            if (observer.isAlive()) {
                                observer.removeOnPreDrawListener(this);
                            }
                            if (!dismissed) Dismiss(true, false);
                            return true;
                        }

                        long nowMs = SystemClock.uptimeMillis();
                        long candidateObservationMs = nowMs
                                - candidateObservationStartedAtMs;
                        long totalObservationMs = nowMs
                                - finalLayoutObservationStartedAtMs;
                        boolean validationDeadlineReached =
                                totalObservationMs
                                        >= MAX_FINAL_LAYOUT_OBSERVATION_MS;
                        boolean hostFits = FitHostInsideContentRoot();
                        boolean valid = hostFits
                                && validator.ValidateAssetGeometry(
                                        nativeAdView
                                      , boundViews
                                      , activePlan
                                      , validationDeadlineReached);
                        if (!valid) {
                            long invalidGeometrySignature =
                                    validator.GeometrySignature(
                                            InFeedAdPresentation.this
                                          , nativeAdView
                                          , boundViews);
                            if (invalidGeometrySignature
                                    == lastInvalidGeometrySignature) {
                                ++invalidStablePasses;
                            } else {
                                lastInvalidGeometrySignature =
                                        invalidGeometrySignature;
                                invalidStablePasses = 1;
                            }
                            boolean deterministicFailure =
                                    invalidStablePasses
                                            >= MIN_STABLE_LAYOUT_PASSES;
                            boolean invalidObservationComplete =
                                    deterministicFailure
                                    || candidateObservationMs
                                            >= MIN_FINAL_LAYOUT_OBSERVATION_MS;
                            if (!invalidObservationComplete) {
                                stablePasses = 0;
                                lastGeometrySignature = Long.MIN_VALUE;
                                InFeedAdPresentation.this
                                        .RequestObservationPass();
                                return true;
                            }
                            if (observer.isAlive()) {
                                observer.removeOnPreDrawListener(this);
                            }
                            String validationReason =
                                    hostFits
                                            ? validator.GetLastFailureReason()
                                            : "layout host is outside "
                                                    + "content root";
                            RejectActivePlanAndTryNext(
                                    "In-feed rejected after final layout; "
                                            + "required asset is clipped, "
                                            + "empty, or overlapping"
                                            + (TextUtils.isEmpty(
                                                    validationReason)
                                                    ? ""
                                                    : ": "
                                                            + validationReason));
                            return true;
                        }
                        invalidStablePasses = 0;
                        lastInvalidGeometrySignature = Long.MIN_VALUE;

                        long geometrySignature =
                                validator.GeometrySignature(
                                        InFeedAdPresentation.this
                                      , nativeAdView
                                      , boundViews);
                        if (geometrySignature == lastGeometrySignature) {
                            ++stablePasses;
                        } else {
                            lastGeometrySignature = geometrySignature;
                            stablePasses = 1;
                        }
                        boolean observedLongEnough =
                                candidateObservationMs
                                        >= MIN_FINAL_LAYOUT_OBSERVATION_MS;
                        if (stablePasses < MIN_STABLE_LAYOUT_PASSES
                                || !observedLongEnough) {
                            boolean stabilizationDeadlineReached =
                                    candidateObservationMs
                                            >= MAX_FINAL_LAYOUT_OBSERVATION_MS
                                    || totalObservationMs
                                                    >= MAX_FINAL_LAYOUT_OBSERVATION_MS
                                            && observedLongEnough;
                            if (stabilizationDeadlineReached) {
                                if (observer.isAlive()) {
                                    observer.removeOnPreDrawListener(this);
                                }
                                RejectActivePlanAndTryNext(
                                        "In-feed rejected after final layout; "
                                                + "asset geometry did not "
                                                + "become stable");
                                return true;
                            }
                            InFeedAdPresentation.this
                                    .RequestObservationPass();
                            return true;
                        }
                        if (observer.isAlive()) {
                            observer.removeOnPreDrawListener(this);
                        }
                        layoutReady = true;
                        if (!readyNotified && listener != null) {
                            readyNotified = true;
                            listener.OnReady();
                        }
                        ApplyRequestedVisibility();
                        return true;
                    }
                });
    }

    boolean SetVisible(boolean visible) {
        if (dismissed) return false;

        visibleRequested = visible;
        ApplyRequestedVisibility();
        return layoutReady;
    }

    void RequestDisplayNotification() {
        if (dismissed) return;

        displayedNotified = false;
        ApplyRequestedVisibility();
    }

    boolean SetPosition(int xPx, int yPx) {
        if (dismissed) return false;

        requestedX = xPx;
        requestedY = yPx;
        layoutEngine.SetPosition(xPx, yPx);

        boolean fitted = FitHostInsideContentRoot();
        if (fitted) RequestObservationPass();
        return fitted;
    }

    String GetFailureMessage() {
        return failureMessage;
    }

    private void ApplyRequestedVisibility() {
        if (dismissed
                || !layoutReady
                || getParent() == null
                || hostDialog == null) {
            return;
        }

        if (!visibleRequested) {
            setVisibility(INVISIBLE);
            SetHostWindowTouchable(false);
            NotifyActualVisibilityIfChanged();
            return;
        }

        SetHostWindowTouchable(true);
        setVisibility(VISIBLE);
        NotifyActualVisibilityIfChanged();
        if (displayedNotified || displayNotificationPending) return;

        displayNotificationPending = true;
        post(() -> {
            displayNotificationPending = false;
            if (!dismissed
                    && visibleRequested
                    && isAttachedToWindow()
                    && getVisibility() == VISIBLE
                    && getParent() != null
                    && listener != null) {
                displayedNotified = true;
                listener.OnDisplayed();
            }
        });
    }

    private boolean IsActuallyVisible() {
        return !dismissed
                && layoutReady
                && visibleRequested
                && hostDialog != null
                && hostDialog.isShowing()
                && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE
                && isShown();
    }

    private void NotifyActualVisibilityIfChanged() {
        boolean isActuallyVisible = IsActuallyVisible();
        if (actualVisibilityKnown
                && lastActualVisibility == isActuallyVisible) {
            return;
        }

        actualVisibilityKnown = true;
        lastActualVisibility = isActuallyVisible;
        if (listener != null) {
            listener.OnActualVisibilityChanged(isActuallyVisible);
        }
    }

    private void SetHostWindowTouchable(boolean touchable) {
        if (hostDialog == null || hostDialog.getWindow() == null) return;
        if (touchable) {
            hostDialog.getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        } else {
            hostDialog.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
    }

    private void Dismiss(boolean notify) {
        Dismiss(notify, true);
    }

    private void Dismiss(boolean notify, boolean removeFromParent) {
        if (dismissed) return;
        dismissed = true;
        NotifyActualVisibilityIfChanged();
        waitingForRoot = false;
        layoutReady = false;
        visibleRequested = false;
        displayNotificationPending = false;

        if (contentRoot != null && rootLayoutListener != null) {
            contentRoot.removeOnLayoutChangeListener(rootLayoutListener);
        }
        if (contentRoot != null && rootAttachStateListener != null) {
            contentRoot.removeOnAttachStateChangeListener(
                    rootAttachStateListener);
        }
        RemoveRootReadyObserver();
        rootLayoutListener = null;
        rootAttachStateListener = null;
        Dialog currentDialog = hostDialog;
        hostDialog = null;
        if (removeFromParent && currentDialog != null) {
            currentDialog.setOnDismissListener(null);
            currentDialog.dismiss();
        } else if (removeFromParent) {
            ViewParentCompat.RemoveFromParent(this);
        }
        DestroyNativeAdView();
        layoutPlans.clear();
        layoutEngine.Clear();

        if (notify && listener != null) listener.OnDismissed();
    }

    private void ObserveContentRootDetach() {
        if (contentRoot == null || rootAttachStateListener != null) return;

        rootAttachStateListener =
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {
                        CheckContentRootReady();
                    }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        if (!dismissed) Dismiss(true);
                    }
                };
        contentRoot.addOnAttachStateChangeListener(
                rootAttachStateListener);
    }

    // The geometry loop only advances on draw passes, and Android skips
    // invalidate() on a view that is not VISIBLE - which this one is not until
    // its layout has been accepted. Drive the host window's decor view instead,
    // or the loop stalls until something unrelated happens to redraw the window.
    private void RequestObservationPass() {
        Window window = hostDialog != null ? hostDialog.getWindow() : null;
        if (window == null) {
            postInvalidateOnAnimation();
            return;
        }
        window.getDecorView().postInvalidateOnAnimation();
    }

    private boolean FitHostInsideContentRoot() {
        if (contentRoot == null
                || activePlan == null
                || hostDialog == null) {
            return false;
        }
        int rootWidth = contentRoot.getWidth();
        int rootHeight = contentRoot.getHeight();
        if (rootWidth <= 0
                || rootHeight <= 0
                || activePlan.width > rootWidth
                || activePlan.height > rootHeight) {
            return false;
        }
        return UpdateHostLayoutForActivePlan();
    }

    // The colour arrives whole from the caller. A fully transparent one is
    // a real answer here, not an unset value - a feed cell that wants no
    // backdrop of its own asks for exactly that.
    private void ConfigureBackground() {
        setBackgroundColor(backgroundColor);
    }

    private void LogAdjustmentIfNeeded(
            InFeedAdLayoutEngine.LayoutPlan plan
          , int actualX
          , int actualY) {
        if (actualX == requestedX
                && actualY == requestedY
                && plan.width == requestedWidth
                && plan.height == requestedHeight) {
            return;
        }
        Log.w(
                TAG
              , "In-feed adjusted: requested=["
                        + requestedX + "," + requestedY + ","
                        + requestedWidth + "," + requestedHeight
                        + "] actual=[" + actualX + "," + actualY + ","
                        + plan.width + "," + plan.height
                        + "] template="
                        + InFeedAdLayoutEngine.TemplateName(
                                plan.template)
                        + " tier="
                        + InFeedAdLayoutEngine.TierName(plan.tier)
                        + " media="
                        + InFeedAdLayoutEngine.MediaName(plan));
    }

    private boolean Fail(String message) {
        RecordFailure(message);
        return false;
    }

    private void RecordFailure(String message) {
        if (failureMessage != null) return;
        failureMessage = message;
        Log.e(TAG, message);
    }

    // Unity lays the slot out in resolution space and hands it over in pixels,
    // while every policy threshold here is expressed in dp. Report both so a log
    // says outright whether the slot clears a dp minimum on that device instead
    // of leaving it to be worked out from the density afterwards.
    private String DescribeRequestedRect() {
        float density =
                activity.getResources().getDisplayMetrics().density;
        return "requested rect ["
                + requestedX + "," + requestedY + ","
                + requestedWidth + "," + requestedHeight + "]px = ["
                + Math.round(requestedWidth / density) + ","
                + Math.round(requestedHeight / density) + "]dp"
                + " (density=" + density + ")";
    }

    private String DescribeLastLayoutFailure() {
        return TextUtils.isEmpty(lastLayoutFailure)
                ? ""
                : "; lastFailure=" + lastLayoutFailure;
    }

    private static String DescribePlan(
            InFeedAdLayoutEngine.LayoutPlan plan) {
        if (plan == null) return "none";
        return InFeedAdLayoutEngine.TemplateName(plan.template)
                + "/" + InFeedAdLayoutEngine.TierName(plan.tier)
                + "/" + InFeedAdLayoutEngine.MediaName(plan)
                + "/icon=" + plan.showIcon
                + "/body=" + plan.showBody
                + "/advertiser=" + plan.showAdvertiser
                + "/rating=" + plan.showRating
                + "/textx" + plan.textScale
                + "/marq=" + (plan.marqueeHeadline
                        ? "all"
                        : plan.marqueeSecondary ? "sec" : "none");
    }


    private static final class ViewParentCompat {
        static void RemoveFromParent(View view) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        }
    }
}
