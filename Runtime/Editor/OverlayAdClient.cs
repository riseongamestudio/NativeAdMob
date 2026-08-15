using System;
using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // Mirrors the device lifecycle rather than shortcutting it: loading
    // takes real time, readiness travels through OnStateChanged like both
    // native platforms, and a show consumes the cached ad and completes
    // through OnShowCompleted so the wrapper's automatic replacement load
    // kicks in exactly as it does on device.
    internal sealed class OverlayAdClient : IOverlayAdClient {
        private const float LOAD_DELAY_SECONDS = 1.0f;
        private const int SUCCESS_CODE = 0;
        private const string AD_NOT_READY_ERROR = "Ad not ready";

        private readonly IOverlayAdCallbacks callbacks;
        private readonly EditorAdConfig config;
        private EditorAd view;
        private bool adReady;
        private bool adLoading;
        private bool released;

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
            if (released || adLoading || adReady) return;

            adLoading = true;
            callbacks.OnStateChanged(false, true);
            callbacks.OnLoadingStarted();
            EditorAdScheduler.Instance.Schedule(LOAD_DELAY_SECONDS, () => {
                if (released) return;

                adLoading = false;
                adReady   = true;
                callbacks.OnStateChanged(true, false);
                callbacks.OnLoadingCompleted(SUCCESS_CODE, string.Empty);
            });
        }

        public void ShowAd(int showId) {
            if (released || !adReady) {
                callbacks.OnShowCompleted(showId, AD_NOT_READY_ERROR, false);
                return;
            }

            adReady = false;
            callbacks.OnStateChanged(false, adLoading);
            try {
                view = EditorAd.Show(
                    config.Snapshot()
                  , () => {
                        view = null;
                        callbacks.OnShowCompleted(showId, string.Empty, true);
                    });
                callbacks.OnDisplayed();
            } catch (Exception exception) {
                Debug.LogException(exception);
                callbacks.OnShowCompleted(showId, exception.Message, true);
            }
        }

        public void HideAd() {
            var current = view;
            if (current) current.Dismiss();
        }

        public void Release() {
            released = true;
            var current = view;
            view = null;
            if (current) current.Release();
        }
    }
}
