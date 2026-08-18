using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    internal sealed class EditorMediaGraphic : MaskableGraphic {
        private const int SUN_SEGMENTS = 18;
        private const float SUN_RADIUS_RATIO = 0.09f;
        private const float SUN_X_RATIO = 0.78f;
        private const float SUN_Y_RATIO = 0.74f;
        private const float BACK_PEAK_X_RATIO = 0.48f;
        private const float BACK_PEAK_Y_RATIO = 0.70f;
        private const float BACK_END_X_RATIO = 0.88f;
        private const float FRONT_START_X_RATIO = 0.24f;
        private const float FRONT_PEAK_X_RATIO = 0.68f;
        private const float FRONT_PEAK_Y_RATIO = 0.58f;

        private static readonly Color TopColor =
                new(0.10f, 0.15f, 0.24f, 1f);
        private static readonly Color BottomColor =
                new(0.08f, 0.43f, 0.48f, 1f);
        private static readonly Color BackMountainColor =
                new(0.22f, 0.61f, 0.62f, 0.88f);
        private static readonly Color FrontMountainColor =
                new(0.10f, 0.28f, 0.38f, 0.94f);
        private static readonly Color SunColor =
                new(1f, 0.76f, 0.24f, 0.95f);

        protected override void OnPopulateMesh(VertexHelper vertexHelper) {
            vertexHelper.Clear();
            var rect = GetPixelAdjustedRect();
            EditorGraphicUtility.AddGradientQuad(
                vertexHelper
              , rect
              , BottomColor
              , TopColor);

            var sunRadius =
                    Mathf.Min(rect.width, rect.height)
                            * SUN_RADIUS_RATIO;
            EditorGraphicUtility.AddCircle(
                vertexHelper
              , new(
                    Mathf.Lerp(rect.xMin, rect.xMax, SUN_X_RATIO)
                  , Mathf.Lerp(rect.yMin, rect.yMax, SUN_Y_RATIO))
              , sunRadius
              , SunColor
              , SUN_SEGMENTS);

            EditorGraphicUtility.AddTriangle(
                vertexHelper
              , new(rect.xMin, rect.yMin)
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , BACK_PEAK_X_RATIO)
                  , Mathf.Lerp(
                        rect.yMin
                      , rect.yMax
                      , BACK_PEAK_Y_RATIO))
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , BACK_END_X_RATIO)
                  , rect.yMin)
              , BackMountainColor);
            EditorGraphicUtility.AddTriangle(
                vertexHelper
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , FRONT_START_X_RATIO)
                  , rect.yMin)
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , FRONT_PEAK_X_RATIO)
                  , Mathf.Lerp(
                        rect.yMin
                      , rect.yMax
                      , FRONT_PEAK_Y_RATIO))
              , new(rect.xMax, rect.yMin)
              , FrontMountainColor);
        }
    }
}
