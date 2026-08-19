using UnityEngine;
using UnityEngine.Android;

namespace RiseOn.NativeAdMob.Android {
    internal sealed class NativeCoverPlatform : INativeCoverPlatform {
        private const string JAVA_CLASS_NAME = "com.riseon.nativeadmob.NativeCover";

        private readonly object javaStateLock = new();

        private AndroidJavaClass javaClass;

        // The full-screen cover is an Activity, so the system pauses Unity
        // behind it without anyone asking - the same pause the full-screen ad
        // Activity gets. Nothing on this side calls UnityPlayer.pause.
        public void ShowFullScreen(int argb) {
            Call("ShowFullScreen", AndroidApplication.currentActivity, argb);
        }

        public void HideFullScreen() => Call("HideFullScreen");

        public void ShowHalfScreen(int argb, float heightRatio) {
            Call(
                "ShowHalfScreen"
              , AndroidApplication.currentActivity
              , argb
              , heightRatio);
        }

        public void HideHalfScreen() => Call("HideHalfScreen");

        private void Call(string methodName, params object[] parameters) {
            lock (javaStateLock) {
                javaClass ??= new(JAVA_CLASS_NAME);
                javaClass.CallStatic(methodName, parameters);
            }
        }
    }
}
