using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    internal sealed class EditorAdConfig {
        private const float FULL_SCREEN_DEFAULT_ALPHA = 0.8f;
        private const float COLLAPSIBLE_DEFAULT_ALPHA = 0.95f;
        private const float IN_FEED_DEFAULT_ALPHA     = 1f;
        private const float DEFAULT_HEIGHT_RATIO      = 0.5f;

        internal string                    AdUnitId          { get; }
        internal EditorAdMode Mode              { get; }
        internal bool                      PausesGame        { get; }
        internal bool                      RandomCloseSide   { get; }
        internal bool                      NumberOpposite    { get; }
        internal float                     HeightRatio       { get; }
        internal float                     BackgroundAlpha   { get; }
        internal Vector2Int                PositionPx        { get; private set; }
        internal Vector2Int                SizePx            { get; }
        internal bool                      AllowsVideo       { get; }
        internal int                       CountdownSec      { get; private set; }

        private EditorAdConfig(
            string adUnitId
          , EditorAdMode mode
          , bool pausesGame
          , int countdownSec
          , bool randomCloseSide
          , bool numberOpposite
          , float heightRatio
          , float backgroundAlpha
          , Vector2Int positionPx
          , Vector2Int sizePx
          , bool allowsVideo) {
            AdUnitId        = adUnitId;
            Mode            = mode;
            PausesGame      = pausesGame;
            CountdownSec    = Mathf.Max(0, countdownSec);
            RandomCloseSide = randomCloseSide;
            NumberOpposite  = numberOpposite;
            HeightRatio     = heightRatio;
            BackgroundAlpha = backgroundAlpha;
            PositionPx      = positionPx;
            SizePx          = sizePx;
            AllowsVideo     = allowsVideo;
        }

        internal static EditorAdConfig CreateFullScreen(
            string adUnitId
          , bool fullscreen
          , int countdownSec
          , bool randomCloseSide
          , bool numberOpposite
          , float heightRatio
          , float backgroundAlpha
          , bool fakeCloseAutoDismiss) {
            return new(
                adUnitId
              , fullscreen
                    ? EditorAdMode.FullScreen
                    : EditorAdMode.Collapsible
              , fullscreen
              , countdownSec
              , randomCloseSide
              , numberOpposite
              , ResolveRatio(heightRatio, DEFAULT_HEIGHT_RATIO)
              , ResolveAlpha(
                    backgroundAlpha
                  , fullscreen
                        ? FULL_SCREEN_DEFAULT_ALPHA
                        : COLLAPSIBLE_DEFAULT_ALPHA)
              , default
              , default
              , true) {
                FakeCloseAutoDismiss = fakeCloseAutoDismiss
            };
        }

        internal static EditorAdConfig CreateInFeed(
            string adUnitId
          , Vector2Int positionPx
          , Vector2Int sizePx
          , float backgroundAlpha) {
            return new(
                adUnitId
              , EditorAdMode.InFeed
              , false
              , default
              , false
              , false
              , default
              , ResolveInFeedAlpha(backgroundAlpha)
              , positionPx
              , sizePx
              , true);
        }

        /// <summary>
        /// Mirrors the device rule: the close button commits the ad's click
        /// on its way out.
        /// </summary>
        internal bool FakeCloseAutoDismiss { get; private set; }

        internal void SetCountdownSec(int countdownSec) {
            CountdownSec = Mathf.Max(0, countdownSec);
        }

        internal void SetPosition(Vector2Int positionPx) {
            PositionPx = positionPx;
        }

        internal EditorAdConfig Snapshot() {
            return new(
                AdUnitId
              , Mode
              , PausesGame
              , CountdownSec
              , RandomCloseSide
              , NumberOpposite
              , HeightRatio
              , BackgroundAlpha
              , PositionPx
              , SizePx
              , AllowsVideo) {
                FakeCloseAutoDismiss = FakeCloseAutoDismiss
            };
        }

        private static float ResolveAlpha(float value, float defaultValue) {
            if (!float.IsFinite(value) || value < 0f) return defaultValue;
            return Mathf.Clamp01(value);
        }

        private static float ResolveInFeedAlpha(float value) {
            return float.IsFinite(value)
                    ? Mathf.Clamp01(value)
                    : IN_FEED_DEFAULT_ALPHA;
        }

        private static float ResolveRatio(float value, float defaultValue) {
            if (!float.IsFinite(value)) return defaultValue;
            return Mathf.Clamp01(value);
        }

        private static float ResolveNonNegative(float value) {
            return !float.IsFinite(value) || value < 0f
                    ? 0f
                    : value;
        }
    }
}
