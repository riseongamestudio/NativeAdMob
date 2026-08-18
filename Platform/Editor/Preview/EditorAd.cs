using System;
using UnityEngine;
using UnityEngine.UI;
namespace RiseOn.NativeAdMob.Editor {
    // The in-Editor stand-in for a device native ad. It mirrors the device
    // renderer's representative layouts - the stacked overlay with its
    // full-bleed media and corner controls, and the in-feed cell as either
    // the scrim-over-media template or the media-top column - so a developer
    // sees in play mode roughly what the device will draw, without any of
    // the device's loading behaviour.
    internal sealed class EditorAd : MonoBehaviour {
        private enum InFeedTier {
            Compact
          , Regular
          , Roomy
        }

        private const string AD_OBJECT_NAME            = "Native Editor Ad";
        private const string BUILT_IN_FONT_NAME        = "LegacyRuntime.ttf";
        private const string AD_ATTRIBUTION_TEXT       = "Ad";
        private const string AD_CHOICES_TEXT           = "AdChoices";
        private const string HEADLINE_TEXT             = "Native Editor Ad";
        private const string ADVERTISER_TEXT           = "Google Mobile Ads placeholder";
        private const string STAR_RATING_TEXT          = "★★★★☆  4.0";
        private const string BODY_TEXT                 = "This GameObject previews the native ad layout in the Unity Editor.";
        private const string CALL_TO_ACTION_TEXT       = "Install";
        private const string IMAGE_MEDIA_TEXT          = "IMAGE MEDIA";
        private const string FLEXIBLE_MEDIA_TEXT       = "IMAGE / VIDEO MEDIA";
        private const string CLICK_LOG_TEXT            = "Native ad preview click";
        private const string TEST_AD_CLICK_URL         = "https://google.com";
        private const string AD_CHOICES_URL            = "https://support.google.com/My-Ad-Center-Help/answer/12155764";
        private const string AD_CHOICES_CLICK_LOG_TEXT = "Native AdChoices preview click";
        private const string PANEL_OBJECT_NAME         = "Native Ad Panel";
        private const string CONTENT_OBJECT_NAME       = "Content";
        private const string DETAILS_OBJECT_NAME       = "Details";
        private const string IDENTITY_ROW_OBJECT_NAME  = "Identity Row";
        private const string IDENTITY_TEXT_OBJECT_NAME = "Identity Text";
        private const string ICON_OBJECT_NAME          = "Icon";
        private const string ICON_TEXT                 = "APP";
        private const string MEDIA_OBJECT_NAME         = "Media";
        private const string SCRIM_OBJECT_NAME         = "Scrim";
        private const string CLOSE_OBJECT_NAME         = "Close";
        private const string COUNTDOWN_OBJECT_NAME     = "Countdown";
        private const string CLOSE_TEXT                = "×";

        private const int FULLSCREEN_CANVAS_SORTING_ORDER = short.MaxValue;
        private const int NON_FULLSCREEN_CANVAS_SORTING_ORDER =
                FULLSCREEN_CANVAS_SORTING_ORDER - 1;
        private const int IN_FEED_CANVAS_SORTING_ORDER =
                NON_FULLSCREEN_CANVAS_SORTING_ORDER - 1;
        // The device spends 8dp a side, not the 20dp gutters an early build
        // had - and only on text: the media bleeds edge to edge.
        private const int CONTENT_HORIZONTAL_PADDING_DP     = 8;
        private const int CONTENT_SPACING_DP                = 4;
        // The device's control strip: CONTROL_STRIP_HEIGHT_DP on Android,
        // kROControlStripHeight on iOS. Both are 30.
        private const int CONTROL_SIZE_DP                   = 30;
        private const int CONTROL_BADGE_SPACING_DP          = 2;
        // The mark Google draws in the corner. The Java side only reserves
        // room for it and never paints, so this is the preview's own
        // assumption: the AdChoices asset is a 15dp glyph, while the 24dp
        // square and 76px width the reserve asks for are the overlay's touch
        // area, not the drawing.
        private const int AD_CHOICES_SIZE_DP                = 15;
        // The device keeps close and timer this far from the right edge, as
        // a flat number: RIGHT_CONTROL_INSET_DP on Android. Deriving it from
        // the AdChoices size would push the controls further out every time
        // that assumption changed.
        private const int CONTROL_RIGHT_INSET_DP            = 18;
        private const int ATTRIBUTION_WIDTH_DP              = 24;
        private const int CONTROL_LEFT_INSET_DP =
                ATTRIBUTION_WIDTH_DP
              + CONTROL_BADGE_SPACING_DP;
        private const int ATTRIBUTION_HEIGHT_DP             = 18;
        private const int ATTRIBUTION_FONT_SIZE             = 10;
        private const int ICON_SIZE_DP                      = 36;
        private const int ICON_GAP_DP                       = 8;
        private const int ICON_FONT_SIZE                    = 10;
        private const float MAX_ICON_ROW_WIDTH_RATIO        = 0.33f;
        private const int MEDIA_MIN_SIZE_DP                 = 120;
        private const float SCRIM_ALPHA                     = 0.7f;
        // The device rule verbatim: a headline line beside an icon keeps at
        // least this width, otherwise the icon stands alone and the text
        // takes the full width below it.
        private const int MIN_ICON_ROW_TEXT_WIDTH_DP        = 72;
        private const int IN_FEED_CTA_MIN_HEIGHT_DP         = 18;
        private const int IN_FEED_CTA_MAX_HEIGHT_DP         = 36;
        private const int IN_FEED_COMPACT_FONT_SIZE         = 12;
        private const int IN_FEED_SECONDARY_FONT_SIZE       = 10;
        private const float IN_FEED_TEXT_HEIGHT_MULTIPLIER  = 1.2f;
        private const int IN_FEED_CTA_MIN_FONT_SIZE         = 10;
        private const int IN_FEED_ATTRIBUTION_MIN_FONT_SIZE = 5;
        private const int MIN_BADGE_SIZE_PX                 = 15;
        private const int IN_FEED_REGULAR_SHORT_SIDE_DP     = 150;
        private const int IN_FEED_ROOMY_SHORT_SIDE_DP       = 280;
        private const int IN_FEED_COMPACT_ICON_DP           = 24;
        private const int IN_FEED_REGULAR_ICON_DP           = 36;
        private const int IN_FEED_ROOMY_ICON_DP             = 48;
        private const int IN_FEED_COMPACT_GAP_DP            = 1;
        private const int IN_FEED_REGULAR_GAP_DP            = 3;
        private const int IN_FEED_ROOMY_GAP_DP              = 5;
        private const int FULLSCREEN_HEADLINE_FONT_SIZE     = 18;
        private const int COMPACT_HEADLINE_FONT_SIZE        = 15;
        private const int ADVERTISER_FONT_SIZE              = 12;
        private const int RATING_FONT_SIZE                  = 12;
        private const int BODY_FONT_SIZE                    = 13;
        private const int MEDIA_LABEL_FONT_SIZE             = 15;
        private const int CLOSE_FONT_SIZE                   = 16;
        private const int COUNTDOWN_FONT_SIZE               = 15;
        private const int CALL_TO_ACTION_FONT_SIZE          = 14;
        private const int CALL_TO_ACTION_HEIGHT_DP          = 44;
        private const float FULL_TIME_SCALE                 = 1f;
        private const float PAUSED_TIME_SCALE               = 0f;
        private const float CANVAS_MATCH_WIDTH_OR_HEIGHT    = 0.5f;
        private const float SCREEN_CHANGE_TOLERANCE         = 0.001f;
        private const float TEXT_HEIGHT_MULTIPLIER          = 1.5f;
        private const float ANDROID_BASELINE_DPI            = 160f;
        private const float MIN_DEVICE_SIMULATOR_DPI        = 160f;
        private const float IN_FEED_CTA_SHORT_SIDE_RATIO    = 0.18f;
        private const float IN_FEED_BADGE_SHORT_SIDE_RATIO  = 0.10f;
        private const float IN_FEED_ATTRIBUTION_ASPECT_RATIO = 4f / 3f;
        private const float IN_FEED_CTA_FONT_HEIGHT_RATIO   = 0.5f;
        // The scrim block's own inset from the cell's edges - a hair on a
        // small cell, a few points on a large one.
        private const float IN_FEED_SCRIM_PADDING_RATIO     = 0.015f;
        private const int   IN_FEED_SCRIM_PADDING_MIN_DP    = 1;
        private const int   IN_FEED_SCRIM_PADDING_MAX_DP    = 4;
        // Text is sized by the box it sits in. The same preview draws a 96dp
        // feed cell and a full screen, so one point size cannot serve both:
        // it looks lost in the roomy box and cramped in the tight one. These
        // are the share of the box a label's cap height should take, with a
        // floor so it stays legible and a ceiling so it never shouts.
        private const float ICON_TEXT_BOX_RATIO             = 0.26f;
        private const int   ICON_TEXT_MIN_SIZE              = 5;
        private const int   ICON_TEXT_MAX_SIZE              = 16;
        private const float CALL_TO_ACTION_TEXT_BOX_RATIO   = 0.40f;
        private const int   CALL_TO_ACTION_TEXT_MIN_SIZE    = 7;
        private const int   CALL_TO_ACTION_TEXT_MAX_SIZE    = 18;
        private const float MEDIA_LABEL_BOX_RATIO           = 0.085f;
        private const int   MEDIA_LABEL_MIN_SIZE            = 6;
        private const int   MEDIA_LABEL_MAX_SIZE            = 18;
        private const float ATTRIBUTION_TEXT_BOX_RATIO      = 0.62f;
        private const int   ATTRIBUTION_TEXT_MIN_SIZE       = 4;
        private const int   ATTRIBUTION_TEXT_MAX_SIZE       = 12;
        private const float CONTROL_GLYPH_BOX_RATIO         = 0.52f;
        private const int   CONTROL_GLYPH_MIN_SIZE          = 8;
        private const int   CONTROL_GLYPH_MAX_SIZE          = 24;
        private static readonly Vector2 ReferenceResolution =
                new(360f, 800f);
        private static readonly Color FullScreenPanelColor =
                Color.black;
        private static readonly Color CompactPanelColor =
                new(27f / 255f, 32f / 255f, 41f / 255f, 1f);
        private static readonly Color SecondaryTextColor =
                new(1f, 1f, 1f, 0.8f);
        private static readonly Color ControlColor =
                new(0f, 0f, 0f, 0.7f);
        private static readonly Color AttributionColor =
                new(1f, 0.76f, 0.03f, 1f);
        // The device call to action verbatim: #2196F3, bordered #1565C0.
        private static readonly Color CallToActionColor =
                new(0x21 / 255f, 0x96 / 255f, 0xF3 / 255f, 1f);
        private static readonly Color CallToActionBorderColor =
                new(0x15 / 255f, 0x65 / 255f, 0xC0 / 255f, 1f);
        private static readonly Color ElementOutlineColor =
                new(0f, 0f, 0f, 0.35f);

