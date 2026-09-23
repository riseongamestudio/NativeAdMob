using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    internal static class EditorGraphicUtility {
        internal static void AddSolidQuad(
            VertexHelper vertexHelper
          , Rect rect
          , Color color) {
            AddGradientQuad(
                vertexHelper
              , rect
              , color
              , color);
        }

        internal static void AddGradientQuad(
            VertexHelper vertexHelper
          , Rect rect
          , Color bottomColor
          , Color topColor) {
            var start = vertexHelper.currentVertCount;
            AddVertex(
                vertexHelper
              , new(rect.xMin, rect.yMin)
              , bottomColor
              , new(0f, 0f));
            AddVertex(
                vertexHelper
              , new(rect.xMin, rect.yMax)
              , topColor
              , new(0f, 1f));
            AddVertex(
                vertexHelper
              , new(rect.xMax, rect.yMax)
              , topColor
              , new(1f, 1f));
            AddVertex(
                vertexHelper
              , new(rect.xMax, rect.yMin)
              , bottomColor
              , new(1f, 0f));
            vertexHelper.AddTriangle(start, start + 1, start + 2);
            vertexHelper.AddTriangle(start + 2, start + 3, start);
        }

        internal static void AddTriangle(
            VertexHelper vertexHelper
          , Vector2 first
          , Vector2 second
          , Vector2 third
          , Color color) {
            var start = vertexHelper.currentVertCount;
            AddVertex(vertexHelper, first, color, Vector2.zero);
            AddVertex(vertexHelper, second, color, Vector2.up);
            AddVertex(vertexHelper, third, color, Vector2.one);
            vertexHelper.AddTriangle(start, start + 1, start + 2);
        }

        internal static void AddCircle(
            VertexHelper vertexHelper
          , Vector2 center
          , float radius
          , Color color
          , int segments) {
            var centerIndex = vertexHelper.currentVertCount;
            AddVertex(
                vertexHelper
              , center
              , color
              , new(0.5f, 0.5f));
            var outerStartIndex = vertexHelper.currentVertCount;
            for (var index = 0; index < segments; ++index) {
                var angle =
                        index * Mathf.PI * 2f / segments;
                var unit = new Vector2(
                    Mathf.Cos(angle)
                  , Mathf.Sin(angle));
                AddVertex(
                    vertexHelper
                  , center + unit * radius
                  , color
                  , new(
                        unit.x * 0.5f + 0.5f
                      , unit.y * 0.5f + 0.5f));
            }

            for (var index = 0; index < segments; ++index) {
                vertexHelper.AddTriangle(
                    centerIndex
                  , outerStartIndex + index
                  , outerStartIndex + (index + 1) % segments);
            }
        }

        internal static void AddVertex(
            VertexHelper vertexHelper
          , Vector2 position
          , Color color
          , Vector2 uv) {
            var vertex = UIVertex.simpleVert;
            vertex.position = position;
            vertex.color = color;
            vertex.uv0 = uv;
            vertexHelper.AddVert(vertex);
        }
    }
}
