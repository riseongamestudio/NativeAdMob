using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The preview's stand-in for a paused Unity, shared by everything that
    // pauses: a full-screen ad and the full-screen cover.
    //
    // On a device the pause requests are OR'd - the game stays stopped while
    // any of them is up. Two owners each writing Time.timeScale would break
    // that: the end card closing first would restart the game under the
    // cover still standing. So the clock only restarts when the last holder
    // lets go, and it restarts at the scale it had before the first one
    // stopped it.
    //
    // Not a reproduction of the device's hazard. A paused Unity runs no C#;
    // an Editor at timeScale 0 still runs every Update and every UniTask
    // continuation. A hide that could never arrive on a device arrives here
    // just fine.
    internal static class EditorPause {
        private const float PAUSED_TIME_SCALE = 0f;
        private const float DEFAULT_TIME_SCALE = 1f;

        private static int holders;
        private static float resumeTimeScale = DEFAULT_TIME_SCALE;

        // Play mode can start without a domain reload, which would carry a
        // count over from the last session and leave the game stopped.
        [RuntimeInitializeOnLoadMethod(
            RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void ResetForPlayMode() {
            holders = 0;
            resumeTimeScale = DEFAULT_TIME_SCALE;
        }

        internal static void Acquire() {
            if (holders++ > 0) return;

            resumeTimeScale = Time.timeScale;
            Time.timeScale = PAUSED_TIME_SCALE;
        }

        internal static void Release() {
            if (holders <= 0) return;
            if (--holders > 0) return;

            Time.timeScale = resumeTimeScale;
        }
    }
}
