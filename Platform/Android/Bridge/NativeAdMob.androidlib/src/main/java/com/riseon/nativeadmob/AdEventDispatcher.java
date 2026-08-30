package com.riseon.nativeadmob;

import android.os.Handler;
import android.os.HandlerThread;

// Every listener this pack calls is a C# object behind an AndroidJavaProxy,
// so invoking one crosses into the IL2CPP runtime - and entering that
// runtime can block for as long as the player stays paused: the GC's
// stop-the-world handshake cannot finish while Unity sleeps under a cover
// or an ad. Measured in production, 2026-08: five Play Console ANR
// clusters, each one a photograph of the MAIN thread parked at a GC
// suspend point inside OnAdCompleted or OnStateChanged ("Input dispatching
// timed out"). The main thread must never be the one standing in that
// door. Events are handed over here instead: the caller enqueues and
// returns at once, this thread is the one that crosses into C# and absorbs
// any stall, and the single queue keeps every event in the order it was
// posted. The stall itself is not fixable from this layer; who it is
// allowed to stall is.
final class AdEventDispatcher {
    private static final Object LOCK = new Object();
    private static Handler handler;

    private AdEventDispatcher() {}

    static void Post(Runnable event) {
        if (event == null) return;
        Handler current;
        synchronized (LOCK) {
            if (handler == null) {
                HandlerThread thread =
                        new HandlerThread("RiseOnNativeAdMobEvents");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            current = handler;
        }
        current.post(event);
    }
}
