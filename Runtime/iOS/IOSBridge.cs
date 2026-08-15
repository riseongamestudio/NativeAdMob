using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using AOT;

namespace RiseOn.NativeAdMob {
    // The loading and paid callbacks are shared by both client kinds; the
    // trampolines reach them through this without caring which one answered.
    internal interface IIOSSharedHandlers {
        void HandleLoadingStarted();
        void HandleLoadingCompleted(int errorCode, string errorMessage);
        void HandleAdPaid(
            string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision);
    }

    /// <summary>
    /// Transport C# ↔ Obj-C++. Callback từ native về qua trampoline static
    /// (yêu cầu của IL2CPP), tra client theo instanceId rồi giao lại; core
    /// wrapper tự marshal về Unity thread.
    /// </summary>
    internal static class IOSBridge {
        internal delegate void LoadingStartedDelegate(int instanceId);
        internal delegate void LoadingCompletedDelegate(
            int instanceId, int errorCode, string errorMessage);
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
          , [MarshalAs(UnmanagedType.I1)] bool adConsumed);
        internal delegate void SlotDisplayedDelegate(
            int instanceId, int slotIndex);
        internal delegate void SlotShowNotReadyDelegate(
            int instanceId, int slotIndex);
        internal delegate void SlotPresentationFailedDelegate(
            int instanceId, int slotIndex, int errorCode, string errorMessage);

        [DllImport("__Internal")]
        internal static extern IntPtr RONativeAdMobInFeed_Create(
            string adUnitId
          , int slotCount
          , int cacheSize
          , float backgroundAlpha
          , int instanceId);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobInFeed_SetListener(
            IntPtr handle
          , LoadingStartedDelegate loadingStarted
          , LoadingCompletedDelegate loadingCompleted
          , AdPaidDelegate adPaid
          , SlotDisplayedDelegate slotDisplayed
          , SlotShowNotReadyDelegate slotShowNotReady
          , SlotPresentationFailedDelegate slotPresentationFailed);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobInFeed_Configure(
            IntPtr handle
          , int slotIndex
          , int xPx
          , int yPx
          , int widthPx
          , int heightPx);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobInFeed_Show(
            IntPtr handle, int slotIndex);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobInFeed_Hide(
            IntPtr handle, int slotIndex);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobInFeed_SetPosition(
            IntPtr handle, int slotIndex, int xPx, int yPx);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobInFeed_Release(IntPtr handle);

        [DllImport("__Internal")]
        internal static extern IntPtr RONativeAdMobFullScreen_Create(
            string adUnitId, int instanceId);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_SetListener(
            IntPtr handle
          , LoadingStartedDelegate loadingStarted
          , LoadingCompletedDelegate loadingCompleted
          , AdPaidDelegate adPaid
          , DisplayedDelegate displayed
          , PresentationFailedDelegate presentationFailed
          , StateChangedDelegate stateChanged
          , ShowNotReadyDelegate showNotReady);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_Configure(
            IntPtr handle
          , [MarshalAs(UnmanagedType.I1)] bool fullscreen
          , int countdownSec
          , [MarshalAs(UnmanagedType.I1)] bool xRandomSide
          , [MarshalAs(UnmanagedType.I1)] bool numberOppositeSide
          , float heightRatio
          , float backgroundAlpha);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_SetCountdownSec(
            IntPtr handle, int countdownSec);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_LoadAd(IntPtr handle);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_ShowAd(
            IntPtr handle, int showId, ShowCompletedDelegate onCompleted);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_HideAd(IntPtr handle);
        [DllImport("__Internal")]
        internal static extern void RONativeAdMobFullScreen_Release(IntPtr handle);

        // Trampoline giữ tham chiếu static để GC không thu delegate mà
        // native còn đang cầm con trỏ.
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
            => (Find(instanceId) as IIOSSharedHandlers)?.HandleLoadingStarted();

        [MonoPInvokeCallback(typeof(LoadingCompletedDelegate))]
        private static void OnLoadingCompleted(
            int instanceId, int errorCode, string errorMessage)
            => (Find(instanceId) as IIOSSharedHandlers)
                ?.HandleLoadingCompleted(errorCode, errorMessage);

        [MonoPInvokeCallback(typeof(AdPaidDelegate))]
        private static void OnAdPaid(
            int instanceId
          , string source
          , string adUnitId
          , double value
          , string currencyCode
          , int precision)
            => (Find(instanceId) as IIOSSharedHandlers)?.HandleAdPaid(
                source, adUnitId, value, currencyCode, precision);

        [MonoPInvokeCallback(typeof(DisplayedDelegate))]
        private static void OnDisplayed(int instanceId)
            => (Find(instanceId) as IOSOverlayClient)?.HandleDisplayed();

        [MonoPInvokeCallback(typeof(PresentationFailedDelegate))]
        private static void OnPresentationFailed(
            int instanceId, int errorCode, string errorMessage)
            => (Find(instanceId) as IOSOverlayClient)
                ?.HandlePresentationFailed(errorCode, errorMessage);

        [MonoPInvokeCallback(typeof(StateChangedDelegate))]
        private static void OnStateChanged(
            int instanceId, bool isReady, bool isLoading)
            => (Find(instanceId) as IOSOverlayClient)
                ?.HandleStateChanged(isReady, isLoading);

        [MonoPInvokeCallback(typeof(ShowNotReadyDelegate))]
        private static void OnShowNotReady(int instanceId)
            => (Find(instanceId) as IOSOverlayClient)?.HandleShowNotReady();

        [MonoPInvokeCallback(typeof(ShowCompletedDelegate))]
        private static void OnShowCompleted(
            int instanceId, int showId, string errorMessage, bool adConsumed)
            => (Find(instanceId) as IOSOverlayClient)
                ?.HandleShowCompleted(showId, errorMessage, adConsumed);

        [MonoPInvokeCallback(typeof(SlotDisplayedDelegate))]
        private static void OnSlotDisplayed(int instanceId, int slotIndex)
            => (Find(instanceId) as IOSInFeedClient)
                ?.HandleSlotDisplayed(slotIndex);

        [MonoPInvokeCallback(typeof(SlotShowNotReadyDelegate))]
        private static void OnSlotShowNotReady(int instanceId, int slotIndex)
            => (Find(instanceId) as IOSInFeedClient)
                ?.HandleSlotShowNotReady(slotIndex);

        [MonoPInvokeCallback(typeof(SlotPresentationFailedDelegate))]
        private static void OnSlotPresentationFailed(
            int instanceId, int slotIndex, int errorCode, string errorMessage)
            => (Find(instanceId) as IOSInFeedClient)
                ?.HandleSlotPresentationFailed(
                    slotIndex, errorCode, errorMessage);
    }
}
