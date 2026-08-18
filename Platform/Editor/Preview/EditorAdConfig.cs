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
        internal CloseSettings             Close             { get; private set; }
        internal float                     HeightRatio       { get; }
        internal float                     BackgroundAlpha   { get; }
        internal Vector2Int                PositionPx        { get; private set; }
        internal Vector2Int                SizePx            { get; }
        internal bool                      AllowsVideo       { get; }

        private EditorAdConfig(
            string adUnitId
          , EditorAdMode mode
          , bool pausesGame
          , in CloseSettings controls
          , float heightRatio
          , float backgroundAlpha
          , Vector2Int positionPx
          , Vector2Int sizePx
          , bool allowsVideo) {
            AdUnitId        = adUnitId;
            Mode            = mode;
            PausesGame      = pausesGame;
            Close = controls;
            HeightRatio     = heightRatio;
            BackgroundAlpha = backgroundAlpha;
            PositionPx      = positionPx;
            SizePx          = sizePx;
            AllowsVideo     = allowsVideo;
        }

        internal static EditorAdConfig CreateFullScreen(
            string adUnitId
          , bool fullscreen
          , float heightRatio
          , float backgroundAlpha
          , in CloseSettings controls) {
            return new(
                adUnitId
              , fullscreen
                    ? EditorAdMode.FullScreen
                    : EditorAdMode.Collapsible
              , fullscreen
              , controls
              , ResolveRatio(heightRatio, DEFAULT_HEIGHT_RATIO)
              , ResolveAlpha(
                    backgroundAlpha
                  , fullscreen
                        ? FULL_SCREEN_DEFAULT_ALPHA
                        : COLLAPSIBLE_DEFAULT_ALPHA)
              , default
              , default
              , true);
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
              , default
              , ResolveInFeedAlpha(backgroundAlpha)
              , positionPx
              , sizePx
              , true);
        }

        internal void SetClose(in CloseSettings controls) {
            Close = controls;
        }

        internal void SetPosition(Vector2Int positionPx) {
            PositionPx = positionPx;
        }

        internal EditorAdConfig Snapshot() {
            return new(
                AdUnitId
              , Mode
              , PausesGame
              , Close
              , HeightRatio
              , BackgroundAlpha
              , PositionPx
              , SizePx
              , AllowsVideo);
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
