using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public sealed partial class InFeed : Ad {
        private const string JAVA_CLASS_NAME = "com.riseon.nativeadmob.InFeed";

        private readonly string adUnitId;
        private Action pendingOnDisplayed;
        private bool checkedOut;

        public InFeed(string adUnitId)
            : base(
                JAVA_CLASS_NAME
              , adUnitId
              , passCurrentActivity: true) {
            this.adUnitId = adUnitId;
            if (supportsIOS) IOSCreate(adUnitId);
            if (!supportsAndroid && !supportsIOS && !supportsEditorPreview) {
                Debug.Log($"{nameof(InFeed)} is not supported on this platform");
            }
        }

        partial void IOSCreate(string adUnitId);
        partial void AndroidConfigure(
            Vector2Int positionPx, Vector2Int sizePx, float backgroundAlpha);
        partial void IOSConfigure(
            Vector2Int positionPx, Vector2Int sizePx, float backgroundAlpha);
        partial void EditorConfigure(
            Vector2Int positionPx, Vector2Int sizePx, float backgroundAlpha);
        partial void EditorReleaseSwappedPreview();
        partial void AndroidShow();
        partial void IOSShow();
        partial void EditorShow();
        partial void AndroidHide();
        partial void IOSHide();
        partial void EditorHide();
        partial void AndroidSetPosition(Vector2Int positionPx);
        partial void IOSSetPosition(Vector2Int positionPx);
        partial void EditorSetPosition(Vector2Int positionPx);
        partial void AndroidTakeReleased();
        partial void IOSTakeReleased();
        partial void EditorTakeReleased();
        partial void AndroidFinishRelease();
        partial void IOSFinishRelease();
        partial void EditorFinishRelease();

        public void Configure(
            Vector2Int positionPx
          , Vector2Int sizePx
          , float backgroundAlpha) {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                checkedOut         = true;
                pendingOnDisplayed = null;
                if (supportsAndroid) {
                    AndroidConfigure(positionPx, sizePx, backgroundAlpha);
                } else if (supportsIOS) {
                    IOSConfigure(positionPx, sizePx, backgroundAlpha);
                } else if (supportsEditorPreview) {
                    EditorConfigure(positionPx, sizePx, backgroundAlpha);
                }
            }
            EditorReleaseSwappedPreview();
        }

        public void Show(Action onDisplayed) {
            lock (nativeAdStateLock) {
                if (releasedManaged || !checkedOut) return;

                pendingOnDisplayed = onDisplayed;
                if (supportsAndroid) {
                    AndroidShow();
                } else if (supportsIOS) {
                    IOSShow();
                } else if (supportsEditorPreview) {
                    EditorShow();
                }
            }
        }

        public void Hide() {
            lock (nativeAdStateLock) {
                if (releasedManaged || !checkedOut) return;

                pendingOnDisplayed = null;
                if (supportsAndroid) {
                    AndroidHide();
                } else if (supportsIOS) {
                    IOSHide();
                } else if (supportsEditorPreview) {
                    EditorHide();
                }
            }
        }

        public void SetPosition(Vector2Int positionPx) {
            lock (nativeAdStateLock) {
                if (releasedManaged || !checkedOut) return;

                if (supportsAndroid) {
                    AndroidSetPosition(positionPx);
                } else if (supportsIOS) {
                    IOSSetPosition(positionPx);
                } else if (supportsEditorPreview) {
                    EditorSetPosition(positionPx);
                }
            }
        }

        public void ReturnToPool() {
            lock (nativeAdStateLock) {
                if (releasedManaged || !checkedOut) return;

                checkedOut         = false;
                pendingOnDisplayed = null;
                if (supportsAndroid) {
                    AndroidHide();
                } else if (supportsIOS) {
                    IOSHide();
                } else if (supportsEditorPreview) {
                    EditorHide();
                }
            }
        }

        internal void InvokePendingOnDisplayed() {
            Action onDisplayed;
            lock (nativeAdStateLock) {
                if (releasedManaged || !checkedOut) return;

                onDisplayed        = pendingOnDisplayed;
                pendingOnDisplayed = null;
            }
            InvokeSafely(onDisplayed);
        }

        protected override void HandleShowNotReady() {
            Debug.LogWarning($"{GetType().Name}: Show Called While Not Ready");
        }

        public void Dispose() {
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                releasedManaged = true;
                checkedOut      = false;

                AndroidTakeReleased();
                IOSTakeReleased();
                EditorTakeReleased();
                pendingOnDisplayed = null;
                InvalidateLoadListener();
            }

            AndroidFinishRelease();
            IOSFinishRelease();
            EditorFinishRelease();
        }
    }
}
