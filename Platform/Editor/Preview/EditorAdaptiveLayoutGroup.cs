using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob.Editor {
    internal sealed class EditorAdaptiveLayoutGroup
        : HorizontalOrVerticalLayoutGroup {
        private bool isVertical = true;

        internal bool IsVertical {
            get => isVertical;
            set {
                if (isVertical == value) return;

                isVertical = value;
                SetDirty();
            }
        }

        public override void CalculateLayoutInputHorizontal() {
            base.CalculateLayoutInputHorizontal();
            CalcAlongAxis(0, isVertical);
        }

        public override void CalculateLayoutInputVertical() {
            CalcAlongAxis(1, isVertical);
        }

        public override void SetLayoutHorizontal() {
            SetChildrenAlongAxis(0, isVertical);
        }

        public override void SetLayoutVertical() {
            SetChildrenAlongAxis(1, isVertical);
        }
    }
}