        private Action onDismissed;
        private EditorAdConfig config;
        private CanvasScaler canvasScaler;
        private RectTransform panel;
        private RectTransform content;
        private RectTransform attributionRect;
        private RectTransform adChoicesRect;
        private Image panelGraphic;
        private EditorAdaptiveLayoutGroup contentLayout;
        private LayoutElement mediaLayoutElement;
        private LayoutElement detailsLayoutElement;
        private VerticalLayoutGroup detailsLayout;
        private RectTransform identityRowRect;
        private RectTransform identityTextRect;
        private LayoutElement iconLayoutElement;
        private Text mediaLabel;
        private Text headlineText;
        private LayoutElement headlineLayoutElement;
        private Text callToActionText;
        private LayoutElement callToActionLayoutElement;
        private Text attributionText;
        private Button closeButton;
        private GameObject countdownControl;
        private Text countdownText;
        private bool closeControlOnLeft;
        private bool countdownControlOnLeft;
        private float countdownRemaining;
        private float lastUiScale = -1f;
        private int lastScreenWidth = -1;
        private int lastScreenHeight = -1;
        private Rect lastSafeArea;
        private bool pausesGame;
        private bool dismissed;
        private bool externalUrlOpening;
        private bool externalUrlFocusLost;

        internal static EditorAd Show(
            EditorAdConfig config
          , Action onDismissed) {
            var previewObject = new GameObject(
                AD_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(Canvas)
              , typeof(CanvasScaler)
              , typeof(GraphicRaycaster)
              , typeof(EditorAd));
            var preview = previewObject.GetComponent<EditorAd>();
            preview.Initialize(config, onDismissed);
            return preview;
        }

        internal void Dismiss() {
            Finish(true);
        }

        internal void Release() {
            Finish(false);
        }

        internal void SetVisible(bool visible) {
            if (!this || dismissed) return;

            gameObject.SetActive(visible);
        }

        internal void SetPosition(Vector2Int positionPx) {
            if (!this || dismissed || config == null) return;

            config.SetPosition(positionPx);
            RefreshResponsiveLayout(true);
        }

        private void Initialize(
            EditorAdConfig config
          , Action dismissedCallback) {
            this.config = config;
            onDismissed = dismissedCallback;
            pausesGame = config.PausesGame;

            var canvas = GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = ResolveCanvasSortingOrder(config.Mode);

            canvasScaler = GetComponent<CanvasScaler>();
            canvasScaler.uiScaleMode =
                    CanvasScaler.ScaleMode.ConstantPixelSize;
            canvasScaler.scaleFactor = ResolveUiScale();

            panel = CreatePanel(config);
            if (config.Mode == EditorAdMode.InFeed) {
                content = CreateInFeedContent(panel, config);
            } else {
                content = CreateContent(panel, config);
            }
            adChoicesRect = CreateBadges(panel);
            CreateControls(panel, config);
            Canvas.ForceUpdateCanvases();
            RefreshResponsiveLayout(true);

            if (pausesGame) Time.timeScale = PAUSED_TIME_SCALE;
        }

        private static int ResolveCanvasSortingOrder(
            EditorAdMode mode) {
            return mode switch {
                EditorAdMode.FullScreen =>
                        FULLSCREEN_CANVAS_SORTING_ORDER
              , EditorAdMode.Collapsible =>
                        NON_FULLSCREEN_CANVAS_SORTING_ORDER
              , _ => IN_FEED_CANVAS_SORTING_ORDER
            };
        }

        private RectTransform CreatePanel(
            EditorAdConfig config) {
            var panelObject = new GameObject(
                PANEL_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(CanvasRenderer)
              , typeof(Image)
              , typeof(Button));
            panelObject.transform.SetParent(transform, false);

            var panel = panelObject.GetComponent<RectTransform>();
            ApplyPanelRect(
                panel
              , config
              , ResolveUiScale()
              , 0f);

            panelGraphic = panelObject.GetComponent<Image>();
            var panelColor =
                    config.Mode == EditorAdMode.FullScreen
                            ? FullScreenPanelColor
                            : CompactPanelColor;
            panelGraphic.color = new(
                panelColor.r
              , panelColor.g
              , panelColor.b
              , config.BackgroundAlpha);
            var backgroundIsClickable =
                    config.Mode != EditorAdMode.InFeed;
            panelGraphic.raycastTarget = backgroundIsClickable;

            var panelButton = panelObject.GetComponent<Button>();
            panelButton.targetGraphic = panelGraphic;
            panelButton.transition = Selectable.Transition.None;
            panelButton.interactable = backgroundIsClickable;
            if (backgroundIsClickable) {
                panelButton.onClick.AddListener(
                    () => TryOpenUrl(
                        TEST_AD_CLICK_URL
                      , CLICK_LOG_TEXT));
            }
            return panel;
        }

