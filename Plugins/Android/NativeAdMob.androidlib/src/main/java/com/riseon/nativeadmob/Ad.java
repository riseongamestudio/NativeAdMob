package com.riseon.nativeadmob;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAdOptions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

// Bridge chung: main-thread dispatch, listener, load generation va request
// options. Lifecycle show/hide cua tung format nam trong subclass.
public abstract class Ad {
    protected static final String TAG = "NativeAdMob";
    protected static final int INTERNAL_LOAD_ERROR = -1;
    protected static final int INTERNAL_PRESENTATION_ERROR = -2;
    private static final double MICROS_PER_CURRENCY_UNIT = 1_000_000.0d;

    protected final AtomicInteger loadGeneration = new AtomicInteger();
    protected final Handler main = new Handler(Looper.getMainLooper());

    protected volatile boolean released;

    private volatile AdLoadListener loadListener;

    protected Ad() {}

    public final void SetListener(AdLoadListener listener) {
        if (released) return;
        loadListener = listener;
        OnLoadListenerAttached();
    }

    protected void OnLoadListenerAttached() {}

    protected final int NextLoadGeneration() {
        return loadGeneration.incrementAndGet();
    }

    protected final void InvalidateLoadGeneration() {
        loadGeneration.incrementAndGet();
    }

    protected final boolean IsCurrentLoadGeneration(int generation) {
        return !released && generation == loadGeneration.get();
    }

    protected final void ClearLoadListener() {
        loadListener = null;
    }

    protected final NativeAdOptions CreateNativeAdOptions(
            boolean startMuted) {
        VideoOptions videoOptions = new VideoOptions.Builder()
                .setStartMuted(startMuted)
                .build();
        return new NativeAdOptions.Builder()
                .setMediaAspectRatio(
                        NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                .setAdChoicesPlacement(
                        NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .setVideoOptions(videoOptions)
                .build();
    }

    protected final void BindPaidEvent(
            com.google.android.gms.ads.nativead.NativeAd ad
          , String paidAdUnitId
          , BooleanSupplier isCurrentAd) {
        ad.setOnPaidEventListener(adValue -> {
            if (released
                    || isCurrentAd == null
                    || !isCurrentAd.getAsBoolean()) {
                return;
            }

            String source = "";
            ResponseInfo responseInfo = ad.getResponseInfo();
            if (responseInfo != null
                    && responseInfo.getMediationAdapterClassName() != null) {
                source = responseInfo.getMediationAdapterClassName();
            }
            NotifyAdPaid(
                    source
                  , paidAdUnitId
                  , adValue.getValueMicros() / MICROS_PER_CURRENCY_UNIT
                  , adValue.getCurrencyCode());
        });
    }

    protected static boolean IsActivityUsable(Activity activity) {
        return activity != null
                && !activity.isFinishing()
                && !activity.isDestroyed();
    }

    protected final void RunOnMainThread(Runnable action) {
        if (action == null) return;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            main.post(action);
        }
    }

    protected final void NotifyLoadingStarted() {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnLoadingStarted();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnLoadingStarted callback failed", exception);
        }
    }

    protected final void NotifyStateChanged(
            boolean isReady
          , boolean isLoading) {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnStateChanged(isReady, isLoading);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnStateChanged callback failed", exception);
        }
    }

    protected final void NotifyShowNotReady() {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnShowNotReady();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnShowNotReady callback failed", exception);
        }
    }

    protected final void NotifyLoadingCompleted(
            int errorCode
          , String errorMessage) {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnLoadingCompleted(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnLoadingCompleted callback failed", exception);
        }
    }

    protected final void NotifyAdPaid(
            String source
          , String paidAdUnitId
          , double value
          , String currencyCode) {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnAdPaid(
                    source
                  , paidAdUnitId
                  , value
                  , currencyCode);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnAdPaid callback failed", exception);
        }
    }

    protected final void NotifyDisplayed() {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnDisplayed();
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnDisplayed callback failed", exception);
        }
    }

    protected final void NotifyPresentationFailed(
            int errorCode
          , String errorMessage) {
        AdLoadListener listener = loadListener;
        if (listener == null) return;
        try {
            listener.OnPresentationFailed(errorCode, errorMessage);
        } catch (RuntimeException exception) {
            Log.e(TAG, "OnPresentationFailed callback failed", exception);
        }
    }
}
