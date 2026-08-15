package com.riseon.nativeadmob;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class FullScreenActivity extends Activity {
    private static final String TAG = "NativeAdMobFullScreen";
    private static final String EXTRA_SESSION_ID =
            "com.riseon.nativeadmob.extra.NATIVE_FULLSCREEN_SESSION_ID";
    private static final String SESSION_ID_PREFIX =
            "native-fullscreen-";
    private static final String PRESENTATION_FAILURE_PREFIX =
            "Failed to show ad: ";
    private static final int NO_TRANSITION_ANIMATION = 0;
    private static final long RESTORE_DELAY_MS = 180L;
    private static final long RESTORE_FOCUS_RETRY_MS = 100L;
    private static final long RESTORE_ATTACH_TIMEOUT_MS = 1200L;
    private static final int MAX_RESTORE_START_ATTEMPTS = 4;
    private static final Object SESSION_LOCK = new Object();
    private static final HashMap<String, Session> SESSIONS =
            new HashMap<>();
    private static final AtomicLong NEXT_SESSION_ID =
            new AtomicLong();
    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static Application lifecycleApplication;
    private static boolean lifecycleCallbacksRegistered;

    private String sessionId;
    private Session session;
    private FullScreenContentView contentView;
    private Object backInvokedCallback;
    private boolean presented;
    private boolean completionStarted;

    static String RegisterSession(
            Activity hostActivity
          , FullScreen owner
          , com.google.android.gms.ads.nativead.NativeAd nativeAd
          , FullScreen.FullScreenStyle style) {
        if (hostActivity == null || owner == null
                || nativeAd == null || style == null) {
            return null;
        }

        EnsureLifecycleCallbacks(hostActivity.getApplication());

        String createdSessionId =
                SESSION_ID_PREFIX + NEXT_SESSION_ID.incrementAndGet();
        Session createdSession = new Session(
                createdSessionId
              , hostActivity
              , owner
              , nativeAd
              , style);
        synchronized (SESSION_LOCK) {
            SESSIONS.put(createdSessionId, createdSession);
        }
        return createdSessionId;
    }

    static boolean StartSession(Activity hostActivity, String targetSessionId) {
        if (!IsActivityUsable(hostActivity) || targetSessionId == null) {
            return false;
        }
        synchronized (SESSION_LOCK) {
            Session targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null || targetSession.completed) return false;
        }

        return StartSessionIntent(hostActivity, targetSessionId);
    }

    static boolean IsSessionActive(String targetSessionId) {
        if (targetSessionId == null) return false;
        synchronized (SESSION_LOCK) {
            Session targetSession = SESSIONS.get(targetSessionId);
            return targetSession != null && !targetSession.completed;
        }
    }

    static void DismissSession(String targetSessionId) {
        RunOnMainThread(() -> {
            Session targetSession;
            synchronized (SESSION_LOCK) {
                targetSession = SESSIONS.get(targetSessionId);
            }
            if (targetSession == null) return;

            FullScreenActivity activity = targetSession.activity;
            if (activity != null) {
                activity.CompletePresentation("");
            } else {
                CompletePendingSession(targetSessionId, targetSession);
            }
        });
    }

    static void CancelSession(String targetSessionId) {
        RunOnMainThread(() -> {
            Session targetSession = RemoveSession(targetSessionId, null);
            if (targetSession == null) return;

            FullScreenActivity activity = targetSession.activity;
            if (activity != null) activity.CancelWithoutCallback();
        });
    }

    static void CommitAdClick(String targetSessionId) {
        RunOnMainThread(() -> {
            Session targetSession;
            synchronized (SESSION_LOCK) {
                targetSession = SESSIONS.get(targetSessionId);
            }
            if (targetSession != null && targetSession.activity != null) {
                targetSession.activity.CommitAdClick();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigureActivityTransitions(this);
        RegisterBackCallback();

        sessionId = getIntent() == null
                ? null
                : getIntent().getStringExtra(EXTRA_SESSION_ID);
        session = AttachActivity(sessionId, this);
        if (session == null) {
            FinishWithoutTransition();
            return;
        }

        try {
            Window window = getWindow();
            ConfigureWindow(window);

            boolean hasVideoContent =
                    session.nativeAd.getMediaContent() != null
                            && session.nativeAd.getMediaContent()
                                    .hasVideoContent();
            int requestedPanelHeight =
                    FullScreenContentView.ResolveInitialPanelHeight(
                            this
                          , true
                          , session.style.heightRatio
                          , hasVideoContent);
            contentView = new FullScreenContentView(
                    this
                  , session.nativeAd
                  , session.GetRemainingCountdownMs()
                  , session.closeOnLeft
                  , session.style.numberOppositeSide
                  , true
                  , session.style.backgroundAlpha
                  , requestedPanelHeight
                  , () -> CompletePresentation(""));
            setContentView(
                    contentView
                  , new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT
                          , ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (RuntimeException exception) {
            CompletePresentation(
                    PRESENTATION_FAILURE_PREFIX + exception.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (completionStarted || contentView == null || session == null) return;

        ConfigureSystemBars(getWindow());
        session.ResumeCountdown();
        contentView.OnPresented(session.GetRemainingCountdownMs());
        if (!presented) {
            presented = true;
            NotifyDisplayed(sessionId, session);
        }
    }

    @Override
    protected void onPause() {
        if (!completionStarted && session != null) {
            session.PauseCountdown();
            if (contentView != null) contentView.OnPaused();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (!completionStarted && session != null) {
            session.PauseCountdown();
        }
        ReleaseContentView();

        Session detachedSession = null;
        if (!completionStarted) {
            detachedSession = DetachActivity(sessionId, this);
        }

        UnregisterBackCallback();
        super.onDestroy();

        if (detachedSession != null) {
            Log.d(TAG, "Presentation Activity destroyed without explicit "
                    + "completion; preserving session " + sessionId);
            ScheduleSessionRestore(detachedSession, RESTORE_DELAY_MS);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
    }

    private void CompletePresentation(String errorMessage) {
        if (completionStarted) return;
        completionStarted = true;

        Session completedSession = RemoveSession(sessionId, this);
        ReleaseContentView();
        FinishWithoutTransition();
        if (completedSession != null) {
            completedSession.owner.OnActivityPresentationCompleted(
                    sessionId
                  , completedSession.nativeAd
                  , errorMessage == null ? "" : errorMessage);
        }
    }

    private void CancelWithoutCallback() {
        if (completionStarted) return;
        completionStarted = true;
        ReleaseContentView();
        FinishWithoutTransition();
    }

    private void CommitAdClick() {
        if (contentView != null) contentView.CommitAdClick();
    }

    private void ReleaseContentView() {
        FullScreenContentView currentContentView = contentView;
        contentView = null;
        if (currentContentView == null) return;

        if (currentContentView.getParent() instanceof ViewGroup) {
            ((ViewGroup) currentContentView.getParent())
                    .removeView(currentContentView);
        }
        currentContentView.Release();
    }

    private void FinishWithoutTransition() {
        if (!isFinishing()) finish();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ApplyLegacyPendingTransition(this);
        }
    }

    private static void ConfigureWindow(Window window) {
        if (window == null) return;

        window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
              , WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT));
        window.setWindowAnimations(0);
        window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT
              , ViewGroup.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams
                            .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
    }

    private void RegisterBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = Api33Impl.RegisterBackCallback(this);
        }
    }

    private void UnregisterBackCallback() {
        Object callback = backInvokedCallback;
        backInvokedCallback = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && callback != null) {
            Api33Impl.UnregisterBackCallback(this, callback);
        }
    }

    private static void ConfigureActivityTransitions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34Impl.ConfigureTransitions(activity);
        } else {
            ApplyLegacyPendingTransition(activity);
        }
    }

    @SuppressWarnings("deprecation")
    private static void ApplyLegacyPendingTransition(Activity activity) {
        activity.overridePendingTransition(
                NO_TRANSITION_ANIMATION
              , NO_TRANSITION_ANIMATION);
    }

    private static void ConfigureSystemBars(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30Impl.ConfigureSystemBars(window);
        } else {
            ConfigureLegacySystemBars(window);
        }
    }

    @SuppressWarnings("deprecation")
    private static void ConfigureLegacySystemBars(Window window) {
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private static boolean StartSessionIntent(
            Activity hostActivity
          , String targetSessionId) {
        try {
            Intent intent = new Intent(
                    hostActivity
                  , FullScreenActivity.class);
            intent.putExtra(EXTRA_SESSION_ID, targetSessionId);
            hostActivity.startActivity(intent);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ApplyLegacyPendingTransition(hostActivity);
            }
            return true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to start presentation Activity", exception);
            return false;
        }
    }

    private static Session AttachActivity(
            String targetSessionId
          , FullScreenActivity activity) {
        if (targetSessionId == null || activity == null) return null;
        synchronized (SESSION_LOCK) {
            Session targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null || targetSession.completed) return null;

            FullScreenActivity existingActivity =
                    targetSession.activity;
            if (existingActivity != null && existingActivity != activity) {
                boolean existingIsGone = existingActivity.isFinishing()
                        || Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                && existingActivity.isDestroyed();
                if (!existingIsGone) return null;
            }

            targetSession.activity = activity;
            targetSession.restorePending = false;
            targetSession.restoreScheduled = false;
            targetSession.restoreStartInFlight = false;
            targetSession.restoreStartAttempts = 0;
            return targetSession;
        }
    }

    private static Session DetachActivity(
            String targetSessionId
          , FullScreenActivity expectedActivity) {
        if (targetSessionId == null || expectedActivity == null) return null;
        synchronized (SESSION_LOCK) {
            Session targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null
                    || targetSession.completed
                    || targetSession.activity != expectedActivity) {
                return null;
            }

            targetSession.activity = null;
            targetSession.restorePending = true;
            targetSession.restoreStartInFlight = false;
            return targetSession;
        }
    }

    private static Session RemoveSession(
            String targetSessionId
          , FullScreenActivity expectedActivity) {
        if (targetSessionId == null) return null;
        synchronized (SESSION_LOCK) {
            Session targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null
                    || targetSession.completed
                    || expectedActivity != null
                            && targetSession.activity != expectedActivity) {
                return null;
            }
            targetSession.completed = true;
            targetSession.restorePending = false;
            targetSession.restoreScheduled = false;
            targetSession.restoreStartInFlight = false;
            SESSIONS.remove(targetSessionId);
            return targetSession;
        }
    }

    private static void CompletePendingSession(
            String targetSessionId
          , Session targetSession) {
        Session completedSession = RemoveSession(targetSessionId, null);
        if (completedSession == null || completedSession != targetSession) {
            return;
        }
        completedSession.owner.OnActivityPresentationCompleted(
                targetSessionId
              , completedSession.nativeAd
              , "");
    }

    private static void NotifyDisplayed(
            String targetSessionId
          , Session targetSession) {
        if (targetSessionId == null || targetSession == null) return;
        synchronized (SESSION_LOCK) {
            if (targetSession.completed
                    || targetSession.displayed
                    || SESSIONS.get(targetSessionId) != targetSession) {
                return;
            }
            targetSession.displayed = true;
        }
        targetSession.owner.OnActivityPresentationDisplayed(
                targetSessionId
              , targetSession.nativeAd);
    }

    private static void EnsureLifecycleCallbacks(Application application) {
        if (application == null) return;
        synchronized (SESSION_LOCK) {
            if (lifecycleCallbacksRegistered
                    && lifecycleApplication == application) {
                return;
            }

            if (lifecycleCallbacksRegistered
                    && lifecycleApplication != null) {
                try {
                    lifecycleApplication.unregisterActivityLifecycleCallbacks(
                            ACTIVITY_LIFECYCLE_CALLBACKS);
                } catch (RuntimeException ignored) {
                }
            }

            application.registerActivityLifecycleCallbacks(
                    ACTIVITY_LIFECYCLE_CALLBACKS);
            lifecycleApplication = application;
            lifecycleCallbacksRegistered = true;
        }
    }

    private static final Application.ActivityLifecycleCallbacks
            ACTIVITY_LIFECYCLE_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(
                        Activity activity
                      , Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    if (activity instanceof FullScreenActivity) return;

                    List<Session> sessionsToRestore = new ArrayList<>();
                    synchronized (SESSION_LOCK) {
                        for (Session targetSession : SESSIONS.values()) {
                            if (!targetSession.MatchesHost(activity)) continue;

                            targetSession.hostResumed = true;
                            targetSession.resumedHostActivity =
                                    new WeakReference<>(activity);
                            if (targetSession.restorePending
                                    && targetSession.activity == null) {
                                sessionsToRestore.add(targetSession);
                            }
                        }
                    }

                    for (Session targetSession : sessionsToRestore) {
                        ScheduleSessionRestore(
                                targetSession
                              , RESTORE_DELAY_MS);
                    }
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    if (activity instanceof FullScreenActivity) return;
                    synchronized (SESSION_LOCK) {
                        for (Session targetSession : SESSIONS.values()) {
                            if (!targetSession.MatchesHost(activity)) continue;

                            Activity rememberedActivity =
                                    targetSession.resumedHostActivity.get();
                            if (rememberedActivity == activity) {
                                targetSession.hostResumed = false;
                            }
                        }
                    }
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(
                        Activity activity
                      , Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    if (activity instanceof FullScreenActivity) return;
                    synchronized (SESSION_LOCK) {
                        for (Session targetSession : SESSIONS.values()) {
                            Activity rememberedActivity =
                                    targetSession.resumedHostActivity.get();
                            if (rememberedActivity == activity) {
                                targetSession.hostResumed = false;
                                targetSession.resumedHostActivity =
                                        new WeakReference<>(null);
                            }
                        }
                    }
                }
            };

    private static void ScheduleSessionRestore(
            Session targetSession
          , long delayMs) {
        if (targetSession == null) return;

        final String targetSessionId;
        synchronized (SESSION_LOCK) {
            if (targetSession.completed
                    || !targetSession.restorePending
                    || targetSession.activity != null
                    || !targetSession.hostResumed
                    || targetSession.restoreScheduled
                    || targetSession.restoreStartInFlight
                    || SESSIONS.get(targetSession.sessionId)
                            != targetSession) {
                return;
            }

            Activity hostActivity =
                    targetSession.resumedHostActivity.get();
            if (!IsActivityUsable(hostActivity)) return;

            targetSession.restoreScheduled = true;
            targetSessionId = targetSession.sessionId;
        }

        MAIN.postDelayed(
                () -> RestoreSessionIfPossible(targetSessionId)
              , Math.max(0L, delayMs));
    }

    private static void RestoreSessionIfPossible(String targetSessionId) {
        Session targetSession;
        Activity hostActivity;
        boolean waitForFocus = false;

        synchronized (SESSION_LOCK) {
            targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null) return;

            targetSession.restoreScheduled = false;
            if (targetSession.completed
                    || !targetSession.restorePending
                    || targetSession.activity != null
                    || !targetSession.hostResumed
                    || targetSession.restoreStartInFlight) {
                return;
            }

            hostActivity = targetSession.resumedHostActivity.get();
            if (!IsActivityUsable(hostActivity)) return;

            if (!hostActivity.hasWindowFocus()) {
                waitForFocus = true;
            } else {
                targetSession.restoreStartInFlight = true;
                ++targetSession.restoreStartAttempts;
            }
        }

        if (waitForFocus) {
            ScheduleSessionRestore(
                    targetSession
                  , RESTORE_FOCUS_RETRY_MS);
            return;
        }

        Log.d(TAG, "Restoring native full-screen session "
                + targetSessionId);
        boolean started = StartSessionIntent(hostActivity, targetSessionId);
        if (!started) {
            OnRestoreStartFailed(targetSessionId);
            return;
        }

        MAIN.postDelayed(
                () -> VerifyRestoreAttached(targetSessionId)
              , RESTORE_ATTACH_TIMEOUT_MS);
    }

    private static void OnRestoreStartFailed(String targetSessionId) {
        Session targetSession;
        synchronized (SESSION_LOCK) {
            targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null) return;
            targetSession.restoreStartInFlight = false;
        }
        RetryOrFailRestore(targetSession);
    }

    private static void VerifyRestoreAttached(String targetSessionId) {
        Session targetSession;
        synchronized (SESSION_LOCK) {
            targetSession = SESSIONS.get(targetSessionId);
            if (targetSession == null
                    || targetSession.completed
                    || targetSession.activity != null) {
                return;
            }
            targetSession.restoreStartInFlight = false;
        }
        RetryOrFailRestore(targetSession);
    }

    private static void RetryOrFailRestore(Session targetSession) {
        if (targetSession == null) return;

        boolean shouldFail;
        synchronized (SESSION_LOCK) {
            shouldFail = targetSession.restoreStartAttempts
                    >= MAX_RESTORE_START_ATTEMPTS;
        }

        if (!shouldFail) {
            ScheduleSessionRestore(
                    targetSession
                  , RESTORE_FOCUS_RETRY_MS);
            return;
        }

        Log.e(TAG, "Unable to restore native full-screen session "
                + targetSession.sessionId);
        Session completedSession = RemoveSession(
                targetSession.sessionId
              , null);
        if (completedSession != null) {
            completedSession.owner.OnActivityPresentationCompleted(
                    completedSession.sessionId
                  , completedSession.nativeAd
                  , "Failed to restore native full-screen presentation");
        }
    }

    private static boolean IsActivityUsable(Activity activity) {
        return activity != null
                && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                        || !activity.isDestroyed());
    }

    private static void RunOnMainThread(Runnable action) {
        if (action == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN.post(action);
        }
    }

    private static final class Api30Impl {
        private Api30Impl() {}

        static void ConfigureSystemBars(Window window) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) return;

            controller.setSystemBarsBehavior(
                    WindowInsetsController
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.systemBars());
        }
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
                            (OnBackInvokedCallback) callback);
        }
    }

    private static final class Api34Impl {
        private Api34Impl() {}

        static void ConfigureTransitions(Activity activity) {
            activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN
                  , NO_TRANSITION_ANIMATION
                  , NO_TRANSITION_ANIMATION);
            activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE
                  , NO_TRANSITION_ANIMATION
                  , NO_TRANSITION_ANIMATION);
        }
    }

    private static final class Session {
        final String sessionId;
        final FullScreen owner;
        final com.google.android.gms.ads.nativead.NativeAd nativeAd;
        final FullScreen.FullScreenStyle style;
        final String hostActivityClassName;
        final int hostTaskId;
        final boolean closeOnLeft;
        long countdownRemainingMs;
        long countdownStartedElapsedRealtime;
        boolean countdownRunning;

        WeakReference<Activity> resumedHostActivity;
        FullScreenActivity activity;
        boolean hostResumed;
        boolean displayed;
        boolean completed;
        boolean restorePending;
        boolean restoreScheduled;
        boolean restoreStartInFlight;
        int restoreStartAttempts;

        Session(
                String sessionId
              , Activity hostActivity
              , FullScreen owner
              , com.google.android.gms.ads.nativead.NativeAd nativeAd
              , FullScreen.FullScreenStyle style) {
            this.sessionId = sessionId;
            this.owner = owner;
            this.nativeAd = nativeAd;
            this.style = style;
            this.hostActivityClassName =
                    hostActivity.getClass().getName();
            this.hostTaskId = hostActivity.getTaskId();
            this.closeOnLeft =
                    style.xRandomSide && Math.random() < 0.5d;
            this.resumedHostActivity =
                    new WeakReference<>(hostActivity);
            this.hostResumed = true;
            this.countdownRemainingMs =
                    Math.max(0, style.countdownSec) * 1000L;
        }

        boolean MatchesHost(Activity candidate) {
            return candidate != null
                    && candidate.getTaskId() == hostTaskId
                    && hostActivityClassName.equals(
                            candidate.getClass().getName());
        }

        void ResumeCountdown() {
            synchronized (SESSION_LOCK) {
                if (completed || countdownRunning || countdownRemainingMs <= 0L) {
                    return;
                }
                countdownStartedElapsedRealtime = SystemClock.elapsedRealtime();
                countdownRunning = true;
            }
        }

        void PauseCountdown() {
            synchronized (SESSION_LOCK) {
                if (!countdownRunning) return;
                long elapsedMs = Math.max(
                        0L
                      , SystemClock.elapsedRealtime()
                                - countdownStartedElapsedRealtime);
                countdownRemainingMs = Math.max(
                        0L
                      , countdownRemainingMs - elapsedMs);
                countdownStartedElapsedRealtime = 0L;
                countdownRunning = false;
            }
        }

        long GetRemainingCountdownMs() {
            synchronized (SESSION_LOCK) {
                if (!countdownRunning) return countdownRemainingMs;
                long elapsedMs = Math.max(
                        0L
                      , SystemClock.elapsedRealtime()
                                - countdownStartedElapsedRealtime);
                return Math.max(0L, countdownRemainingMs - elapsedMs);
            }
        }
    }
}
