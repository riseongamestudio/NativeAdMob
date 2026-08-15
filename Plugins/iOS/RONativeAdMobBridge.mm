// The transport between Unity C# and the two ad classes. Handles are
// retained bridge casts; Release both tears the ad down and balances the
// retain. Pixels from Unity convert to points here, once, at the boundary.

#import "RONativeAdMobBridge.h"

#import <UIKit/UIKit.h>

#import "RONativeOverlayAdMob.h"
#import "RONativeInFeedAdMob.h"

static CGFloat HBPointsFromPixels(int32_t px) {
    return px / MAX(1, UIScreen.mainScreen.nativeScale);
}

static NSString *HBStringFromUtf8(const char *value) {
    return value == NULL ? @"" : [NSString stringWithUTF8String:value];
}

static RONativeAdMobListenerCallbacks HBMakeCallbacks(
        RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeAdMobDisplayedCallback displayed
      , RONativeAdMobPresentationFailedCallback presentationFailed
      , RONativeAdMobStateChangedCallback stateChanged
      , RONativeAdMobShowNotReadyCallback showNotReady) {
    RONativeAdMobListenerCallbacks callbacks;
    callbacks.loadingStarted = loadingStarted;
    callbacks.loadingCompleted = loadingCompleted;
    callbacks.adPaid = adPaid;
    callbacks.displayed = displayed;
    callbacks.presentationFailed = presentationFailed;
    callbacks.stateChanged = stateChanged;
    callbacks.showNotReady = showNotReady;
    return callbacks;
}

// ---------------------------------------------------------------------------
// In-feed
// ---------------------------------------------------------------------------

void* RONativeInFeedAdMob_Create(
        const char* adUnitId
      , int32_t slotCount
      , int32_t cacheSize
      , float backgroundAlpha
      , int32_t instanceId) {
    NSString *unit = HBStringFromUtf8(adUnitId);
    if (unit.length == 0) return NULL;
    RONativeInFeedAdMob *ad =
            [[RONativeInFeedAdMob alloc] initWithAdUnitId:unit
                                                slotCount:slotCount
                                                cacheSize:cacheSize
                                          backgroundAlpha:backgroundAlpha
                                               instanceId:instanceId];
    return (void *)CFBridgingRetain(ad);
}

void RONativeInFeedAdMob_SetListener(
        void* handle
      , RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeInFeedAdMobSlotDisplayedCallback slotDisplayed
      , RONativeInFeedAdMobSlotShowNotReadyCallback slotShowNotReady
      , RONativeInFeedAdMobSlotPresentationFailedCallback slotPresentationFailed) {
    if (handle == NULL) return;
    RONativeInFeedAdMob *ad = (__bridge RONativeInFeedAdMob *)handle;
    RONativeInFeedAdMobListenerCallbacks callbacks;
    callbacks.loadingStarted = loadingStarted;
    callbacks.loadingCompleted = loadingCompleted;
    callbacks.adPaid = adPaid;
    callbacks.slotDisplayed = slotDisplayed;
    callbacks.slotShowNotReady = slotShowNotReady;
    callbacks.slotPresentationFailed = slotPresentationFailed;
    [ad setInFeedListenerCallbacks:callbacks];
}

void RONativeInFeedAdMob_Configure(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx
      , int32_t widthPx
      , int32_t heightPx) {
    if (handle == NULL) return;
    RONativeInFeedAdMob *ad = (__bridge RONativeInFeedAdMob *)handle;
    [ad configureSlot:slotIndex
                    x:HBPointsFromPixels(xPx)
                    y:HBPointsFromPixels(yPx)
                width:HBPointsFromPixels(widthPx)
               height:HBPointsFromPixels(heightPx)];
}

void RONativeInFeedAdMob_Show(void* handle, int32_t slotIndex) {
    if (handle == NULL) return;
    [(__bridge RONativeInFeedAdMob *)handle showSlot:slotIndex];
}

void RONativeInFeedAdMob_Hide(void* handle, int32_t slotIndex) {
    if (handle == NULL) return;
    [(__bridge RONativeInFeedAdMob *)handle hideSlot:slotIndex];
}

void RONativeInFeedAdMob_SetPosition(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx) {
    if (handle == NULL) return;
    [(__bridge RONativeInFeedAdMob *)handle
            setSlot:slotIndex
          positionX:HBPointsFromPixels(xPx)
                  y:HBPointsFromPixels(yPx)];
}

void RONativeInFeedAdMob_Release(void* handle) {
    if (handle == NULL) return;
    RONativeInFeedAdMob *ad = (RONativeInFeedAdMob *)CFBridgingRelease(handle);
    [ad releaseAd];
}

// ---------------------------------------------------------------------------
// Full screen
// ---------------------------------------------------------------------------

void* RONativeOverlayAdMob_Create(const char* adUnitId, int32_t instanceId) {
    NSString *unit = HBStringFromUtf8(adUnitId);
    if (unit.length == 0) return NULL;
    RONativeOverlayAdMob *ad =
            [[RONativeOverlayAdMob alloc] initWithAdUnitId:unit
                                                instanceId:instanceId];
    return (void *)CFBridgingRetain(ad);
}

void RONativeOverlayAdMob_SetListener(
        void* handle
      , RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeAdMobDisplayedCallback displayed
      , RONativeAdMobPresentationFailedCallback presentationFailed
      , RONativeAdMobStateChangedCallback stateChanged
      , RONativeAdMobShowNotReadyCallback showNotReady) {
    if (handle == NULL) return;
    RONativeOverlayAdMob *ad = (__bridge RONativeOverlayAdMob *)handle;
    [ad setListenerCallbacks:HBMakeCallbacks(
            loadingStarted
          , loadingCompleted
          , adPaid
          , displayed
          , presentationFailed
          , stateChanged
          , showNotReady)];
}

void RONativeOverlayAdMob_Configure(
        void* handle
      , bool fullscreen
      , int32_t countdownSec
      , bool xRandomSide
      , bool numberOppositeSide
      , float heightRatio
      , float backgroundAlpha) {
    if (handle == NULL) return;
    [(__bridge RONativeOverlayAdMob *)handle
            configureWithFullscreen:fullscreen
                       countdownSec:countdownSec
                        xRandomSide:xRandomSide
                 numberOppositeSide:numberOppositeSide
                        heightRatio:heightRatio
                    backgroundAlpha:backgroundAlpha];
}

void RONativeOverlayAdMob_SetCountdownSec(void* handle, int32_t countdownSec) {
    if (handle == NULL) return;
    [(__bridge RONativeOverlayAdMob *)handle setCountdownSec:countdownSec];
}

void RONativeOverlayAdMob_LoadAd(void* handle) {
    if (handle == NULL) return;
    [(__bridge RONativeOverlayAdMob *)handle loadAd];
}

void RONativeOverlayAdMob_ShowAd(
        void* handle
      , int32_t showId
      , RONativeAdMobShowCompletedCallback onCompleted) {
    if (handle == NULL) {
        if (onCompleted != NULL) {
            onCompleted(0, showId, "Ad released", false);
        }
        return;
    }
    [(__bridge RONativeOverlayAdMob *)handle showAdWithShowId:showId
                                                  onCompleted:onCompleted];
}

void RONativeOverlayAdMob_HideAd(void* handle) {
    if (handle == NULL) return;
    [(__bridge RONativeOverlayAdMob *)handle hideAd];
}

void RONativeOverlayAdMob_Release(void* handle) {
    if (handle == NULL) return;
    RONativeOverlayAdMob *ad =
            (RONativeOverlayAdMob *)CFBridgingRelease(handle);
    [ad releaseAd];
}
