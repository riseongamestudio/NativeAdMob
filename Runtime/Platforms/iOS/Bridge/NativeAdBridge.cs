using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using AOT;
namespace RiseOn.NativeAdMob.iOS {
    /// <summary>
    /// The C# side of the Obj-C++ bridge.<br/>
    /// Native calls back through static trampolines (an IL2CPP requirement) that look the client up by its instance id and hand over; the core wrappers marshal to the Unity thread themselves.
    /// </summary>
    internal static class NativeAdBridge {
        internal delegate void LoadingStartedDelegate(int instanceId);
        internal delegate void LoadingCompletedDelegate(
            int instanceId
          , int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize);
        internal delegate void AdPaidDelegate(
            int instanceId
          , string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision);
        internal delegate void DisplayedDelegate(int instanceId);
        internal delegate void PresentationFailedDelegate(
            int instanceId, int errorCode, string errorMessage);
        internal delegate void StateChangedDelegate(
            int instanceId
          , [MarshalAs(UnmanagedType.I1)] bool isReady
          , [MarshalAs(UnmanagedType.I1)] bool isLoading);
        internal delegate void ShowNotReadyDelegate(int instanceId);
        internal delegate void ShowCompletedDelegate(
            int instanceId
          , int showId
          , string errorMessage
          , [MarshalAs(UnmanagedType.I1)] bool adConsumed
          , int cachedCount);
        internal delegate void SlotDisplayedDelegate(
            int instanceId, int slotIndex);
        internal delegate void SlotShowNotReadyDelegate(
            int instanceId, int slotIndex);
        internal delegate void SlotPresentationFailedDelegate(
            int instanceId, int slotIndex, int errorCode, string errorMessage);

        [DllImport("__Internal")]
        internal static extern IntPtr ROInFeedAd_Create(
            string adUnitId
          , int slotCount
          , int cacheSize
          , int backgroundColor
          , int instanceId);
        [DllImport("__Internal")]
        internal static extern void ROInFeedAd_SetListener(
            IntPtr handle
          , LoadingStartedDelegate loadingStarted
          , LoadingCompletedDelegate loadingCompleted
          , AdPaidDelegate adPaid
          , SlotDisplayedDelegate slotDisplayed
          , SlotShowNotReadyDelegate slotShowNotReady
          , SlotPresentationFailedDelegate slotPresentationFailed);
        [DllImport("__Internal")]
        internal static extern void ROInFeedAd_Configure(
            IntPtr handle
          , int slotIndex
          , int xPx
          , int yPx
          , int widthPx
          , int heightPx
          , int roundCornerPx);
        [DllImport("__Internal")]
        internal static extern void ROInFeedAd_Show(
            IntPtr handle, int slotIndex);
        [DllImport("__Internal")]
        internal static extern void ROInFeedAd_Hide(
            IntPtr handle, int slotIndex);
        [DllImport("__Internal")]
        internal static extern void ROInFeedAd_SetPosition(
            IntPtr handle, int slotIndex, int xPx, int yPx);
        [DllImport("__Internal")]
        internal static extern void ROInFeedAd_Release(IntPtr handle);

        [DllImport("__Internal")]
        internal static extern IntPtr ROOverlayAd_Create(
            string adUnitId, int instanceId);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_SetListener(
            IntPtr handle
          , LoadingStartedDelegate loadingStarted
          , LoadingCompletedDelegate loadingCompleted
          , AdPaidDelegate adPaid
          , DisplayedDelegate displayed
          , PresentationFailedDelegate presentationFailed
          , StateChangedDelegate stateChanged
          , ShowNotReadyDelegate showNotReady);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_Configure(
            IntPtr handle
          , [MarshalAs(UnmanagedType.I1)] bool fullScreen
          , float heightRatio
          , int backgroundColor
          , int cacheSize
          , int cooldown
          , int closeSide
          , int timerSide
          , [MarshalAs(UnmanagedType.I1)] bool redirectOnClose);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_SetClose(
            IntPtr handle
          , int cooldown
          , int closeSide
          , int timerSide
          , [MarshalAs(UnmanagedType.I1)] bool redirectOnClose);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_Load(IntPtr handle);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_Show(
            IntPtr handle, int showId, ShowCompletedDelegate onCompleted);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_Hide(IntPtr handle);
        [DllImport("__Internal")]
        internal static extern void ROOverlayAd_Release(IntPtr handle);

