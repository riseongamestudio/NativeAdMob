# RiseOn.NativeAdMob

Native ad của AdMob mặc áo full-screen / half-screen (collapsible) / in-feed,
cộng hai màn che trơn (`FullScreenCover`, `HalfScreenCover`) xếp cùng thang
với ad. `Core/` là C# dùng chung; `Platform/Android`, `Platform/iOS` là bridge
sang Java và Obj-C++; `Platform/Editor` là bản mô phỏng trong Editor.

Tài liệu này viết để **một người (hay một AI) mất sạch ngữ cảnh vẫn làm việc
được ngay**: không chỉ luật, mà cả cách mọi thứ vận hành, ý tưởng layout,
từng đánh đổi đã chọn kèm phương án bị bác, và trạng thái bàn giao. Vấn đề
riêng từng nền tảng ở README nền tảng:

- [Platform/Android/README.md](Platform/Android/README.md) — Activity,
  window type, luồng giao sự kiện, cổng javac, adb.
- [Platform/iOS/README.md](Platform/iOS/README.md) — bảng đối chiếu file,
  quy ước pt/px, subview, UnityPause, giới hạn chưa compile.
- [Platform/Editor/README.md](Platform/Editor/README.md) — preview là ảnh
  chụp chứ không phải mô phỏng, asmdef, sorting order, pause.

Ba nền tảng là ba hiện thực **ngang hàng** của bộ luật này — không bên nào là
bản sao của bên nào. **Mỗi thay đổi layout hay hành vi phải làm ở cả ba bên**,
trừ khi README nền tảng nói rõ bên đó không vẽ được thứ ấy. Không bao giờ hạ
cấp bản máy thật cho khớp với Editor.

## Cách đọc

- **10 phút để làm việc được**: A1 → A2 → A8 (thuật ngữ) → A9 (đánh đổi) →
  C4 (bàn giao).
- **Trước khi sửa layout**: A5, A6, A7, rồi B6–B12, rồi A10 để tìm hàm.
- **Trước khi sửa luồng load/show/callback**: A3, A4, B1–B4, README nền tảng.
- **Khi thấy một hành vi "kỳ"**: C2 sổ sự cố trước, rất có thể đã gặp.

Mục lục — Phần A (bức tranh): A1 bản đồ · A2 kiến trúc C# · A3 vòng đời
overlay · A4 vòng đời in-feed · A5 engine in-feed · A6 dựng layout overlay ·
A7 template · A8 thuật ngữ · A9 đánh đổi · A10 bản đồ đọc code · A11 tích hợp
game. Phần B (luật): B1 sự kiện/luồng · B2 cache · B3 xếp lớp · B4 pause ·
B5 RedirectOnClose · B6 tháp ưu tiên/chữ · B7 media · B8 side-media · B9
padding 5dp · B10 sân media/né control · B11 bo góc · B12 badge/AdChoices ·
B13 slot in-feed · B14 tên/phong cách. Phần C (vận hành): C1 quy trình/cổng ·
C2 sổ sự cố · C3 việc còn mở · C4 bàn giao.

---

# Phần A — Bức tranh

## A1. Bản đồ package

```
RiseOn.NativeAdMob/
  README.md                       luật chung (file này)
  RiseOn.NativeAdMob.asmdef       core: API công khai + máy trạng thái + SPI internal
  AssemblyInfo.cs                 InternalsVisibleTo ba assembly nền tảng
  Core/
    BaseAd.cs                     xương sống: cổng release, DispatchFromNative, OnAdLoaded/OnAdLoadFailed/OnAdPaid
    InFeedAd.cs                   một unit = một ad unit id sống trọn đời app, N slot, Item là handle không trạng thái
    OverlayAd.cs                  máy trạng thái show/hide chung; FullScreenAd + HalfScreenAd là hai mặt của nó
    FullScreenCover.cs / HalfScreenCover.cs   màn che trơn, static vì mỗi game có đúng một cái
    I*Client.cs / I*Callbacks.cs / IAdPlatform.cs / ICoverPlatform.cs   SPI internal
    AdPlatformRegistry.cs         nơi assembly nền tảng cắm factory vào
  Platform/Android/               AdPlatform + InFeedAdClient + OverlayAdClient + CoverPlatform (AndroidJavaObject)
    Bridge/NativeAdMob.androidlib  Java: com.riseon.nativeadmob.*
  Platform/iOS/                   cùng bộ client qua DllImport "__Internal"
    Bridge/Native/                Obj-C++: RO*.h/.mm
  Platform/Editor/                cùng bộ client, vẽ bằng uGUI
    Preview/                      EditorAd, EditorCover, EditorPause, EditorSortingOrder
```

Ba tầng vẽ đè lên game, hai tầng trên mỗi tầng một cặp ad + màn che:

    in-feed  →  half-screen (ad, HalfScreenCover)  →  full-screen (ad, FullScreenCover)

Hai bản native đặt tên lớp giống nhau (Java `InFeedAd`, Obj-C++ `ROInFeedAd`).
In-feed tách làm hai lớp: **unit** (cache ad thô, load, backoff no-fill) và
**slot** (mọi thứ có hình chữ nhật: layout, dwell, xoay vòng, watchdog). Một
slot dựng giao diện qua `*Presentation`, chọn layout bằng `*LayoutEngine`
(chấm điểm ứng viên), dựng view bằng `*ViewFactory`, bị `*LayoutValidator`
gác về chính sách. Overlay chỉ có một lớp native `OverlayAd` cho cả full và
half; nội dung dựng trong `OverlayAdContentView` (một cây view tự co giãn,
không có engine chấm điểm), trình bày qua `OverlayAdActivity` (full) hoặc
`OverlayAdPresentation` (Dialog, half). Bảng đối chiếu tên file giữa hai bản
native ở README iOS.

## A2. Kiến trúc C#: một core, ba assembly nền tảng

- **Không `#if`, không partial trong C# của pack** (e6473593). Core
  `RiseOn.NativeAdMob` giữ API và máy trạng thái, nói chuyện với nền tảng qua
  SPI internal (`IInFeedAdClient`, `IOverlayAdClient`, hai interface callback,
  `IAdPlatform`, `ICoverPlatform`). Ba assembly `RiseOn.NativeAdMob.Android` /
  `.iOS` / `.Editor` mỗi cái chỉ biên dịch cho nền tảng của nó và tự cắm
  factory bằng `[RuntimeInitializeOnLoadMethod(SubsystemRegistration)]` gác
  `Application.platform`. Bản build nào cũng chỉ có đúng một assembly nền
  tảng. Chủ dự án thích asm hơn `#if`; chỉ dùng `#if` khi bắt buộc.
- Client là **transport câm**: không giữ trạng thái, không gác generation.
  Cổng release (`releasedManaged` + `nativeAdStateLock`), `showGeneration`,
  cờ ready/loading đều nằm trong core. Thêm hành vi thì thêm ở core, một lần
  cho ba bên.
