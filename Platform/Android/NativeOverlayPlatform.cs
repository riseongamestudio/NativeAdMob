using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class NativeOverlayPlatform : INativeOverlayPlatform {
        private const string JAVA_CLASS_NAME = "com.riseon.nativeadmob.NativeOverlay";

        private readonly object javaStateLock = new();

        private AndroidJavaClass javaClass;

        // The full-screen cover is an Activity, so the system pauses Unity
        // behind it without anyone asking - the same pause the full-screen ad
        // Activity gets. Nothing on this side calls UnityPlayer.pause.
        public void ShowFullScreen(int argb) {
            Call("ShowFullScreen", AndroidApplication.currentActivity, argb);
        }

        public void HideFullScreen() => Call("HideFullScreen");

        public void SetFullScreenColor(int argb) => Call("SetFullScreenColor", argb);

        public void ShowHalfScreen(int argb, float heightRatio) {
            Call(
                "ShowHalfScreen"
              , AndroidApplication.currentActivity
              , argb
              , heightRatio);
        }

        public void HideHalfScreen() => Call("HideHalfScreen");

        public void SetHalfScreenColor(int argb)
            => Call("SetHalfScreenColor", argb);

        private void Call(string methodName, params object[] parameters) {
            lock (javaStateLock) {
                javaClass ??= new(JAVA_CLASS_NAME);
                javaClass.CallStatic(methodName, parameters);
            }
        }
    }
}
