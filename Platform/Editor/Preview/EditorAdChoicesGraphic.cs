using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    internal sealed class EditorAdChoicesGraphic : MaskableGraphic {
        private const int INFO_DOT_SEGMENTS = 12;
        private const float ICON_SIZE_RATIO = 0.72f;
        private const float HALF = 0.5f;
        private const float INFO_X_OFFSET_RATIO = 0.24f;
        private const float INFO_BAR_WIDTH_RATIO = 0.12f;
        private const float INFO_BAR_HEIGHT_RATIO = 0.34f;
        private const float INFO_BAR_Y_OFFSET_RATIO = 0.34f;
        private const float INFO_DOT_Y_OFFSET_RATIO = 0.28f;
        private const float INFO_DOT_RADIUS_RATIO = 0.62f;

        private static readonly Color BackgroundColor =
                new(1f, 1f, 1f, 0.92f);
        private static readonly Color IconColor =
                new(0.10f, 0.56f, 0.82f, 1f);
        private static readonly Color InfoColor =
                Color.white;

        protected override void OnPopulateMesh(VertexHelper vertexHelper) {
            vertexHelper.Clear();
            var rect = GetPixelAdjustedRect();
            EditorGraphicUtility.AddSolidQuad(
                vertexHelper
              , rect
              , BackgroundColor);

            var size =
                    Mathf.Min(rect.width, rect.height)
                            * ICON_SIZE_RATIO;
            var center = rect.center;
            var half = size * HALF;
            EditorGraphicUtility.AddTriangle(
                vertexHelper
              , new(center.x - half, center.y - half)
              , new(center.x + half, center.y)
              , new(center.x - half, center.y + half)
              , IconColor);

            var barWidth = size * INFO_BAR_WIDTH_RATIO;
            var barHeight = size * INFO_BAR_HEIGHT_RATIO;
            EditorGraphicUtility.AddSolidQuad(
                vertexHelper
              , new Rect(
                    center.x
                            - half * INFO_X_OFFSET_RATIO
                            - barWidth * HALF
                  , center.y
                            - half * INFO_BAR_Y_OFFSET_RATIO
                  , barWidth
                  , barHeight)
              , InfoColor);
            EditorGraphicUtility.AddCircle(
                vertexHelper
              , new(
                    center.x - half * INFO_X_OFFSET_RATIO
                  , center.y
                            + half * INFO_DOT_Y_OFFSET_RATIO)
              , barWidth * INFO_DOT_RADIUS_RATIO
              , InfoColor
              , INFO_DOT_SEGMENTS);
        }
    }
}
