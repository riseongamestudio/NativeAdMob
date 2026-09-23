package com.riseon.nativeadmob;

import android.graphics.Rect;
import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.ads.MediaContent;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.NativeAd;

import java.text.Normalizer;
import java.util.ArrayList;

final class InFeedAdLayoutValidator {
    private static final String TAG = "InFeedAd";
    private static final float MIN_VIDEO_MEDIA_DP = 120f;
    private static final int MIN_VIDEO_LONG_SIDE_PX = 256;
    private static final int MIN_VISIBLE_ASSET_SIZE_PX = 1;
    private static final int MIN_BADGE_SIZE_PX = 15;
    private static final int SINGLE_WIDTH_TEXT_UNITS = 1;
    private static final int DOUBLE_WIDTH_TEXT_UNITS = 2;
    private static final double VIDEO_SIZE_TOLERANCE_PX = 0.5d;
    private static final float TEXT_BOUNDS_TOLERANCE_PX = 1f;
    private static final float UNKNOWN_VIDEO_ASPECT_RATIO = 1f;
    private static final float MIN_MEDIA_ASPECT_RATIO = 0.2f;
    private static final float MAX_MEDIA_ASPECT_RATIO = 5f;
    private static final long GEOMETRY_HASH_OFFSET = 1469598103934665603L;
    private static final long GEOMETRY_HASH_PRIME = 1099511628211L;
    private static final int NULL_VIEW_SIGNATURE = -1;
    private static final int INVALID_VIEW_BOUNDS_SIGNATURE = -2;

    private final NativeAd nativeAd;
    private final float density;
    private final InFeedAdViewFactory viewFactory;
    private String lastFailureReason;

    InFeedAdLayoutValidator(
            NativeAd nativeAd
          , float density
          , InFeedAdViewFactory viewFactory) {
        this.nativeAd = nativeAd;
        this.density = density;
        this.viewFactory = viewFactory;
    }