        private static void ApplyPanelRect(
            RectTransform panel
          , EditorAdConfig config
          , float uiScale
          , float minimumPanelHeight) {
            if (config.Mode == EditorAdMode.FullScreen) {
                panel.anchorMin = Vector2.zero;
                panel.anchorMax = Vector2.one;
                panel.pivot = new(0.5f, 0.5f);
                panel.anchoredPosition = Vector2.zero;
                panel.sizeDelta = Vector2.zero;
                panel.offsetMin = Vector2.zero;
                panel.offsetMax = Vector2.zero;
                return;
            }

            if (config.Mode == EditorAdMode.Collapsible) {
                var heightRatio = config.HeightRatio;
                var logicalScreenHeight = Screen.height / uiScale;
                var resolvedHeight = Mathf.Min(
                    logicalScreenHeight
                  , Mathf.Max(
                        logicalScreenHeight * heightRatio
                      , minimumPanelHeight));
                panel.anchorMin = new(0f, 0f);
                panel.anchorMax = new(1f, 0f);
                panel.pivot = new(0.5f, 0f);
                panel.anchoredPosition = Vector2.zero;
                panel.sizeDelta = new(0f, resolvedHeight);
                return;
            }

            var screenWidth = Screen.width / uiScale;
            var screenHeight = Screen.height / uiScale;
            var width = Mathf.Min(
                screenWidth
              , Mathf.Max(1f, config.SizePx.x / uiScale));
            var height = Mathf.Min(
                screenHeight
              , Mathf.Max(1f, config.SizePx.y / uiScale));
            var x = Mathf.Clamp(
                config.PositionPx.x / uiScale
              , 0f
              , Mathf.Max(0f, screenWidth - width));
            var y = Mathf.Clamp(
                config.PositionPx.y / uiScale
              , 0f
              , Mathf.Max(0f, screenHeight - height));
            panel.anchorMin = new(0f, 1f);
            panel.anchorMax = new(0f, 1f);
            panel.pivot = new(0f, 1f);
            panel.anchoredPosition = new(
                x
              , -y);
            panel.sizeDelta = new(width, height);
        }

        // ------------------------------------------------------------------
        // Overlay content - the stacked layout the device renders: media
        // bleeding edge to edge, the identity row, the body and a full-width
        // call to action, with the corner controls overlaid at the top.
        // ------------------------------------------------------------------

        private RectTransform CreateContent(
            RectTransform panel
          , EditorAdConfig config) {
            var fullscreen = config.Mode == EditorAdMode.FullScreen;
            var contentObject = new GameObject(
                CONTENT_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(EditorAdaptiveLayoutGroup));
            contentObject.transform.SetParent(panel, false);

            var content = contentObject.GetComponent<RectTransform>();
            content.anchorMin = Vector2.zero;
            content.anchorMax = Vector2.one;
            // Both overlay modes share the device's inset: the media box is
            // an element like every other, 8dp off the sides, sitting below
            // the control band.
            content.offsetMin = new(
                CONTENT_HORIZONTAL_PADDING_DP
              , 0f);
            content.offsetMax = new(
                -CONTENT_HORIZONTAL_PADDING_DP
              , -CONTROL_SIZE_DP);

            contentLayout =
                    contentObject
                            .GetComponent<EditorAdaptiveLayoutGroup>();
            ConfigureContentLayout(contentLayout);
            if (fullscreen) {
                contentLayout.childAlignment = TextAnchor.LowerCenter;
            }

            var mediaText = config.AllowsVideo
                    ? FLEXIBLE_MEDIA_TEXT
                    : IMAGE_MEDIA_TEXT;
            mediaLayoutElement =
                    CreateMedia(
                        content
                      , mediaText
                      , MEDIA_MIN_SIZE_DP
                      , out mediaLabel);

            var detailsObject = new GameObject(
                DETAILS_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(VerticalLayoutGroup)
              , typeof(LayoutElement));
            detailsObject.transform.SetParent(content, false);
            detailsLayoutElement =
                    detailsObject.GetComponent<LayoutElement>();
            detailsLayoutElement.flexibleWidth = 1f;
            detailsLayout =
                    detailsObject.GetComponent<VerticalLayoutGroup>();
            detailsLayout.spacing = CONTENT_SPACING_DP;
            detailsLayout.childAlignment = TextAnchor.MiddleLeft;
            detailsLayout.childControlWidth = true;
            detailsLayout.childControlHeight = true;
            detailsLayout.childForceExpandWidth = true;
            detailsLayout.childForceExpandHeight = false;

            var identityTextParent =
                    CreateIdentityRow(detailsObject.transform);
            var headlineFontSize = fullscreen
                    ? FULLSCREEN_HEADLINE_FONT_SIZE
                    : COMPACT_HEADLINE_FONT_SIZE;
            headlineText = CreateText(
                identityTextParent
              , HEADLINE_TEXT
              , headlineFontSize
              , TextAnchor.MiddleLeft
              , Color.white);
            headlineLayoutElement =
                    headlineText.GetComponent<LayoutElement>();

            CreateText(
                identityTextParent
              , ADVERTISER_TEXT
              , ADVERTISER_FONT_SIZE
              , TextAnchor.MiddleLeft
              , SecondaryTextColor);
            CreateText(
                identityTextParent
              , STAR_RATING_TEXT
              , RATING_FONT_SIZE
              , TextAnchor.MiddleLeft
              , AttributionColor);
            CreateText(
                detailsObject.transform
              , BODY_TEXT
              , BODY_FONT_SIZE
              , TextAnchor.MiddleLeft
              , SecondaryTextColor);

            callToActionLayoutElement = CreateCallToAction(
                detailsObject.transform
              , config.AdUnitId
              , CALL_TO_ACTION_HEIGHT_DP
              , CALL_TO_ACTION_FONT_SIZE
              , out callToActionText);
            return content;
        }

        private static void ConfigureContentLayout(
            EditorAdaptiveLayoutGroup layout) {
            layout.IsVertical = true;
            layout.spacing = CONTENT_SPACING_DP;
            layout.childAlignment = TextAnchor.MiddleCenter;
            layout.childControlWidth = true;
            layout.childControlHeight = true;
            layout.childForceExpandWidth = true;
            layout.childForceExpandHeight = false;
        }

        private Transform CreateIdentityRow(Transform parent) {
            var identityRowObject = new GameObject(
                IDENTITY_ROW_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(HorizontalLayoutGroup)
              , typeof(LayoutElement));
            identityRowObject.transform.SetParent(parent, false);
            identityRowRect =
                    identityRowObject.GetComponent<RectTransform>();

            var identityRowLayout =
                    identityRowObject.GetComponent<HorizontalLayoutGroup>();
            identityRowLayout.spacing = ICON_GAP_DP;
            identityRowLayout.childAlignment = TextAnchor.MiddleLeft;
            identityRowLayout.childControlWidth = true;
            identityRowLayout.childControlHeight = true;
            identityRowLayout.childForceExpandWidth = false;
            identityRowLayout.childForceExpandHeight = false;

            var identityRowLayoutElement =
                    identityRowObject.GetComponent<LayoutElement>();
            identityRowLayoutElement.minHeight = ICON_SIZE_DP;
            identityRowLayoutElement.flexibleWidth = 1f;

            iconLayoutElement = CreateIcon(
                identityRowObject.transform
              , ICON_SIZE_DP);

            var identityTextObject = new GameObject(
                IDENTITY_TEXT_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(VerticalLayoutGroup)
              , typeof(LayoutElement));
            identityTextObject.transform.SetParent(
                identityRowObject.transform
              , false);
            identityTextRect =
                    identityTextObject.GetComponent<RectTransform>();

            var identityTextLayout =
                    identityTextObject.GetComponent<VerticalLayoutGroup>();
            identityTextLayout.childAlignment = TextAnchor.MiddleLeft;
            identityTextLayout.childControlWidth = true;
            identityTextLayout.childControlHeight = true;
            identityTextLayout.childForceExpandWidth = true;
            identityTextLayout.childForceExpandHeight = false;

            var identityTextLayoutElement =
                    identityTextObject.GetComponent<LayoutElement>();
            identityTextLayoutElement.flexibleWidth = 1f;
            return identityTextObject.transform;
        }

