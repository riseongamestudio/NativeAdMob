using System;
using System.Threading;
using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The preview's covers: one of each size, drawn on canvases stacked by
    // EditorSortingOrder so they land where the device puts them - the
    // full-screen cover over the game and under every full-screen ad, the
    // half-screen cover under its ad and over the in-feed cells.
    //
    // What the preview cannot test, it does not pretend to. A full-screen
    // cover on a device takes the game down with it, and a hide scheduled
    // through the player loop never arrives; here the loop keeps running and
    // every hide arrives. A cover that comes down cleanly in the Editor says
    // nothing about whether it comes down on a device.
    internal sealed class CoverPlatform : ICoverPlatform {
        private const string FULL_SCREEN_OBJECT_NAME = "Native Full Screen Cover";
        private const string HALF_SCREEN_OBJECT_NAME = "Native Half Screen Cover";
        private const float WHOLE_SCREEN_HEIGHT_RATIO = 1f;

        private static SynchronizationContext unityContext;
        private static int unityThreadId;

        private EditorCover fullScreen;
        private EditorCover halfScreen;

        // Captured before the first scene loads, on Unity's own thread, so a
        // call arriving from anywhere else can be handed back to it. Not at
        // SubsystemRegistration: that runs before Unity has installed its
        // synchronization context, and there would be nothing to capture.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.BeforeSceneLoad)]
        private static void CaptureUnityThread() {
            unityContext = SynchronizationContext.Current;
            unityThreadId = Thread.CurrentThread.ManagedThreadId;
        }

        public void ShowFullScreen(int argb) => OnUnityThread(() => {
            if (!fullScreen) {
                fullScreen = EditorCover.Create(
                    FULL_SCREEN_OBJECT_NAME
                  , EditorSortingOrder.FULL_SCREEN_COVER
                  , pausesGame: true);
            }
            fullScreen.ApplyFullScreen(AdColor.Unpack(argb));
        });

        public void HideFullScreen() => OnUnityThread(() => {
            if (fullScreen) fullScreen.Close();
            fullScreen = null;
        });

        public void ShowHalfScreen(int argb, float heightRatio)
            => OnUnityThread(() => {
                if (!halfScreen) {
                    halfScreen = EditorCover.Create(
                        HALF_SCREEN_OBJECT_NAME
                      , EditorSortingOrder.HALF_SCREEN_COVER
                      , pausesGame: false);
                }
                halfScreen.Apply(
                    AdColor.Unpack(argb)
                  , ResolveHeightRatio(heightRatio));
            });

        public void HideHalfScreen() => OnUnityThread(() => {
            if (halfScreen) halfScreen.Close();
            halfScreen = null;
        });

        // At or below 0, at or above 1, or not a number at all: the whole
        // screen, as HalfScreenCover documents and the device reads it.
        private static float ResolveHeightRatio(float heightRatio) {
            return float.IsFinite(heightRatio)
                && heightRatio > 0f
                && heightRatio < WHOLE_SCREEN_HEIGHT_RATIO
                    ? heightRatio
                    : WHOLE_SCREEN_HEIGHT_RATIO;
        }

        // The covers promise a call from any thread, and the game takes them
        // at their word: AdsManager hands Hide to the SDKs' any-thread
        // completion callbacks. Every Unity object here has to be touched on
        // Unity's thread, so anything else is posted back to it.
        private static void OnUnityThread(Action action) {
            if (unityContext == null
                    || Thread.CurrentThread.ManagedThreadId == unityThreadId) {
                action();
                return;
            }

            unityContext.Post(static state => ((Action)state)(), action);
        }
    }
}