    boolean ValidateAssetGeometry(
            View rootView
          , InFeedAdViewFactory.AssetViews views
          , InFeedAdLayoutEngine.LayoutPlan plan
          , boolean logFailure) {
        lastFailureReason = null;
        if (!(rootView instanceof ViewGroup)
                || views == null
                || plan == null) {
            return ValidationFailure(
                    logFailure
                  , "missing layout root or assets");
        }
        ViewGroup root = (ViewGroup) rootView;
        int rootWidth = root.getWidth() > 0
                ? root.getWidth()
                : root.getMeasuredWidth();
        int rootHeight = root.getHeight() > 0
                ? root.getHeight()
                : root.getMeasuredHeight();
        if (rootWidth <= 0 || rootHeight <= 0) {
            return ValidationFailure(logFailure, "layout has zero size");
        }
        View adChoicesView = ResolveAdChoicesView(root, views);

        ArrayList<View> visibleAssets = new ArrayList<>();
        if (TextUtils.isEmpty(nativeAd.getHeadline())
                || !AddValidatedAsset(
                        root
                      , views.headline
                      , "headline"
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , visibleAssets
                      , logFailure)
                || !ValidatePolicyVisibleText(
                        views.headline
                        , "headline"
                        , logFailure)) {
            return false;
        }

        if (plan.showBody
                && !TextUtils.isEmpty(nativeAd.getBody())
                && (!AddValidatedAsset(
                            root
                          , views.body
                          , "body"
                          , MIN_VISIBLE_ASSET_SIZE_PX
                          , MIN_VISIBLE_ASSET_SIZE_PX
                          , visibleAssets
                          , logFailure)
                    || !ValidatePolicyVisibleText(
                            views.body
                            , "body"
                            , logFailure))) {
            return false;
        }
        if (TextUtils.isEmpty(nativeAd.getCallToAction())
                || !AddValidatedAsset(
                        root
                      , views.callToAction
                      , "call to action"
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , viewFactory.CallToActionHeightPx(plan)
                      , visibleAssets
                      , logFailure)
                || !ValidatePolicyVisibleText(
                        views.callToAction
                        , "call to action"
                        , logFailure)) {
            return false;
        }
        if (plan.showIcon
                && viewFactory.HasRenderableIcon()
                && !AddValidatedAsset(
                        root
                      , views.icon
                      , "icon"
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , visibleAssets
                      , logFailure)) {
            return false;
        }

        if (!AddValidatedAsset(
                root
              , views.attribution
                      , "ad attribution"
                      , MIN_BADGE_SIZE_PX
                      , MIN_BADGE_SIZE_PX
              , visibleAssets
              , logFailure)) {
            return false;
        }
        if (nativeAd.getAdChoicesInfo() != null
                && !AddValidatedAsset(
                        root
                      , adChoicesView
                      , "AdChoices"
                      , adChoicesView == views.adChoicesReserve
                                ? MIN_BADGE_SIZE_PX
                                : MIN_VISIBLE_ASSET_SIZE_PX
                      , adChoicesView == views.adChoicesReserve
                                ? MIN_BADGE_SIZE_PX
                                : MIN_VISIBLE_ASSET_SIZE_PX
                      , visibleAssets
                      , logFailure)) {
            return false;
        }

        if (plan.renderVideo) {
            if (!AddValidatedAsset(
                    root
                  , views.media
                  , "MediaView"
                  , Dp(MIN_VIDEO_MEDIA_DP)
                  , Dp(MIN_VIDEO_MEDIA_DP)
                  , visibleAssets
                  , logFailure)) {
                return false;
            }
            Rect mediaRect = DescendantBounds(root, views.media);
            if (mediaRect == null
                    || !IsPolicySafeVideoSize(
                            mediaRect.width()
                          , mediaRect.height())) {
                return ValidationFailure(
                        logFailure
                      , "fitted video is below the 120dp/256px minimum");
            }
        } else if (plan.showMedia
                && !AddValidatedAsset(
                        root
                      , views.media
                      , "main image"
                      , viewFactory.MediaPolicyFloorPx()
                      , viewFactory.MediaPolicyFloorPx()
                      , visibleAssets
                      , logFailure)) {
            // Video's floor for a video creative, the image floor otherwise:
            // the SDK's 120dp warning is conditional on the view showing
            // video, and a creative with none never will.
            return false;
        }

        if (plan.showAdvertiser
                && views.advertiser != null
                && (!AddValidatedAsset(
                            root
                          , views.advertiser
                          , "advertiser"
                          , MIN_VISIBLE_ASSET_SIZE_PX
                          , MIN_VISIBLE_ASSET_SIZE_PX
                          , visibleAssets
                          , logFailure)
                    || !ValidatePolicyVisibleText(
                            views.advertiser
                            , "advertiser"
                            , logFailure))) {
            return false;
        }
        if (plan.showRating
                && views.rating != null
                && !AddValidatedAsset(
                        root
                      , views.rating
                      , "star rating"
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , MIN_VISIBLE_ASSET_SIZE_PX
                      , visibleAssets
                      , logFailure)) {
            return false;
        }

        for (int first = 0; first < visibleAssets.size(); ++first) {
            Rect firstRect = DescendantBounds(
                    root
                  , visibleAssets.get(first));
            for (int second = first + 1;
                 second < visibleAssets.size();
                 ++second) {
                Rect secondRect = DescendantBounds(
                        root
                      , visibleAssets.get(second));
                if (firstRect != null
                        && secondRect != null
                        && Rect.intersects(firstRect, secondRect)
                        && !IsAllowedBadgeOverlay(
                                visibleAssets.get(first)
                              , visibleAssets.get(second)
                              , views
                              , adChoicesView)
                        && !IsAllowedScrimOverlay(
                                visibleAssets.get(first)
                              , visibleAssets.get(second)
                              , views)) {
                    return ValidationFailure(
                            logFailure
                          , "registered assets overlap");
                }
            }
        }
        return true;
    }

    String GetLastFailureReason() {
        return lastFailureReason;
    }

    // A badge is drawn on top with its own opaque background, so it can sit over
    // a picture without hiding anything that has to be read, and the asset
    // underneath still has to keep its own visible area through
    // AddValidatedAsset. Text is the exception: a headline or a call to action
    // with a badge across it cannot be read at all, so those overlaps stay
    // rejected and the candidate is discarded.
    private static boolean IsAllowedBadgeOverlay(
            View first
          , View second
          , InFeedAdViewFactory.AssetViews views
          , View adChoicesView) {
        return IsBadge(first, views, adChoicesView)
                        && !IsReadableText(second, views)
                || IsBadge(second, views, adChoicesView)
                        && !IsReadableText(first, views);
    }

