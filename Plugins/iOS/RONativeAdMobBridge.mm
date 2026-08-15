// The transport between Unity C# and the two ad classes. Handles are
// retained bridge casts; Release both tears the ad down and balances the
// retain. Pixels from Unity convert to points here, once, at the boundary.

#import "RONativeAdMobBridge.h"

#import <UIKit/UIKit.h>

#import "RONativeAdMobFullScreen.h"
#import "RONativeAdMobInFeed.h"

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

void* RONativeAdMobInFeed_Create(
        const char* adUnitId
      , int32_t slotCount
      , int32_t cacheSize
      , float backgroundAlpha
      , int32_t instanceId) {
    NSString *unit = HBStringFromUtf8(adUnitId);
    if (unit.length == 0) return NULL;
    RONativeAdMobInFeed *ad =
            [[RONativeAdMobInFeed alloc] initWithAdUnitId:unit
                                                slotCount:slotCount
                                                cacheSize:cacheSize
                                          backgroundAlpha:backgroundAlpha
                                               instanceId:instanceId];
    return (void *)CFBridgingRetain(ad);
}

void RONativeAdMobInFeed_SetListener(
        void* handle
      , RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeAdMobInFeedSlotDisplayedCallback slotDisplayed
      , RONativeAdMobInFeedSlotShowNotReadyCallback slotShowNotReady
      , RONativeAdMobInFeedSlotPresentationFailedCallback slotPresentationFailed) {
    if (handle == NULL) return;
    RONativeAdMobInFeed *ad = (__bridge RONativeAdMobInFeed *)handle;
    RONativeAdMobInFeedListenerCallbacks callbacks;
    callbacks.loadingStarted = loadingStarted;
    callbacks.loadingCompleted = loadingCompleted;
    callbacks.adPaid = adPaid;
    callbacks.slotDisplayed = slotDisplayed;
    callbacks.slotShowNotReady = slotShowNotReady;
    callbacks.slotPresentationFailed = slotPresentationFailed;
    [ad setInFeedListenerCallbacks:callbacks];
}

void RONativeAdMobInFeed_Configure(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx
      , int32_t widthPx
      , int32_t heightPx) {
    if (handle == NULL) return;
    RONativeAdMobInFeed *ad = (__bridge RONativeAdMobInFeed *)handle;
    [ad configureSlot:slotIndex
                    x:HBPointsFromPixels(xPx)
                    y:HBPointsFromPixels(yPx)
                width:HBPointsFromPixels(widthPx)
               height:HBPointsFromPixels(heightPx)];
}

void RONativeAdMobInFeed_Show(void* handle, int32_t slotIndex) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobInFeed *)handle showSlot:slotIndex];
}

void RONativeAdMobInFeed_Hide(void* handle, int32_t slotIndex) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobInFeed *)handle hideSlot:slotIndex];
}

void RONativeAdMobInFeed_SetPosition(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobInFeed *)handle
            setSlot:slotIndex
          positionX:HBPointsFromPixels(xPx)
                  y:HBPointsFromPixels(yPx)];
}

void RONativeAdMobInFeed_Release(void* handle) {
    if (handle == NULL) return;
    RONativeAdMobInFeed *ad = (RONativeAdMobInFeed *)CFBridgingRelease(handle);
    [ad releaseAd];
}

// ---------------------------------------------------------------------------
// Full screen
// ---------------------------------------------------------------------------

void* RONativeAdMobFullScreen_Create(const char* adUnitId, int32_t instanceId) {
    NSString *unit = HBStringFromUtf8(adUnitId);
    if (unit.length == 0) return NULL;
    RONativeAdMobFullScreen *ad =
            [[RONativeAdMobFullScreen alloc] initWithAdUnitId:unit
                                                instanceId:instanceId];
    return (void *)CFBridgingRetain(ad);
}

void RONativeAdMobFullScreen_SetListener(
        void* handle
      , RONativeAdMobLoadingStartedCallback loadingStarted
      , RONativeAdMobLoadingCompletedCallback loadingCompleted
      , RONativeAdMobPaidCallback adPaid
      , RONativeAdMobDisplayedCallback displayed
      , RONativeAdMobPresentationFailedCallback presentationFailed
      , RONativeAdMobStateChangedCallback stateChanged
      , RONativeAdMobShowNotReadyCallback showNotReady) {
    if (handle == NULL) return;
    RONativeAdMobFullScreen *ad = (__bridge RONativeAdMobFullScreen *)handle;
    [ad setListenerCallbacks:HBMakeCallbacks(
            loadingStarted
          , loadingCompleted
          , adPaid
          , displayed
          , presentationFailed
          , stateChanged
          , showNotReady)];
}

void RONativeAdMobFullScreen_Configure(
        void* handle
      , bool fullscreen
      , int32_t countdownSec
      , bool xRandomSide
      , bool numberOppositeSide
      , float heightRatio
      , float backgroundAlpha) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobFullScreen *)handle
            configureWithFullscreen:fullscreen
                       countdownSec:countdownSec
                        xRandomSide:xRandomSide
                 numberOppositeSide:numberOppositeSide
                        heightRatio:heightRatio
                    backgroundAlpha:backgroundAlpha];
}

void RONativeAdMobFullScreen_SetCountdownSec(void* handle, int32_t countdownSec) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobFullScreen *)handle setCountdownSec:countdownSec];
}

void RONativeAdMobFullScreen_LoadAd(void* handle) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobFullScreen *)handle loadAd];
}

void RONativeAdMobFullScreen_ShowAd(
        void* handle
      , int32_t showId
      , RONativeAdMobShowCompletedCallback onCompleted) {
    if (handle == NULL) {
        if (onCompleted != NULL) {
            onCompleted(0, showId, "Ad released", false);
        }
        return;
    }
    [(__bridge RONativeAdMobFullScreen *)handle showAdWithShowId:showId
                                                  onCompleted:onCompleted];
}

void RONativeAdMobFullScreen_HideAd(void* handle) {
    if (handle == NULL) return;
    [(__bridge RONativeAdMobFullScreen *)handle hideAd];
}

void RONativeAdMobFullScreen_Release(void* handle) {
    if (handle == NULL) return;
    RONativeAdMobFullScreen *ad =
            (RONativeAdMobFullScreen *)CFBridgingRelease(handle);
    [ad releaseAd];
}
