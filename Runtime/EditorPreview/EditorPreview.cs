#if UNITY_EDITOR
using System;
using UnityEngine;
using UnityEngine.UI;

namespace RiseOn.NativeAdMob {
    internal enum EditorPreviewMode {
        FullScreen
      , Collapsible
      , InFeed
    }

    internal sealed class EditorPreviewConfig {
        private const float FULL_SCREEN_DEFAULT_ALPHA = 0.8f;
        private const float COLLAPSIBLE_DEFAULT_ALPHA = 0.95f;
        private const float IN_FEED_DEFAULT_ALPHA     = 1f;
        private const float DEFAULT_HEIGHT_RATIO      = 0.5f;

        internal string                    AdUnitId          { get; }
        internal EditorPreviewMode Mode              { get; }
        internal bool                      PausesGame        { get; }
        internal bool                      RandomCloseSide   { get; }
        internal bool                      NumberOpposite    { get; }
        internal float                     HeightRatio       { get; }
        internal float                     BackgroundAlpha   { get; }
        internal Vector2Int                PositionPx        { get; private set; }
        internal Vector2Int                SizePx            { get; }
        internal bool                      AllowsVideo       { get; }
        internal int                       CountdownSec      { get; private set; }

        private EditorPreviewConfig(
            string adUnitId
          , EditorPreviewMode mode
          , bool pausesGame
          , int countdownSec
          , bool randomCloseSide
          , bool numberOpposite
          , float heightRatio
          , float backgroundAlpha
          , Vector2Int positionPx
          , Vector2Int sizePx
          , bool allowsVideo) {
            AdUnitId        = adUnitId;
            Mode            = mode;
            PausesGame      = pausesGame;
            CountdownSec    = Mathf.Max(0, countdownSec);
            RandomCloseSide = randomCloseSide;
            NumberOpposite  = numberOpposite;
            HeightRatio     = heightRatio;
            BackgroundAlpha = backgroundAlpha;
            PositionPx      = positionPx;
            SizePx          = sizePx;
            AllowsVideo     = allowsVideo;
        }

        internal static EditorPreviewConfig CreateFullScreen(
            string adUnitId
          , bool fullscreen
          , int countdownSec
          , bool randomCloseSide
          , bool numberOpposite
          , float heightRatio
          , float backgroundAlpha) {
            return new(
                adUnitId
              , fullscreen
                    ? EditorPreviewMode.FullScreen
                    : EditorPreviewMode.Collapsible
              , fullscreen
              , countdownSec
              , randomCloseSide
              , numberOpposite
              , ResolveRatio(heightRatio, DEFAULT_HEIGHT_RATIO)
              , ResolveAlpha(
                    backgroundAlpha
                  , fullscreen
                        ? FULL_SCREEN_DEFAULT_ALPHA
                        : COLLAPSIBLE_DEFAULT_ALPHA)
              , default
              , default
              , true);
        }

        internal static EditorPreviewConfig CreateInFeed(
            string adUnitId
          , Vector2Int positionPx
          , Vector2Int sizePx
          , float backgroundAlpha) {
            return new(
                adUnitId
              , EditorPreviewMode.InFeed
              , false
              , default
              , false
              , false
              , default
              , ResolveInFeedAlpha(backgroundAlpha)
              , positionPx
              , sizePx
              , true);
        }

        internal void SetCountdownSec(int countdownSec) {
            CountdownSec = Mathf.Max(0, countdownSec);
        }

        internal void SetPosition(Vector2Int positionPx) {
            PositionPx = positionPx;
        }

        internal EditorPreviewConfig Snapshot() {
            return new(
                AdUnitId
              , Mode
              , PausesGame
              , CountdownSec
              , RandomCloseSide
              , NumberOpposite
              , HeightRatio
              , BackgroundAlpha
              , PositionPx
              , SizePx
              , AllowsVideo);
        }

        private static float ResolveAlpha(float value, float defaultValue) {
            if (!float.IsFinite(value) || value < 0f) return defaultValue;
            return Mathf.Clamp01(value);
        }

        private static float ResolveInFeedAlpha(float value) {
            return float.IsFinite(value)
                    ? Mathf.Clamp01(value)
                    : IN_FEED_DEFAULT_ALPHA;
        }

        private static float ResolveRatio(float value, float defaultValue) {
            if (!float.IsFinite(value)) return defaultValue;
            return Mathf.Clamp01(value);
        }

        private static float ResolveNonNegative(float value) {
            return !float.IsFinite(value) || value < 0f
                    ? 0f
                    : value;
        }
    }

    internal sealed class EditorPreviewAdaptiveLayoutGroup
        : HorizontalOrVerticalLayoutGroup {
        private bool isVertical = true;

        internal bool IsVertical {
            get => isVertical;
            set {
                if (isVertical == value) return;

                isVertical = value;
                SetDirty();
            }
        }

        public override void CalculateLayoutInputHorizontal() {
            base.CalculateLayoutInputHorizontal();
            CalcAlongAxis(0, isVertical);
        }

        public override void CalculateLayoutInputVertical() {
            CalcAlongAxis(1, isVertical);
        }

        public override void SetLayoutHorizontal() {
            SetChildrenAlongAxis(0, isVertical);
        }

        public override void SetLayoutVertical() {
            SetChildrenAlongAxis(1, isVertical);
        }
    }

    internal sealed class EditorPreview : MonoBehaviour {
        private enum InFeedTier {
            Compact
          , Regular
          , Roomy
        }

        private enum InFeedMediaMode {
            None
          , Image
          , Video
        }

