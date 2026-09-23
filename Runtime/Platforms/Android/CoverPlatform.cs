using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class CoverPlatform : ICoverPlatform {
        // One Java class per role, exactly as on this side.
        private const string FULL_SCREEN_CLASS =
                "com.riseon.nativeadmob.FullScreenCover";
        private const string HALF_SCREEN_CLASS =
                "com.riseon.nativeadmob.HalfScreenCover";

        private readonly object javaStateLock = new();

        private AndroidJavaClass fullScreenClass;
        private AndroidJavaClass halfScreenClass;

        // The full-screen cover is an Activity, so the system pauses Unity
        // behind it without anyone asking - the same pause the full-screen ad
        // Activity gets. Nothing on this side calls UnityPlayer.pause.
        public void ShowFullScreen(int argb) {
            CallFullScreen("Show", AndroidApplication.currentActivity, argb);
        }

        public void HideFullScreen() {
            CallFullScreen("Hide");
        }

        public void ShowHalfScreen(int argb, float heightRatio) {
            CallHalfScreen(
                "Show"
              , AndroidApplication.currentActivity
              , argb
              , heightRatio);
        }

        public void HideHalfScreen() {
            CallHalfScreen("Hide");
        }

        private void CallFullScreen(string methodName, params object[] parameters) {
            lock (javaStateLock) {
                fullScreenClass ??= new(FULL_SCREEN_CLASS);
                fullScreenClass.CallStatic(methodName, parameters);
            }
        }

        private void CallHalfScreen(string methodName, params object[] parameters) {
            lock (javaStateLock) {
                halfScreenClass ??= new(HALF_SCREEN_CLASS);
                halfScreenClass.CallStatic(methodName, parameters);
            }
        }
    }
}
