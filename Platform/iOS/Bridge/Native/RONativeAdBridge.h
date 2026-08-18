// The contract between Unity C# (DllImport "__Internal") and the iOS native
// ad implementation. It mirrors, call for call, the surface C# already uses
// on Android through AndroidJavaObject - same names, same ordering, same
// semantics - so the C# layer stays one code path with two transports.
//
// Threading: every function may be called from Unity's thread; the native
// side hops to the main thread itself, exactly as the Java side posts to its
// main Handler. Callbacks may arrive on any thread; the C# proxies marshal
// them through MobileAdsEventExecutor, the same way the Android proxies do.
//
// Handles: Create returns a retained opaque handle. Release both tears the
// ad down and balances the retain; no call is valid on a handle after it.
//
// Strings: UTF-8, valid only for the duration of the callback invocation.

#ifndef RO_NATIVE_AD_MOB_BRIDGE_H
#define RO_NATIVE_AD_MOB_BRIDGE_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ---------------------------------------------------------------------------
// Load listener callbacks - the AdLoadListener interface on Android.
// instanceId is the C#-chosen identity of the managed wrapper; the native
// side stores and echoes it so a late callback can be matched (or dropped)
// against the wrapper generation, mirroring the Android proxy check.
// ---------------------------------------------------------------------------
typedef void (*RONativeAdLoadingStartedCallback)(int32_t instanceId);
typedef void (*RONativeAdLoadingCompletedCallback)(
        int32_t instanceId
      , int32_t errorCode
      , const char* errorMessage);
typedef void (*RONativeAdPaidCallback)(
        int32_t instanceId
      , const char* source
      , const char* adUnitId
      , double value
      , const char* currencyCode
      , int32_t precision);
typedef void (*RONativeAdDisplayedCallback)(int32_t instanceId);
typedef void (*RONativeAdPresentationFailedCallback)(
        int32_t instanceId
      , int32_t errorCode
      , const char* errorMessage);
typedef void (*RONativeAdStateChangedCallback)(
        int32_t instanceId
      , bool isReady
      , bool isLoading);
typedef void (*RONativeAdShowNotReadyCallback)(int32_t instanceId);

// The AdCompletedListener interface on Android. showId is the C#-side
// show generation, echoed back so a completion can only resolve the show
// that registered it.
typedef void (*RONativeAdShowCompletedCallback)(
        int32_t instanceId
      , int32_t showId
      , const char* errorMessage
      , bool adConsumed);

// The slot-indexed half of the InFeedListener interface on Android: one
// in-feed unit owns several display slots, and these callbacks say which
// one is speaking.
typedef void (*ROInFeedAdSlotDisplayedCallback)(
        int32_t instanceId
      , int32_t slotIndex);
typedef void (*ROInFeedAdSlotShowNotReadyCallback)(
        int32_t instanceId
      , int32_t slotIndex);
typedef void (*ROInFeedAdSlotPresentationFailedCallback)(
        int32_t instanceId
      , int32_t slotIndex
      , int32_t errorCode
      , const char* errorMessage);

// ---------------------------------------------------------------------------
// In-feed - com.riseon.nativeadmob.InFeed. One handle is one ad unit id with
// slotCount display slots; every slot operation names its slot. Positions and
// sizes are native screen pixels, exactly what Unity's Screen coordinates
// produce; the native side converts to points internally the way the Java
// side converts to dp.
// ---------------------------------------------------------------------------
void* ROInFeedAd_Create(
        const char* adUnitId
      , int32_t slotCount
      , int32_t cacheSize
      , float backgroundAlpha
      , int32_t instanceId);
void ROInFeedAd_SetListener(
        void* handle
      , RONativeAdLoadingStartedCallback loadingStarted
      , RONativeAdLoadingCompletedCallback loadingCompleted
      , RONativeAdPaidCallback adPaid
      , ROInFeedAdSlotDisplayedCallback slotDisplayed
      , ROInFeedAdSlotShowNotReadyCallback slotShowNotReady
      , ROInFeedAdSlotPresentationFailedCallback slotPresentationFailed);
void ROInFeedAd_Configure(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx
      , int32_t widthPx
      , int32_t heightPx);
void ROInFeedAd_Show(void* handle, int32_t slotIndex);
void ROInFeedAd_Hide(void* handle, int32_t slotIndex);
void ROInFeedAd_SetPosition(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx);
void ROInFeedAd_Release(void* handle);

// ---------------------------------------------------------------------------
// Full screen - com.riseon.nativeadmob.Overlay.
// ---------------------------------------------------------------------------
void* ROOverlayAd_Create(const char* adUnitId, int32_t instanceId);
void ROOverlayAd_SetListener(
        void* handle
      , RONativeAdLoadingStartedCallback loadingStarted
      , RONativeAdLoadingCompletedCallback loadingCompleted
      , RONativeAdPaidCallback adPaid
      , RONativeAdDisplayedCallback displayed
      , RONativeAdPresentationFailedCallback presentationFailed
      , RONativeAdStateChangedCallback stateChanged
      , RONativeAdShowNotReadyCallback showNotReady);
void ROOverlayAd_Configure(
        void* handle
      , bool fullscreen
      , float heightRatio
      , float backgroundAlpha
      , int32_t cacheSize
      , int32_t cooldown
      , int32_t closeSide
      , int32_t timerSide
      , bool redirectOnClose);
void ROOverlayAd_SetClose(
        void* handle
      , int32_t cooldown
      , int32_t closeSide
      , int32_t timerSide
      , bool redirectOnClose);
void ROOverlayAd_Load(void* handle);
void ROOverlayAd_Show(
        void* handle
      , int32_t showId
      , RONativeAdShowCompletedCallback onCompleted);
void ROOverlayAd_Hide(void* handle);
void ROOverlayAd_Release(void* handle);

#ifdef __cplusplus
}
#endif

#endif // RO_NATIVE_AD_MOB_BRIDGE_H