        private const string PREVIEW_OBJECT_NAME      = "Native Ad Editor Preview";
        private const string BUILT_IN_FONT_NAME       = "LegacyRuntime.ttf";
        private const string AD_ATTRIBUTION_TEXT      = "Ad";
        private const string AD_CHOICES_TEXT          = "AdChoices";
        private const string HEADLINE_TEXT            = "Native Ad Editor Preview";
        private const string ADVERTISER_TEXT          = "Google Mobile Ads placeholder";
        private const string STAR_RATING_TEXT         = "★★★★☆  4.0";
        private const string BODY_TEXT                = "This GameObject simulates the native ad lifecycle in the Unity Editor.";
        private const string CALL_TO_ACTION_TEXT      = "Install";
        private const string IMAGE_MEDIA_TEXT         = "IMAGE MEDIA";
        private const string VIDEO_MEDIA_TEXT         = "VIDEO MEDIA";
        private const string FLEXIBLE_MEDIA_TEXT      = "IMAGE / VIDEO MEDIA";
        private const string CLICK_LOG_TEXT           = "Native ad preview click";
        private const string TEST_AD_CLICK_URL         = "https://google.com";
        private const string AD_CHOICES_PREVIEW_URL   = "https://support.google.com/My-Ad-Center-Help/answer/12155764";
        private const string AD_CHOICES_CLICK_LOG_TEXT = "Native AdChoices preview click";
        private const string PANEL_OBJECT_NAME        = "Native Ad Panel";
        private const string CONTENT_OBJECT_NAME      = "Content";
        private const string DETAILS_OBJECT_NAME      = "Details";
        private const string IDENTITY_ROW_OBJECT_NAME = "Identity Row";
        private const string IDENTITY_TEXT_OBJECT_NAME = "Identity Text";
        private const string ICON_OBJECT_NAME         = "Icon";
        private const string ICON_TEXT                = "APP";
        private const string MEDIA_OBJECT_NAME        = "Media";
        private const string CLOSE_OBJECT_NAME        = "Close";
        private const string COUNTDOWN_OBJECT_NAME    = "Countdown";
        private const string CLOSE_TEXT               = "×";

        private const int FULLSCREEN_CANVAS_SORTING_ORDER  = short.MaxValue;
        private const int NON_FULLSCREEN_CANVAS_SORTING_ORDER =
                FULLSCREEN_CANVAS_SORTING_ORDER - 1;
        private const int IN_FEED_CANVAS_SORTING_ORDER =
                NON_FULLSCREEN_CANVAS_SORTING_ORDER - 1;
        private const int CONTENT_HORIZONTAL_PADDING_DP    = 20;
        private const int CONTENT_SPACING_DP               = 4;
        private const int CONTROL_SIZE_DP                  = 34;
        private const int CONTROL_BADGE_SPACING_DP         = 2;
        private const int AD_CHOICES_PREVIEW_SIZE_DP       = 18;
        private const int CONTROL_RIGHT_INSET_DP =
                AD_CHOICES_PREVIEW_SIZE_DP
              + CONTROL_BADGE_SPACING_DP;
        private const int ATTRIBUTION_WIDTH_DP             = 24;
        private const int CONTROL_LEFT_INSET_DP =
                ATTRIBUTION_WIDTH_DP
              + CONTROL_BADGE_SPACING_DP;
        private const int ATTRIBUTION_HEIGHT_DP            = 18;
        private const int ATTRIBUTION_FONT_SIZE            = 10;
        private const int ICON_SIZE_DP                      = 36;
        private const int ICON_GAP_DP                       = 8;
        private const int ICON_FONT_SIZE                    = 10;
        private const float MAX_ICON_ROW_WIDTH_RATIO        = 0.33f;
        private const int MEDIA_MIN_SIZE_DP                = 120;
        private const int IN_FEED_NATIVE_MIN_SIZE_DP       = 32;
        private const int IN_FEED_IMAGE_MIN_SIZE_DP        = 32;
        private const int IN_FEED_VIDEO_MIN_SIZE_DP        = 120;
        private const int IN_FEED_VIDEO_MIN_LONG_SIDE_PX   = 256;
        private const int IN_FEED_COMPACT_PADDING_DP       = 0;
        private const int IN_FEED_REGULAR_PADDING_DP       = 2;
        private const int IN_FEED_ROOMY_PADDING_DP         = 4;
        private const int IN_FEED_COMPACT_SPACING_DP       = 1;
        private const int IN_FEED_REGULAR_SPACING_DP       = 3;
        private const int IN_FEED_ROOMY_SPACING_DP         = 5;
        private const int IN_FEED_CTA_MIN_HEIGHT_DP        = 18;
        private const int IN_FEED_CTA_MAX_HEIGHT_DP        = 36;
        private const int IN_FEED_COMPACT_FONT_SIZE        = 12;
        private const int IN_FEED_CTA_MIN_FONT_SIZE        = 10;
        private const int IN_FEED_ATTRIBUTION_MIN_FONT_SIZE = 5;
        private const int MIN_BADGE_SIZE_PX                = 15;
        private const int IN_FEED_REGULAR_SHORT_SIDE_DP    = 150;
        private const int IN_FEED_ROOMY_SHORT_SIDE_DP      = 280;
        private const int FULLSCREEN_HEADLINE_FONT_SIZE    = 18;
        private const int COMPACT_HEADLINE_FONT_SIZE       = 15;
        private const int ADVERTISER_FONT_SIZE             = 12;
        private const int RATING_FONT_SIZE                 = 12;
        private const int BODY_FONT_SIZE                   = 13;
        private const int MEDIA_LABEL_FONT_SIZE            = 15;
        private const int CLOSE_FONT_SIZE                  = 16;
        private const int COUNTDOWN_FONT_SIZE              = 15;
        private const int CALL_TO_ACTION_FONT_SIZE         = 14;
        private const int CALL_TO_ACTION_HEIGHT_DP         = 40;
        private const float FULL_TIME_SCALE                = 1f;
        private const float PAUSED_TIME_SCALE              = 0f;
        private const float CANVAS_MATCH_WIDTH_OR_HEIGHT   = 0.5f;
        private const float SCREEN_CHANGE_TOLERANCE        = 0.001f;
        private const int IN_FEED_LAYOUT_RETRY_FRAMES       = 2;
        private const float TEXT_HEIGHT_MULTIPLIER         = 1.5f;
        private const float ANDROID_BASELINE_DPI           = 160f;
        private const float MIN_DEVICE_SIMULATOR_DPI       = 160f;
        private const float IN_FEED_ROW_ASPECT_THRESHOLD    = 1.35f;
        private const float IN_FEED_ROW_MEDIA_WIDTH_RATIO   = 0.42f;
        private const float IN_FEED_CTA_SHORT_SIDE_RATIO    = 0.18f;
        private const float IN_FEED_BADGE_SHORT_SIDE_RATIO  = 0.10f;
        private const float IN_FEED_ATTRIBUTION_ASPECT_RATIO = 4f / 3f;
        private const float IN_FEED_CTA_FONT_HEIGHT_RATIO   = 0.5f;
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
        private static readonly Color CallToActionColor =
                new(0.16f, 0.54f, 0.92f, 1f);