        // Static references keep the delegates alive while native code
        // still holds their function pointers.
        internal static readonly LoadingStartedDelegate OnLoadingStartedCallback
            = OnLoadingStarted;
        internal static readonly LoadingCompletedDelegate OnLoadingCompletedCallback
            = OnLoadingCompleted;
        internal static readonly AdPaidDelegate OnAdPaidCallback = OnAdPaid;
        internal static readonly DisplayedDelegate OnDisplayedCallback
            = OnDisplayed;
        internal static readonly PresentationFailedDelegate
            OnPresentationFailedCallback = OnPresentationFailed;
        internal static readonly StateChangedDelegate OnStateChangedCallback
            = OnStateChanged;
        internal static readonly ShowNotReadyDelegate OnShowNotReadyCallback
            = OnShowNotReady;
        internal static readonly ShowCompletedDelegate OnShowCompletedCallback
            = OnShowCompleted;
        internal static readonly SlotDisplayedDelegate OnSlotDisplayedCallback
            = OnSlotDisplayed;
        internal static readonly SlotShowNotReadyDelegate
            OnSlotShowNotReadyCallback = OnSlotShowNotReady;
        internal static readonly SlotPresentationFailedDelegate
            OnSlotPresentationFailedCallback = OnSlotPresentationFailed;

        private static readonly Dictionary<int, object> instances = new();
        private static readonly object registryLock = new();
        private static int nextInstanceId;

        internal static int Register(object client) {
            lock (registryLock) {
                var instanceId = ++nextInstanceId;
                instances[instanceId] = client;
                return instanceId;
            }
        }

        internal static void Unregister(int instanceId) {
            lock (registryLock) {
                instances.Remove(instanceId);
            }
        }

        private static object Find(int instanceId) {
            lock (registryLock) {
                return instances.TryGetValue(instanceId, out var client)
                    ? client
                    : null;
            }
        }

        [MonoPInvokeCallback(typeof(LoadingStartedDelegate))]
        private static void OnLoadingStarted(int instanceId)
            => (Find(instanceId) as INativeAdSharedHandlers)?.HandleLoadingStarted();

        [MonoPInvokeCallback(typeof(LoadingCompletedDelegate))]
        private static void OnLoadingCompleted(
            int instanceId
          , int errorCode
          , string errorMessage
          , int cachedCount
          , int cacheSize)
            => (Find(instanceId) as INativeAdSharedHandlers)
                ?.HandleLoadingCompleted(
                    errorCode
                  , errorMessage
                  , cachedCount
                  , cacheSize);

        [MonoPInvokeCallback(typeof(AdPaidDelegate))]
        private static void OnAdPaid(
            int instanceId
          , string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            => (Find(instanceId) as INativeAdSharedHandlers)?.HandleAdPaid(
                source, adUnitId, value, currencyCode, precision);

        [MonoPInvokeCallback(typeof(DisplayedDelegate))]
        private static void OnDisplayed(int instanceId)
            => (Find(instanceId) as OverlayAdClient)?.HandleDisplayed();

        [MonoPInvokeCallback(typeof(PresentationFailedDelegate))]
        private static void OnPresentationFailed(
            int instanceId, int errorCode, string errorMessage)
            => (Find(instanceId) as OverlayAdClient)
                ?.HandlePresentationFailed(errorCode, errorMessage);

        [MonoPInvokeCallback(typeof(StateChangedDelegate))]
        private static void OnStateChanged(
            int instanceId, bool isReady, bool isLoading)
            => (Find(instanceId) as OverlayAdClient)
                ?.HandleStateChanged(isReady, isLoading);

        [MonoPInvokeCallback(typeof(ShowNotReadyDelegate))]
        private static void OnShowNotReady(int instanceId)
            => (Find(instanceId) as OverlayAdClient)?.HandleShowNotReady();

        [MonoPInvokeCallback(typeof(ShowCompletedDelegate))]
        private static void OnShowCompleted(
            int instanceId
          , int showId
          , string errorMessage
          , bool adConsumed
          , int cachedCount)
            => (Find(instanceId) as OverlayAdClient)
                ?.HandleShowCompleted(
                    showId, errorMessage, adConsumed, cachedCount);

        [MonoPInvokeCallback(typeof(SlotDisplayedDelegate))]
        private static void OnSlotDisplayed(int instanceId, int slotIndex)
            => (Find(instanceId) as InFeedAdClient)
                ?.HandleSlotDisplayed(slotIndex);

        [MonoPInvokeCallback(typeof(SlotShowNotReadyDelegate))]
        private static void OnSlotShowNotReady(int instanceId, int slotIndex)
            => (Find(instanceId) as InFeedAdClient)
                ?.HandleSlotShowNotReady(slotIndex);

        [MonoPInvokeCallback(typeof(SlotPresentationFailedDelegate))]
        private static void OnSlotPresentationFailed(
            int instanceId, int slotIndex, int errorCode, string errorMessage)
            => (Find(instanceId) as InFeedAdClient)
                ?.HandleSlotPresentationFailed(
                    slotIndex, errorCode, errorMessage);
    }
}
