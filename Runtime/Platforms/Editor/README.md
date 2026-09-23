# RiseOn.NativeAdMob — Editor

Assembly `RiseOn.NativeAdMob.Editor`: cùng bộ client như hai nền tảng thật,
nhưng vẽ bằng uGUI (`Preview/EditorAd`, `EditorCover`) để developer **nhìn
thấy** ad và màn che trong play mode. Luật chung ở
[tài liệu core](../../README.md); file này ghi những gì **riêng Editor**.

## 1. Preview là gương, không phải máy bay mô phỏng

- Client Editor là **tức thời**: `Show` dựng ad ngay, báo
  `OnLoadingCompleted(1, 1)` ("một ad ấm, chỗ cho một") rồi `OnSlotDisplayed`.
  Không có độ trễ load, không dwell, không xoay vòng, không scheduler. Bộ mô
  phỏng hành vi từng tồn tại (e81149c2) và đã bị gỡ theo yêu cầu chủ dự án
  (516759f6): preview để xem ad **trông ra sao**, không để tái tạo lifecycle.
  Đừng đem nó trở lại.
- **Không port layout engine sang Editor** (chủ dự án chốt): preview chỉ vẽ
  vài layout đại diện — overlay xếp chồng (media dưới dải control, stack dồn
  đáy) và ô in-feed (scrim khi ước lượng band `48 + headline + CTA + gaps`
  không vừa, ngược lại cột media-top nuốt height thừa). Nó **không có**
  MEDIA_LEFT, ticker, icon-hero, thang marquee. Creative đứng xem trong Editor
  ra thẻ xếp chồng, trên máy ra rail — kiểm side-media phải build lên máy.
- Vì preview là gương, mọi hằng số nó dùng phải **khớp máy thật**: 5dp
  padding ngang, 30dp control, 72dp sàn icon-row, 48/120dp sàn media, ngưỡng
  tier 150/280dp, icon 24/36/48dp, gap 1/3/5dp, CTA `0.18×shortSide` kẹp
  24–36dp, badge `0.10×shortSide` kẹp 15px–18dp, scrim veil 70%, tỉ lệ thụt
  góc `1 − 1/√2`. Sửa số bên Java/Obj-C++ thì sửa `EditorAd` cùng lượt.

## 2. asmdef: runtime trong Editor, vắng mặt trong build

`RiseOn.NativeAdMob.Editor.asmdef`: `includePlatforms: []` +
`defineConstraints: ["UNITY_EDITOR"]` — **không** phải `includePlatforms:
["Editor"]`. Unity **cấm** `AddComponent` một MonoBehaviour thuộc assembly
editor-only ("Can't add script behaviour ... because it is an editor script")
→ `EditorAd` không gắn được, `GetComponent` trả null, NRE (93bc80be). Với
define constraint, asm được phân loại runtime nên attach được trong play mode,
còn `UNITY_EDITOR` không tồn tại khi build player nên vẫn bị loại — đúng
pattern asm test của Unity Test Framework. Bootstrap gác `Application.isEditor`.

## 3. Sorting order: thang xếp lớp bằng canvas

Trên máy mỗi tầng là window/Activity riêng; trong Editor mọi thứ là Screen
Space Overlay và sorting order là thứ tự duy nhất. `EditorSortingOrder`:

| Tầng | Order | Vì sao |
|---|---|---|
| `FULL_SCREEN_AD` | `short.MaxValue` | chủ dự án chốt: ad full của ta ngang cấp ad full của SDK, không nhường |
| `FULL_SCREEN_COVER` | `-1` | = min(stub MAX 999, stub AdMob 0) − 1 để stub ad của SDK mở **trên** màn che như Activity của họ mở trên Activity màn che |
| `HALF_SCREEN_AD` | cover − 1 | |
| `HALF_SCREEN_COVER` | ad half − 1 | bật trước ad nên dưới ad |
| `IN_FEED_AD` | cover half − 1 | |