        private Action onDismissed;
        private EditorPreviewConfig config;
        private CanvasScaler canvasScaler;
        private RectTransform panel;
        private RectTransform content;
        private RectTransform attributionRect;
        private RectTransform adChoicesRect;
        private Image panelGraphic;
        private EditorPreviewAdaptiveLayoutGroup contentLayout;
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
        private bool inFeedLayoutUnavailable;
        private bool inFeedLayoutWarningIssued;
        private int  inFeedLayoutRetryFramesRemaining;
        private bool externalUrlOpening;
        private bool externalUrlFocusLost;

        internal static EditorPreview Show(
            EditorPreviewConfig config
          , Action onDismissed) {
            var previewObject = new GameObject(
                PREVIEW_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(Canvas)
              , typeof(CanvasScaler)
              , typeof(GraphicRaycaster)
              , typeof(EditorPreview));
            var preview = previewObject.GetComponent<EditorPreview>();
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
            EditorPreviewConfig config
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
            content = CreateContent(panel, config);
            adChoicesRect = CreateBadges(panel);
            CreateControls(panel, config);
            Canvas.ForceUpdateCanvases();
            RefreshResponsiveLayout(true);

            if (pausesGame) Time.timeScale = PAUSED_TIME_SCALE;
        }

        private static int ResolveCanvasSortingOrder(
            EditorPreviewMode mode) {
            return mode switch {
                EditorPreviewMode.FullScreen =>
                        FULLSCREEN_CANVAS_SORTING_ORDER
              , EditorPreviewMode.Collapsible =>
                        NON_FULLSCREEN_CANVAS_SORTING_ORDER
              , _ => IN_FEED_CANVAS_SORTING_ORDER
            };
        }

        private RectTransform CreatePanel(
            EditorPreviewConfig config) {
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
                    config.Mode == EditorPreviewMode.FullScreen
                            ? FullScreenPanelColor
                            : CompactPanelColor;
            panelGraphic.color = new(
                panelColor.r
              , panelColor.g
              , panelColor.b
              , config.BackgroundAlpha);
            var backgroundIsClickable =
                    config.Mode != EditorPreviewMode.InFeed;
            panelGraphic.raycastTarget = backgroundIsClickable;

            var panelButton = panelObject.GetComponent<Button>();
            panelButton.targetGraphic = panelGraphic;
            panelButton.transition = Selectable.Transition.None;
            panelButton.interactable = backgroundIsClickable;
            if (backgroundIsClickable) {
                panelButton.onClick.AddListener(
                    () => TryOpenPreviewUrl(
                        TEST_AD_CLICK_URL
                      , CLICK_LOG_TEXT));
            }
            return panel;
        }