        private LayoutElement CreateIcon(
            Transform parent
          , int sizeDp) {
            var iconObject = new GameObject(
                ICON_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(Image)
              , typeof(Button)
              , typeof(LayoutElement));
            iconObject.transform.SetParent(parent, false);

            var image = iconObject.GetComponent<Image>();
            image.color = CallToActionColor;
            AddOutline(image, ElementOutlineColor);

            var button = iconObject.GetComponent<Button>();
            button.targetGraphic = image;
            button.onClick.AddListener(
                () => TryOpenUrl(
                    TEST_AD_CLICK_URL
                  , CLICK_LOG_TEXT));

            var layout = iconObject.GetComponent<LayoutElement>();
            layout.minWidth = sizeDp;
            layout.minHeight = sizeDp;
            layout.preferredWidth = sizeDp;
            layout.preferredHeight = sizeDp;

            var label = CreateOverlayText(
                iconObject.transform
              , ICON_TEXT
              , ICON_FONT_SIZE
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
            label.horizontalOverflow = HorizontalWrapMode.Overflow;
            ScaleToBox(
                label
              , label.rectTransform
              , ICON_TEXT_BOX_RATIO
              , ICON_TEXT_MIN_SIZE
              , ICON_TEXT_MAX_SIZE
              , followsShortSide: true);
            return layout;
        }

        private LayoutElement CreateMedia(
            Transform parent
          , string text
          , float minimumHeight
          , out Text label) {
            var mediaObject = new GameObject(
                MEDIA_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(CanvasRenderer)
              , typeof(EditorMediaGraphic)
              , typeof(Button)
              , typeof(LayoutElement));
            mediaObject.transform.SetParent(parent, false);

            var graphic =
                    mediaObject.GetComponent<EditorMediaGraphic>();
            graphic.raycastTarget = true;
            AddOutline(graphic, ElementOutlineColor);

            var button = mediaObject.GetComponent<Button>();
            button.targetGraphic = graphic;
            button.onClick.AddListener(
                () => TryOpenUrl(
                    TEST_AD_CLICK_URL
                  , CLICK_LOG_TEXT));

            var layout = mediaObject.GetComponent<LayoutElement>();
            layout.minHeight = minimumHeight;
            layout.flexibleHeight = 1f;

            label = CreateOverlayText(
                mediaObject.transform
              , text
              , MEDIA_LABEL_FONT_SIZE
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
            ScaleToBox(
                label
              , label.rectTransform
              , MEDIA_LABEL_BOX_RATIO
              , MEDIA_LABEL_MIN_SIZE
              , MEDIA_LABEL_MAX_SIZE
              , followsShortSide: true);
            return layout;
        }

        private LayoutElement CreateCallToAction(
            Transform parent
          , string adUnitId
          , int heightDp
          , int fontSize
          , out Text label) {
            var buttonObject = new GameObject(
                CALL_TO_ACTION_TEXT
              , typeof(RectTransform)
              , typeof(Image)
              , typeof(Button)
              , typeof(LayoutElement));
            buttonObject.transform.SetParent(parent, false);

            var image = buttonObject.GetComponent<Image>();
            image.color = CallToActionColor;
            AddOutline(image, CallToActionBorderColor);

            var button = buttonObject.GetComponent<Button>();
            button.targetGraphic = image;
            button.onClick.AddListener(
                () => TryOpenUrl(
                    TEST_AD_CLICK_URL
                  , $"{CLICK_LOG_TEXT}. Ad unit ID: {adUnitId}"));

            var layout = buttonObject.GetComponent<LayoutElement>();
            layout.minHeight = heightDp;
            layout.preferredHeight = heightDp;

            label = CreateOverlayText(
                buttonObject.transform
              , CALL_TO_ACTION_TEXT
              , fontSize
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
            ScaleToBox(
                label
              , label.rectTransform
              , CALL_TO_ACTION_TEXT_BOX_RATIO
              , CALL_TO_ACTION_TEXT_MIN_SIZE
              , CALL_TO_ACTION_TEXT_MAX_SIZE);
            return layout;
        }

        // ------------------------------------------------------------------
        // In-feed content - the two representative device templates. A
        // compact cell renders the scrim template: the media as the cell's
        // background under one full veil, the texts and button over it. A
        // larger cell renders the media-top column.
        // ------------------------------------------------------------------

        private RectTransform CreateInFeedContent(
            RectTransform panel
          , EditorAdConfig config) {
            var uiScale = ResolveUiScale();
            var cellWidth = Mathf.Max(1f, config.SizePx.x / uiScale);
            var cellHeight = Mathf.Max(1f, config.SizePx.y / uiScale);
            var shortSide = Mathf.Min(cellWidth, cellHeight);
            var tier = ResolveInFeedTier(shortSide);
            var gap = ResolveInFeedGap(tier);
            var headlineHeight =
                    IN_FEED_COMPACT_FONT_SIZE * TEXT_HEIGHT_MULTIPLIER;
            var callToActionBandHeight = Mathf.Clamp(
                shortSide * IN_FEED_CTA_SHORT_SIDE_RATIO
              , IN_FEED_CTA_MIN_HEIGHT_DP
              , IN_FEED_CTA_MAX_HEIGHT_DP);
            // The device rule: the scrim template is how media survives a
            // cell that cannot host a dedicated media band; any cell that
            // fits the band gets the media-top column.
            var mediaBandEstimate =
                    IN_FEED_MEDIA_FLOOR_DP
                  + headlineHeight
                  + callToActionBandHeight
                  + 4 * gap;
            var scrimTemplate = cellHeight < mediaBandEstimate
                    || cellWidth < IN_FEED_MEDIA_FLOOR_DP;

            // The media: the whole cell on the scrim template, the top band
            // of the column otherwise.
            var mediaObject = new GameObject(
                MEDIA_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(CanvasRenderer)
              , typeof(EditorMediaGraphic)
              , typeof(Button));
            mediaObject.transform.SetParent(panel, false);
            var mediaRect = mediaObject.GetComponent<RectTransform>();
            var mediaGraphic =
                    mediaObject.GetComponent<EditorMediaGraphic>();
            mediaGraphic.raycastTarget = true;
            AddOutline(mediaGraphic, ElementOutlineColor);
            var mediaButton = mediaObject.GetComponent<Button>();
            mediaButton.targetGraphic = mediaGraphic;
            mediaButton.onClick.AddListener(
                () => TryOpenUrl(
                    TEST_AD_CLICK_URL
                  , CLICK_LOG_TEXT));
            mediaLabel = CreateOverlayText(
                mediaObject.transform
              , config.AllowsVideo
                        ? FLEXIBLE_MEDIA_TEXT
                        : IMAGE_MEDIA_TEXT
              , MEDIA_LABEL_FONT_SIZE
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(mediaLabel.rectTransform);
            ScaleToBox(
                mediaLabel
              , mediaLabel.rectTransform
              , MEDIA_LABEL_BOX_RATIO
              , MEDIA_LABEL_MIN_SIZE
              , MEDIA_LABEL_MAX_SIZE
              , followsShortSide: true);

            if (scrimTemplate) {
                Stretch(mediaRect);

                // One veil over the whole cell, exactly like the device: no
                // uncovered strip above the text block.
                var scrimObject = new GameObject(
                    SCRIM_OBJECT_NAME
                  , typeof(RectTransform)
                  , typeof(Image));
                scrimObject.transform.SetParent(panel, false);
                Stretch(scrimObject.GetComponent<RectTransform>());
                var scrimImage = scrimObject.GetComponent<Image>();
                scrimImage.color = new(0f, 0f, 0f, SCRIM_ALPHA);
                scrimImage.raycastTarget = false;
            } else {
                // The media absorbs whatever the text stack leaves free,
                // the way the device's media-top column grows its picture
                // instead of keeping an empty band.
                var secondaryLineHeight =
                        (tier == InFeedTier.Compact
                                ? IN_FEED_SECONDARY_FONT_SIZE
                                : ADVERTISER_FONT_SIZE)
                        * IN_FEED_TEXT_HEIGHT_MULTIPLIER;
                var lowerEstimate =
                        IN_FEED_COMPACT_FONT_SIZE
                                * IN_FEED_TEXT_HEIGHT_MULTIPLIER
                      + secondaryLineHeight * 2f
                      + (tier != InFeedTier.Compact
                                ? secondaryLineHeight
                                : 0f)
                      + callToActionBandHeight
                      + 5 * gap
                      + 2 * Mathf.Max(gap, 2);
                mediaRect.anchorMin = new(0f, 1f);
                mediaRect.anchorMax = new(1f, 1f);
                mediaRect.pivot = new(0.5f, 1f);
                mediaRect.anchoredPosition = Vector2.zero;
                mediaRect.sizeDelta = new(
                    0f
                  , Mathf.Max(
                        IN_FEED_MEDIA_FLOOR_DP
                      , cellHeight - lowerEstimate));
            }

            // The bottom stack: icon by the device's shared-line floor, the
            // headline, the body on roomier tiers, then the button.
            var contentObject = new GameObject(
                CONTENT_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(VerticalLayoutGroup)
              , typeof(ContentSizeFitter));
            contentObject.transform.SetParent(panel, false);
            var content = contentObject.GetComponent<RectTransform>();
            content.anchorMin = new(0f, 0f);
            content.anchorMax = new(1f, 0f);
            content.pivot = new(0.5f, 0f);
            content.anchoredPosition = Vector2.zero;
            content.sizeDelta = new(0f, 0f);

            var stack = contentObject.GetComponent<VerticalLayoutGroup>();
            // The device spends nothing here: PaddingForTier returns 0 and
            // the comment says why - the slot is already bounded by the card
            // it sits in, so an inset buys nothing. Only the scrim template
            // insets its block, because there the text sits over the media.
            var pad = scrimTemplate
                    ? Mathf.Clamp(
                        Mathf.RoundToInt(shortSide * IN_FEED_SCRIM_PADDING_RATIO)
                      , IN_FEED_SCRIM_PADDING_MIN_DP
                      , IN_FEED_SCRIM_PADDING_MAX_DP)
                    : 0;
            stack.padding = new(pad, pad, pad, pad);
            stack.spacing = gap;
            stack.childAlignment = TextAnchor.LowerLeft;
            stack.childControlWidth = true;
            stack.childControlHeight = true;
            stack.childForceExpandWidth = true;
            stack.childForceExpandHeight = false;

            var fitter = contentObject.GetComponent<ContentSizeFitter>();
            fitter.verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            var iconSize = ResolveInFeedIconSize(tier);
            var headlineFontSize = tier switch {
                InFeedTier.Roomy => FULLSCREEN_HEADLINE_FONT_SIZE
              , InFeedTier.Regular => COMPACT_HEADLINE_FONT_SIZE
              , _ => IN_FEED_COMPACT_FONT_SIZE
            };
            var secondaryFontSize = tier == InFeedTier.Compact
                    ? IN_FEED_SECONDARY_FONT_SIZE
                    : ADVERTISER_FONT_SIZE;
            var callToActionHeight = Mathf.RoundToInt(
                Mathf.Clamp(
                    shortSide * IN_FEED_CTA_SHORT_SIDE_RATIO
                  , IN_FEED_CTA_MIN_HEIGHT_DP
                  , IN_FEED_CTA_MAX_HEIGHT_DP));
            var callToActionFontSize = Mathf.Clamp(
                Mathf.RoundToInt(
                    callToActionHeight * IN_FEED_CTA_FONT_HEIGHT_RATIO)
              , IN_FEED_CTA_MIN_FONT_SIZE
              , CALL_TO_ACTION_FONT_SIZE);

            if (scrimTemplate) {
                // The device's shared-line floor: the icon only joins the
                // headline's line when the line keeps 72dp of text.
                var lineTextWidth =
                        cellWidth - 2 * pad - iconSize - gap;
                if (lineTextWidth < MIN_ICON_ROW_TEXT_WIDTH_DP) {
                    var iconHolder = new GameObject(
                        IDENTITY_ROW_OBJECT_NAME
                      , typeof(RectTransform)
                      , typeof(HorizontalLayoutGroup));
                    iconHolder.transform.SetParent(
                        contentObject.transform
                      , false);
                    var iconHolderLayout =
                            iconHolder.GetComponent<HorizontalLayoutGroup>();
                    iconHolderLayout.childAlignment =
                            TextAnchor.MiddleCenter;
                    iconHolderLayout.childControlWidth = false;
                    iconHolderLayout.childControlHeight = false;
                    iconHolderLayout.childForceExpandWidth = false;
                    iconHolderLayout.childForceExpandHeight = false;
                    var iconElement = CreateIcon(
                        iconHolder.transform
                      , iconSize);
                    iconElement.GetComponent<RectTransform>().sizeDelta =
                            new(iconSize, iconSize);

                    headlineText = CreateText(
                        contentObject.transform
                      , HEADLINE_TEXT
                      , headlineFontSize
                      , TextAnchor.MiddleLeft
                      , Color.white
                      , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
                } else {
                    var identityRowObject = new GameObject(
                        IDENTITY_ROW_OBJECT_NAME
                      , typeof(RectTransform)
                      , typeof(HorizontalLayoutGroup));
                    identityRowObject.transform.SetParent(
                        contentObject.transform
                      , false);
                    var identityRowLayout =
                            identityRowObject
                                    .GetComponent<HorizontalLayoutGroup>();
                    identityRowLayout.spacing = gap;
                    identityRowLayout.childAlignment =
                            TextAnchor.MiddleLeft;
                    identityRowLayout.childControlWidth = true;
                    identityRowLayout.childControlHeight = true;
                    identityRowLayout.childForceExpandWidth = false;
                    identityRowLayout.childForceExpandHeight = false;
                    CreateIcon(identityRowObject.transform, iconSize);
                    headlineText = CreateText(
                        identityRowObject.transform
                      , HEADLINE_TEXT
                      , headlineFontSize
                      , TextAnchor.MiddleLeft
                      , Color.white
                      , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
                    headlineText
                            .GetComponent<LayoutElement>()
                            .flexibleWidth = 1f;
                }
                headlineLayoutElement =
                        headlineText.GetComponent<LayoutElement>();

                var body = CreateText(
                    contentObject.transform
                  , BODY_TEXT
                  , secondaryFontSize
                  , TextAnchor.MiddleLeft
                  , SecondaryTextColor
                  , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
                body.verticalOverflow = VerticalWrapMode.Truncate;

                callToActionLayoutElement = CreateCallToAction(
                    contentObject.transform
                  , config.AdUnitId
                  , callToActionHeight
                  , callToActionFontSize
                  , out callToActionText);
                callToActionLayoutElement.minHeight = callToActionHeight;
                callToActionLayoutElement.preferredHeight =
                        callToActionHeight;
                return content;
            }

            // The media-top column carries the full element set the device
            // prefers: headline, body, advertiser - rating on roomier tiers
            // - with the icon riding the button's row at the button's
            // height.
            headlineText = CreateText(
                contentObject.transform
              , HEADLINE_TEXT
              , headlineFontSize
              , TextAnchor.MiddleLeft
              , Color.white
              , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
            headlineLayoutElement =
                    headlineText.GetComponent<LayoutElement>();

            var bodyLine = CreateText(
                contentObject.transform
              , BODY_TEXT
              , secondaryFontSize
              , TextAnchor.MiddleLeft
              , SecondaryTextColor
              , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
            bodyLine.verticalOverflow = VerticalWrapMode.Truncate;
            CreateText(
                contentObject.transform
              , ADVERTISER_TEXT
              , secondaryFontSize
              , TextAnchor.MiddleLeft
              , SecondaryTextColor
              , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
            if (tier != InFeedTier.Compact) {
                CreateText(
                    contentObject.transform
                  , STAR_RATING_TEXT
                  , secondaryFontSize
                  , TextAnchor.MiddleLeft
                  , AttributionColor
                  , IN_FEED_TEXT_HEIGHT_MULTIPLIER);
            }

            var actionRowObject = new GameObject(
                IDENTITY_ROW_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(HorizontalLayoutGroup));
            actionRowObject.transform.SetParent(
                contentObject.transform
              , false);
            var actionRowLayout =
                    actionRowObject.GetComponent<HorizontalLayoutGroup>();
            actionRowLayout.spacing = gap;
            actionRowLayout.childAlignment = TextAnchor.MiddleLeft;
            actionRowLayout.childControlWidth = true;
            actionRowLayout.childControlHeight = true;
            actionRowLayout.childForceExpandWidth = false;
            actionRowLayout.childForceExpandHeight = false;
            CreateIcon(
                actionRowObject.transform
              , Mathf.Min(iconSize, callToActionHeight));
            callToActionLayoutElement = CreateCallToAction(
                actionRowObject.transform
              , config.AdUnitId
              , callToActionHeight
              , callToActionFontSize
              , out callToActionText);
            callToActionLayoutElement.minHeight = callToActionHeight;
            callToActionLayoutElement.preferredHeight = callToActionHeight;
            callToActionLayoutElement.flexibleWidth = 1f;
            return content;
        }

        private static InFeedTier ResolveInFeedTier(float shortSide) {
            if (shortSide >= IN_FEED_ROOMY_SHORT_SIDE_DP) {
                return InFeedTier.Roomy;
            }
            if (shortSide >= IN_FEED_REGULAR_SHORT_SIDE_DP) {
                return InFeedTier.Regular;
            }
            return InFeedTier.Compact;
        }

        private static int ResolveInFeedGap(InFeedTier tier) {
            return tier switch {
                InFeedTier.Roomy => IN_FEED_ROOMY_GAP_DP
              , InFeedTier.Regular => IN_FEED_REGULAR_GAP_DP
              , _ => IN_FEED_COMPACT_GAP_DP
            };
        }

        private static int ResolveInFeedIconSize(InFeedTier tier) {
            return tier switch {
                InFeedTier.Roomy => IN_FEED_ROOMY_ICON_DP
              , InFeedTier.Regular => IN_FEED_REGULAR_ICON_DP
              , _ => IN_FEED_COMPACT_ICON_DP
            };
        }

        // The image policy floor the device applies to a registered
        // MediaView; only a video creative demands the 120dp minimum.
        private const int IN_FEED_MEDIA_FLOOR_DP = 48;

        // ------------------------------------------------------------------
        // Badges and controls
        // ------------------------------------------------------------------

        private RectTransform CreateBadges(RectTransform panel) {
            var attributionObject = new GameObject(
                AD_ATTRIBUTION_TEXT
              , typeof(RectTransform)
              , typeof(Image));
            attributionObject.transform.SetParent(panel, false);
            attributionRect =
                    attributionObject.GetComponent<RectTransform>();
            SetTopLeftRect(
                attributionRect
              , Vector2.zero
              , new(
                    ATTRIBUTION_WIDTH_DP
                  , ATTRIBUTION_HEIGHT_DP));
            attributionObject.GetComponent<Image>().color =
                    AttributionColor;
            attributionText = CreateOverlayText(
                attributionObject.transform
              , AD_ATTRIBUTION_TEXT
              , ATTRIBUTION_FONT_SIZE
              , TextAnchor.MiddleCenter
              , Color.black);
            Stretch(attributionText.rectTransform);
            attributionText.horizontalOverflow = HorizontalWrapMode.Overflow;
            ScaleToBox(
                attributionText
              , attributionText.rectTransform
              , ATTRIBUTION_TEXT_BOX_RATIO
              , ATTRIBUTION_TEXT_MIN_SIZE
              , ATTRIBUTION_TEXT_MAX_SIZE);

            var adChoicesObject = new GameObject(
                AD_CHOICES_TEXT
              , typeof(RectTransform)
              , typeof(CanvasRenderer)
              , typeof(EditorAdChoicesGraphic)
              , typeof(Button));
            adChoicesObject.transform.SetParent(panel, false);
            var adChoicesRect =
                    adChoicesObject.GetComponent<RectTransform>();
            PlaceAdChoices(adChoicesRect, safeTopInset: 0f);
            var graphic =
                    adChoicesObject
                            .GetComponent<EditorAdChoicesGraphic>();
            graphic.raycastTarget = true;
            var button = adChoicesObject.GetComponent<Button>();
            button.targetGraphic = graphic;
            button.onClick.AddListener(
                () => TryOpenUrl(
                    AD_CHOICES_URL
                  , AD_CHOICES_CLICK_LOG_TEXT));
            return adChoicesRect;
        }

        private void CreateControls(
            RectTransform panel
          , EditorAdConfig config) {
            if (config.Mode == EditorAdMode.InFeed) return;

            // Random is resolved once per presentation, the way the device
            // does it; the relative timer modes then read that answer.
            var closeOnLeft = config.Close.CloseSide switch {
                CloseSide.Left  => true
              , CloseSide.Right => false
              , _                  => UnityEngine.Random.value < 0.5f
            };
            closeControlOnLeft = closeOnLeft;
            closeButton = CreateControlButton(
                panel
              , CLOSE_OBJECT_NAME
              , CLOSE_TEXT
              , CLOSE_FONT_SIZE
              , closeOnLeft);
            closeButton.onClick.AddListener(() => {
                if (config.Close.RedirectOnClose) {
                    TryOpenUrl(TEST_AD_CLICK_URL, CLICK_LOG_TEXT);
                }
                Dismiss();
            });

            countdownControlOnLeft = config.Close.TimerSide switch {
                TimerSide.Left            => true
              , TimerSide.Right           => false
              , TimerSide.SameAsClose     => closeOnLeft
              , TimerSide.OppositeOfClose => !closeOnLeft
              , _                         => UnityEngine.Random.value < 0.5f
            };
            countdownControl = CreateControlText(
                panel
              , COUNTDOWN_OBJECT_NAME
              , config.Close.Cooldown.ToString()
              , COUNTDOWN_FONT_SIZE
              , countdownControlOnLeft
              , out countdownText);
            countdownRemaining = config.Close.Cooldown;

            var countdownFinished = config.Close.Cooldown <= 0;
            closeButton.gameObject.SetActive(countdownFinished);
            countdownControl.SetActive(!countdownFinished);
        }

        private static Button CreateControlButton(
            RectTransform panel
          , string objectName
          , string text
          , int fontSize
          , bool onLeft) {
            var buttonObject = new GameObject(
                objectName
              , typeof(RectTransform)
              , typeof(Image)
              , typeof(Button));
            buttonObject.transform.SetParent(panel, false);

            var rect = buttonObject.GetComponent<RectTransform>();
            SetControlRect(rect, onLeft);

            var image = buttonObject.GetComponent<Image>();
            image.color = ControlColor;

            var button = buttonObject.GetComponent<Button>();
            button.targetGraphic = image;

            var label = CreateOverlayText(
                buttonObject.transform
              , text
              , fontSize
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
            label.horizontalOverflow = HorizontalWrapMode.Overflow;
            ScaleToBox(
                label
              , label.rectTransform
              , CONTROL_GLYPH_BOX_RATIO
              , CONTROL_GLYPH_MIN_SIZE
              , CONTROL_GLYPH_MAX_SIZE
              , followsShortSide: true);
            return button;
        }

        private static GameObject CreateControlText(
            RectTransform panel
          , string objectName
          , string text
          , int fontSize
          , bool onLeft
          , out Text label) {
            var controlObject = new GameObject(
                objectName
              , typeof(RectTransform)
              , typeof(Image));
            controlObject.transform.SetParent(panel, false);
            var controlRect =
                    controlObject.GetComponent<RectTransform>();
            SetControlRect(controlRect, onLeft);
            controlObject.GetComponent<Image>().color = ControlColor;

            label = CreateOverlayText(
                controlObject.transform
              , text
              , fontSize
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
            label.horizontalOverflow = HorizontalWrapMode.Overflow;
            ScaleToBox(
                label
              , label.rectTransform
              , CONTROL_GLYPH_BOX_RATIO
              , CONTROL_GLYPH_MIN_SIZE
              , CONTROL_GLYPH_MAX_SIZE
              , followsShortSide: true);
            return controlObject;
        }

        private static void SetControlRect(
            RectTransform rect
          , bool onLeft
          , float topInset = 0f) {
            if (onLeft) {
                SetTopLeftRect(
                    rect
                  , new(CONTROL_LEFT_INSET_DP, -topInset)
                  , new(CONTROL_SIZE_DP, CONTROL_SIZE_DP));
            } else {
                SetTopRightRect(
                    rect
                  , new(-CONTROL_RIGHT_INSET_DP, -topInset)
                  , new(CONTROL_SIZE_DP, CONTROL_SIZE_DP));
            }
        }

        private static Text CreateText(
            Transform parent
          , string text
          , int fontSize
          , TextAnchor alignment
          , Color color
          , float heightMultiplier = TEXT_HEIGHT_MULTIPLIER) {
            var label = CreateOverlayText(
                parent
              , text
              , fontSize
              , alignment
              , color);
            var layout = label.gameObject.AddComponent<LayoutElement>();
            layout.minHeight = fontSize * heightMultiplier;
            layout.preferredHeight = layout.minHeight;
            return label;
        }

        // Every label that lives inside a box of its own gets its size from
        // that box rather than from a constant.
        private static void ScaleToBox(
            Text label
          , RectTransform box
          , float ratio
          , int minimumSize
          , int maximumSize
          , bool followsShortSide = false) {
            label.gameObject
                    .AddComponent<EditorScaledText>()
                    .Configure(
                        box
                      , ratio
                      , minimumSize
                      , maximumSize
                      , followsShortSide);
        }

        private static Text CreateOverlayText(
            Transform parent
          , string text
          , int fontSize
          , TextAnchor alignment
          , Color color) {
            var textObject = new GameObject(
                text
              , typeof(RectTransform)
              , typeof(Text));
            textObject.transform.SetParent(parent, false);

            var label = textObject.GetComponent<Text>();
            label.font = Resources.GetBuiltinResource<Font>(
                BUILT_IN_FONT_NAME);
            label.text = text;
            label.fontSize = fontSize;
            label.alignment = alignment;
            label.color = color;
            label.raycastTarget = false;
            return label;
        }

        // The border the device buttons carry: a thin rim drawn INSIDE the
        // element's own bounds - an inner stroke, the way GradientDrawable
        // strokes on Android - so the border never grows the element.
        private const float INNER_STROKE_WIDTH_DP = 1.5f;

        private static void AddOutline(Graphic graphic, Color color) {
            var target = graphic.rectTransform;
            CreateStrokeEdge(
                target, color, new(0f, 1f), new(1f, 1f)
              , new(0.5f, 1f), new(0f, INNER_STROKE_WIDTH_DP));
            CreateStrokeEdge(
                target, color, new(0f, 0f), new(1f, 0f)
              , new(0.5f, 0f), new(0f, INNER_STROKE_WIDTH_DP));
            // The side bars stop short of the corners the horizontal bars
            // already own, so a translucent stroke never doubles up there.
            CreateStrokeEdge(
                target, color, new(0f, 0f), new(0f, 1f)
              , new(0f, 0.5f)
              , new(INNER_STROKE_WIDTH_DP, -2f * INNER_STROKE_WIDTH_DP));
            CreateStrokeEdge(
                target, color, new(1f, 0f), new(1f, 1f)
              , new(1f, 0.5f)
              , new(INNER_STROKE_WIDTH_DP, -2f * INNER_STROKE_WIDTH_DP));
        }

        private static void CreateStrokeEdge(
            RectTransform parent
          , Color color
          , Vector2 anchorMin
          , Vector2 anchorMax
          , Vector2 pivot
          , Vector2 size) {
            var edgeObject = new GameObject(
                "Stroke"
              , typeof(RectTransform)
              , typeof(Image));
            edgeObject.transform.SetParent(parent, false);
            var rect = edgeObject.GetComponent<RectTransform>();
            rect.anchorMin = anchorMin;
            rect.anchorMax = anchorMax;
            rect.pivot = pivot;
            rect.anchoredPosition = Vector2.zero;
            rect.sizeDelta = size;
            var image = edgeObject.GetComponent<Image>();
            image.color = color;
            image.raycastTarget = false;
        }

        private static void Stretch(RectTransform rect) {
            rect.anchorMin = Vector2.zero;
            rect.anchorMax = Vector2.one;
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;
        }

        private static void SetTopLeftRect(
            RectTransform rect
          , Vector2 position
          , Vector2 size) {
            rect.anchorMin = new(0f, 1f);
            rect.anchorMax = new(0f, 1f);
            rect.pivot = new(0f, 1f);
            rect.anchoredPosition = position;
            rect.sizeDelta = size;
        }

        // The mark hugs the corner, the way the SDK draws it. Both the build
        // and the safe-inset pass come through here, so neither can undo the
        // other.
        private static void PlaceAdChoices(
            RectTransform adChoices
          , float safeTopInset) {
            if (adChoices == null) return;

            SetTopRightRect(
                adChoices
              , new(0f, -safeTopInset)
              , new(AD_CHOICES_SIZE_DP, AD_CHOICES_SIZE_DP));
        }

        private static void SetTopRightRect(
            RectTransform rect
          , Vector2 position
          , Vector2 size) {
            rect.anchorMin = Vector2.one;
            rect.anchorMax = Vector2.one;
            rect.pivot = Vector2.one;
            rect.anchoredPosition = position;
            rect.sizeDelta = size;
        }

        // ------------------------------------------------------------------
        // Responsive refresh
        // ------------------------------------------------------------------

        private void LateUpdate() {
            RefreshResponsiveLayout(false);
        }

        private void RefreshResponsiveLayout(bool force) {
            if (dismissed
                    || config == null
                    || panel == null
                    || content == null) {
                return;
            }

            var uiScale = ResolveUiScale();
            if (!force
                    && Screen.width == lastScreenWidth
                    && Screen.height == lastScreenHeight
                    && Screen.safeArea == lastSafeArea
                    && Mathf.Abs(uiScale - lastUiScale)
                            <= SCREEN_CHANGE_TOLERANCE) {
                return;
            }

            canvasScaler.scaleFactor = uiScale;
            ApplyPanelRect(panel, config, uiScale, 0f);
            Canvas.ForceUpdateCanvases();
            var safeTopInset = ResolveSafeTopInset(uiScale);
            ApplySafeTopInset(safeTopInset);
            LayoutRebuilder.ForceRebuildLayoutImmediate(content);
            if (config.Mode != EditorAdMode.InFeed) {
                if (MatchIconSizeToIdentityText()) {
                    LayoutRebuilder.ForceRebuildLayoutImmediate(content);
                }
                var minimumPanelHeight =
                        CONTROL_SIZE_DP + LayoutUtility.GetMinHeight(content);
                ApplyPanelRect(
                    panel
                  , config
                  , uiScale
                  , minimumPanelHeight);
                Canvas.ForceUpdateCanvases();
            }
            ApplyBadgeRects(
                safeTopInset
              , uiScale);
            LayoutRebuilder.ForceRebuildLayoutImmediate(content);

            lastScreenWidth = Screen.width;
            lastScreenHeight = Screen.height;
            lastSafeArea = Screen.safeArea;
            lastUiScale = uiScale;
        }

        private bool MatchIconSizeToIdentityText() {
            if (config.Mode == EditorAdMode.InFeed
                    || identityRowRect == null
                    || identityTextRect == null
                    || iconLayoutElement == null) {
                return false;
            }

            var textHeight = Mathf.Max(
                identityTextRect.rect.height
              , LayoutUtility.GetPreferredHeight(identityTextRect));
            var maximumIconSize = Mathf.Max(
                ICON_SIZE_DP
              , identityRowRect.rect.width
                    * MAX_ICON_ROW_WIDTH_RATIO);
            var resolvedIconSize = Mathf.Clamp(
                textHeight
              , ICON_SIZE_DP
              , maximumIconSize);
            if (Mathf.Approximately(
                        iconLayoutElement.preferredWidth
                      , resolvedIconSize)
                    && Mathf.Approximately(
                        iconLayoutElement.preferredHeight
                      , resolvedIconSize)) {
                return false;
            }

            iconLayoutElement.minWidth = resolvedIconSize;
            iconLayoutElement.minHeight = resolvedIconSize;
            iconLayoutElement.preferredWidth = resolvedIconSize;
            iconLayoutElement.preferredHeight = resolvedIconSize;
            return true;
        }

        private float ResolveSafeTopInset(float uiScale) {
            if (config.Mode != EditorAdMode.FullScreen) {
                return 0f;
            }

            return Mathf.Max(
                0f
              , (Screen.height - Screen.safeArea.yMax) / uiScale);
        }

        private void ApplySafeTopInset(float safeTopInset) {
            if (config.Mode == EditorAdMode.InFeed) return;

            // Full screen: the content stays below the control band - the
            // device's avoidance keeps close and timer off the media.
            // Collapsible: the content stays below the control strip.
            if (config.Mode == EditorAdMode.FullScreen) {
                content.offsetMax = new(
                    -CONTENT_HORIZONTAL_PADDING_DP
                  , -(CONTROL_SIZE_DP + safeTopInset));
            } else {
                content.offsetMax = new(
                    -CONTENT_HORIZONTAL_PADDING_DP
                  , -CONTROL_SIZE_DP);
            }
            if (attributionRect != null) {
                SetTopLeftRect(
                    attributionRect
                  , new(0f, -safeTopInset)
                  , new(
                        ATTRIBUTION_WIDTH_DP
                      , ATTRIBUTION_HEIGHT_DP));
            }
            if (closeButton != null) {
                SetControlRect(
                    closeButton.GetComponent<RectTransform>()
                  , closeControlOnLeft
                  , safeTopInset);
            }
            if (countdownControl != null) {
                SetControlRect(
                    countdownControl.GetComponent<RectTransform>()
                  , countdownControlOnLeft
                  , safeTopInset);
            }
        }

        private void ApplyBadgeRects(
            float safeTopInset
          , float uiScale) {
            if (config.Mode == EditorAdMode.InFeed) {
                var minimumBadgeSize = MIN_BADGE_SIZE_PX / uiScale;
                var badgeSize = ResolveInFeedBadgeSize(uiScale);
                var availableAttributionWidth = Mathf.Max(
                    minimumBadgeSize
                  , panel.rect.width
                            - badgeSize);
                var attributionWidth = Mathf.Clamp(
                    badgeSize * IN_FEED_ATTRIBUTION_ASPECT_RATIO
                  , minimumBadgeSize
                  , Mathf.Min(
                        ATTRIBUTION_WIDTH_DP
                      , availableAttributionWidth));
                if (attributionRect != null) {
                    SetTopLeftRect(
                        attributionRect
                      , default
                      , new(attributionWidth, badgeSize));
                }
                if (adChoicesRect != null) {
                    SetTopRightRect(
                        adChoicesRect
                      , default
                      , new(badgeSize, badgeSize));
                }
                return;
            }

            if (attributionRect != null) {
                SetTopLeftRect(
                    attributionRect
                  , new(0f, -safeTopInset)
                  , new(
                        ATTRIBUTION_WIDTH_DP
                      , ATTRIBUTION_HEIGHT_DP));
            }
            PlaceAdChoices(adChoicesRect, safeTopInset);
        }

        private float ResolveInFeedBadgeSize(float uiScale) {
            var minimumBadgeSize = MIN_BADGE_SIZE_PX / uiScale;
            var shortSide = Mathf.Min(
                panel.rect.width
              , panel.rect.height);
            return Mathf.Clamp(
                shortSide * IN_FEED_BADGE_SHORT_SIDE_RATIO
              , minimumBadgeSize
              , AD_CHOICES_SIZE_DP);
        }

        private static float ResolveUiScale() {
            if (float.IsFinite(Screen.dpi)
                    && Screen.dpi >= MIN_DEVICE_SIMULATOR_DPI) {
                return Screen.dpi / ANDROID_BASELINE_DPI;
            }

            if (Screen.width <= 0
                    || Screen.height <= 0
                    || ReferenceResolution.x <= 0f
                    || ReferenceResolution.y <= 0f) {
                return 1f;
            }

            var widthScale = Screen.width / ReferenceResolution.x;
            var heightScale = Screen.height / ReferenceResolution.y;
            var logWidth = Mathf.Log(widthScale, 2f);
            var logHeight = Mathf.Log(heightScale, 2f);
            return Mathf.Max(
                Mathf.Epsilon
              , Mathf.Pow(
                    2f
                  , Mathf.Lerp(
                        logWidth
                      , logHeight
                      , CANVAS_MATCH_WIDTH_OR_HEIGHT)));
        }

        private void TryOpenUrl(string url, string logMessage) {
            if (externalUrlOpening) return;

            externalUrlOpening = true;
            externalUrlFocusLost = false;
            Debug.Log($"{logMessage}: {url}");
            Application.OpenURL(url);
        }

        private void OnApplicationFocus(bool hasFocus) {
            if (!externalUrlOpening) return;
            if (!hasFocus) {
                externalUrlFocusLost = true;
                return;
            }
            if (!externalUrlFocusLost) return;

            externalUrlOpening = false;
            externalUrlFocusLost = false;
        }

        private void Update() {
            if (countdownControl == null
                    || !countdownControl.activeSelf) {
                return;
            }

            countdownRemaining -= Time.unscaledDeltaTime;
            if (countdownRemaining > 0) {
                countdownText.text =
                        Mathf.CeilToInt(countdownRemaining).ToString();
                return;
            }

            countdownControl.SetActive(false);
            closeButton.gameObject.SetActive(true);
        }

        private void Finish(bool notify) {
            if (!this || dismissed) return;
            dismissed = true;

            if (pausesGame) Time.timeScale = FULL_TIME_SCALE;

            var callback = notify ? onDismissed : null;
            onDismissed = null;
            gameObject.SetActive(false);
            Destroy(gameObject);
            callback?.Invoke();
        }
    }
}