Hai số stub nằm trong prefab của package (`MaxSdk/Prefabs/Interstitial|Rewarded`
order 999; `GoogleMobileAds PlaceholderAds/*/768x1024` order 0, GUIManager
overlay của SDK 9999) — pack không đọc được, **kiểm lại mỗi khi nâng SDK**.
Canvas của game dùng pack phải nằm **dưới** cả thang — việc đó thuộc về
game, pack không đụng tới canvas của game. `-1` là số duy nhất đặt tay, phần
còn lại suy ra.

## 4. Pause

`EditorPause`: bộ đếm holder trên `Time.timeScale`, dùng chung cho ad
full-screen và màn che full (trên máy hai yêu cầu pause được OR). Đồng hồ chỉ
chạy lại khi holder cuối nhả, và chạy lại ở scale trước khi bị dừng. Preview
bị phá bởi bất cứ gì ngoài `Finish` (đổi scene, thoát play) vẫn phải nhả
trong `OnDestroy`. Reset ở `SubsystemRegistration` vì play mode có thể vào
không domain reload.

**Không phải bản tái tạo hiểm hoạ của máy**: Unity pause thật thì C# không
chạy; Editor ở timeScale 0 vẫn chạy mọi `Update` và UniTask continuation.
Một hide "không bao giờ tới" trên máy tới ngon lành ở đây — Editor không
kiểm được luật B4 của tài liệu core.

`CoverPlatform` bắt `SynchronizationContext` ở `BeforeSceneLoad` (không phải
`SubsystemRegistration` — lúc đó Unity chưa cài context) để `Show/Hide` gọi từ
thread SDK (`onCompletedAnyThread`) được đưa về thread Unity.

## 5. Bo góc và mask

- Panel mang cung bằng **sliced sprite** rasterize từ distance field
  (`RoundedRectSprite(radius)`, cache theo bán kính texel); `Mask` cắt con
  theo cung, `showMaskGraphic = false`; nền nhìn thấy chuyển sang con
  `Backdrop` vì Mask đọc alpha của chính graphic — màu trong suốt (câu trả lời
  thật trên máy) sẽ xoá luôn mask.
- **`pixelsPerUnitMultiplier = canvas.referencePixelsPerUnit`**: uGUI chia
  border của sliced sprite cho `sprite.pixelsPerUnit / canvas.referencePixelsPerUnit`
  trước khi vẽ; sprite rasterize vài texel/unit nên tỉ lệ đó thổi border lên
  ~25–100× và mask thành cục tròn không cạnh thẳng. Multiplier triệt đúng nửa
  canvas của tỉ lệ.
- Khoản thụt góc tính **theo cách máy tính** — `ceil` bằng px — rồi chia
  `uiScale` sang canvas; sàn (không cộng) cho scrim pad và badge như máy.

## 6. Quy ước vẽ

- Viền element là **inner stroke** (bốn thanh vẽ trong mép, thanh dọc rút
  2×độ dày để không đè góc) như `GradientDrawable` stroke của Android; Unity
  `Outline` là outer, chủ dự án bác (87652fd1). CTA `#2196F3` viền `#1565C0`
  như máy, phần còn lại đen 35%.
- Dấu close **vẽ bằng hai nét** (capsule distance field) đúng hình học máy
  (`0.44` cạnh ngắn, nét 2.5dp), không gõ U+2715 (mỗi OEM một font). Close
  20sp, countdown 18sp trong box 30dp — close cố ý to hơn.
- Chữ trong box lấy cỡ **từ box** (tỉ lệ cap-height, có sàn/trần) vì cùng một
  preview vẽ ô 96dp lẫn full màn.
- AdChoices: giả định glyph 15dp; badge Ad 24×18dp; cả hai thụt theo khoản
  thụt góc (in-feed) hoặc safe-inset (overlay). Trên máy dấu thật do SDK vẽ,
  hộp 15dp; Android dời nó vào bằng padding `NativeAdView` (README Android
  §7), iOS chưa.
- Màu nền in-feed tôn trọng **alpha 0** như máy; không thay bằng màu nhà.
- Ô in-feed trong Editor vẽ body bị cắt vẫn hiện (ưu tiên số lượng element),
  khác máy (máy không bao giờ cắt, sẽ marquee).
