using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    /// <summary>
    /// Keeps a label in proportion to the box it lives in.
    /// <para>
    /// The preview renders the same elements at wildly different sizes - a
    /// 96dp feed cell and a full screen - so a point size that suits one is
    /// wrong in the other: too small in the roomy box, too big in the tight
    /// one. Best-fit does not solve it either, because it only shrinks text
    /// that overflows and leaves a large box looking empty.
    /// </para>
    /// <para>
    /// This asks the opposite question: how tall should the text be for this
    /// box? The answer follows the box on every layout pass.
    /// </para>
    /// </summary>
    [ExecuteAlways]
    [RequireComponent(typeof(Text))]
    internal sealed class EditorScaledText : MonoBehaviour {
        [SerializeField] private RectTransform reference;
        [SerializeField] private float         ratio;
        [SerializeField] private int           minimumSize;
        [SerializeField] private int           maximumSize;
        [SerializeField] private bool          followsShortSide;

        private Text          label;
        private RectTransform own;

        internal void Configure(
            RectTransform reference
          , float ratio
          , int minimumSize
          , int maximumSize
          , bool followsShortSide = false) {
            this.reference        = reference;
            this.ratio            = ratio;
            this.minimumSize      = minimumSize;
            this.maximumSize      = maximumSize;
            this.followsShortSide = followsShortSide;
            Apply();
        }

        private void OnEnable() => Apply();

        private void OnRectTransformDimensionsChange() => Apply();

        private void Apply() {
            label ??= GetComponent<Text>();
            own   ??= (RectTransform)transform;
            if (label == null || ratio <= 0f) return;

            RectTransform box  = reference != null ? reference : own;
            Rect          rect = box.rect;
            if (rect.height <= 0f && rect.width <= 0f) return;

            float driver = followsShortSide
                    ? Mathf.Min(rect.width, rect.height)
                    : rect.height;
            int size = Mathf.Clamp(
                Mathf.RoundToInt(driver * ratio)
              , minimumSize
              , maximumSize);
            if (label.fontSize == size) return;

            // A fitted size is a computed size: best-fit would fight it.
            label.resizeTextForBestFit = false;
            label.fontSize             = size;
        }
    }
}
