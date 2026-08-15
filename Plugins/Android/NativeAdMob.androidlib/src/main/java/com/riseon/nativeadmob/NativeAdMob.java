package com.riseon.nativeadmob;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAdOptions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

// Shared base of every ad format: main-thread dispatch, load generations,
// request options, and the paid-event binding. The show/hide lifecycle of
// each format lives in its subclass.
public abstract class NativeAdMob {
    protected static final String TAG = "NativeAdMob";
    protected static final int INTERNAL_LOAD_ERROR = -1;
    protected static final int INTERNAL_PRESENTATION_ERROR = -2;
    private static final double MICROS_PER_CURRENCY_UNIT = 1_000_000.0d;

    protected final AtomicInteger loadGeneration = new AtomicInteger();
    protected final Handler main = new Handler(Looper.getMainLooper());

    protected volatile boolean released;

    protected NativeAdMob() {}

    protected final int NextLoadGeneration() {
        return loadGeneration.incrementAndGet();
    }

    protected final void InvalidateLoadGeneration() {
        loadGeneration.incrementAndGet();
    }

    protected final boolean IsCurrentLoadGeneration(int generation) {
        return !released && generation == loadGeneration.get();
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
                  , adValue.getCurrencyCode()
                  , adValue.getPrecisionType());
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

    // Each format owns its listener - the two surfaces differ (the in-feed
    // one is slot-indexed) - but the paid event fires from inside
    // BindPaidEvent, so delivering it is the one duty the base demands.
    protected abstract void NotifyAdPaid(
            String source
          , String paidAdUnitId
          , double value
          , String currencyCode
          , int precision);
}
