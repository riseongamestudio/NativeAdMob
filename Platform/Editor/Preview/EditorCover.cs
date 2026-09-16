using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    // One cover as the preview draws it: a flat colour on a canvas of its
    // own, at the order EditorSortingOrder gives its kind.
    //
    // It swallows touches over its area the way the device's window does,
    // and nothing outside it: a half-screen cover leaves the strip above it
    // empty, so touches there reach the game. It survives scene loads,
    // because the device's cover is not part of any scene either - a cover
    // raised over a level change is still up when the next level arrives.
    internal sealed class EditorCover : MonoBehaviour {
        private const string AREA_OBJECT_NAME = "Area";
        private const float FULL_HEIGHT_RATIO = 1f;

        private Image area;
        private bool holdsPause;

        internal static EditorCover Create(
            string objectName
          , int sortingOrder
          , bool pausesGame) {
            var coverObject = new GameObject(
                objectName
              , typeof(RectTransform)
              , typeof(Canvas)
              , typeof(GraphicRaycaster)
              , typeof(EditorCover));
            DontDestroyOnLoad(coverObject);

            var canvas = coverObject.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = sortingOrder;

            var areaObject = new GameObject(
                AREA_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(CanvasRenderer)
              , typeof(Image));
            areaObject.transform.SetParent(coverObject.transform, false);

            var cover = coverObject.GetComponent<EditorCover>();
            cover.area = areaObject.GetComponent<Image>();
            // Even when fully transparent: the device's window still takes
            // the touches that land on it.
            cover.area.raycastTarget = true;

            if (pausesGame) {
                EditorPause.Acquire();
                cover.holdsPause = true;
            }
            return cover;
        }

        internal void ApplyFullScreen(Color color) {
            Apply(color, FULL_HEIGHT_RATIO);
        }

        // Measured from the bottom edge, as the device measures it.
        internal void Apply(Color color, float heightRatio) {
            area.color = color;

            var rect = area.rectTransform;
            rect.anchorMin = Vector2.zero;
            rect.anchorMax = new(1f, heightRatio);
            rect.pivot = new(.5f, 0f);
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;
        }

        internal void Close() {
            ReleasePause();
            Destroy(gameObject);
        }

        // A cover torn down by anything other than Close - leaving play mode,
        // say - still has to give its pause back, or the next ad to stop the
        // game would find the clock already counting a holder that is gone.
        private void OnDestroy() {
            ReleasePause();
        }

        private void ReleasePause() {
            if (!holdsPause) return;

            holdsPause = false;
            EditorPause.Release();
        }
    }
}
