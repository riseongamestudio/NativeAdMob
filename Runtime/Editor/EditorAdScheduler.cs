using System;
using System.Collections.Generic;
using UnityEngine;

namespace RiseOn.NativeAdMob.Editor {
    // The editor counterpart of the native main-thread Handler: a hidden
    // driver object that runs delayed actions on the update loop, so the
    // simulated clients can keep the same schedule-and-cancel shape as the
    // Android controller.
    internal sealed class EditorAdScheduler : MonoBehaviour {
        private sealed class Entry {
            public double dueAt;
            public Action action;
        }

        private static EditorAdScheduler instance;

        private readonly List<Entry> entries = new();

        internal static double Now => Time.unscaledTimeAsDouble;

        internal static EditorAdScheduler Instance {
            get {
                if (instance == null) {
                    var host = new GameObject("NativeAdMob Editor Scheduler") {
                        hideFlags = HideFlags.HideAndDontSave
                    };
                    DontDestroyOnLoad(host);
                    instance = host.AddComponent<EditorAdScheduler>();
                }
                return instance;
            }
        }

        /// <summary>Runs the action after the delay; the token cancels it.</summary>
        internal object Schedule(float delaySeconds, Action action) {
            var entry = new Entry {
                dueAt  = Now + delaySeconds
              , action = action
            };
            entries.Add(entry);
            return entry;
        }

        internal void Cancel(object token) {
            if (token is Entry entry) entries.Remove(entry);
        }

        private void Update() {
            for (var i = 0; i < entries.Count; ++i) {
                if (entries[i].dueAt > Now) continue;

                var entry = entries[i];
                entries.RemoveAt(i--);
                try {
                    entry.action?.Invoke();
                } catch (Exception exception) {
                    Debug.LogException(exception);
                }
            }
        }
    }
}
