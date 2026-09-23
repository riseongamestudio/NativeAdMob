using UnityEngine;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// A flat color over the whole screen, drawn by the platform above everything Unity draws, with the game PAUSED behind it: rendering stops and audio stops.<br/>
    /// Android gets that from the cover being an Activity; iOS asks for it.<br/>
    /// <para><b>Whatever takes this down must not need Unity's player loop.<br/>
    /// </b> A paused Unity runs no C#, and <see cref="Hide"/> is a C# call - so a hide routed through the player loop (Update, a coroutine, UniTask.Post, an SDK callback marshalled to the main thread) never arrives, and the screen stays black for good with back deliberately swallowed.<br/>
    /// Only a force-stop clears it.<br/>
    /// The call itself is fine from any thread; it is the SCHEDULING that must stay off the loop.<br/>
    /// In this game that is AdMaxProvider's onCompletedAnyThread callbacks - and every path that ends a show has to carry one, the display-failed ones included.</para><br/>
    /// <para>A full-screen ad opened on top raises its own pause request anyway - the two are OR'd, so the second ask changes nothing while this cover is up, and the game still stops if the cover comes down first.</para><br/>
    /// <para>For a cover with no pause and no such rule, use <see cref="HalfScreenCover"/> - at a ratio of 1 for the whole screen.<br/>
    /// It is a window rather than an Activity, so Unity keeps running and Hide is always reachable.</para><br/>
    /// <para>Static because a game has one of these.</para>
    /// </summary>
    public static class FullScreenCover {
        /// <summary>Black, the color asked for when none is named.</summary>
        public static void Show() {
            Show(Color.black);
        }

        public static void Show(Color color) {
            var platform = CoverTransport.Platform;
            if (platform == null) return;

            platform.ShowFullScreen(AdColor.Pack(color));
        }

        public static void Hide() {
            var platform = CoverTransport.Platform;
            if (platform == null) return;

            platform.HideFullScreen();
        }
    }
}