    // Anything inside the scrim block sits over the full-cell veil the
    // media-background template paints, so it may lie over the media without
    // costing readability - the same warrant the badges carry. The scrim
    // only exists on that template.
    private static boolean IsAllowedScrimOverlay(
            View first
          , View second
          , InFeedAdViewFactory.AssetViews views) {
        if (views.scrim == null) return false;

        return first == views.media && IsInsideScrim(second, views)
                || second == views.media && IsInsideScrim(first, views);
    }

    private static boolean IsInsideScrim(
            View view
          , InFeedAdViewFactory.AssetViews views) {
        if (view == views.scrim) return true;

        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent == views.scrim) return true;
            parent = ((View) parent).getParent();
        }
        return false;
    }

    private static boolean IsReadableText(
            View view
          , InFeedAdViewFactory.AssetViews views) {
        return view == views.headline
                || view == views.body
                || view == views.advertiser
                || view == views.rating
                || view == views.callToAction;
    }

    private static boolean IsBadge(
            View view
          , InFeedAdViewFactory.AssetViews views
          , View adChoicesView) {
        return view == views.attribution || view == adChoicesView;
    }

    long GeometrySignature(
            View host
          , ViewGroup root
          , InFeedAdViewFactory.AssetViews views) {
        long signature = GEOMETRY_HASH_OFFSET;
        signature = MixGeometry(signature, root.getWidth());
        signature = MixGeometry(signature, root.getHeight());
        signature = MixGeometry(signature, host.getLeft());
        signature = MixGeometry(signature, host.getTop());
        signature = MixViewGeometry(signature, root, views.headline);
        signature = MixViewGeometry(signature, root, views.body);
        signature = MixViewGeometry(signature, root, views.advertiser);
        signature = MixViewGeometry(signature, root, views.rating);
        signature = MixViewGeometry(signature, root, views.icon);
        signature = MixViewGeometry(signature, root, views.callToAction);
        signature = MixViewGeometry(signature, root, views.media);
        signature = MixViewGeometry(signature, root, views.attribution);
        return MixViewGeometry(
                signature
              , root
              , ResolveAdChoicesView(root, views));
    }

    int RequiredVideoWidth(float creativeAspect) {
        int minimumSide = Dp(MIN_VIDEO_MEDIA_DP);
        int minimumContentWidth;
        if (creativeAspect >= 1f) {
            minimumContentWidth = MIN_VIDEO_LONG_SIDE_PX;
        } else {
            minimumContentWidth = (int) Math.ceil(
                    MIN_VIDEO_LONG_SIDE_PX * (double) creativeAspect);
        }
        return Math.max(minimumSide, minimumContentWidth);
    }

    int RequiredVideoHeight(float creativeAspect) {
        int minimumSide = Dp(MIN_VIDEO_MEDIA_DP);
        int minimumContentHeight;
        if (creativeAspect >= 1f) {
            minimumContentHeight = (int) Math.ceil(
                    MIN_VIDEO_LONG_SIDE_PX / (double) creativeAspect);
        } else {
            minimumContentHeight = MIN_VIDEO_LONG_SIDE_PX;
        }
        return Math.max(minimumSide, minimumContentHeight);
    }

    boolean IsPolicySafeVideoSize(int width, int height) {
        int minSide = Dp(MIN_VIDEO_MEDIA_DP);
        if (width < minSide || height < minSide) return false;

        float aspect = VideoAspectRatio();
        double fittedWidth = width;
        double fittedHeight = fittedWidth / aspect;
        if (fittedHeight > height) {
            fittedHeight = height;
            fittedWidth = fittedHeight * aspect;
        }
        return Math.max(fittedWidth, fittedHeight)
                + VIDEO_SIZE_TOLERANCE_PX
                >= MIN_VIDEO_LONG_SIDE_PX;
    }

    private long MixViewGeometry(
            long signature
          , ViewGroup root
          , View view) {
        if (view == null) {
            return MixGeometry(signature, NULL_VIEW_SIGNATURE);
        }

        signature = MixGeometry(signature, view.getVisibility());
        Rect rect = DescendantBounds(root, view);
        if (rect == null) {
            return MixGeometry(
                    signature
                  , INVALID_VIEW_BOUNDS_SIGNATURE);
        }

        signature = MixGeometry(signature, rect.left);
        signature = MixGeometry(signature, rect.top);
        signature = MixGeometry(signature, rect.right);
        signature = MixGeometry(signature, rect.bottom);
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            signature = MixGeometry(signature, text.getText().length());
            Layout layout = text.getLayout();
            if (layout == null) {
                return MixGeometry(signature, NULL_VIEW_SIGNATURE);
            }
            signature = MixGeometry(signature, layout.getLineCount());
            for (int line = 0; line < layout.getLineCount(); ++line) {
                signature = MixGeometry(
                        signature
                      , layout.getEllipsisStart(line));
                signature = MixGeometry(
                        signature
                      , layout.getEllipsisCount(line));
            }
        }
        return signature;
    }

    private static long MixGeometry(long signature, int value) {
        return (signature ^ value) * GEOMETRY_HASH_PRIME;
    }

    private boolean AddValidatedAsset(
            ViewGroup root
          , View asset
          , String name
          , int minWidth
          , int minHeight
          , ArrayList<View> assets
          , boolean logFailure) {
        Rect rect = DescendantBounds(root, asset);
        if (rect == null
                || rect.width() < minWidth
                || rect.height() < minHeight) {
            return ValidationFailure(
                    logFailure
                  , name + " is missing or too small");
        }
        int rootWidth = root.getWidth() > 0
                ? root.getWidth()
                : root.getMeasuredWidth();
        int rootHeight = root.getHeight() > 0
                ? root.getHeight()
                : root.getMeasuredHeight();
        if (rect.left < 0
                || rect.top < 0
                || rect.right > rootWidth
                || rect.bottom > rootHeight) {
            return ValidationFailure(logFailure, name + " is clipped");
        }
        if (asset instanceof TextView) {
            TextView text = (TextView) asset;
            Layout layout = text.getLayout();
            if (layout != null
                    && layout.getHeight()
                    > text.getHeight()
                    - text.getPaddingTop()
                    - text.getPaddingBottom()) {
                return ValidationFailure(
                        logFailure
                      , name + " text is clipped");
            }
            if (layout != null) {
                float layoutWidth = layout.getWidth();
                for (int line = 0;
                     line < layout.getLineCount();
                     ++line) {
                    if (layout.getEllipsisCount(line) > 0) continue;
                    if (layout.getLineLeft(line)
                                    < -TEXT_BOUNDS_TOLERANCE_PX
                            || layout.getLineRight(line)
                                    > layoutWidth
                                            + TEXT_BOUNDS_TOLERANCE_PX) {
                        return ValidationFailure(
                                logFailure
                              , name + " text overflows horizontally");
                    }
                }
            }
        }
        assets.add(asset);
        return true;
    }

    // Either the value is on screen in full, or it is on a line that scrolls
    // and will be. A part of it followed by an ellipsis is not an outcome the
    // layout is allowed to settle for. The exemption is per text - the plan
    // may scroll its body while its headline is held to showing everything.
    private boolean ValidatePolicyVisibleText(
            TextView text
          , String name
          , boolean logFailure) {
        if (text.getEllipsize() == TextUtils.TruncateAt.MARQUEE) return true;

        Layout layout = text.getLayout();
        if (layout == null) {
            return ValidationFailure(
                    logFailure
                  , name + " has no measured text layout");
        }

        CharSequence sourceText = text.getText();
        int visibleEndOffset = sourceText.length();
        for (int line = 0; line < layout.getLineCount(); ++line) {
            if (layout.getEllipsisCount(line) <= 0) continue;

            int ellipsisOffset = layout.getLineStart(line)
                    + layout.getEllipsisStart(line);
            visibleEndOffset = Math.min(
                    visibleEndOffset
                  , Math.max(0, ellipsisOffset));
        }
        if (layout.getLineCount() > 0) {
            visibleEndOffset = Math.min(
                    visibleEndOffset
                  , Math.max(
                        0
                      , layout.getLineEnd(layout.getLineCount() - 1)));
        }
        if (visibleEndOffset >= sourceText.length()) return true;

        return ValidationFailure(
                logFailure
              , name + " shows only "
                        + CountPolicyTextUnits(sourceText, visibleEndOffset)
                        + " of "
                        + CountPolicyTextUnits(
                            sourceText
                          , sourceText.length())
                        + " policy units without scrolling");
    }

    private static int CountPolicyTextUnits(
            CharSequence text
          , int endOffset) {
        int safeEndOffset = Math.max(
                0
              , Math.min(endOffset, text.length()));
        int units = 0;
        for (int offset = 0; offset < safeEndOffset;) {
            int codePoint = Character.codePointAt(text, offset);
            int codePointLength = Character.charCount(codePoint);
            if (offset + codePointLength > safeEndOffset) break;
            units += IsDoubleWidthCodePoint(codePoint)
                    ? DOUBLE_WIDTH_TEXT_UNITS
                    : SINGLE_WIDTH_TEXT_UNITS;
            offset += codePointLength;
        }
        return units;
    }

    static String TruncatePolicyText(
            String value
          , int maximumPolicyUnits) {
        String normalized = Normalizer.normalize(
                value == null ? "" : value
              , Normalizer.Form.NFC);
        if (maximumPolicyUnits <= 0 || normalized.length() == 0) {
            return normalized;
        }

        int units = 0;
        int endOffset = 0;
        while (endOffset < normalized.length()) {
            int codePoint = normalized.codePointAt(endOffset);
            units += IsDoubleWidthCodePoint(codePoint)
                    ? DOUBLE_WIDTH_TEXT_UNITS
                    : SINGLE_WIDTH_TEXT_UNITS;
            endOffset += Character.charCount(codePoint);
            if (units >= maximumPolicyUnits) break;
        }
        if (endOffset >= normalized.length()) return normalized;
        return normalized.substring(0, endOffset);
    }

    private static boolean IsDoubleWidthCodePoint(int codePoint) {
        int eastAsianWidth = UCharacter.getIntPropertyValue(
                codePoint
              , UProperty.EAST_ASIAN_WIDTH);
        return eastAsianWidth == UCharacter.EastAsianWidth.WIDE
                || eastAsianWidth == UCharacter.EastAsianWidth.FULLWIDTH;
    }

    private static Rect DescendantBounds(
            ViewGroup root
          , View descendant) {
        if (descendant == null
                || descendant.getVisibility() != View.VISIBLE
                || descendant.getWidth() <= 0
                || descendant.getHeight() <= 0) {
            return null;
        }
        Rect rect = new Rect(
                0
              , 0
              , descendant.getWidth()
              , descendant.getHeight());
        try {
            root.offsetDescendantRectToMyCoords(descendant, rect);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return rect;
    }

    private boolean ValidationFailure(boolean logFailure, String reason) {
        lastFailureReason = reason;
        if (logFailure) {
            Log.e(TAG, "In-feed validation failed: " + reason);
        }
        return false;
    }

    private float VideoAspectRatio() {
        MediaContent content = nativeAd.getMediaContent();
        if (content != null && content.getAspectRatio() > 0f) {
            return Math.max(
                    MIN_MEDIA_ASPECT_RATIO
                  , Math.min(
                        MAX_MEDIA_ASPECT_RATIO
                      , content.getAspectRatio()));
        }
        return UNKNOWN_VIDEO_ASPECT_RATIO;
    }

    private static View ResolveAdChoicesView(
            ViewGroup root
          , InFeedAdViewFactory.AssetViews views) {
        if (root instanceof NativeAdView) {
            View sdkAdChoices =
                    ((NativeAdView) root).getAdChoicesView();
            if (DescendantBounds(root, sdkAdChoices) != null) {
                return sdkAdChoices;
            }
        }
        return views.adChoicesReserve;
    }

    private int Dp(float value) {
        return (int) Math.ceil(value * density);
    }
}
