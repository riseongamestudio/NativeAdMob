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
              , settings.HeightRatio
              , AdColor.Unpack(settings.BackgroundColor)
              , settings.Close);
        }

        public void SetClose(in CloseSettings controls) {
            config?.SetClose(controls);
        }

        public void Load() {
            if (adLoading || adReady) return;

            adLoading = true;
            callbacks.OnStateChanged(false, true);

            adLoading = false;
            adReady   = true;
            callbacks.OnStateChanged(true, false);
            callbacks.OnLoadingCompleted(0, string.Empty, 1, 1);
        }

        public void Show(int showId) {
            if (!adReady) {
                callbacks.OnShowCompleted(showId, AD_NOT_READY_ERROR, false, 0);
                return;
            }

            adReady = false;
            callbacks.OnStateChanged(false, adLoading);
            try {
                preview = EditorAd.Show(
                    config.Snapshot()
                  , () => {
                        preview = null;
                        // Not a flat zero: OnDisplayed below triggers the
                        // wrapper's replacement Load, which this client
                        // serves instantly - so by the time the preview
                        // closes the seat is usually already refilled.
                        // Reporting zero here overwrote that truth and
                        // stranded IsReady at false for the whole session,
                        // because the follow-up Load found adReady already
                        // true and returned without republishing state.
                        callbacks.OnShowCompleted(
                            showId, string.Empty, true, adReady ? 1 : 0);
                    });
                callbacks.OnDisplayed();
            } catch (Exception exception) {
                Debug.LogException(exception);
                callbacks.OnShowCompleted(
                    showId, exception.Message, true, adReady ? 1 : 0);
            }
        }

        public void Hide() {
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
