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
typedef void (*RONativeAdMobLoadingStartedCallback)(int32_t instanceId);
typedef void (*RONativeAdMobLoadingCompletedCallback)(
        int32_t instanceId
      , int32_t errorCode
      , const char* errorMessage);
typedef void (*RONativeAdMobPaidCallback)(
        int32_t instanceId
      , const char* source
      , const char* adUnitId
      , double value
      , const char* currencyCode
      , int32_t precision);
typedef void (*RONativeAdMobDisplayedCallback)(int32_t instanceId);
typedef void (*RONativeAdMobPresentationFailedCallback)(
        int32_t instanceId
      , int32_t errorCode
      , const char* errorMessage);
typedef void (*RONativeAdMobStateChangedCallback)(
        int32_t instanceId
      , bool isReady
      , bool isLoading);
typedef void (*RONativeAdMobShowNotReadyCallback)(int32_t instanceId);

// The AdCompletedListener interface on Android. showId is the C#-side
// show generation, echoed back so a completion can only resolve the show
// that registered it.
typedef void (*RONativeAdMobShowCompletedCallback)(
        int32_t instanceId
      , int32_t showId
      , const char* errorMessage
      , bool adConsumed);

// The slot-indexed half of the InFeedListener interface on Android: one
// in-feed unit owns several display slots, and these callbacks say which
// one is speaking.
typedef void (*RONativeAdMobInFeedSlotDisplayedCallback)(
        int32_t instanceId
      , int32_t slotIndex);
typedef void (*RONativeAdMobInFeedSlotShowNotReadyCallback)(
        int32_t instanceId
      , int32_t slotIndex);
typedef void (*RONativeAdMobInFeedSlotPresentationFailedCallback)(
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
void* RONativeAdMobInFeed_Create(
        const char* adUnitId
      , int32_t slotCount
      , int32_t cacheSize
      , float backgroundAlpha
      , int32_t instanceId);
void RONativeAdMobInFeed_SetListener(
        void* handle
      , RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeAdMobInFeedSlotDisplayedCallback slotDisplayed
      , RONativeAdMobInFeedSlotShowNotReadyCallback slotShowNotReady
      , RONativeAdMobInFeedSlotPresentationFailedCallback slotPresentationFailed);
void RONativeAdMobInFeed_Configure(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx
      , int32_t widthPx
      , int32_t heightPx);
void RONativeAdMobInFeed_Show(void* handle, int32_t slotIndex);
void RONativeAdMobInFeed_Hide(void* handle, int32_t slotIndex);
void RONativeAdMobInFeed_SetPosition(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx);
void RONativeAdMobInFeed_Release(void* handle);

// ---------------------------------------------------------------------------
// Full screen - com.riseon.nativeadmob.Overlay.
// ---------------------------------------------------------------------------
void* RONativeAdMobOverlay_Create(const char* adUnitId, int32_t instanceId);
void RONativeAdMobOverlay_SetListener(
        void* handle
      , RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeAdMobDisplayedCallback displayed
      , RONativeAdMobPresentationFailedCallback presentationFailed
      , RONativeAdMobStateChangedCallback stateChanged
      , RONativeAdMobShowNotReadyCallback showNotReady);
void RONativeAdMobOverlay_Configure(
        void* handle
      , bool fullscreen
      , int32_t countdownSec
      , bool xRandomSide
      , bool numberOppositeSide
      , float heightRatio
      , float backgroundAlpha);
void RONativeAdMobOverlay_SetCountdownSec(void* handle, int32_t countdownSec);
void RONativeAdMobOverlay_LoadAd(void* handle);
void RONativeAdMobOverlay_ShowAd(
        void* handle
      , int32_t showId
      , RONativeAdMobShowCompletedCallback onCompleted);
void RONativeAdMobOverlay_HideAd(void* handle);
void RONativeAdMobOverlay_Release(void* handle);

#ifdef __cplusplus
}
#endif

#endif // RO_NATIVE_AD_MOB_BRIDGE_H