        private static void ApplyPanelRect(
            RectTransform panel
          , EditorPreviewConfig config
          , float uiScale
          , float minimumPanelHeight) {
            if (config.Mode == EditorPreviewMode.FullScreen) {
                panel.anchorMin = Vector2.zero;
                panel.anchorMax = Vector2.one;
                panel.pivot = new(0.5f, 0.5f);
                panel.anchoredPosition = Vector2.zero;
                panel.sizeDelta = Vector2.zero;
                panel.offsetMin = Vector2.zero;
                panel.offsetMax = Vector2.zero;
                return;
            }

            if (config.Mode == EditorPreviewMode.Collapsible) {
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

        private RectTransform CreateContent(
            RectTransform panel
          , EditorPreviewConfig config) {
            var contentObject = new GameObject(
                CONTENT_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(EditorPreviewAdaptiveLayoutGroup));
            contentObject.transform.SetParent(panel, false);

            var content = contentObject.GetComponent<RectTransform>();
            content.anchorMin = Vector2.zero;
            content.anchorMax = Vector2.one;
            content.offsetMin = new(
                CONTENT_HORIZONTAL_PADDING_DP
              , 0f);
            content.offsetMax = new(
                -CONTENT_HORIZONTAL_PADDING_DP
              , -CONTROL_SIZE_DP);

            contentLayout =
                    contentObject
                            .GetComponent<EditorPreviewAdaptiveLayoutGroup>();
            ConfigureContentLayout(contentLayout);

            var mediaText = config.AllowsVideo
                    ? FLEXIBLE_MEDIA_TEXT
                    : IMAGE_MEDIA_TEXT;
            var mediaMinHeight =
                    config.Mode == EditorPreviewMode.InFeed
                            ? 0f
                            : MEDIA_MIN_SIZE_DP;
            mediaLayoutElement =
                    CreateMedia(
                        content
                      , mediaText
                      , mediaMinHeight
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
                    config.Mode == EditorPreviewMode.InFeed
                            ? detailsObject.transform
                            : CreateIdentityRow(detailsObject.transform);
            var headlineFontSize =
                    config.Mode == EditorPreviewMode.FullScreen
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

            if (config.Mode == EditorPreviewMode.FullScreen) {
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
            }

            callToActionLayoutElement = CreateCallToAction(
                detailsObject.transform
              , config.AdUnitId
              , out callToActionText);
            return content;
        }

        private static void ConfigureContentLayout(
            EditorPreviewAdaptiveLayoutGroup layout) {
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

            CreateIcon(identityRowObject.transform);

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

        private void CreateIcon(Transform parent) {
            var iconObject = new GameObject(
                ICON_OBJECT_NAME
              , typeof(RectTransform)
              , typeof(Image)
              , typeof(Button)
              , typeof(LayoutElement));
            iconObject.transform.SetParent(parent, false);

            var image = iconObject.GetComponent<Image>();
            image.color = CallToActionColor;

            var button = iconObject.GetComponent<Button>();
            button.targetGraphic = image;
            button.onClick.AddListener(
                () => TryOpenPreviewUrl(
                    TEST_AD_CLICK_URL
                  , CLICK_LOG_TEXT));

            iconLayoutElement =
                    iconObject.GetComponent<LayoutElement>();
            iconLayoutElement.minWidth = ICON_SIZE_DP;
            iconLayoutElement.minHeight = ICON_SIZE_DP;
            iconLayoutElement.preferredWidth = ICON_SIZE_DP;
            iconLayoutElement.preferredHeight = ICON_SIZE_DP;

            var label = CreateOverlayText(
                iconObject.transform
              , ICON_TEXT
              , ICON_FONT_SIZE
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
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
              , typeof(EditorPreviewMediaGraphic)
              , typeof(Button)
              , typeof(LayoutElement));
            mediaObject.transform.SetParent(parent, false);

            var graphic =
                    mediaObject.GetComponent<EditorPreviewMediaGraphic>();
            graphic.raycastTarget = true;

            var button = mediaObject.GetComponent<Button>();
            button.targetGraphic = graphic;
            button.onClick.AddListener(
                () => TryOpenPreviewUrl(
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
            return layout;
        }

        private LayoutElement CreateCallToAction(
            Transform parent
          , string adUnitId
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

            var button = buttonObject.GetComponent<Button>();
            button.targetGraphic = image;
            button.onClick.AddListener(
                () => TryOpenPreviewUrl(
                    TEST_AD_CLICK_URL
                  , $"{CLICK_LOG_TEXT}. Ad unit ID: {adUnitId}"));

            var layout = buttonObject.GetComponent<LayoutElement>();
            layout.minHeight = CALL_TO_ACTION_HEIGHT_DP;
            layout.preferredHeight = CALL_TO_ACTION_HEIGHT_DP;

            label = CreateOverlayText(
                buttonObject.transform
              , CALL_TO_ACTION_TEXT
              , CALL_TO_ACTION_FONT_SIZE
              , TextAnchor.MiddleCenter
              , Color.white);
            Stretch(label.rectTransform);
            return layout;
        }

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
            attributionText.resizeTextForBestFit = true;
            attributionText.resizeTextMinSize =
                    IN_FEED_ATTRIBUTION_MIN_FONT_SIZE;
            attributionText.resizeTextMaxSize = ATTRIBUTION_FONT_SIZE;
            Stretch(attributionText.rectTransform);

            var adChoicesObject = new GameObject(
                AD_CHOICES_TEXT
              , typeof(RectTransform)
              , typeof(CanvasRenderer)
              , typeof(EditorPreviewAdChoicesGraphic)
              , typeof(Button));
            adChoicesObject.transform.SetParent(panel, false);
            var adChoicesRect =
                    adChoicesObject.GetComponent<RectTransform>();
            SetTopRightRect(
                adChoicesRect
              , Vector2.zero
              , new(
                    AD_CHOICES_PREVIEW_SIZE_DP
                  , AD_CHOICES_PREVIEW_SIZE_DP));
            var graphic =
                    adChoicesObject
                            .GetComponent<EditorPreviewAdChoicesGraphic>();
            graphic.raycastTarget = true;
            var button = adChoicesObject.GetComponent<Button>();
            button.targetGraphic = graphic;
            button.onClick.AddListener(
                () => TryOpenPreviewUrl(
                    AD_CHOICES_PREVIEW_URL
                  , AD_CHOICES_CLICK_LOG_TEXT));
            return adChoicesRect;
        }

        private void CreateControls(
            RectTransform panel
          , EditorPreviewConfig config) {
            if (config.Mode == EditorPreviewMode.InFeed) return;

            var closeOnLeft =
                    config.RandomCloseSide && UnityEngine.Random.value < 0.5f;
            closeControlOnLeft = closeOnLeft;
            closeButton = CreateControlButton(
                panel
              , CLOSE_OBJECT_NAME
              , CLOSE_TEXT
              , CLOSE_FONT_SIZE
              , closeOnLeft);
            closeButton.onClick.AddListener(Dismiss);

            countdownControlOnLeft =
                    config.NumberOpposite
                            ? !closeOnLeft
                            : closeOnLeft;
            countdownControl = CreateControlText(
                panel
              , COUNTDOWN_OBJECT_NAME
              , config.CountdownSec.ToString()
              , COUNTDOWN_FONT_SIZE
              , countdownControlOnLeft
              , out countdownText);
            countdownRemaining = config.CountdownSec;

            var countdownFinished = config.CountdownSec <= 0;
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
          , Color color) {
            var label = CreateOverlayText(
                parent
              , text
              , fontSize
              , alignment
              , color);
            var layout = label.gameObject.AddComponent<LayoutElement>();
            layout.minHeight = fontSize * TEXT_HEIGHT_MULTIPLIER;
            layout.preferredHeight = layout.minHeight;
            return label;
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

        private void LateUpdate() {
            var forceInFeedRetry =
                    config != null
                 && config.Mode == EditorPreviewMode.InFeed
                 && inFeedLayoutUnavailable
                 && inFeedLayoutRetryFramesRemaining > 0;
            if (forceInFeedRetry) {
                --inFeedLayoutRetryFramesRemaining;
            }
            RefreshResponsiveLayout(forceInFeedRetry);
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
            ConfigureAdaptiveContentLayout();
            LayoutRebuilder.ForceRebuildLayoutImmediate(content);
            if (MatchIconSizeToIdentityText()) {
                LayoutRebuilder.ForceRebuildLayoutImmediate(content);
            }
            if (config.Mode != EditorPreviewMode.InFeed) {
                var minimumPanelHeight =
                        CONTROL_SIZE_DP + LayoutUtility.GetMinHeight(content);
                ApplyPanelRect(
                    panel
                  , config
                  , uiScale
                  , minimumPanelHeight);
                Canvas.ForceUpdateCanvases();
                ConfigureAdaptiveContentLayout();
            }
            ApplyBadgeRects(
                safeTopInset
              , uiScale);
            LayoutRebuilder.ForceRebuildLayoutImmediate(content);
            if (MatchIconSizeToIdentityText()) {
                LayoutRebuilder.ForceRebuildLayoutImmediate(content);
            }

            lastScreenWidth = Screen.width;
            lastScreenHeight = Screen.height;
            lastSafeArea = Screen.safeArea;
            lastUiScale = uiScale;
        }

        private bool MatchIconSizeToIdentityText() {
            if (config.Mode == EditorPreviewMode.InFeed
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
            if (config.Mode != EditorPreviewMode.FullScreen) {
                return 0f;
            }

            return Mathf.Max(
                0f
              , (Screen.height - Screen.safeArea.yMax) / uiScale);
        }

        private void ApplySafeTopInset(float safeTopInset) {
            if (config.Mode == EditorPreviewMode.InFeed) {
                content.offsetMin = default;
                content.offsetMax = default;
                return;
            }

            content.offsetMax = new(
                -CONTENT_HORIZONTAL_PADDING_DP
              , -(CONTROL_SIZE_DP + safeTopInset));
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

        private void ConfigureAdaptiveContentLayout() {
            if (contentLayout == null
                    || mediaLayoutElement == null
                    || detailsLayoutElement == null
                    || detailsLayout == null
                    || headlineText == null
                    || headlineLayoutElement == null
                    || callToActionText == null
                    || callToActionLayoutElement == null) {
                return;
            }

            if (config.Mode != EditorPreviewMode.InFeed) {
                mediaLayoutElement.gameObject.SetActive(true);
                contentLayout.IsVertical = true;
                contentLayout.spacing = CONTENT_SPACING_DP;
                contentLayout.childForceExpandWidth = true;
                contentLayout.childForceExpandHeight = false;
                detailsLayout.spacing = CONTENT_SPACING_DP;
                ResetStandardLayoutSizes();
                LayoutRebuilder.MarkLayoutForRebuild(content);
                return;
            }

            ConfigureInFeedContentLayout();
            LayoutRebuilder.MarkLayoutForRebuild(content);
        }

        private void ConfigureInFeedContentLayout() {
            var tier = ResolveInFeedTier();
            var spacing = ResolveInFeedSpacing(tier);
            var headlineFontSize = ResolveInFeedHeadlineFontSize(tier);
            var textPadding = ResolveInFeedPadding(tier);
            var callToActionHeight = ResolveInFeedCallToActionHeight();
            var callToActionFontSize =
                    ResolveInFeedCallToActionFontSize(callToActionHeight);

            ApplyTextSize(
                headlineText
              , headlineLayoutElement
              , headlineFontSize);
            ApplyTextSize(
                callToActionText
              , null
              , callToActionFontSize);
            callToActionLayoutElement.minWidth = 0f;
            callToActionLayoutElement.minHeight = callToActionHeight;
            callToActionLayoutElement.preferredHeight =
                    callToActionHeight;
            detailsLayout.spacing = spacing;

            var baseRequiredDetailsHeight =
                    headlineLayoutElement.minHeight
                  + spacing
                  + callToActionHeight
                  + textPadding;
            var requiredDetailsWidth = textPadding * 2f;
            var badgeAvoidanceHeight =
                    ResolveInFeedBadgeSize(ResolveUiScale()) + spacing;
            var minimumVideoMediaSize =
                    ResolveInFeedVideoMinimumMediaSize(ResolveUiScale());
            var availableWidth = Mathf.Max(0f, content.rect.width);
            var availableHeight = Mathf.Max(0f, content.rect.height);
            var nativeSizeIsValid =
                    panel.rect.width >= IN_FEED_NATIVE_MIN_SIZE_DP
                 && panel.rect.height >= IN_FEED_NATIVE_MIN_SIZE_DP;
            if (!nativeSizeIsValid) {
                ConfigureEditorOnlyInFeedImageFallback();
                SetInFeedLayoutAvailable(false);
                return;
            }

            var prefersRow = panel.rect.height > Mathf.Epsilon
                    && panel.rect.width / panel.rect.height
                            >= IN_FEED_ROW_ASPECT_THRESHOLD;
            var rowRequiredDetailsHeight =
                    baseRequiredDetailsHeight + badgeAvoidanceHeight;
            var videoFitsRow =
                    availableHeight >= Mathf.Max(
                        minimumVideoMediaSize
                      , rowRequiredDetailsHeight)
                 && availableWidth
                            >= minimumVideoMediaSize
                             + spacing
                             + requiredDetailsWidth;
            var videoFitsColumn =
                    availableWidth >= minimumVideoMediaSize
                 && availableHeight
                            >= minimumVideoMediaSize
                             + spacing
                             + baseRequiredDetailsHeight;
            var imageFitsRow =
                    availableHeight >= Mathf.Max(
                        IN_FEED_IMAGE_MIN_SIZE_DP
                      , rowRequiredDetailsHeight)
                 && availableWidth
                            >= IN_FEED_IMAGE_MIN_SIZE_DP
                             + spacing
                             + requiredDetailsWidth;
            var imageFitsColumn =
                    availableWidth >= IN_FEED_IMAGE_MIN_SIZE_DP
                 && availableHeight
                            >= IN_FEED_IMAGE_MIN_SIZE_DP
                             + spacing
                             + baseRequiredDetailsHeight;

            var mediaMode = InFeedMediaMode.None;
            var useRow = false;
            if (config.AllowsVideo && (videoFitsRow || videoFitsColumn)) {
                mediaMode = InFeedMediaMode.Video;
                useRow = ResolveRowPreference(
                    prefersRow
                  , videoFitsRow
                  , videoFitsColumn);
            } else if (imageFitsRow || imageFitsColumn) {
                mediaMode = InFeedMediaMode.Image;
                useRow = ResolveRowPreference(
                    prefersRow
                  , imageFitsRow
                  , imageFitsColumn);
            }

            var detailsAvoidBadges =
                    mediaMode == InFeedMediaMode.None || useRow;
            var requiredDetailsHeight =
                    baseRequiredDetailsHeight
                  + (detailsAvoidBadges ? badgeAvoidanceHeight : 0f);
            var requiredAssetsFit =
                    availableWidth > requiredDetailsWidth
                  && availableHeight >= requiredDetailsHeight;
            SetInFeedLayoutAvailable(requiredAssetsFit);
            if (!requiredAssetsFit) {
                ConfigureEditorOnlyInFeedImageFallback();
                return;
            }

            var roundedTextPadding = Mathf.RoundToInt(textPadding);
            detailsLayout.padding = new(
                roundedTextPadding
              , roundedTextPadding
              , detailsAvoidBadges
                        ? Mathf.CeilToInt(badgeAvoidanceHeight)
                        : 0
              , roundedTextPadding);
            ConfigureInFeedMedia(
                mediaMode
              , useRow
              , spacing
              , availableWidth
              , availableHeight
              , requiredDetailsHeight
              , requiredDetailsWidth
              , minimumVideoMediaSize);
        }

        private void ConfigureInFeedMedia(
            InFeedMediaMode mediaMode
          , bool useRow
          , float spacing
          , float availableWidth
          , float availableHeight
          , float requiredDetailsHeight
          , float requiredDetailsWidth
          , float minimumVideoMediaSize) {
            var showMedia = mediaMode != InFeedMediaMode.None;
            mediaLayoutElement.gameObject.SetActive(showMedia);
            contentLayout.IsVertical = !useRow;
            contentLayout.spacing = showMedia ? spacing : 0f;
            contentLayout.childForceExpandWidth = !useRow;
            contentLayout.childForceExpandHeight = useRow;

            detailsLayoutElement.minWidth =
                    requiredDetailsWidth;
            detailsLayoutElement.minHeight = requiredDetailsHeight;
            detailsLayoutElement.preferredWidth = -1f;
            detailsLayoutElement.preferredHeight = -1f;
            detailsLayoutElement.flexibleWidth = 1f;
            detailsLayoutElement.flexibleHeight = 0f;
            if (!showMedia) {
                ResetMediaLayoutSizes();
                return;
            }

            var minimumMediaSize =
                    mediaMode == InFeedMediaMode.Video
                            ? minimumVideoMediaSize
                            : IN_FEED_IMAGE_MIN_SIZE_DP;
            mediaLabel.text =
                    mediaMode == InFeedMediaMode.Video
                            ? VIDEO_MEDIA_TEXT
                            : IMAGE_MEDIA_TEXT;
            if (useRow) {
                var maximumMediaWidth = Mathf.Max(
                    minimumMediaSize
                  , availableWidth
                            - spacing
                            - requiredDetailsWidth);
                mediaLayoutElement.minWidth = minimumMediaSize;
                mediaLayoutElement.preferredWidth = Mathf.Clamp(
                    availableWidth * IN_FEED_ROW_MEDIA_WIDTH_RATIO
                  , minimumMediaSize
                  , maximumMediaWidth);
                mediaLayoutElement.flexibleWidth = 0f;
                mediaLayoutElement.minHeight = minimumMediaSize;
                mediaLayoutElement.preferredHeight = -1f;
                mediaLayoutElement.flexibleHeight = 1f;
                return;
            }

            var maximumMediaHeight = Mathf.Max(
                minimumMediaSize
              , availableHeight - spacing - requiredDetailsHeight);
            mediaLayoutElement.minWidth = minimumMediaSize;
            mediaLayoutElement.preferredWidth = -1f;
            mediaLayoutElement.flexibleWidth = 1f;
            mediaLayoutElement.minHeight = minimumMediaSize;
            mediaLayoutElement.preferredHeight = maximumMediaHeight;
            mediaLayoutElement.flexibleHeight = 1f;
        }

        private void ConfigureEditorOnlyInFeedImageFallback() {
            mediaLayoutElement.gameObject.SetActive(true);
            mediaLabel.text = IMAGE_MEDIA_TEXT;

            contentLayout.IsVertical = true;
            contentLayout.spacing = 0f;
            contentLayout.childForceExpandWidth = true;
            contentLayout.childForceExpandHeight = true;

            detailsLayout.padding = new(0, 0, 0, 0);
            detailsLayout.spacing = 0f;
            detailsLayoutElement.minWidth = 0f;
            detailsLayoutElement.minHeight = 0f;
            detailsLayoutElement.preferredWidth = -1f;
            detailsLayoutElement.preferredHeight = -1f;
            detailsLayoutElement.flexibleWidth = 1f;
            detailsLayoutElement.flexibleHeight = 0f;

            mediaLayoutElement.minWidth = 0f;
            mediaLayoutElement.minHeight = 0f;
            mediaLayoutElement.preferredWidth = -1f;
            mediaLayoutElement.preferredHeight = -1f;
            mediaLayoutElement.flexibleWidth = 1f;
            mediaLayoutElement.flexibleHeight = 1f;
        }

        private void SetInFeedLayoutAvailable(bool available) {
            // Editor preview must never disappear merely because its local
            // approximation cannot prove that a policy-safe layout fits.
            panelGraphic.enabled = true;
            content.gameObject.SetActive(true);
            if (attributionRect != null) {
                attributionRect.gameObject.SetActive(true);
            }
            if (adChoicesRect != null) {
                adChoicesRect.gameObject.SetActive(true);
            }

            if (available) {
                inFeedLayoutUnavailable = false;
                inFeedLayoutWarningIssued = false;
                inFeedLayoutRetryFramesRemaining = 0;
                return;
            }

            if (!inFeedLayoutUnavailable) {
                inFeedLayoutUnavailable = true;
                inFeedLayoutRetryFramesRemaining =
                        IN_FEED_LAYOUT_RETRY_FRAMES;
            }
            if (inFeedLayoutRetryFramesRemaining > 0
                    || inFeedLayoutWarningIssued) {
                return;
            }

            inFeedLayoutWarningIssued = true;
            Debug.LogWarning(
                $"{nameof(EditorPreview)} cannot prove a "
              + "policy-safe native in-feed layout inside "
              + $"{config.SizePx.x}x{config.SizePx.y}px. "
              + "Showing an editor-only image fallback; Android runtime "
              + "will perform the authoritative layout validation.");
        }

        private void ResetStandardLayoutSizes() {
            mediaLayoutElement.minWidth = 0f;
            mediaLayoutElement.minHeight = MEDIA_MIN_SIZE_DP;
            mediaLayoutElement.preferredWidth = -1f;
            mediaLayoutElement.preferredHeight = -1f;
            mediaLayoutElement.flexibleWidth = 1f;
            mediaLayoutElement.flexibleHeight = 1f;
            detailsLayoutElement.minWidth = 0f;
            detailsLayoutElement.minHeight = 0f;
            detailsLayoutElement.preferredWidth = -1f;
            detailsLayoutElement.preferredHeight = -1f;
            detailsLayoutElement.flexibleWidth = 1f;
            detailsLayoutElement.flexibleHeight = 0f;
        }

        private void ResetMediaLayoutSizes() {
            mediaLayoutElement.minWidth = 0f;
            mediaLayoutElement.minHeight = 0f;
            mediaLayoutElement.preferredWidth = -1f;
            mediaLayoutElement.preferredHeight = -1f;
            mediaLayoutElement.flexibleWidth = 1f;
            mediaLayoutElement.flexibleHeight = 1f;
        }

        private static bool ResolveRowPreference(
            bool prefersRow
          , bool rowFits
          , bool columnFits) {
            if (prefersRow && rowFits) return true;
            if (!prefersRow && columnFits) return false;
            return rowFits;
        }

        private InFeedTier ResolveInFeedTier() {
            var shortSide = Mathf.Min(panel.rect.width, panel.rect.height);
            if (shortSide >= IN_FEED_ROOMY_SHORT_SIDE_DP) {
                return InFeedTier.Roomy;
            }
            if (shortSide >= IN_FEED_REGULAR_SHORT_SIDE_DP) {
                return InFeedTier.Regular;
            }
            return InFeedTier.Compact;
        }

        private static float ResolveInFeedPadding(InFeedTier tier) {
            return tier switch {
                InFeedTier.Roomy => IN_FEED_ROOMY_PADDING_DP
              , InFeedTier.Regular => IN_FEED_REGULAR_PADDING_DP
              , _ => IN_FEED_COMPACT_PADDING_DP
            };
        }

        private static float ResolveInFeedSpacing(InFeedTier tier) {
            return tier switch {
                InFeedTier.Roomy => IN_FEED_ROOMY_SPACING_DP
              , InFeedTier.Regular => IN_FEED_REGULAR_SPACING_DP
              , _ => IN_FEED_COMPACT_SPACING_DP
            };
        }

        private static int ResolveInFeedHeadlineFontSize(InFeedTier tier) {
            return tier switch {
                InFeedTier.Roomy => FULLSCREEN_HEADLINE_FONT_SIZE
              , InFeedTier.Regular => COMPACT_HEADLINE_FONT_SIZE
              , _ => IN_FEED_COMPACT_FONT_SIZE
            };
        }

        private float ResolveInFeedCallToActionHeight() {
            var shortSide = Mathf.Min(panel.rect.width, panel.rect.height);
            return Mathf.Clamp(
                shortSide * IN_FEED_CTA_SHORT_SIDE_RATIO
              , IN_FEED_CTA_MIN_HEIGHT_DP
              , IN_FEED_CTA_MAX_HEIGHT_DP);
        }

        private static int ResolveInFeedCallToActionFontSize(
            float callToActionHeight) {
            return Mathf.Clamp(
                Mathf.RoundToInt(
                    callToActionHeight * IN_FEED_CTA_FONT_HEIGHT_RATIO)
              , IN_FEED_CTA_MIN_FONT_SIZE
              , CALL_TO_ACTION_FONT_SIZE);
        }

        private static void ApplyTextSize(
            Text text
          , LayoutElement layout
          , int fontSize) {
            text.fontSize = fontSize;
            if (layout == null) return;

            layout.minHeight = fontSize * TEXT_HEIGHT_MULTIPLIER;
            layout.preferredHeight = layout.minHeight;
        }

        private void ApplyBadgeRects(
            float safeTopInset
          , float uiScale) {
            if (config.Mode == EditorPreviewMode.InFeed) {
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
            if (adChoicesRect != null) {
                SetTopRightRect(
                    adChoicesRect
                  , new(0f, -safeTopInset)
                  , new(
                        AD_CHOICES_PREVIEW_SIZE_DP
                      , AD_CHOICES_PREVIEW_SIZE_DP));
            }
        }

        private float ResolveInFeedBadgeSize(float uiScale) {
            var minimumBadgeSize = MIN_BADGE_SIZE_PX / uiScale;
            var shortSide = Mathf.Min(
                panel.rect.width
              , panel.rect.height);
            return Mathf.Clamp(
                shortSide * IN_FEED_BADGE_SHORT_SIDE_RATIO
              , minimumBadgeSize
              , AD_CHOICES_PREVIEW_SIZE_DP);
        }

        private static float ResolveInFeedVideoMinimumMediaSize(
            float uiScale) {
            return Mathf.Max(
                IN_FEED_VIDEO_MIN_SIZE_DP
              , IN_FEED_VIDEO_MIN_LONG_SIDE_PX
                        / Mathf.Max(Mathf.Epsilon, uiScale));
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

        private void TryOpenPreviewUrl(string url, string logMessage) {
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

    internal sealed class EditorPreviewMediaGraphic : MaskableGraphic {
        private const int SUN_SEGMENTS = 18;
        private const float SUN_RADIUS_RATIO = 0.09f;
        private const float SUN_X_RATIO = 0.78f;
        private const float SUN_Y_RATIO = 0.74f;
        private const float BACK_PEAK_X_RATIO = 0.48f;
        private const float BACK_PEAK_Y_RATIO = 0.70f;
        private const float BACK_END_X_RATIO = 0.88f;
        private const float FRONT_START_X_RATIO = 0.24f;
        private const float FRONT_PEAK_X_RATIO = 0.68f;
        private const float FRONT_PEAK_Y_RATIO = 0.58f;

        private static readonly Color TopColor =
                new(0.10f, 0.15f, 0.24f, 1f);
        private static readonly Color BottomColor =
                new(0.08f, 0.43f, 0.48f, 1f);
        private static readonly Color BackMountainColor =
                new(0.22f, 0.61f, 0.62f, 0.88f);
        private static readonly Color FrontMountainColor =
                new(0.10f, 0.28f, 0.38f, 0.94f);
        private static readonly Color SunColor =
                new(1f, 0.76f, 0.24f, 0.95f);

        private protected override void OnPopulateMesh(VertexHelper vertexHelper) {
            vertexHelper.Clear();
            var rect = GetPixelAdjustedRect();
            EditorPreviewGraphicUtility.AddGradientQuad(
                vertexHelper
              , rect
              , BottomColor
              , TopColor);

            var sunRadius =
                    Mathf.Min(rect.width, rect.height)
                            * SUN_RADIUS_RATIO;
            EditorPreviewGraphicUtility.AddCircle(
                vertexHelper
              , new(
                    Mathf.Lerp(rect.xMin, rect.xMax, SUN_X_RATIO)
                  , Mathf.Lerp(rect.yMin, rect.yMax, SUN_Y_RATIO))
              , sunRadius
              , SunColor
              , SUN_SEGMENTS);

            EditorPreviewGraphicUtility.AddTriangle(
                vertexHelper
              , new(rect.xMin, rect.yMin)
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , BACK_PEAK_X_RATIO)
                  , Mathf.Lerp(
                        rect.yMin
                      , rect.yMax
                      , BACK_PEAK_Y_RATIO))
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , BACK_END_X_RATIO)
                  , rect.yMin)
              , BackMountainColor);
            EditorPreviewGraphicUtility.AddTriangle(
                vertexHelper
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , FRONT_START_X_RATIO)
                  , rect.yMin)
              , new(
                    Mathf.Lerp(
                        rect.xMin
                      , rect.xMax
                      , FRONT_PEAK_X_RATIO)
                  , Mathf.Lerp(
                        rect.yMin
                      , rect.yMax
                      , FRONT_PEAK_Y_RATIO))
              , new(rect.xMax, rect.yMin)
              , FrontMountainColor);
        }
    }

    internal sealed class EditorPreviewAdChoicesGraphic : MaskableGraphic {
        private const int INFO_DOT_SEGMENTS = 12;
        private const float ICON_SIZE_RATIO = 0.72f;
        private const float HALF = 0.5f;
        private const float INFO_X_OFFSET_RATIO = 0.24f;
        private const float INFO_BAR_WIDTH_RATIO = 0.12f;
        private const float INFO_BAR_HEIGHT_RATIO = 0.34f;
        private const float INFO_BAR_Y_OFFSET_RATIO = 0.34f;
        private const float INFO_DOT_Y_OFFSET_RATIO = 0.28f;
        private const float INFO_DOT_RADIUS_RATIO = 0.62f;

        private static readonly Color BackgroundColor =
                new(1f, 1f, 1f, 0.92f);
        private static readonly Color IconColor =
                new(0.10f, 0.56f, 0.82f, 1f);
        private static readonly Color InfoColor =
                Color.white;

        private protected override void OnPopulateMesh(VertexHelper vertexHelper) {
            vertexHelper.Clear();
            var rect = GetPixelAdjustedRect();
            EditorPreviewGraphicUtility.AddSolidQuad(
                vertexHelper
              , rect
              , BackgroundColor);

            var size =
                    Mathf.Min(rect.width, rect.height)
                            * ICON_SIZE_RATIO;
            var center = rect.center;
            var half = size * HALF;
            EditorPreviewGraphicUtility.AddTriangle(
                vertexHelper
              , new(center.x - half, center.y - half)
              , new(center.x + half, center.y)
              , new(center.x - half, center.y + half)
              , IconColor);

            var barWidth = size * INFO_BAR_WIDTH_RATIO;
            var barHeight = size * INFO_BAR_HEIGHT_RATIO;
            EditorPreviewGraphicUtility.AddSolidQuad(
                vertexHelper
              , new Rect(
                    center.x
                            - half * INFO_X_OFFSET_RATIO
                            - barWidth * HALF
                  , center.y
                            - half * INFO_BAR_Y_OFFSET_RATIO
                  , barWidth
                  , barHeight)
              , InfoColor);
            EditorPreviewGraphicUtility.AddCircle(
                vertexHelper
              , new(
                    center.x - half * INFO_X_OFFSET_RATIO
                  , center.y
                            + half * INFO_DOT_Y_OFFSET_RATIO)
              , barWidth * INFO_DOT_RADIUS_RATIO
              , InfoColor
              , INFO_DOT_SEGMENTS);
        }
    }

    internal static class EditorPreviewGraphicUtility {
        internal static void AddSolidQuad(
            VertexHelper vertexHelper
          , Rect rect
          , Color color) {
            AddGradientQuad(
                vertexHelper
              , rect
              , color
              , color);
        }

        internal static void AddGradientQuad(
            VertexHelper vertexHelper
          , Rect rect
          , Color bottomColor
          , Color topColor) {
            var start = vertexHelper.currentVertCount;
            AddVertex(
                vertexHelper
              , new(rect.xMin, rect.yMin)
              , bottomColor
              , new(0f, 0f));
            AddVertex(
                vertexHelper
              , new(rect.xMin, rect.yMax)
              , topColor
              , new(0f, 1f));
            AddVertex(
                vertexHelper
              , new(rect.xMax, rect.yMax)
              , topColor
              , new(1f, 1f));
            AddVertex(
                vertexHelper
              , new(rect.xMax, rect.yMin)
              , bottomColor
              , new(1f, 0f));
            vertexHelper.AddTriangle(start, start + 1, start + 2);
            vertexHelper.AddTriangle(start + 2, start + 3, start);
        }

        internal static void AddTriangle(
            VertexHelper vertexHelper
          , Vector2 first
          , Vector2 second
          , Vector2 third
          , Color color) {
            var start = vertexHelper.currentVertCount;
            AddVertex(vertexHelper, first, color, Vector2.zero);
            AddVertex(vertexHelper, second, color, Vector2.up);
            AddVertex(vertexHelper, third, color, Vector2.one);
            vertexHelper.AddTriangle(start, start + 1, start + 2);
        }

        internal static void AddCircle(
            VertexHelper vertexHelper
          , Vector2 center
          , float radius
          , Color color
          , int segments) {
            var centerIndex = vertexHelper.currentVertCount;
            AddVertex(
                vertexHelper
              , center
              , color
              , new(0.5f, 0.5f));
            var outerStartIndex = vertexHelper.currentVertCount;
            for (var index = 0; index < segments; ++index) {
                var angle =
                        index * Mathf.PI * 2f / segments;
                var unit = new Vector2(
                    Mathf.Cos(angle)
                  , Mathf.Sin(angle));
                AddVertex(
                    vertexHelper
                  , center + unit * radius
                  , color
                  , new(
                        unit.x * 0.5f + 0.5f
                      , unit.y * 0.5f + 0.5f));
            }

            for (var index = 0; index < segments; ++index) {
                vertexHelper.AddTriangle(
                    centerIndex
                  , outerStartIndex + index
                  , outerStartIndex + (index + 1) % segments);
            }
        }

        internal static void AddVertex(
            VertexHelper vertexHelper
          , Vector2 position
          , Color color
          , Vector2 uv) {
            var vertex = UIVertex.simpleVert;
            vertex.position = position;
            vertex.color = color;
            vertex.uv0 = uv;
            vertexHelper.AddVert(vertex);
        }
    }
}
#endif
