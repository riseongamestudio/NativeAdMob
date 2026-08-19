using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    internal sealed class EditorAdConfig {
        private const float DEFAULT_HEIGHT_RATIO      = 0.5f;

        internal string                    AdUnitId          { get; }
        internal EditorAdMode Mode              { get; }
        internal bool                      PausesGame        { get; }
        internal CloseSettings             Close             { get; private set; }
        internal float                     HeightRatio       { get; }
        internal Color                     BackgroundColor   { get; }
        internal Vector2Int                PositionPx        { get; private set; }
        internal Vector2Int                SizePx            { get; }
        internal bool                      AllowsVideo       { get; }

        private EditorAdConfig(
            string adUnitId
          , EditorAdMode mode
          , bool pausesGame
          , in CloseSettings controls
          , float heightRatio
          , Color backgroundColor
          , Vector2Int positionPx
          , Vector2Int sizePx
          , bool allowsVideo) {
            AdUnitId        = adUnitId;
            Mode            = mode;
            PausesGame      = pausesGame;
            Close = controls;
            HeightRatio     = heightRatio;
            BackgroundColor = backgroundColor;
            PositionPx      = positionPx;
            SizePx          = sizePx;
            AllowsVideo     = allowsVideo;
        }

        internal static EditorAdConfig CreateFullScreen(
            string adUnitId
          , bool fullscreen
          , float heightRatio
          , Color backgroundColor
          , in CloseSettings controls) {
            return new(
                adUnitId
              , fullscreen
                    ? EditorAdMode.FullScreen
                    : EditorAdMode.Collapsible
              , fullscreen
              , controls
              , ResolveRatio(heightRatio, DEFAULT_HEIGHT_RATIO)
              , backgroundColor
              , default
              , default
              , true);
        }

        internal static EditorAdConfig CreateInFeed(
            string adUnitId
          , Vector2Int positionPx
          , Vector2Int sizePx
          , Color backgroundColor) {
            return new(
                adUnitId
              , EditorAdMode.InFeed
              , false
              , default
              , default
              , backgroundColor
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
              , BackgroundColor
              , PositionPx
              , SizePx
              , AllowsVideo);
        }

        private static float ResolveAlpha(float value, float defaultValue) {
            if (!float.IsFinite(value) || value < 0f) return defaultValue;
            return Mathf.Clamp01(value);
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