- **Máy trạng thái `OverlayAd` (C#)**: `Show()` — từ chối nếu released /
  `showPendingOrActive` (raise `OnAdDisplayFailed` mã −1 ngay), không thì
  `showPendingOrActive = true`, `++showGeneration`, gọi client với generation
  làm `showId`. `OnShowCompleted(showId, message, adConsumed, cachedCount)` →
  `TryTakeShow` (chỉ khớp generation hiện tại) → `OnAdHidden` hoặc
  `OnAdDisplayFailed` → nếu `adConsumed` thì `Load()`. `OnDisplayed` →
  `OnAdDisplayed` + `Load()` (prefetch). `OnStateChanged(isReady, isLoading)`
  ghi đồng bộ dưới lock; `IsReady()` = không released, không đang show, có
  client, `cachedAdReady`. `Release()` huỷ show đang treo bằng một
  DisplayFailed.
- `InFeedAd` (C#): `Initialize(sizePx[, roundCornerPx])` là bắt buộc trước
  Show/SetPosition (slot không có kích thước thì không có chỗ vẽ); mỗi
  `Item.Show(onDisplayed)` giữ callback theo slot tới `OnSlotDisplayed`.
  `SlotCount ∈ [1, 8]`; indexer mang `[IndexerName("Slots")]` vì metadata
  indexer mặc định trùng tên struct `Item` (CS0102, f7d2106e).
- `OverlayAd` abstract, constructor `private protected`: `FullScreenAd` (app
  open / interstitial / end card) và `HalfScreenAd` (collapsible,
  `HeightRatio`) là hai mặt duy nhất.
- Settings là struct nhận lúc sinh; đổi sau thì có API riêng (`SetClose`,
  `Initialize`). `Format` là chuỗi placement do game đặt, đi kèm mọi
  `OnAdPaid` (`AdInfo` ráp ở `BaseAd.RaiseAdPaid`, rời pack đã đầy đủ).
- Bề mặt public ngoài assembly đúng bằng: các class, `Settings`, `Item`, event,
  method public. `protected` đã hạ hết xuống `private protected`, trừ override
  `Graphic.OnPopulateMesh` bên Editor (CS0507, e0e656cd).
- `using X;` **không** resolve namespace con — vụ `NativeAdMob.InFeed` từng vỡ
  compile; dùng alias hoặc tên đầy đủ.
- Lỗi: `AdError{Code, Message}` chỉ đi kèm event thất bại. Mã −1 là show bị
  từ chối ngay phía C#; no-fill Android 3, iOS 1 — C# chỉ phân biệt 0/khác 0.
- Màu đi qua cầu là một `int` ARGB (`AdColor`). **Trong suốt hoàn toàn là câu
  trả lời thật**, không phải "chưa đặt".

## A3. Vòng đời một overlay ad (native)

Đọc theo `OverlayAd.java` (iOS: `ROOverlayAd.mm`, cùng tên hàm không prefix):

1. `Configure(...)` → `configuredStyle` (`OverlayAdStyle`, bất biến;
   `SetClose` tạo bản mới bằng `WithClose`). `Random` của close side được
   **roll một lần mỗi presentation** (`ResolveCloseOnLeft`), timer đọc kết
   quả đó (`ResolveTimerOnLeft`), không roll lại.
2. `Load(activity)` → `StartLoad`: **cổng duy nhất** — chỉ đi khi cache còn
   ghế và không có load đang bay (`ShouldStartLoad`-tương đương), bất kể ai
   đang trên màn hình. `DoLoadAd` dùng `AdLoader` với
   `CreateNativeAdOptions` (aspect ANY, AdChoices top-right, video muted theo
   style).
3. Load về → `AddCachedAd` (deque **cũ nhất trước**, `ownedAds` cho paid
   event), `BindPaidEvent` theo **identity ad**; nếu ad là **đầu hàng** →
   `PrepareFace`: half-screen dựng cả `OverlayAdPresentation` và `Prepare()`,
   full-screen dựng `OverlayAdContentView` (`PrepareFullScreenContent`), kèm
   `MediaSignature` (video|mainImage|anyImage|aspect). Creative không layout
   được bị `DropUnrenderableAd` ngay tại đây — lúc không tốn gì của người
   chơi. Một load thành công **tự nối** load kế tới khi đầy;
   `NotifyLoadingCompleted(cachedCount, cacheSize)` + `NotifyStateChanged`.
4. Load hỏng → hai chuỗi retry riêng: no-fill (`ScheduleNoFillRetry`,
   `BackoffDelayMs(streak, 0)`: 1s×2^n, trần 32s) và layout
   (`ScheduleLayoutRetry`, `BackoffDelayMs(streak, 2)`: hai lượt đầu ngay).
   Chưa có gì từng layout được (`layoutProven == false`) mà cứ hỏng → panel
   là nghi phạm → poll chậm.
5. `Show(activity, onCompleted)` (hop main): từ chối nếu released / chưa
   configure / đang show; **quét hết hạn trước** (`RemoveExpiredCachedAds` →
   `RefreshHeadFace`, `NotifyCurrentState`, `StartLoad`); cache rỗng →
   "Ad not ready"; `TakeCachedAd` → `activeNativeAd`; post: `StartLoad` +
   `PrepareFace(head mới)` (chờ một nhịp để show này lấy đúng face đã hứa).
   - Full: `TakePreparedFullScreenContent` → `OverlayAdActivity.RegisterSession`
     + `StartSession` (kèm `VerifyInitialAttach`, xem README Android). Không
     có trạm "unrenderable" ở đường này (chủ dự án chốt).
   - Half: `TakePreparedPresentation` hoặc dựng tươi (`CreatePresentation` +
     `Prepare`), `IsLayoutUnrenderable` → `ScheduleLayoutRetry` + complete
     lỗi; `Show()`.
6. Kết thúc: `CompletePresentation(ad, message)` → `ForgetAd`/destroy,
   `NotifyCompleted(onCompleted, message, adConsumed)` với `cachedCount` chụp
   tại chỗ, rồi `NotifyCurrentState`. Message rỗng = đóng bình thường; khác
   rỗng = C# đổi thành `OnAdDisplayFailed`. `Hide()` ép đóng.
7. Hết hạn: `ScheduleCacheExpiry` theo ad già nhất; `HandleCacheExpiry` huỷ
   và nạp lại, face dựng trên ad đó bị vứt trước (`RefreshHeadFace`).
   `RequestPreparedFaceRebuild` khi asset về muộn đổi `MediaSignature`.

Tất cả `Notify*` đi qua `AdEventDispatcher` (B1). Tham số chụp local trước
khi post.

## A4. Vòng đời một ô in-feed (native)

`InFeedAd.java` (unit) + `InFeedAdSlot.java` + `InFeedAdPresentation.java`:

1. Constructor unit(activity, adUnitId, slotCount, cacheSize, backgroundColor)
   → bắt đầu `StartLoad` ngay (không có `Load()` công khai). Cache
   `CachedAd{ad, loadedAtMs}`, `MAX_CACHE_SIZE` 5, cacheSize 0 → slotCount+1.
2. `Configure(activity, slot, x, y, w, h, roundCornerPx)` → `AdoptActivity`
   (Activity đổi → mọi slot `HandleActivityChanged`: bỏ hết đã materialize,
   chỉ cache thô sống) → `slot.Configure(SlotRect)`. Rect khác phải "chứng
   minh lại" (streak layout về 0). Bán kính đi theo rect (B11).
3. `Show(activity, slot)` → `ShowCore`: có entry active đã layout → bật
   window lên thẳng; không → `PresentCachedAd`: lấy ad khỏi cache
   (`TakeCachedAd`, unit `RequestLoad` bù ngay) → tạo `DisplayEntry`
   **materializing** với một `InFeedAdPresentation` → `Show()` của
   presentation (A5 bước 5) → `HandlePresentationReady` → promote thành
   **active** → `HandlePresentationDisplayed` → `NotifySlotDisplayed`.
   Cặp active/materializing là hai mặt của một slot: ad đang hiện và ad đang
   được dựng ngầm để thay.
4. `Hide(slot)` → ẩn window → `PostHiddenSwap` (post từ frame callback để
   hide được vẽ trước) → `HandleHiddenSwap`: xoay nếu ad đã đứng đủ
   `MIN_DWELL_MS` 4s và cách lần đổi trước ≥ `MIN_SWAP_INTERVAL_MS` 3s.
5. Timer: `HandleRefresh` (dwell 30s cho slot không bao giờ ẩn) →
   `TrySwapActiveEntry`; `HandleEntryExpiry` (1 giờ, bỏ qua dwell);
   `HandleWatchdog` (1s) dọn entry kẹt; `HandleForegroundRecheck` (1s khi
   Activity nền) — chỉ present cho slot trống; `HandleLayoutRetry`.
6. Unit: `HandleLoadedAd` → `OfferCacheToSlots` (slot đầu tiên đã configure
   và trống, `WantsCachedAd`); `HandleRetry`/`ScheduleNoFillRetry`;
   `HandleCacheExpiry`; `OwnsAd` (set concurrent) cho paid event;
   `CommitAdClick` latch chống double-click nhả theo window visibility.

## A5. Engine in-feed: layout được chọn thế nào

`InFeedAdPresentation.Show()`:

1. Chờ `android.R.id.content` sẵn sàng (tối đa 10s, hết thì dismiss bình
   thường — không được park slot). Creative thiếu headline hoặc CTA → fail;
   icon có mà không có drawable → fail (không render an toàn được).
2. `viewFactory.FindMainImage()`, `HasVideoContent()` →
   `layoutEngine.ChoosePlans(screenW, screenH, hasVideo, hasMainImage)`.
3. **`ChoosePlans`**: với mỗi tier ∈ {COMPACT, REGULAR, ROOMY} × mỗi tổ hợp
   asset tuỳ chọn (body, advertiser, rating, icon — 16 mask, bỏ mask đòi asset
   không có) → `EvaluatePlan` cho từng template: `COMPACT_ROW`,
   `COMPACT_COLUMN` luôn; có ảnh chính → thêm `MEDIA_LEFT`, `MEDIA_TOP`,
   `MEDIA_BACKGROUND`, `MEDIA_RIGHT`; có video → `MEDIA_LEFT` video + hai
   fallback không media (không bao giờ ảnh tĩnh dưới sàn, không bao giờ nền).
   `UpdateVariantBest` giữ bản tốt nhất mỗi (template, tier, mask).
4. **`EvaluatePlan`**: chặn bề rộng tối thiểu (`MinimumWidth(tier)` = sàn
   media + 2×thụt góc, trừ compact không media) → `ResolveMediaDimensions`
   (cỡ media theo tier/aspect, `ResolveVideoSlot` cho video) → **thang chữ**
   `TEXT_LADDER_MODES/SCALES` (B6): mỗi nấc lấy cây probe cache theo
   (template, tier, media, video, mask) từ `GetProbeLayout`, áp cỡ/chế độ
   chữ (`ConfigureProbeLayout`), đo chiều cao tự nhiên
   (`MeasureNaturalContentHeight`, UNSPECIFIED), MEDIA_TOP ảnh thì
   `ShrinkImageTopToFitHeight` (co media về sàn rồi nhị phân lên), đo EXACT,
   `NudgeCutTextsWhole` (co riêng dòng cắt 0.92/bước tới 0.7), rồi
   `validator.ValidateAssetGeometry(probe)`; nấc đầu tiên qua thì tính điểm
   (bảng ở B6), ghi `measuredContentHeight`. Không nấc nào qua →
   `RecordRejection` (đọc được qua `DescribeLastRejections` trong log fail).
5. `FinalizePlanList`: sort theo điểm, `PruneDominatedPlans` (mỗi họ
   template/tier/media giữ bản tốt nhất + một bản "chỉ asset bắt buộc" làm
   dự phòng), `ExpandMediaWithinPlan` cho vài plan media đầu (nở media trong
   `ContentWidth/Height`, giữ chỗ chữ `RequiredTextSlotWidth`).
6. Presentation đi **theo thứ tự** danh sách: `ActivateNextLayoutPlan` →
   `viewFactory.BuildNativeAdView(plan, mainImage)` (dựng cây thật, đăng ký
   asset, `setNativeAd`) → `CreateAndShowHostDialog` (window
   `TYPE_APPLICATION_PANEL`, không focusable, dưới nav bar) →
   `ObserveFinalAssetGeometry`: chờ hình học ổn định (`GeometrySignature`
   giống nhau 3 pass, quan sát 80–500ms), validate lần cuối trên cây thật
   (SDK có thể đã thêm view của nó); hỏng → `RejectActivePlanAndTryNext`;
   qua → `HandlePresentationReady`. `FitHostInsideContentRoot` /
   `UpdateHostLayoutForActivePlan` đặt window đúng rect.

Log `In-feed layout ready <plan>` là bản tóm tắt bước 6 — đọc nó trước khi
đoán vì sao ô trông như vậy.

## A6. Overlay: layout được dựng thế nào

`OverlayAdContentView.Build(requestedPanelHeight)` — một cây `LinearLayout`
tự co giãn, quyết định theo thứ tự:

1. Media có gì: video / `mainImage` / ảnh fallback (`FindFallbackMediaImage`);
   `minimumMediaSize` = 120dp (video) hay 48dp (ảnh); aspect từ
   `GetMediaAspectRatio` (cờ "có báo" hay không).
2. Panel: full = cả màn; half = `ResolveHalfScreenPanelHeight(ratio)` (một
   nơi duy nhất, màn che dùng chung). `panelHostsMedia` = full hoặc panel ≥
   sàn media + 88dp nội dung dưới. `tickerLayout` = half và panel < 120dp.
   `iconHero` = half, không ticker, (không media hoặc không chứa nổi) và có
   icon. `sideMediaLayout` = có media và `ShouldUseSideMedia` (aspect < 0.85,
   panel ≤ 1.3×bề rộng, media ≥ sàn, rail ≥ 120dp; bề rộng media =
   min(panelH×aspect, 0.56×W)).
3. Dựng: `NativeAdView` → `contentColumn` (padding 5dp hai bên, 30dp dải
   control trên, `clipToPadding false` để media bleed bằng margin âm) →
   `MediaView` (nền đen cố ý, FIT_CENTER, min = sàn) → identity row (icon
   36dp + headline) → advertiser/rating → body → CTA (44–56dp) →
   `CreateAttributionView`, `CreateControlView` ×2 (close vẽ bằng
   `CloseGlyphDrawable`, countdown) → `BindAssets` → `setNativeAd`.
4. Chọn đường:
   - **Side-media**: `ConfigureResponsiveSideRail` (ngân sách B8; rail: icon
     đứng riêng ≤30% rail, chữ full width, `ApplyRailContentScale`,
     `GrowTextLines`, `GrowRailIcon`, `RailFits`); media để thừa height thì
     CTA/body **tràn xuống dưới** full width.
   - **Half-screen xếp chồng**: `ConfigureResponsiveCollapsibleContent`:
     thử **né dải control** trước (`RunCollapsibleFitPipeline` với media thụt
     lề, nếu hụt thì `ApplyStripAvoidingFloors` CTA 36 / icon 28 / bỏ
     padding phụ rồi `ContentFitsWithMinimumMedia`); vừa → giữ bộ sàn gọn
     làm **trang phục mặc định** + `EnableControlAvoidance`; không vừa →
     bleed media + chạy lại pipeline; vẫn không → **scrim**
     (`ApplyScrimLayout`) hoặc **không media** cho video
     (`AdoptMedialessVideoLayout`); cả hai không được → `layoutUnrenderable`.
   - **Full-screen**: cùng `EnableControlAvoidance` (từ 1c848203 thang co
     chrome sống trong pass né, chung hai format).
5. **Né control** (`EnableControlAvoidance` → `RecomputeMediaControlAvoidance`):
   stack dồn đáy (`Gravity.BOTTOM`), đo phần dưới (`MeasureLowerContent`),
   bốn ứng viên vị trí media (B10), chọn to nhất chạm tường nhìn thấy,
   control di chuyển theo; box thắng bị chặn chiều cao mà chưa chạm ngang →
   `TrimChromeForStarvedMedia` rồi tính lại; không ứng viên nào chứa nổi sàn
   → `avoidanceFoundNoBand` → scrim/medialess. **Kế hoạch được lập trong
   `onMeasure`** theo kích thước thật của panel, idempotent
   (`plannedPanelWidth/Height`, `avoidancePlanDirty`, `planningInMeasure`
   chặn `requestLayout` thừa) — xem B6 mục "layout pass".
6. `SettleContentBeforeLayout` trước lần layout đầu: `SettleIconSize`
   (icon theo chữ, trừ khi pipeline đã "tiêu" icon), `SettleTextFitting`
   (mỗi text co tối đa một lần), tối đa 4 vòng; `MatchMediaSizeToAvailableSpace`,
   `ObserveMediaSize`, `ObserveBadgePositions`, `UpdateControlInsets`.
7. Trình bày: half → `OverlayAdPresentation` (Dialog, `TYPE_APPLICATION_SUB_PANEL`,
   `FLAG_NOT_FOCUSABLE`, dưới nav bar); full → `OverlayAdActivity` attach
   content dựng sẵn từ `Session`. `OnPresented(remainingMs)` bật countdown;
   `OnPaused`/visibility đóng băng nó.

## A7. Các template

In-feed (`InFeedAdLayoutEngine.TEMPLATE_*`), mỗi ô có badge "Ad" góc trái
trên và AdChoices góc phải trên, nội dung thụt theo bo góc:

```
COMPACT_ROW            COMPACT_COLUMN           MEDIA_TOP
┌──────────────────┐   ┌──────────────────┐     ┌──────────────────┐
│Ad            (i) │   │Ad   [ icon ]  (i)│     │Ad   ┌──────┐ (i)│
│[ic] Headline     │   │  Headline        │     │     │media │    │
│     body… [ CTA ]│   │  body            │     │     └──────┘    │
└──────────────────┘   │[ic][   CTA     ] │     │Headline / body   │
 hàng, căn giữa dọc    └──────────────────┘     │[ic][   CTA     ] │
                        icon nuốt height dư     └──────────────────┘
                                                 media nuốt height dư
MEDIA_LEFT (RIGHT là gương)                    MEDIA_BACKGROUND (scrim)
┌──────────────────┐                           ┌──────────────────┐
│Ad            (i) │                           │Ad  ảnh phủ ô (i) │
│┌─────┐ [ic] Head │                           │  + ambient sau    │
││media│ body      │                           │  + veil 70% đen   │
│└─────┘ advertiser│                           │[ic] Headline      │
│[      CTA       ]│ ← CTA xuống dưới khi     │body / advertiser  │
└──────────────────┘   media để thừa height   │[      CTA       ] │← khối neo đáy
                                               └──────────────────┘
```

Video chỉ có `MEDIA_LEFT` (rail: icon / spacer / CTA, headline dưới) và
`MEDIA_TOP` (footer: headline + CTA); không có scrim, không có ảnh tĩnh.

Overlay (`OverlayAdContentView`), dải control 30dp trên cùng với badge Ad,
close, timer:

```
Xếp chồng (mặc định)      Side-media (creative dọc)   Ticker (panel < 120dp)
┌────────────────────┐    ┌────────────────────┐      ┌────────────────────┐
│Ad  [t]        [✕]  │    │Ad [t]         [✕]  │      │Ad[ic]Headline…[CTA]│
│ ┌────────────────┐ │    │┌──────┐  [ icon ]  │      └────────────────────┘
│ │  media (né     │ │    ││      │  Headline  │       icon-hero: icon thế
│ │  control)      │ │    ││media │  body      │       chỗ media khi không
│ └────────────────┘ │    ││      │  advertiser│       media / panel thấp
│ [ic] Headline      │    │└──────┘  [ CTA ]   │
│ body               │    │[  CTA tràn xuống  ]│  ← khi media thừa height
│ [      CTA       ] │    └────────────────────┘
└────────────────────┘
 stack dồn đáy, media mọc lên tới tường nhìn thấy
```

Scrim overlay = ảnh phủ panel + veil + chữ trên veil, chỉ khi xếp chồng đã
hết mọi nấc. Không media (video không vừa 120dp): icon, headline, body, CTA.

## A8. Thuật ngữ

| Từ | Nghĩa trong pack |
|---|---|
| **unit / slot** | in-feed: một ad unit id (cache, load) / một rect hiển thị của unit |
| **entry active / materializing** | ad đang hiện trong slot / ad đang được dựng ngầm để thay |
| **face, bản dựng sẵn** | presentation (half) hay content view (full) dựng lúc load cho ad đầu hàng |
| **head** | ad cũ nhất trong cache — cái `Show` kế tiếp lấy |
| **plan** | một ứng viên layout in-feed: template + tier + bộ asset + cỡ chữ + cờ marquee + điểm |
| **probe** | cây view thăm dò để đo plan, cache theo cấu trúc, không bind ad |
| **tier** | thang cỡ chữ/icon COMPACT/REGULAR/ROOMY — **không** phải cỡ ô |
| **nấc (rung)** | một bước của thang chữ (chế độ + scale) |
| **wrapped / marquee** | chữ nằm trọn trong số dòng cho phép / một dòng cuộn ngang |
| **nudge** | co riêng một dòng bị cắt trước khi đổi nấc |
| **band** | dải media ngang (MEDIA_TOP, overlay xếp chồng) |
| **rail** | cột chữ+nút bên cạnh media dọc (MEDIA_LEFT, side-media) |
| **scrim / veil** | template ảnh làm nền + tấm phủ đen 70% + khối chữ trên tấm phủ |
| **ambient** | bản copy ảnh crop-phủ, dim 55%, đứng sau ảnh FIT trong scrim |
| **ticker / icon-hero** | overlay panel thấp: một hàng cuộn / icon thế chỗ media |
| **control strip** | dải 30dp chứa badge Ad, close, timer của overlay |
| **badge** | "Ad" và AdChoices; được đè media/icon, không đè chữ |
| **reserve** | ô giữ chỗ trong suốt ở góc AdChoices — cho layout và validator, không phải dấu thật (dấu thật do SDK vẽ) |
| **né control (avoidance)** | pass chọn vị trí media để không nằm dưới close/timer |
| **tường nhìn thấy** | trần panel, vạch badge, cột close/timer, lề sync — thứ media được chạm |
| **lề sync** | lề 8dp hai bên mà media và mọi element dưới cùng tôn trọng |
| **sàn chính sách** | 120dp video / 48dp ảnh cho MediaView đã đăng ký |
| **session / restore** | một lượt show full-screen Activity và máy mở lại nó khi bị hệ thống vứt |
| **cover, màn che** | mảng màu trơn full/half, hạ bằng `Hide` từ thread SDK |
| **dwell** | thời gian ad đã đứng trên màn — điều kiện để được xoay |
| **generation** | bộ đếm show (C#) / load (native) để callback trễ không nhận nhầm |
| **gate** | script biên dịch kiểm tra trước khi nói "xong" (javac / csc / balance) |

## A9. Đánh đổi đã chọn (và phương án bị bác)

| Quyết định | Bị bác | Vì sao | Ở đâu |
|---|---|---|---|
| Full-screen là **Activity** | Dialog trên Unity Activity (31bcbd2a) | Activity cho pause miễn phí và đúng thang; delay thật là bind creative → dựng sẵn lúc load (4e4d73d4) | README Android §3 |
| Sự kiện Java→C# qua **HandlerThread riêng** | gọi thẳng trên thread SDK / main | main thread parked ở GC suspend của IL2CPP khi Unity pause = ANR (e4504f2c) | B1 |
| Pack **không marshal** về thread Unity | marshal mọi callback | caller trễ một frame dù không cần; cover phải hạ từ thread không qua player loop | B1, B4 |
| Readiness **chỉ native công bố**, count đi kèm completion | C# tự suy từ consumed | suy sai đè lên sự thật; thông báo riêng trễ 1.5 frame | B1 |
| Prefetch **khi displayed**, không huỷ ad ấm | load khi đóng; huỷ để nạp mới | miss collapsible lần 2; prefetch tự vứt việc | B2 |
| Retry ở **native**, provider không chồng | retry ở provider | nhân đôi request lúc no-fill | B2 |
| Hết hạn **1 giờ**, quét cả trước show | 4 giờ (AppOpenAd) | native ad hết hạn sau 1 giờ theo docs; timer uptime đứng khi máy ngủ | B2 |
| Chữ **trọn hoặc cuộn**, không cắt | ellipsize | chính sách + đọc được; cuộn là giá trả cho element | B6 |
| **Nguyên vẹn > cỡ chữ > cuộn**; cuộn phụ trước headline | cuộn ngay khi hụt | chữ tĩnh dễ đọc hơn; headline là thứ cuối cùng được động | B6 |
| Điểm: element −10 000, cuộn +3 000, tier ±1 000, trống +100/px | tối ưu trống trước | tháp ưu tiên của chủ dự án: nhiều element nhất trước | B6 |
| Spacing **chặn theo cạnh ngắn ô** | spacing theo tier | ô 96dp chạy ROOMY được padding 5% mỗi bên | B6 |
| Asset rỗng **GONE**, thiếu asset **ẩn** không loại ad | chừa chỗ / xin ad khác | chính sách chỉ đòi hiện thứ SDK có gửi; Google template cũng ẩn | B6 |
| MediaView **luôn đăng ký** asset chính; ảnh fallback là **con** của nó | `setImageView` | mã 3 "Asset uses ImageView" — unit ngừng fill | B7 |
| Sàn **120dp chỉ video**, ảnh 48dp | 120dp mọi media | warning SDK có điều kiện "shows video"; ô 96dp mất fill | B7 |
| Video không vừa → **không media** | video nhỏ / ảnh tĩnh / scrim | không render / đăng ký dưới sàn là vi phạm / veil trên video = viewability | B7 |
| Nền MediaView **đen** | màu panel | tell cho creative render không kín | B7 |
| **Scrim là phương án cuối** (penalty 6 000), ảnh crop-phủ + ambient | scrim ưu tiên vì diện tích media | không được thắng band cùng element; đen mép xấu | B7 |
| Ambient **chỉ scrim** | ambient mọi band | user thu hẹp: band giữ đúng cỡ creative, letterbox màu panel | B7 |
| Media **trái**, phải chỉ dự phòng | phải | thuận tay phải: chữ+nút dưới ngón cái | B7 |
| Side-media **chỉ aspect < 0.85** | nới 1.05 | ad vuông Meta lọt rail, xấu | B7 |
| **Không hardcode ratio fallback** | 1.0 / 16:9 | không báo → nguyên dải; có báo → đúng cỡ (cả hai đều từng bị làm sai) | B7 |
| Media **không bleed**, lề 8dp sync | bleed hết bề ngang | user đòi lại lề sau khi bị đục | B10 |
| Chỉ **tường nhìn thấy** mới tính | mọi vạch | media dừng ở vạch vô hình = "chưa chạm gì mà không to" | B10 |
| **Cả hai vị trí control** là vật cản | chỉ nút đang hiện | đổi neo khi timer→close bị cấm tuyệt đối | B10 |
| Không đệm buffer | 2dp quanh control | "sát sàn sạt miễn là không overlap" | B10 |
| Bộ sàn gọn là **mặc định** của stacked-avoid | phương án cuối | height dôi dồn hết cho media | B10 |
| Kế hoạch né lập **trong `onMeasure`**, idempotent | `post()` từ `onSizeChanged` (c335fdc4) | bản post là cú nhảy sau frame đầu trên máy có cutout khác | A6, B6 |
| Panel thấp: **tôn trọng ratio**, thang media→icon-hero→ticker→nới tối thiểu | nới panel cho vừa media | panel là chiều cao game xin | B10 |
| Full-screen **không** trạm unrenderable | như half | chrome vượt cả màn hình không có thật; chỉ cần đẹp | A3 |
| Icon pipeline "tiêu" thì settle **không nở lại** | icon theo chữ | chứng chỉ fit bị vô hiệu sau lưng pipeline | A6 |
| Bo góc: thụt = **sàn**, không cộng | cộng thêm | tiêu một lề hai lần | B11 |
| Bán kính đi theo **rect** (`ConfigureSlot`) | Settings lúc sinh | unit ấm từ khởi động, ô đo sau | B11 |
| AdChoices in-feed: **padding `NativeAdView`** để lớp của SDK co vào | đăng ký `AdChoicesView`; với vào view con của SDK | đăng ký không dời được dấu (đo trên máy); với vào con của SDK phụ thuộc thứ tự/thời điểm | B12 |
| Xoay theo **số lần xuất hiện** + dwell 4s | xoay theo đồng hồ / mỗi hide | liếc không phải lượt; foreground không phải rotation | B13 |
| Xoay **trong lúc ẩn**, sau một frame | lúc trở lại / cùng message hide | bản thay thế có cả quãng ẩn; hide phải được vẽ trước | B13 |
| Android: half ad và half cover cùng type 1002, **hợp đồng cover-trước** | type 1003 cho ad | 1003 vẽ **dưới** 1002 (dumpsys) | B3, README Android |
| iOS: cover là **subview** | UIWindow / present | window đè cả ad SDK; present chiếm slot của SDK | README iOS |
| iOS: `ROPauseGuard` display link | tin `UnityPause` | SDK mediation xoá cờ khi ad họ đóng | README iOS |
| Cover half **đo bằng resolver của ad** | `decorView.getHeight()` | lệch trên máy cutout → dải đen | B10 |
| Cover theme **đục** | thừa hưởng translucent | game lộ qua khe chưa vẽ | README Android |
| Editor là **ảnh chụp**, không mô phỏng | lifecycle giả (33f45878) | user chốt: để nhìn, không để tái tạo độ trễ | README Editor |
| Editor **không port engine** | port | "vài layout tiêu biểu" là đủ | README Editor |
| asm Editor: `defineConstraints UNITY_EDITOR` | `includePlatforms Editor` | Unity cấm AddComponent từ asm editor-only | README Editor |
| C#: **asm-per-platform**, không `#if` | `#if` + partial | user thích asm; một đường code | A2 |
| Java method **PascalCase**, phẩy đầu dòng | camelCase | đọc như C#, ba ngôn ngữ một nhịp | B14 |

## A10. Bản đồ đọc code (câu hỏi → hàm)

Tên là của Java; Obj-C++ cùng tên với prefix `ro_`/không prefix (README iOS).

| Câu hỏi | Đọc |
|---|---|
| Vì sao ô in-feed chọn template/tier này | `InFeedAdLayoutEngine.EvaluatePlan` (điểm), `ChoosePlans` (ứng viên), log `layout ready` |
| Vì sao plan bị loại | `RecordRejection`/`DescribeLastRejections`, `InFeedAdLayoutValidator.ValidateAssetGeometry`, `ValidatePolicyVisibleText` |
| Vì sao media in-feed to/nhỏ | `ResolveMediaDimensions`, `ExpandMediaWithinPlan`, `ShrinkImageTopToFitHeight`, `MediaSizeForTier`, `MediaPolicyFloorPx` |
| Vì sao chữ cuộn | `TEXT_LADDER_MODES`, `NudgeCutTextsWhole`, `ApplyTextMode`, `BodyMaxLines`/`HeadlineMaxLines`/`AdvertiserMaxLines` |
| Cây view in-feed của một template | `InFeedAdViewFactory.BuildContent` (nhánh template), `BuildIdentityAndText`, `BuildHeadlineAndActionStack`, `BuildTextStack`, `AddBodyAndOptional`, `CreateIconFiller` |
| Badge, dải badge, bo góc | `AddBadgeOverlays`, `ConfigureBadgeOverlays`, `BadgeEdgeInsetPx`, `ConfigureInsetContent`, `CornerInsetForRadius`, `PaddingForTier`, `InFeedAdPresentation.ConfigureBackground` |
| Dấu AdChoices của SDK nằm đâu / thụt thế nào | `InFeedAdViewFactory.InsetSdkOverlay` (Android); cây view thật bằng `uiautomator dump --windows` (README Android §9) |
| Khi nào ô hiện, hình học có ổn không | `InFeedAdPresentation.Show`, `ActivateNextLayoutPlan`, `ObserveFinalAssetGeometry`, `RejectActivePlanAndTryNext`, `GeometrySignature` |
| Slot xoay/không xoay | `InFeedAdSlot.HandleHiddenSwap`, `TrySwapActiveEntry`, `HandleRefresh`, `CurrentVisibleDurationMs`, `HandleForegroundRecheck` |
| Slot kẹt/trống | `HandleWatchdog`, `RemoveExpiredMaterializingEntry`, `RemoveExpiredActiveEntry`, `HandleEntryExpiry`, `ScheduleLayoutRetry` |
| Unit load/retry/hết hạn | `InFeedAd.StartLoad`, `RequestLoad`, `HandleLoadedAd`, `OfferCacheToSlots`, `BackoffDelayMs`, `HandleCacheExpiry` |
| Overlay load/cache/face | `OverlayAd.StartLoad`, `DoLoadAd`, `AddCachedAd`, `PrepareFace`, `PreparePresentation`, `PrepareFullScreenContent`, `MediaSignature` |
| Overlay show/complete | `OverlayAd.Show`, `CompletePresentation`, `NotifyCompleted`, `NotifyCurrentState`, `OnActivityPresentationCompleted` |
| Full-screen không mở / mở lại | `OverlayAdActivity.StartSession`, `VerifyInitialAttach`, `ScheduleSessionRestore`, `RestoreSessionIfPossible`, `RetryOrFailRestore`, `Session` |
| Overlay chọn side/ticker/hero/scrim | `OverlayAdContentView.Build` (đầu hàm), `ShouldUseSideMedia`, `ConfigureResponsiveCollapsibleContent`, `ApplyScrimLayout`, `AdoptMedialessVideoLayout` |
| Media overlay không to ra / đè control | `EnableControlAvoidance`, `RecomputeMediaControlAvoidance`, `TrimChromeForStarvedMedia`, `MeasureLowerContent`, log `avoidance` |
| Rail side-media | `ConfigureResponsiveSideRail`, `SideRailPadding`, `ApplyRailContentScale`, `GrowTextLines`, `GrowRailIcon`, `RailFits`, log `padding a/b/c of x` |
| Chữ overlay co/cuộn | `SettleContentBeforeLayout`, `SettleTextFitting`, `ApplyResponsiveContentScale`, `MarqueeWhenTooLong` |
| Close/timer/redirect | `ArmCloseFromPress`, `ForgetClosePress`, `RunCloseFromRedirect`, `CommitAdClick`, `StartCountdown`, `OnPresented`, `OnPaused` |
| Chiều cao panel half / cover | `ResolveHalfScreenPanelHeight`, `HalfScreenCover` |
| Window/thứ tự/system UI | `OverlayAdPresentation.ConfigureWindow*`, `InFeedAdPresentation.ConfigureHostWindow`, `OverlayAdActivity.ConfigureSystemBars` |
| Sự kiện về C# | `AdEventDispatcher.Post`, `Notify*` ở hai unit, `*Proxy.cs` (tên method phải khớp Java) |
| C# ready/show/complete | `OverlayAd.Show`, `TryTakeShow`, `OnStateChanged`, `InFeedAd.ShowSlot`, `HandleSlotDisplayed` |
| Editor vẽ gì | `EditorAd.Show`, `CreateBadges`, `PlaceAdChoices`, `ApplyRoundedCorners`, `EditorSortingOrder`, `EditorPause` |

## A11. Tích hợp với game (GetInTheBowl)

Hai file dùng pack: `_Game/.../Core/Ads/AdsManager.cs` (gate + luồng) và
`AdMobProvider.cs` (tạo instance, log, analytics). MAX (`AdMaxProvider`) đặt
`MaxSdk.InvokeEventsOnUnityMainThread = false` nên callback MAX cũng ở thread
SDK — đó là nguồn của `onCompletedAnyThread`.

| Placement | Type | Settings đáng nhớ | Format |
|---|---|---|---|
| App open native | `FullScreenAd` | | `NATIVE_APP_OPEN` |
| Interstitial native | `FullScreenAd` | Close cooldown 5, side Random, timer đối diện | `NATIVE_INTERSTITIAL` |
| End card | `FullScreenAd` | cooldown 0 | `NATIVE_END_CARD` |
| Collapsible | `HalfScreenAd` | `HeightRatio` từ remote config, `CacheSize 2` (plan chained), cooldown 3, Random/đối diện | `NATIVE_COLLAPSIBLE` |
| In-feed | `InFeedAd` | `SlotCount 1`, nền `Color.clear`; một instance **ấm sẵn** từ init (`nativeInFeedAd_Warmed`), `CreateNativeInFeed()` trả nó trước | `NATIVE_IN_FEED` |

- Gate game (remove-ads, cheat, remote flag, interval) nằm ở `AdsManager`
  (`IsNativeCollapsibleReadyAndCanBeShown`, `ShouldShowNativeEndCard`,
  `ShowNativeInFeed`), **không** trong pack.
- Màn che: collapsible → `HalfScreenCover.Show(black, ratio)` **trước**
  `ShowNativeCollapsible`, hạ bằng `onCompletedAnyThread = HalfScreenCover.Hide`.
  MAX interstitial/rewarded → `FullScreenCover.Show()` trước, tính
  `shouldShowNativeEndCard` **trên thread Unity** rồi
  `ShowNativeEndCardThreadSafe(bool, onCompletedAnyThread, onCompleted)` từ
  callback any-thread của MAX; end card hạ cover khi xong. Mọi nhánh kể cả
  thất bại đều gọi `onCompletedAnyThread`.
- `AdProvider.InvokeSafely` bọc mọi callback any-thread; `showingFSACount`
  atomic (`Interlocked.Increment` / `utils.AtomicDecrementIfAbove`).
- Ô in-feed: `CanvasChapterPopup` / `CanvasLevelPopup` đo ô bằng
  `ScreenRect(rectTransform, padding, roundCorner)` sau
  `Canvas.ForceUpdateCanvases()`; chỉ show khi `IsFullyInside(viewport)`;
  `AdsManager.CreateNativeInFeed(rect.SizeRounded, rect.RoundCornerRounded)`
  rồi `SetPosition(0, rect.PositionRounded)` + `Show(0, onDisplayed)`; slot id
  luôn 0. Gizmo `AdCellGizmos.DrawRoundedRect` vẽ đúng số gửi cho ad để căn
  padding/bán kính bằng mắt. Placeholder của ô (`ChapterItem.SetAdPlaceHolder`,
  LevelBox) giữ nền đen, chỉ tắt chữ "Ad" khi ad thật hiện.
- Editor: canvas game phải nằm dưới thang sorting của pack (README Editor).

---

# Phần B — Luật

## B1. Sự kiện và luồng

- **Pack không marshal callback về thread Unity.** `BaseAd.DispatchFromNative`
  chỉ gác release rồi gọi thẳng, đúng cách SDK AdMob làm: listener nào cần
  thread Unity thì tự nói. Hệ quả cho bên gọi: trong handler **không được**
  đụng API Unity đòi main thread — `PlayerPrefs`, `FindAnyObjectByType`, thao
  tác GameObject đều ném; `UnityEngine.Object == null` thì không. Game dùng
  `UniTask.Post` ở chỗ cần. Có một loại callback **bắt buộc** ở ngoài player
  loop — B4.
- Android: mọi `Notify*` qua `HandlerThread` `RiseOnNativeAdMobEvents`
  (`AdEventDispatcher`), **không bao giờ main thread** — lý do ANR ở README
  Android. Tham số chụp local **trước** khi post; một queue giữ thứ tự. iOS:
  thread SDK chọn; Editor: đồng bộ trên thread Unity (màn che có
  `SynchronizationContext`).
- **Readiness do native công bố, và chỉ native** (31c5562d). `OnStateChanged`
  áp đồng bộ dưới lock; `cachedCount` đi kèm `OnShowCompleted` (thông báo tách
  riêng đo được trễ 1.5 frame).
- `Show` bị từ chối → `OnAdDisplayFailed` mã −1 ngay. `showId` echo để
  completion chỉ giải quyết đúng show đã đăng ký.
- `OnDisplayed → Load()`: prefetch **khi ad lên màn hình** (8706d5cf).
- Handler người dùng gọi qua `InvokeSafely` (log, nuốt) — điều đó **không** hạ
  màn che (B4).

## B2. Cache: bao nhiêu, nạp khi nào, hết hạn khi nào

Overlay và in-feed dùng chung một mô hình: giữ sẵn `CacheSize` ad ấm.

- **Mặc định 1** (`CacheSize = 0` nghĩa là 1 với overlay; với in-feed là
  `SlotCount + 1`). Trần là 5 (`MAX_CACHE_SIZE` / `kROMaxCacheSize`).
- Collapsible để **2** vì plan chained bày ad thứ hai ngay khi ad đầu đóng.
- Chỉ **ad ở đầu hàng** được dựng sẵn giao diện (`MediaSignature`: asset về
  muộn làm chữ ký đổi thì dựng lại, c335fdc4).
- **Một ad ấm không bao giờ bị huỷ để nạp ad khác** (236384ce): ghế trống là
  lý do duy nhất để load, bất kể ai đang trên màn hình.
- **Paid event bám identity ad, không bám load generation** (87e2d1c9).

| | Overlay | In-feed |
|---|---|---|
| Ai gọi nạp | provider `Load()`; load thành công tự nối; `Show` rút ad; `OnDisplayed` gọi `Load()` | **không có `Load()` công khai**: constructor, slot hết hàng (`RequestLoad`), retry, quét hết hạn |
| Load hỏng | no-fill backoff từ lần hụt đầu; layout fail hai lượt đầu ngay; chưa từng layout được mà cứ hỏng → poll chậm | `ScheduleNoFillRetry` → `BackoffDelayMs`: 1s, mũ trần 5 → 32s; slot layout fail: 2 lượt ngay, sau 20 lần chưa từng render → 5 phút/lần |

**Đừng chồng thêm retry phía provider** — nhân đôi request lúc no-fill.

### Hết hạn: 1 giờ

Docs AdMob, *Request ads*
([Android](https://developers.google.com/admob/android/native/start),
[iOS](https://developers.google.com/admob/ios/native/start)): "Since ads
expire after an hour, you should clear this cache and reload with new ads
every hour." → `MAX_CACHED_AD_AGE_MS = 3_600_000` / `kROMaxCachedAdAge = 3600`.
Quét theo ad già nhất, **và quét trước mỗi `Show`** (overlay) / **khi trở lại
foreground** (in-feed): timer chạy uptime, đứng khi máy ngủ; tuổi ad đo bằng
elapsed real time. Ad hết hạn **đang trên màn hình** cũng bị thay, bản thay thế
cố ý bỏ dwell — đừng "khôi phục" check đó. Không có ad ấm → slot trống tới khi
load về (impression là của SDK). **4 giờ** là của `AppOpenAd` thật, không áp
cho pack.

## B3. Xếp lớp: ai đứng trên ai

    in-feed  →  half-screen  →  full-screen

Màn che nằm chung pack vì phải chen vào đúng thang này. **Hợp đồng của người
gọi, đúng ở cả hai nền tảng: bật màn che half *trước*, mở ad half *sau*.**
Android bắt buộc thế vì ad half và màn che half cùng window type và không có
API đổi z-order (chi tiết + vụ type 1003: README Android); iOS xếp bằng vị trí
`subviews` và vẫn đúng với thứ tự đó (README iOS). Ad full-screen luôn trên
cùng mà không ai phải sắp (Activity / present). Editor mô phỏng bằng sorting
order (README Editor).

## B4. Pause và màn che

| | Pause |
|---|---|
| Màn che full | **có** |
| Màn che half | không |
| Ad full-screen | có |
| Ad half | không |

Android được pause miễn phí (Activity); iOS tự gọi `UnityPause` và canh bên
thứ ba xoá cờ (README iOS); Editor đếm holder trên `Time.timeScale`.

### Luật phải trả cho cái pause đó

Unity dừng thì **C# không chạy**, mà `Hide()` là một lời gọi C#. Nên:

> Thứ gỡ màn che full **không được** xếp lịch qua player loop của Unity.

Lời gọi thì thread nào cũng được (JNI/DllImport post sang main native, còn
sống khi Unity dừng). Cái chết là **chỗ xếp lịch**: `Update`, coroutine,
`UniTask.Post`, callback SDK đã marshal về main thread. Trong game đường sống
là `onCompletedAnyThread` (MAX với `InvokeEventsOnUnityMainThread = false`;
trên iOS là NSOperationQueue nền — **iOS không được miễn**). **Mọi** đường
kết thúc một lượt show phải mang nó, kể cả `FailedToDisplay`. Mọi thứ đứng
trước `Hide` trong callback đó phải không ném và không đụng API Unity; điều
kiện (`PlayerPrefs`, remote config, cheat) **tính trước trên thread Unity**,
truyền bool (179d66e4).

Đã trả giá: `Hide` qua callback marshal về main → `mResumedActivity` là màn
che, `UnityPlayerActivity` STOPPED, log Unity im — màn đen vĩnh viễn, chỉ
force-stop thoát. Ad full-screen mở trên màn che cũng xin pause; hai yêu cầu
OR.

## B5. RedirectOnClose: đóng ad đúng lúc redirect chiếm màn hình

`CloseSettings.RedirectOnClose` biến nút close thành một cú click thật vào ad.
Mốc đóng **không** phải "SDK báo click" (ad biến mất trước khi trình duyệt tới
→ game trần) mà là **cửa sổ ad mất foreground**.

    IDLE
     │ chạm nút close
     ▼
    ARMED ──────────── hết CLOSE_REPORT_TIMEOUT (1s) ──► KHÔNG đóng
     │ SDK báo click                                     (chạm không trúng asset,
     ▼                                                    coi như chưa bấm)
    REDIRECT_PENDING
     │ mất foreground ──► đóng   ← redirect đã chiếm màn
     └ hết REDIRECT_TIMEOUT (3s) ──► đóng

Nút không được thành bẫy: **lần hụt thứ 3 liên tiếp** vẫn chờ hết 1s rồi
đóng trần. Bấm lại lúc chờ → claim mới, tính vào lưới; bấm lúc
`REDIRECT_PENDING` → bỏ qua. Deadline lưới là lời hứa: click sau có thể huỷ
nó, không đẩy lùi được. Cú chạm **thả xuyên** xuống `NativeAdView` (SDK đếm
touch, `performClick()` vô nghĩa). Chỉ chạm **bắt đầu trên nút close** mới vũ
trang; CTA thật thì ad còn nguyên khi quay lại. Countdown đóng băng khi window
mất visibility.

| | Android | iOS |
|---|---|---|
| lấy click | thả xuyên touch | thả xuyên từ `hitTest:` |
| mất foreground | `onWindowFocusChanged(false)` (Activity) **và** `onWindowVisibilityChanged` (window half không focusable) | `onPaused` từ resign-active |

## B6. Layout: tháp ưu tiên và luật chữ

Mục tiêu: **luôn ra layout hợp lệ với mọi rect/ratio được yêu cầu.** Tháp ưu
tiên (chủ dự án): (1) nhiều element nhất → (2) bố cục đẹp → (3) phóng to
element quan trọng.

- **Chữ: trọn hoặc một dòng cuộn, không bao giờ cắt.** Thứ tự nhượng bộ:
  **nguyên vẹn > cỡ chữ > cuộn.** In-feed: `TEXT_LADDER_MODES/SCALES` —
  wrapped 1.0/0.85/0.7 → cuộn dòng phụ 0.8/0.65 → wrapped 0.55 → cuộn phụ
  0.5 → cuộn cả headline 0.8/0.65/0.5; nấc cuộn luôn nhỏ hơn cỡ wrapped đã
  thất bại; `NudgeCutTextsWhole` trước mỗi nấc. Overlay rail: trần scale theo
  **bề rộng** rail (160dp→0, 400dp→1), thêm dòng (headline ≤4, body ≤6), hạ
  scale 3 nấc 0.34, rồi mới marquee (2ebe96c2); `MARQUEE_TEXT_SHRINK` 0.8.
- **Điểm in-feed** (`EvaluatePlan`): di chuyển 1 000 000/px · trống 100/px ·
  lệch tier 1 000 · mỗi chữ cuộn 3 000 · media phải 500 · scrim 6 000 · diện
  tích media −30/% · mỗi element −10 000. Cuộn là giá giữ một element, không
  bao giờ thắng element bị bỏ.
- **Tier là thang cỡ chữ, không phải cỡ ô** (ca935661). Scorer được leo ROOMY
  cho ô 96dp → **mọi spacing tier phát ra bị chặn theo cạnh ngắn ô**
  (`CellSpacingCapPx` = clamp(shortSide×0.02, 1dp, 5dp); CTA padding ≤
  shortSide×0.035; scrim pad = clamp(shortSide×0.015, 1dp, 4dp)).
- **Asset rỗng không chiếm chỗ** (996ae2bf); provider thiếu asset → **ẩn,
  nhường chỗ**, không loại ad. Validator chỉ chặn vì chính sách (chữ cắt, media
  dưới sàn, đè nhau).
- **Sàn 72dp** (`MIN_ICON_ROW_TEXT_WIDTH_DP`): headline cạnh icon phải còn
  ≥72dp chữ, không thì icon đứng riêng, chữ full width. **Một neo mỗi khối**
  (f9b3ffc4): icon đứng riêng thì chữ căn giữa dưới nó; icon cạnh thì lề trái.
- **Icon ngồi hàng nút lấy chiều cao nút** (`min(iconTier, ctaHeight)`).
- Box lấy cỡ từ ô/ad, chữ từ box: CTA = clamp(shortSide×0.18, 24dp, 36dp),
  badge = clamp(shortSide×0.10, 15px, 18dp); tỉ lệ chữ 0.45 / 0.55. Ngưỡng
  viết dp; vài sàn px có chủ ý (15, 19, 256).
- Cả rect click được; latch chống double-click nhả theo visibility (in-feed,
  half) / focus (full).
- **Layout pass**: không bao giờ measure-thăm-dò rồi `requestLayout` từ giữa
  một layout pass mà không có khoá idempotent — Android vứt `requestLayout`
  giữa pass và GMA lay cột ở cỡ thăm dò (c335fdc4, tìm bằng `dumpsys activity
  top`). Bản sửa đầu (`post()` từ `onSizeChanged`) sau đó bị thay (loạt 21/8):
  nó tự là cú nhảy sau frame đầu trên máy báo `heightPixels` có/không cutout
  khác nhau. Hiện tại kế hoạch né lập **trong `onMeasure`** theo kích thước
  thật, chỉ khi `avoidancePlanDirty` hoặc panel đổi cỡ (`plannedPanelWidth/
  Height`), `planningInMeasure` chặn `requestLayout` thừa. Sửa vùng này thì
  giữ tính idempotent, nếu không vòng measure→mutate→layout chạy mãi.

## B7. Media: sàn chính sách và template

- **Asset chính luôn qua `MediaView`/`GADMediaView`.** `setImageView()` là
  AdMob từ chối request với mã 3. Ảnh fallback là `ImageView` **con** của
  MediaView.
- **120dp chỉ cho creative có video**; ảnh: thang 56/88/120dp, sàn **48dp**
  (`MediaPolicyFloorPx`). Video không vừa → layout **không media** (không video
  nhỏ, không ảnh tĩnh trong MediaView, không scrim). Ô 96dp trên máy thật
  **không bao giờ hiện video**.
- **Không cắm view vào TRONG MediaView** (e7f74bc0): con của nó thuộc SDK,
  không sống qua bind. Ambient là anh em đứng sau.
- **Nền đen MediaView là chủ đích** (17281dba); band letterbox bằng màu panel,
  đen chỉ cho video.
- **Media trái**; `MEDIA_RIGHT` phạt 500, không thắng hoà.
- **Scrim là phương án cuối** (penalty 6 000): ảnh FIT + ambient crop-phủ dim
  55% sau, **một** veil `#B3000000` phủ toàn ô, khối chữ neo đáy. Ambient chỉ
  cho scrim (8a91da54). `IsAllowedScrimOverlay` cho chữ trong khối scrim đè
  media.
- **Video không bao giờ làm nền** (644724e3).
- **Không hardcode ratio fallback**: không báo → nguyên dải trống lớn nhất đã
  qua sàn; có báo → box đúng cỡ, nở tới giới hạn thật, slack chia đôi
  (8f3a4db1 → d95fc911). `DEFAULT_MEDIA_ASPECT_RATIO` chỉ để thăm dò.
- Band MEDIA_TOP in-feed nuốt height thừa (min = plan size để validator đo
  không đổi).
- Overlay side-media: aspect < 0.85 **nghiêm ngặt** (e8c7ff98), panel ≤
  1.3×W, share ≤ 0.56, cột media cao đúng aspect, rail ≥ 120dp.

## B8. Layout side-media: biến thể đẹp không bao giờ được chật hơn bản gốc

Creative dọc + panel đủ thấp → media cột trái, mọi thứ vào rail phải —
**MEDIA_LEFT thuần**. Media để thừa height → CTA (và có thể body) rơi xuống
dưới — **MEDIA_LEFT có element ở dưới**. Biến thể thứ hai sinh ra để làm đẹp
biến thể thứ nhất, nên:

> Ad nào bày vừa ở bản thuần thì phải bày vừa ở bản có element ở dưới.

Có element ở dưới thì media bỏ tràn mép trái, bắt đầu cùng mép với mọi element
— mép đó **cắt từ ngân sách cũ, không cộng thêm**:

```
x = (khe media ↔ rail) + (padding phải panel) = 2 × SideRailPadding(railOuterPlain)
share = x / 3   (px nguyên, Android)
dư 1 → khe media↔rail;  dư 2 → mép trái + mép phải (cặp cân)
lòng rail = W − media − x   (cả hai bản, đúng từng pixel)
```

Nên `ConfigureResponsiveSideRail` nhận cùng đầu vào → cùng kết quả;
`railScaleCap` đọc bề rộng cột **bản thuần**. Cạm bẫy đã dính: trong
`sideRow` media width cố định, rail mang weight — padding-left cho `sideRow`
mà không cắt từ ngân sách là trừ thẳng vào rail. Android
`OverlayAdContentView` nhánh `if (sideMediaLayout)` (`sidePaddingBudget`,
`sideRowLeftInsetPx`, `railGap`, `sideRowRightPadding`); iOS
`ROOverlayAdContentView.mm` nhánh `if (_sideMediaLayout)`. Mọi thứ đo từ mép
media cộng mép trái đó; badge Ad trong side-media **né media**, bám mép phải
media, control trái đứng cạnh (e7f74bc0). Log `padding <trái>/<khe>/<phải>
of <x>`.

## B9. Padding ngang: một hằng số, ba nền tảng

`5dp`: hai mép panel, margin element dưới, **trần** padding rail (thật =
`max(2, min(5, railOuterWidth×3%))`). Ba mép của side-media có element dưới
**không** lấy hằng này (B8).

| Nơi | Hằng số |
|---|---|
| Android | `OverlayAdContentView.HORIZONTAL_PADDING_DP` |
| iOS | `kROHorizontalPadding` |
| Editor | `EditorAd.CONTENT_HORIZONTAL_PADDING_DP` |

Ô in-feed: **padding 0** — chỉ còn thụt góc bo (B11) và dải badge khi phần tử
trên cùng là chữ.

## B10. Overlay: sân media và né control

Một câu (df038876): **media mọc từ stack lên, theo aspect (có báo; không báo =
nguyên sân), trong sân cố định: hai bên không vượt lề 8dp sync (không bleed —
user đòi lại lề); trần = mép trên panel; vươn vào dải control thì hai cột
close/timer là tường. Nở tới tường đầu tiên; slack chỉ khi bị chặn ngang, chia
đôi căn giữa.**

- Bốn ứng viên (29571ce3): len khe hàng control trên / dưới hàng control trên /
  len khe giữa hai control bám mép ngay dưới badge / dưới control mép. Chọn
  media to nhất; control **di chuyển theo**; badge được đè, close/timer phải
  né; không ứng viên nào chứa sàn → đè như cũ. Half chạy cùng maximizer.
- **Chỉ tường nhìn thấy** (32ccf46a); ứng viên control-mép bắt đầu `top = 0`
  (d58aebbc).
- **Cả hai vị trí close/timer là vật cản** dù nút nào đang hiện (59f14ccb);
  **không buffer**. Khoảng trống là [trái..phải] thật, căn giữa trong khoảng
  (75f037ba).
- **Thang co chrome chạy cho cả hai format** (1c848203); bộ sàn gọn là mặc
  định của stacked-avoid (2234d84d) nhưng **không xoá padding ngăn hàng**
  (72e655d9); icon đã bị tiêu thì settle không nở lại.
- Margin media tính từ mép padding cột → trừ `paddingLeft` (72e655d9).
- Half: **một resolver chiều cao** (`ResolveHalfScreenPanelHeight`), cover
  dùng chung; panel đo chiều cao **nhận được** (5c638d92).
- Panel thấp **tôn trọng ratio**: media → icon-hero → ticker (< 120dp) → nới
  tới 48dp khi ticker cũng không vừa. Stacked half né dải control trước, co
  CTA 44→36, icon 36→28, padding; chỉ đè media khi thật sự không vừa.
- Full-screen không có trạm unrenderable. Control box 30dp, close vẽ hai nét.
- Ad **đang hiện** mà sai: đọc cây view thật trên máy (cách lấy: README
  Android §9) và log `avoidance` (panel/box/top/interval/slack) trước khi
  đoán (e4e48a18).

## B11. Bo góc ô in-feed

`InFeedAd.Settings.RoundCornerPx` là mặc định; **bán kính thật đi theo rect
qua `Initialize(sizePx, roundCornerPx)` → `ConfigureSlot`** (unit ấm từ khởi
động, ô đo sau). Kẹp ở nửa cạnh ngắn. Hình học: `d = ceil(r × (1 − 1/√2))`
(`CornerInsetForRadius` / `kROCornerInsetRatio` / `IN_FEED_CORNER_INSET_RATIO`).

- Layout thường: **nội dung thụt vào chạm khít góc bo** — cột ngoài tiêu khoản
  thụt làm padding; engine đo media theo `ContentWidth/Height`.
- Scrim: **ảnh nền phủ hết ô và bị cung cắt** (`clipToOutline` /
  `clipsToBounds`), cột ngoài không thụt, khối chữ tự lùi.
- **Thụt là SÀN, không cộng**: `scrimPad = max(padding ô, cornerInset)`; badge
  `edgeInset = max(CONTENT_EDGE_INSET_PX, cornerInset)`.
- Badge trên root, tự mang thụt, mọi template kể cả scrim.
- Màu trong suốt vẫn clip.
- Game: Screen Space Overlay → world unit = px; `scale = (topRight.x −
  topLeft.x)/paddedWidth` = `lossyScale.x`; `roundCornerPx = round(radius ×
  scale)` (`ScreenRect`).

## B12. Badge "Ad" và AdChoices

- `ATTRIBUTION_TEXT` **phải là `"Ad"`** ở cả ba bên. Rule rename từng cắn
  literal thành `"NativeAd"` (1c9f692b) → máy thật mất fill. **Rename không
  đụng string literal; audit chuỗi sau rename.**
- Badge/control đè media và icon, **không đè chữ** (`IsAllowedBadgeOverlay`);
  badge là góc, không phải dải.
- **Dấu AdChoices nhìn thấy là của SDK, không phải của ta.** SDK vẽ nó trong
  một lớp riêng gắn vào `NativeAdView`, ghim vào góc lớp đó theo
  `ADCHOICES_TOP_RIGHT`. `reserve` của ta chỉ là ô giữ chỗ trong suốt để
  layout và validator biết góc đó đã có chủ — **không điều khiển được vị trí
  dấu**.
- **Đăng ký `AdChoicesView` qua `setAdChoicesView` KHÔNG dời được dấu** trên
  Android — đã thử và đã sai: cây view trên máy (2026-09-21) cho thấy SDK
  nhận view đó (gắn listener, view thành bấm được) nhưng vẫn vẽ dấu ở lớp
  của nó, sát góc thô; ô của ta trống. Đừng thử lại cách này.
- Cách đang dùng (Android, in-feed): **padding cho `NativeAdView`** bằng đúng
  khoảng thụt của badge (`BadgeEdgeInsetPx`), để lớp của SDK co vào và dấu
  theo vào; nội dung của ta bù lại bằng margin âm và `setClipToPadding(false)`
  nên phủ kín ô y như cũ (`InsetSdkOverlay`). Chi tiết và bằng chứng
  bytecode: README Android §7.
- iOS và overlay chưa sửa (C3). Editor giả định glyph AdChoices 15dp.

## B13. Slot in-feed: xoay vòng, dwell, watchdog

Xoay theo **số lần xuất hiện**; `DWELL_REFRESH_INTERVAL_MS` 30s chỉ cho slot
không bao giờ ẩn.

- **Liếc không phải lượt** (880f7d2d): `MIN_DWELL_MS` 4s trước khi hide được
  xoay; `MIN_SWAP_INTERVAL_MS` 3s giãn đổi.
- **Xoay trong lúc ẩn**, sau khi frame commit hide (post từ frame callback).
- **Foreground là resume, không phải rotation** (c6234465): chỉ present slot
  trống; đọc tuổi ad bằng elapsed real time ở đây.
- Activity nền: window không vẽ → chờ foreground, không coi là lỗi.
- Watchdog 1s: materializing kẹt (`MATERIALIZING_ENTRY_TIMEOUT_MS` 20s, lưới
  cuối); active không bao giờ visible (`ACTIVE_ENTRY_VISIBILITY_TIMEOUT_MS` 3s,
  chỉ khi có Show đang chờ — prefetch lúc feed ẩn không được vứt).
- Hẹn giờ hết hạn arm **tại nơi entry ra đời**.
- Show khi chưa sẵn sàng → re-arm delay hiện tại, không tính hỏng; slot hỏng
  display liên tục đi qua backoff.

## B14. Đặt tên và phong cách code

- **Container mang họ, type mang vai trò** (bdb69553): `RiseOn.NativeAdMob` /
  `com.riseon.nativeadmob` / `RO` / `NativeAdMob.androidlib`; type `BaseAd`,
  `InFeedAd(+Slot/Listener/Presentation/ViewFactory/LayoutEngine/LayoutValidator)`,
  `OverlayAd(+Activity/ContentView/Presentation)`, `FullScreenAd`,
  `HalfScreenAd`, `*Cover`. Tag logcat `NativeAd`/`InFeedAd`/`OverlayAd`/
  `Cover`. Đổi tên một nơi = đổi cả ba, kể cả JNI string, manifest, tên class
  trong proxy.
- **`AndroidJavaProxy` dispatch theo tên**: tên method C# = tên method
  interface Java (`OnSlotPresentationFailed`; tên cũ nuốt lặng mọi lỗi slot).
- Java: method **PascalCase**, hằng UPPER_SNAKE, phẩy đầu dòng, comment nói
  **vì sao**. Obj-C++: `ro_`, `kRO*`. C#: K&R; so sánh hằng bằng `is`/`is not`;
  `null` giữ `==`; một type một file; namespace = tên assembly.
- Comment tiếng Anh; README tiếng Việt.
- Mọi quyết định layout **log số**; mắt và code mâu thuẫn nhiều vòng → gắn log.

---

# Phần C — Vận hành

## C1. Quy trình làm việc và cổng kiểm tra

- **Chủ dự án tự build và chạy.** AI không build, không inject input
  (`adb shell input`), **không click vào nội dung quảng cáo**.
- **Mỗi thay đổi quan sát được → build → nhìn → mới đi tiếp.**
- Lỗi hiển nhiên → sửa ngay và báo. Quyết định thiết kế → trình bày, chờ chốt.
  "Sao không chạy?" → chỉ chẩn đoán. Chỉ đụng file/hàm được giao. Đọc lại file
  ngay trước khi ghi (chủ dự án sửa và commit song song).
- **Cổng bắt buộc trước khi nói "xong"** (hai build Android đã vỡ vì javac).
  Chúng chỉ kiểm **biên dịch**, không kiểm hành vi — không có test nào ở đây.
  - **Java**: javac của OpenJDK Unity + `android.jar` (android-36) +
    `classes.jar` của `play-services-ads-api` và `play-services-ads` trong
    `~/.gradle/caches`. Công thức đầy đủ: README Android §8.
  - **C#**: dùng compiler của chính Unity, đọc tham số từ file rsp Unity để
    lại — nên reference và define giống hệt lúc Unity biên dịch.
    Đường dẫn, dưới `<Unity>/Editor/Data` của bản Unity project đang mở:
    `NetCoreRuntime/dotnet.exe` chạy `DotNetSdkRoslyn/csc.dll`.
    Rsp nằm ở `Library/Bee/artifacts/*.dag/<Tên assembly>.rsp` — lấy bản mới
    nhất theo thời gian sửa; **project phải được mở trong Unity ít nhất một
    lần** thì mới có. Chạy: `dotnet.exe csc.dll -nologo @<rsp đã sửa>`, rồi
    lọc dòng chứa `: error `.
    Bốn chỗ phải sửa trong rsp trước khi chạy:
    1. `-out:` trỏ sang thư mục tạm của mình; bỏ hẳn dòng `-refout:`.
    2. Với assembly của game: mọi `-r:` trỏ tới dll RiseOn phải **trỏ sang bản
       pack vừa dựng**, không thì đổi chữ ký hàm trong pack mà game vẫn "PASS"
       vì đang tham chiếu dll cũ Unity để lại.
    3. Bỏ dòng file nguồn đã bị xoá (rsp chỉ được ghi lại khi Unity biên dịch,
       nên một commit vừa pull về xoá file là rsp thành cũ).
    4. Thêm file `.cs` mới trong `Assets/_Game` mà rsp chưa liệt kê — trừ file
       trong thư mục `Editor` và trong cây của một asmdef nào đó.
    Thứ tự: dựng 4 assembly của pack trước (core rồi tới ba assembly nền tảng,
    vì chúng tham chiếu core), rồi mới tới `Assembly-CSharp` của game.
    Mẹo khi chủ dự án đang sửa dở một file: cho phép thay tạm đường dẫn file đó
    bằng bản đã commit (`git show HEAD:<path>`) để phần còn lại vẫn kiểm được.
  - **iOS**: **không có compiler trên Windows** — chỉ kiểm được hai thứ: ngoặc
    `{}()[]` cân sau khi bỏ comment, chuỗi và ký tự; và chữ ký ABI
    (`ROInFeedAd_Create`, `ROInFeedAd_Configure`) khớp giữa
    `RONativeAdBridge.h`, `.mm` và `NativeAdBridge.cs`. Mọi hành vi iOS là
    "chưa kiểm chứng" cho tới khi build trên máy Mac.
  - **Script gate không nằm trong repo** (chủ dự án chốt 2026-09-21): phiên
    sau đọc công thức trên rồi dựng lại. Chúng hardcode đường dẫn máy và chỉ
    phục vụ việc phát triển, không đáng nằm trong sản phẩm.
- Sau sửa Java: kiểm build đã chứa code mới (grep symbol trong `classes/` của
  `unityLibrary/NativeAdMob.androidlib`).
- Ghi chú công cụ cho AI trong harness này: heredoc của Bash tool bị bóc nháy
  và nuốt backslash — script ghi bằng Write tool rồi chạy file.

## C2. Sổ sự cố

| Khi | Triệu chứng | Nguyên nhân gốc | Luật rút ra |
|---|---|---|---|
| 2026-08-15 | Ô 96×120dp máy thật mất fill | Rule rename cắn literal, badge "NativeAd" (1c9f692b) | Rename không đụng literal (B12) |
| 2026-08-15 | EditorAd không gắn, NRE | asm editor-only không AddComponent được (c2d5c777) | `defineConstraints UNITY_EDITOR` |
| 2026-08-15 | Hai build Android vỡ javac | forward reference / definite assignment; backslash bị nuốt (bf207c86, 6786ef5e) | Cổng javac; script qua Write |
| 2026-08-15 | Full-screen "delay" | bind creative trong `onCreate`, không phải Activity; Dialog 31bcbd2a revert | Activity + content dựng sẵn (4e4d73d4) |
| 2026-08-16 | Collapsible miss mỗi lần thứ hai | load chỉ khi đóng; native chặn load khi show (8706d5cf) | Prefetch khi displayed |
| 2026-08-16 | Prefetch vô ích | load lúc dismiss huỷ ad vừa nạp (236384ce) | Ad ấm không bị huỷ |
| 2026-08-16 | Cache có ad mà "chưa có" | C# xoá cờ ready đè native (31c5562d) | Readiness chỉ native |
| 2026-08-16 | Doanh thu ad đang xem mất | paid event gác generation (87e2d1c9) | Bám identity |
| 2026-08-16 | Layout thỉnh thoảng vỡ (cột 421px, media 0) | recompute giữa layout pass (c335fdc4) | Idempotent trong `onMeasure` (B6) |
| 2026-08-16 | Ambient không hiện | view cắm trong MediaView bị nuốt (e7f74bc0) | Không cắm con vào MediaView |
| 2026-08-16 | Padding scrim to ở ô 96dp | ô chạy ROOMY, spacing theo tier (ca935661) | Spacing chặn theo ô |
| 2026-08-16 | Media full-screen không to | pipeline co chrome sau `if (!fullscreen)` (1c848203) | Chạy cho cả hai format |
| 2026-08-16 | Media "chưa chạm gì" mà không scale | tường vô hình; `top = badgeHeight` (32ccf46a, d58aebbc) | Chỉ tường nhìn thấy |
| 2026-08-16 | Ad vuông lọt rail | cổng 1.05 (e8c7ff98) | 0.85 nghiêm ngặt |
| 2026-08-16 | Mọi ad thành khung đen | nguyên dải cho cả ad có ratio (8f3a4db1→d95fc911) | Nguyên dải chỉ ca không báo |
| 2026-08-16 | Headline trống chiếm chỗ | (996ae2bf) | Asset rỗng GONE |
| 2026-08-16 | Focus lại là xoay ad | foreground present slot đã có ad (c6234465) | Resume ≠ rotation |
| 2026-08-19 | Dải đen trên ad half máy cutout | cover đo `decorView`, ad đo `displayMetrics` | Một resolver |
| 2026-08-19 | Collapsible mảng đen trống | type 1003 (số lớn không vẽ trên) | 1002 + hợp đồng cover-trước |
| 2026-08-21 | Panel half bị đẩy lên nav bar | window tôn trọng bar (Samsung 3 nút) | Lay out dưới bar, sticky |
| 2026-08-21 | Full-screen nhảy một bước sau frame đầu | `post()` từ `onSizeChanged` với `heightPixels` đoán | Lập kế hoạch trong `onMeasure` |
| 2026-08-30 | 5 cụm ANR "Input dispatching timed out" | main parked ở GC suspend IL2CPP khi Unity pause (e4504f2c) | `AdEventDispatcher` |
| 2026-08-30 | Game đen sau khi quay lại từ ad | BAL vứt `startActivity` end card (76fa0612) | `VerifyInitialAttach` → restore |
| 2026-08-30 | Crash Android 8.0 orientation (7 user) | translucent + `screenOrientation` manifest (9164a0b0) | Xin ở `onCreate`, bỏ API 26 |
| 2026-09 | Màn đen sau rewarded/interstitial, cả hai OS | `IsCheatAds` đọc PlayerPrefs trên thread SDK (179d66e4) | Tính bool trước trên thread Unity |
| 2026-09 | End card rewarded không hiện (Editor) | cờ reward set qua `UniTask.Post`, đọc trước khi post (a9ad7014) | Set ngay trong callback SDK |
| 2026-09 | Thụt góc bo tính hai lần | `scrimPad + cornerInset` | Sàn, không cộng |
| 2026-09-19 | AdChoices bị cung bo cắt | SDK ghim dấu vào góc thô của lớp riêng nó; reserve của ta không điều khiển được | (xem dòng dưới) |
| 2026-09-21 | Fix "đăng ký `AdChoicesView`" không có tác dụng | Giả định SDK vẽ vào view đăng ký, chưa kiểm trên máy; cây view cho thấy SDK vẫn vẽ ở lớp của nó | Kiểm bằng cây view thật trước khi báo xong; padding `NativeAdView` (B12) |

## C3. Việc còn mở

- Scrim in-feed: icon là hằng số tier, không nuốt phần dư như filler của
  `COMPACT_COLUMN`; dải ảnh ló dưới veil 70% đọc thành trống. Chưa chốt cho
  icon giãn và trần bao nhiêu.
- Body in-feed: `BodyMaxLines` = **1** ở COMPACT với `COMPACT_ROW` và scrim →
  body dài luôn cuộn dù ô còn chỗ. Chưa chốt: nới 2 dòng / nấc co CTA / đổi
  trọng số body-trọn vs dòng phụ.
- AdChoices iOS in-feed **chưa sửa**: vẫn như HEAD, dấu SDK có thể bị cung bo
  cắt y như Android trước đây. Chưa biết SDK iOS đặt dấu vào đâu — cần cây
  view thật trên iPhone trước khi đổi gì (bài học 2026-09-21).
- Overlay (full/half) vẫn dùng ô giữ chỗ AdChoices; không bo góc nên chưa lộ.
- Hộp dấu AdChoices của SDK cao 45px (cố định 15dp) trong khi dải trên cùng
  của ô in-feed chỉ chừa ~31px → hộp lấn vào dòng headline (có từ trước);
  validator không thấy vì nó đo reserve 23px chứ không đo dấu thật.
- Validator sàn ảnh 48dp chưa kiểm chứng trên máy với creative ảnh thật.
- Toàn bộ Obj-C++ chưa qua compiler (README iOS).
- Comment đầu `RONativeAdBridge.h` về `MobileAdsEventExecutor` đã cũ.

## C4. Trạng thái bàn giao

Cập nhật mục này ở cuối mỗi phiên làm việc; ngày là của lần cập nhật.

**2026-09-21**

- Chưa commit trong package: fix AdChoices in-feed Android — **chỉ**
  `InFeedAdViewFactory.java` khác HEAD (`InsetSdkOverlay`, `BadgeEdgeInsetPx`),
  đã stage; javac PASS. Fix sai trước đó (đăng ký `AdChoicesView`) đã gỡ, các
  file validator Android và 3 file iOS trả về đúng HEAD. **Chưa thử trên
  máy** — cần build rồi đọc cây view: dấu "Ad Choices Icon" phải thụt đúng
  bằng badge "Ad" (lần đo trước: 7px). Bốn README này chưa stage; README iOS
  đã `git mv` từ `Bridge/Native/`. Ngoài package: `mainTemplate.gradle`,
  `UnityConnectSettings.asset` là của chủ dự án.
- Phía game đã commit (2ff3a7e5 và trước): `InvokeSafely` trong `AdProvider`,
  FSA counter atomic, `ShouldShowNativeEndCard` tính trước, reward flag set
  trực tiếp. `chapterAdRoundCorner` 22.5 / `levelAdRoundCorner` 15 đã đặt
  trong `Canvas-ChapterPopup.prefab`.
- Việc kế tiếp chờ chủ dự án chốt: icon scrim và body 2 dòng (C3); AdChoices
  iOS (cần cây view trên iPhone); hộp AdChoices 45px lấn headline (C3).
