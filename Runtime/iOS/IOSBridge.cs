#if UNITY_IOS
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using AOT;

namespace RiseOn.NativeAdMob {
    /// <summary>
    /// Transport C# ↔ Obj-C++ cho package native ad trên iOS — phản chiếu
    /// đúng bề mặt AndroidJavaObject đang dùng trên Android. Callback từ
    /// native về qua trampoline static (yêu cầu của IL2CPP), tra instance
    /// theo instanceId rồi giao lại cho wrapper, wrapper tự marshal về
    /// Unity thread như các proxy Android.
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
          , string currencyCode);
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

        private static readonly Dictionary<int, Ad> instances = new();
        private static readonly object registryLock = new();
        private static int nextInstanceId;

        internal static int Register(Ad ad) {
            lock (registryLock) {
                var instanceId = ++nextInstanceId;
                instances[instanceId] = ad;
                return instanceId;
            }
        }

        internal static void Unregister(int instanceId) {
            lock (registryLock) {
                instances.Remove(instanceId);
            }
        }

        private static Ad Find(int instanceId) {
            lock (registryLock) {
                return instances.TryGetValue(instanceId, out var ad)
                    ? ad
                    : null;
            }
        }

        [MonoPInvokeCallback(typeof(LoadingStartedDelegate))]
        private static void OnLoadingStarted(int instanceId)
            => Find(instanceId)?.IOSHandleLoadingStarted();

        [MonoPInvokeCallback(typeof(LoadingCompletedDelegate))]
        private static void OnLoadingCompleted(
            int instanceId, int errorCode, string errorMessage)
            => Find(instanceId)?.IOSHandleLoadingCompleted(
                errorCode, errorMessage);

        [MonoPInvokeCallback(typeof(AdPaidDelegate))]
        private static void OnAdPaid(
            int instanceId
          , string source
          , string adUnitId
          , double value
          , string currencyCode)
            => Find(instanceId)?.IOSHandleAdPaid(
                source, adUnitId, value, currencyCode);

        [MonoPInvokeCallback(typeof(DisplayedDelegate))]
        private static void OnDisplayed(int instanceId)
            => (Find(instanceId) as FullScreen)?.IOSHandleDisplayed();

        [MonoPInvokeCallback(typeof(PresentationFailedDelegate))]
        private static void OnPresentationFailed(
            int instanceId, int errorCode, string errorMessage)
            => (Find(instanceId) as FullScreen)?.IOSHandlePresentationFailed(
                errorCode, errorMessage);

        [MonoPInvokeCallback(typeof(StateChangedDelegate))]
        private static void OnStateChanged(
            int instanceId, bool isReady, bool isLoading)
            => Find(instanceId)?.IOSHandleStateChanged(isReady, isLoading);

        [MonoPInvokeCallback(typeof(ShowNotReadyDelegate))]
        private static void OnShowNotReady(int instanceId)
            => Find(instanceId)?.IOSHandleShowNotReady();

        [MonoPInvokeCallback(typeof(ShowCompletedDelegate))]
        private static void OnShowCompleted(
            int instanceId, int showId, string errorMessage, bool adConsumed)
            => (Find(instanceId) as FullScreen)
                ?.IOSHandleShowCompleted(showId, errorMessage, adConsumed);

        [MonoPInvokeCallback(typeof(SlotDisplayedDelegate))]
        private static void OnSlotDisplayed(int instanceId, int slotIndex)
            => (Find(instanceId) as InFeed)?.IOSHandleSlotDisplayed(slotIndex);

        [MonoPInvokeCallback(typeof(SlotShowNotReadyDelegate))]
        private static void OnSlotShowNotReady(int instanceId, int slotIndex)
            => (Find(instanceId) as InFeed)?.IOSHandleSlotShowNotReady(slotIndex);

        [MonoPInvokeCallback(typeof(SlotPresentationFailedDelegate))]
        private static void OnSlotPresentationFailed(
            int instanceId, int slotIndex, int errorCode, string errorMessage)
            => (Find(instanceId) as InFeed)?.IOSHandleSlotPresentationFailed(
                slotIndex, errorCode, errorMessage);
    }
}
#endif
