// The transport between Unity C# and the two ad classes. Handles are
// retained bridge casts; Release both tears the ad down and balances the
// retain. Pixels from Unity convert to points here, once, at the boundary.

#import "RONativeAdBridge.h"

#import <UIKit/UIKit.h>

#import "ROOverlayAd.h"
#import "ROInFeedAd.h"

static CGFloat HBPointsFromPixels(int32_t px) {
    return px / MAX(1, UIScreen.mainScreen.nativeScale);
}

static NSString *HBStringFromUtf8(const char *value) {
    return value == NULL ? @"" : [NSString stringWithUTF8String:value];
}

static RONativeAdListenerCallbacks HBMakeCallbacks(
        RONativeAdLoadingStartedCallback loadingStarted
      , RONativeAdLoadingCompletedCallback loadingCompleted
      , RONativeAdPaidCallback adPaid
      , RONativeAdDisplayedCallback displayed
      , RONativeAdPresentationFailedCallback presentationFailed
      , RONativeAdStateChangedCallback stateChanged
      , RONativeAdShowNotReadyCallback showNotReady) {
    RONativeAdListenerCallbacks callbacks;
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

void* ROInFeedAd_Create(
        const char* adUnitId
      , int32_t slotCount
      , int32_t cacheSize
      , float backgroundAlpha
      , int32_t instanceId) {
    NSString *unit = HBStringFromUtf8(adUnitId);
    if (unit.length == 0) return NULL;
    ROInFeedAd *ad =
            [[ROInFeedAd alloc] initWithAdUnitId:unit
                                                slotCount:slotCount
                                                cacheSize:cacheSize
                                          backgroundAlpha:backgroundAlpha
                                               instanceId:instanceId];
    return (void *)CFBridgingRetain(ad);
}

void ROInFeedAd_SetListener(
        void* handle
      , RONativeAdLoadingStartedCallback loadingStarted
      , RONativeAdLoadingCompletedCallback loadingCompleted
      , RONativeAdPaidCallback adPaid
      , ROInFeedAdSlotDisplayedCallback slotDisplayed
      , ROInFeedAdSlotShowNotReadyCallback slotShowNotReady
      , ROInFeedAdSlotPresentationFailedCallback slotPresentationFailed) {
    if (handle == NULL) return;
    ROInFeedAd *ad = (__bridge ROInFeedAd *)handle;
    ROInFeedAdListenerCallbacks callbacks;
    callbacks.loadingStarted = loadingStarted;
    callbacks.loadingCompleted = loadingCompleted;
    callbacks.adPaid = adPaid;
    callbacks.slotDisplayed = slotDisplayed;
    callbacks.slotShowNotReady = slotShowNotReady;
    callbacks.slotPresentationFailed = slotPresentationFailed;
    [ad setInFeedListenerCallbacks:callbacks];
}

void ROInFeedAd_Configure(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx
      , int32_t widthPx
      , int32_t heightPx) {
    if (handle == NULL) return;
    ROInFeedAd *ad = (__bridge ROInFeedAd *)handle;
    [ad configureSlot:slotIndex
                    x:HBPointsFromPixels(xPx)
                    y:HBPointsFromPixels(yPx)
                width:HBPointsFromPixels(widthPx)
               height:HBPointsFromPixels(heightPx)];
}

void ROInFeedAd_Show(void* handle, int32_t slotIndex) {
    if (handle == NULL) return;
    [(__bridge ROInFeedAd *)handle showSlot:slotIndex];
}

void ROInFeedAd_Hide(void* handle, int32_t slotIndex) {
    if (handle == NULL) return;
    [(__bridge ROInFeedAd *)handle hideSlot:slotIndex];
}

void ROInFeedAd_SetPosition(
        void* handle
      , int32_t slotIndex
      , int32_t xPx
      , int32_t yPx) {
    if (handle == NULL) return;
    [(__bridge ROInFeedAd *)handle
            setSlot:slotIndex
          positionX:HBPointsFromPixels(xPx)
                  y:HBPointsFromPixels(yPx)];
}

void ROInFeedAd_Release(void* handle) {
    if (handle == NULL) return;
    ROInFeedAd *ad = (ROInFeedAd *)CFBridgingRelease(handle);
    [ad releaseAd];
}

// ---------------------------------------------------------------------------
// Full screen
// ---------------------------------------------------------------------------

void* ROOverlayAd_Create(const char* adUnitId, int32_t instanceId) {
    NSString *unit = HBStringFromUtf8(adUnitId);
    if (unit.length == 0) return NULL;
    ROOverlayAd *ad =
            [[ROOverlayAd alloc] initWithAdUnitId:unit
                                                instanceId:instanceId];
    return (void *)CFBridgingRetain(ad);
}

void ROOverlayAd_SetListener(
        void* handle
      , RONativeAdLoadingStartedCallback loadingStarted
      , RONativeAdLoadingCompletedCallback loadingCompleted
      , RONativeAdPaidCallback adPaid
      , RONativeAdDisplayedCallback displayed
      , RONativeAdPresentationFailedCallback presentationFailed
      , RONativeAdStateChangedCallback stateChanged
      , RONativeAdShowNotReadyCallback showNotReady) {
    if (handle == NULL) return;
    ROOverlayAd *ad = (__bridge ROOverlayAd *)handle;
    [ad setListenerCallbacks:HBMakeCallbacks(
            loadingStarted
          , loadingCompleted
          , adPaid
          , displayed
          , presentationFailed
          , stateChanged
          , showNotReady)];
}

void ROOverlayAd_Configure(
        void* handle
      , bool fullscreen
      , float heightRatio
      , float backgroundAlpha
      , int32_t cacheSize
      , int32_t cooldown
      , int32_t closeSide
      , int32_t timerSide
      , bool redirectOnClose) {
    if (handle == NULL) return;
    [(__bridge ROOverlayAd *)handle
            configureWithFullscreen:fullscreen
                        heightRatio:heightRatio
                    backgroundAlpha:backgroundAlpha
                          cacheSize:cacheSize
                           cooldown:cooldown
                          closeSide:closeSide
                          timerSide:timerSide
                    redirectOnClose:redirectOnClose];
}

void ROOverlayAd_SetClose(
        void* handle
      , int32_t cooldown
      , int32_t closeSide
      , int32_t timerSide
      , bool redirectOnClose) {
    if (handle == NULL) return;
    [(__bridge ROOverlayAd *)handle setCloseWithCooldown:cooldown
                                               closeSide:closeSide
                                               timerSide:timerSide
                                         redirectOnClose:redirectOnClose];
}

void ROOverlayAd_Load(void* handle) {
    if (handle == NULL) return;
    [(__bridge ROOverlayAd *)handle load];
}

void ROOverlayAd_Show(
        void* handle
      , int32_t showId
      , RONativeAdShowCompletedCallback onCompleted) {
    if (handle == NULL) {
        if (onCompleted != NULL) {
            onCompleted(0, showId, "Ad released", false);
        }
        return;
    }
    [(__bridge ROOverlayAd *)handle showWithShowId:showId
                                                  onCompleted:onCompleted];
}

void ROOverlayAd_Hide(void* handle) {
    if (handle == NULL) return;
    [(__bridge ROOverlayAd *)handle hide];
}

void ROOverlayAd_Release(void* handle) {
    if (handle == NULL) return;
    ROOverlayAd *ad =
            (ROOverlayAd *)CFBridgingRelease(handle);
    [ad releaseAd];
}
