using UnityEngine;

namespace RiseOn.NativeAdMob {
    // What the two covers share, and nothing more. It is not a base class:
    // there is exactly one full-screen cover and exactly one half-screen
    // cover in a game, so both are static and neither has an instance to
    // inherit anything.
    internal static class OverlayTransport {
        private static bool missingPlatformLogged;

        // One registry for the whole pack: every platform assembly - Android,
        // iOS and the Editor preview - installs a single object that answers
        // for ads and for covers alike. So this only comes back empty when
        // something is wrong with the build, never because a target simply
        // has no covers: a target that does not draw them says so itself and
        // is still installed.
        internal static INativeOverlayPlatform Platform {
            get {
                var installed = AdPlatformRegistry.Installed;
                if (installed is INativeOverlayPlatform cover) return cover;

                LogMissingPlatform(installed);
                return null;
            }
        }

        // Errors, not warnings, for the reason above. Nothing installed means
        // no bootstrap ran and no ad works either; installed without the
        // interface means a platform assembly shipped missing a whole
        // feature. Both are bugs to fix, not conditions to live with. Said
        // once, then quiet.
        private static void LogMissingPlatform(IAdPlatform installed) {
            if (missingPlatformLogged) return;

            missingPlatformLogged = true;
            Debug.LogError(
                installed is null
                    ? "No ad platform is installed on "
                      + $"{Application.platform}; covers and ads alike do "
                      + "nothing. The platform assembly's bootstrap did not "
                      + "run."
                    : $"{installed.GetType().Name} does not implement "
                      + $"{nameof(INativeOverlayPlatform)}; covers do nothing "
                      + $"on {Application.platform}.");
        }
    }
}
