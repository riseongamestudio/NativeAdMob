using System;
using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The wrapper serializes every call under its own lock and the Editor is
    // single-threaded, so this client needs no locking of its own. Readiness
    // is reported through OnStateChanged like the device platforms, so the
    // wrapper's cache flags are the one source of truth.
    internal sealed class OverlayAdClient : IOverlayAdClient {
        private const string AD_NOT_READY_ERROR = "Ad not ready";

        private readonly IOverlayAdCallbacks callbacks;
        private readonly EditorAdConfig config;
        private EditorAd preview;
        private bool adReady;
        private bool adLoading;

        internal OverlayAdClient(
            OverlayAdSettings settings
          , IOverlayAdCallbacks callbacks) {
            this.callbacks = callbacks;
            config = EditorAdConfig.CreateFullScreen(
                settings.AdUnitId
              , settings.CoversFullScreen
              , settings.CountdownSec
              , settings.XRandomSide
              , settings.NumberOppositeSide
              , settings.HeightRatio
              , settings.BackgroundAlpha);
        }

        public void SetCountdownSec(int countdownSec) {
            config?.SetCountdownSec(countdownSec);
        }

        public void LoadAd() {
            if (adLoading || adReady) return;

            adLoading = true;
            callbacks.OnStateChanged(false, true);
            callbacks.OnLoadingStarted();

            adLoading = false;
            adReady   = true;
            callbacks.OnStateChanged(true, false);
            callbacks.OnLoadingCompleted(0, string.Empty);
        }

        public void ShowAd(int showId) {
            if (!adReady) {
                callbacks.OnShowCompleted(showId, AD_NOT_READY_ERROR, false);
                return;
            }

            adReady = false;
            callbacks.OnStateChanged(false, adLoading);
            try {
                preview = EditorAd.Show(
                    config.Snapshot()
                  , () => {
                        preview = null;
                        callbacks.OnShowCompleted(showId, string.Empty, true);
                    });
                callbacks.OnDisplayed();
            } catch (Exception exception) {
                Debug.LogException(exception);
                callbacks.OnShowCompleted(showId, exception.Message, true);
            }
        }

        public void HideAd() {
            var current = preview;
            if (current) current.Dismiss();
        }

        public void Release() {
            var current = preview;
            preview = null;
            if (current) current.Release();
        }
    }
}
