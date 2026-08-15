#if UNITY_EDITOR
using System;
using UnityEngine;

namespace RiseOn.NativeAdMob {
    public abstract partial class NativeOverlayAdMob {
        private const int EDITOR_PREVIEW_LOAD_ERROR = -1;

        private EditorPreviewConfig editorPreviewConfig;
        private EditorPreview editorPreview;
        private bool editorAdReady;
        private bool editorAdLoading;

        partial void EditorSetPreviewConfig(
            bool fullscreen
          , int countdownSec
          , bool xRandomSide
          , bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha) {
            if (!supportsEditorPreview) return;

            var previewConfig = EditorPreviewConfig.CreateFullScreen(
                adUnitId
              , fullscreen
              , countdownSec
              , xRandomSide
              , numberOppositeSide
              , heightRatio
              , backgroundAlpha);
            lock (nativeAdStateLock) {
                if (!releasedManaged) editorPreviewConfig = previewConfig;
            }
        }

        partial void EditorSetPreviewCountdownSec(int countdownSec) {
            if (!supportsEditorPreview) return;

            lock (nativeAdStateLock) {
                if (!releasedManaged) {
                    editorPreviewConfig?.SetCountdownSec(countdownSec);
                }
            }
        }

        partial void EditorLoadAd() {
            EditorPreviewConfig previewConfig;
            int                 listenerGeneration;
            lock (nativeAdStateLock) {
                if (releasedManaged || editorAdLoading) return;

                editorAdLoading    = true;
                editorAdReady      = false;
                previewConfig      = editorPreviewConfig;
                listenerGeneration = loadListenerGeneration;
            }

            RaiseLoadingStarted();

            var errorCode = 0;
            var errorMessage = string.Empty;
            lock (nativeAdStateLock) {
                if (releasedManaged
                 || listenerGeneration != loadListenerGeneration) {
                    editorAdLoading = false;
                    return;
                }

                editorAdLoading = false;
                if (previewConfig == null) {
                    editorAdReady = false;
                    errorCode     = EDITOR_PREVIEW_LOAD_ERROR;
                    errorMessage  = AD_NOT_CONFIGURED_ERROR;
                } else {
                    editorAdReady = true;
                }
            }

            RaiseLoadingCompleted(errorCode, errorMessage);
        }

        partial void EditorShowAd(ShowCompletedHandler onAdCompleted) {
            EditorPreviewConfig previewConfig = null;
            int                 generation = INVALID_GENERATION;
            string              rejectedError = null;
            lock (nativeAdStateLock) {
                if (releasedManaged) {
                    rejectedError = AD_RELEASED_ERROR;
                } else if (showPendingOrActive) {
                    rejectedError = AD_ALREADY_SHOWING_ERROR;
                } else if (editorPreviewConfig == null) {
                    rejectedError = AD_NOT_CONFIGURED_ERROR;
                } else if (!editorAdReady) {
                    rejectedError = AD_NOT_READY_ERROR;
                } else {
                    editorAdReady        = false;
                    showPendingOrActive  = true;
                    currentShowCompleted = onAdCompleted;
                    generation           = ++showGeneration;
                    previewConfig        = editorPreviewConfig.Snapshot();
                }
            }

            if (rejectedError != null) {
                InvokeCompletionSafely(
                    onAdCompleted
                  , rejectedError
                  , false);
                return;
            }

            try {
                var preview = EditorPreview.Show(
                    previewConfig
                  , () => CompleteEditorPreview(
                        generation
                      , string.Empty
                      , true));
                var accepted = false;
                lock (nativeAdStateLock) {
                    if (!releasedManaged
                     && showPendingOrActive
                     && generation == showGeneration) {
                        editorPreview = preview;
                        accepted      = true;
                    }
                }

                if (!accepted) {
                    preview.Release();
                    return;
                }

                RaiseDisplayed();
            } catch (Exception exception) {
                Debug.LogException(exception);
                CompleteEditorPreview(
                    generation
                  , exception.Message
                  , true);
            }
        }

        private void CompleteEditorPreview(
            int generation
          , string errorMessage
          , bool adConsumed) {
            lock (nativeAdStateLock) {
                if (generation == showGeneration) editorPreview = null;
            }

            if (!TryTakeShowCompletion(
                    generation
                  , adConsumed
                  , out var completed))
                return;

            InvokeCompletionSafely(
                completed
              , errorMessage
              , adConsumed);
            if (adConsumed) LoadAd();
        }

        partial void EditorHideAd() {
            EditorPreview preview;
            lock (nativeAdStateLock) {
                preview = editorPreview;
            }
            if (preview) preview.Dismiss();
        }

        partial void EditorIsAdReady(ref bool isReady) {
            lock (nativeAdStateLock) {
                isReady = !releasedManaged
                    && editorAdReady
                    && !showPendingOrActive;
            }
        }

        partial void EditorIsAdLoading(ref bool isLoading) {
            lock (nativeAdStateLock) {
                isLoading = !releasedManaged && editorAdLoading;
            }
        }

        partial void EditorRelease() {
            EditorPreview        preview;
            ShowCompletedHandler completed;
            lock (nativeAdStateLock) {
                if (releasedManaged) return;

                releasedManaged = true;
                preview         = editorPreview;
                editorPreview   = null;

                completed            = showPendingOrActive
                        ? currentShowCompleted
                        : null;
                showPendingOrActive  = false;
                currentShowCompleted = null;
                editorAdReady        = false;
                editorAdLoading      = false;
                ++showGeneration;
                InvalidateLoadListener();

                editorPreviewConfig = null;
            }

            if (preview) preview.Release();
            InvokeCompletionSafely(
                completed
              , AD_RELEASED_ERROR
              , false);
        }
    }
}
#endif
