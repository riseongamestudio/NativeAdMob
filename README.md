# RiseOn.NativeAdMob

Native ad của AdMob mặc áo full-screen / collapsible / in-feed. `Core/` là C#
dùng chung, `Platform/Android|iOS` là bridge sang Java và Obj-C++,
`Platform/Editor/Preview` là bản mô phỏng trong Editor.

Ghi ở đây những luật **không suy ra được từ việc đọc code từng chỗ một** — thứ
sẽ bị phá vỡ một cách vô tình nếu quên.

## 1. Layout side-media: biến thể đẹp không bao giờ được chật hơn bản gốc

Khi creative đứng (aspect < `SIDE_MEDIA_MAX_ASPECT`) và panel đủ thấp, media
chiếm cột trái còn mọi thứ khác dồn vào rail bên phải — gọi là **MEDIA_LEFT
thuần**. Nếu media để thừa nhiều chiều cao, nút CTA (và có thể cả body) rơi
xuống dưới hàng đó cho đỡ phí — gọi là **MEDIA_LEFT có element ở dưới**.

Biến thể thứ hai **sinh ra để làm đẹp biến thể thứ nhất**. Từ đó ra một luật
bắt buộc:

> Ad nào bày vừa ở MEDIA_LEFT thuần thì phải bày vừa ở MEDIA_LEFT có element
> ở dưới. Biến thể đẹp hơn không bao giờ được là biến thể hết chỗ trước.

Khi có element ở dưới, media phải bỏ tràn mép trái và bắt đầu từ cùng một
mép với mọi element khác — nếu không cả thẻ có hai mép trái, nhìn như lỗi.
Mép trái đó tốn chỗ, và **chỗ đó phải cắt ra từ ngân sách cũ, không được cộng
thêm vào**.

Ngân sách là thứ **bản thuần** tiêu theo chiều ngang, đúng hai chỗ:

```
x = (khoảng cách media ↔ rail) + (padding phải của panel)
  = 2 × SideRailPadding(railOuterPlain)      // ROSideRailPadding bên iOS
```

Bản có element ở dưới có ba chỗ — thêm mép trái của media — và **ba chỗ đó
chia nhau đúng x**:

```
share = x / 3        (số nguyên, px, trên Android)
dư 1  →  pixel thừa cho khoảng cách media ↔ rail (khe khép lại là xấu nhất)
dư 2  →  hai pixel thừa cho mép trái và mép phải (phải là một cặp cân)
```

Vì `trái + khe + phải == x` ở mọi nhánh, bề rộng lòng rail bằng nhau **đúng
đến từng pixel** ở hai biến thể:

```
lòng rail = W − media − x     (cả hai bản, không sai số)
```

Nên `ConfigureResponsiveSideRail` nhận cùng một đầu vào → cho cùng một kết
quả. Ad nào bày vừa ở bản thuần thì bày vừa ở bản này, không cần chứng minh
bất đẳng thức nào, không phụ thuộc máy hay creative. Trần cỡ chữ
(`railScaleCap`) cũng đọc bề rộng cột của **bản thuần**, để mép trái của ảnh
không ăn mất một nấc cỡ chữ.

Media giữ nguyên bề rộng. Element bị đẩy xuống dưới dùng đúng cặp mép
trái/phải mới, nên cả thẻ chỉ có một mép trái và một mép phải.

Cạm bẫy đã dính một lần: trong `sideRow`, media có width **cố định** còn rail
mới là con mang weight. Cho `sideRow` một padding-left mà không cắt từ ngân
sách thì LinearLayout / HBLinearLayout sẽ **trừ thẳng vào rail**, tức làm đúng
cái điều luật trên cấm.

- Android: `OverlayAdContentView.java`, nhánh `if (sideMediaLayout)` —
  `sidePaddingBudget`, `sideRowLeftInsetPx`, `railGap`, `sideRowRightPadding`.
- iOS: `ROOverlayAdContentView.mm`, nhánh `if (_sideMediaLayout)` — cùng tên.
  Point chia hết nên bên này không có pixel thừa để phát.

Mọi thứ đo từ mép media phải cộng mép trái đó vào: bề rộng rail truyền cho
`ConfigureResponsiveSideRail`, `railOuterWidth`, và toạ độ x của badge.

Log một dòng mỗi lần dựng: `padding <trái>/<khe>/<phải> of <x>` — đọc log là
biết ngay ba số có cộng đúng bằng ngân sách không.

## 2. Padding ngang: một hằng số, ba nền tảng

`5dp` cho tất cả: hai mép panel, margin của element nằm dưới ở các layout
khác, và **trần** của padding rail (padding rail thật là
`max(2, min(HORIZONTAL_PADDING_DP, railOuterWidth × 3%))` — rail hẹp thì
không kham nổi padding cố định). Riêng ba mép trong layout side-media có
element ở dưới thì **không lấy hằng số này**, chúng chia ngân sách ở mục 1;
hằng số chỉ tham gia gián tiếp qua cái trần đó.

| Nơi | Hằng số |
|---|---|
| Android | `OverlayAdContentView.HORIZONTAL_PADDING_DP` |
| iOS | `kROHorizontalPadding` |
| Editor preview | `EditorAd.CONTENT_HORIZONTAL_PADDING_DP` |

Sửa một chỗ thì **sửa cả ba**, nếu không bản preview trong Editor sẽ nói dối
về thứ máy thật hiển thị.

Nhưng đồng bộ được con số đó **không có nghĩa là preview trung thực**. Preview
chỉ dựng hai bố cục đại diện: overlay xếp chồng (media trên, chữ dưới) và ô
in-feed. Nó **không có** MEDIA_LEFT, **không có** ticker, **không có**
icon-hero. Một creative đứng xem trong Editor sẽ ra thẻ xếp chồng, còn trên
máy ra rail bên phải — mục 1 nói về bố cục mà preview không vẽ được. Muốn
kiểm bố cục side-media thì phải build lên máy và đọc dòng log
`Side-media layout:`.

## 3. Cache: bao nhiêu, nạp khi nào, hết hạn khi nào

Overlay và in-feed dùng chung một mô hình: giữ sẵn `CacheSize` ad ấm.

- **Mặc định 1** (`CacheSize = 0` nghĩa là 1 với overlay; với in-feed là
  `SlotCount + 1`). Trần là 5 (`MAX_CACHE_SIZE` / `kROMaxCacheSize`).
- Chỉnh ở C#: `FullScreenAd.Settings.CacheSize`, `HalfScreenAd.Settings.CacheSize`,
  `InFeedAd.Settings.CacheSize`. Collapsible để **2** vì plan chained bày ad
  thứ hai ngay khi ad đầu đóng.
- Chỉ **ad ở đầu hàng** được dựng sẵn giao diện; ad phía sau dựng khi lên đầu.

Nhưng **cách nạp thì hai bên khác hẳn nhau**, đừng suy từ bên này sang bên kia:

| | Overlay | In-feed |
|---|---|---|
| Ai gọi nạp | provider gọi `Load()`; một lượt load **thành công** tự nối lượt sau tới khi đầy; và một `Show` vừa rút ad ra khỏi cache | **không có `Load()` công khai**. Tự nạp từ constructor, từ slot khi hết hàng (`RequestLoad`), từ lượt retry của chính nó, và từ lượt quét hết hạn |
| Load hỏng | **dừng hẳn** — pack không retry, không backoff. Quyền retry thuộc provider (`AdMobProvider` dùng backoff luỹ thừa 2, trần 32 giây) | **tự retry**: `ScheduleNoFillRetry` → `BackoffDelayMs`, gốc 1 giây, mũ trần 5 → tối đa 32 giây |

Hệ quả cần nhớ: **đừng chồng thêm retry phía provider cho in-feed** — nó đã có
sẵn backoff riêng, chồng thêm là nhân đôi số request lúc no-fill.

### Hết hạn: 1 giờ

Tài liệu chính thức của AdMob, mục *Request ads* của cả hai nền tảng
([Android](https://developers.google.com/admob/android/native/start),
[iOS](https://developers.google.com/admob/ios/native/start)):

> "Since ads expire after an hour, you should clear this cache and reload with
> new ads every hour."

Nên `MAX_CACHED_AD_AGE_MS = 3_600_000` (Android) / `kROMaxCachedAdAge = 3600`
(iOS), cho cả overlay lẫn in-feed. Ad quá hạn bị huỷ và nạp lại ngay — quét
bằng hẹn giờ theo ad già nhất, **và quét thêm một lần ngay trước mỗi `Show`**
vì đồng hồ hẹn giờ đứng yên trong lúc máy ngủ còn tuổi của ad thì không.

Con số **4 giờ** hay được nhắc là của format `AppOpenAd` thật, **không áp cho
pack này**: "native app open" ở đây là native ad mặc áo app-open, nên luật 1
giờ mới là luật của nó.


## 4. Xếp lớp: ai đứng trên ai

Pack có ba tầng vẽ đè lên game, và thứ tự giữa chúng là cố định:

    in-feed  →  half-screen  →  full-screen

Hai tầng sau mỗi tầng có hai cư dân: một ad thật và một màn che trơn cùng
kích thước (`HalfScreenOverlay`, `FullScreenOverlay`).

Màn che (`FullScreenOverlay`, `HalfScreenOverlay`) nằm chung pack chính vì
điều này: chúng phải chen vào đúng thang đó, không thể xếp lớp từ một pack
đứng ngoài.

**Android** có sẵn thang: in-feed là window `TYPE_APPLICATION_PANEL` (1000),
half-screen là `TYPE_APPLICATION_SUB_PANEL` (1002), full-screen là Activity —
mà Activity thì luôn lên trên cùng. Hệ điều hành lo, không phải mình lo.

Window không có index như subview — `WindowManager` không có API đổi z-order.
Chỉ hai đòn bẩy: **type**, và thứ tự `addView` trong cùng type. Đòn bẩy thứ hai
vô dụng ở đây vì muốn leo lên phải gỡ window ra thêm lại (flicker, mà cũng chỉ
tới được *đỉnh* của type chứ không chèn xuống dưới được). Nên thang được viết
bằng type:

| | | |
|---|---|---|
| 1000 | `TYPE_APPLICATION_PANEL` | in-feed |
| 1002 | `TYPE_APPLICATION_SUB_PANEL` | màn che half |
| 1003 | `TYPE_APPLICATION_ATTACHED_DIALOG` | ad half |
| — | Activity | full-screen (ad và màn che) |

Đúng bằng thứ tự subview bên iOS, và khác hợp đồng gọi hàm ở chỗ nó đúng bất kể
cái nào được bật trước.

**Chỗ này chưa được bảo đảm bởi tài liệu.** Bản thân hằng số 1003 là public API
bình thường, không deprecated, cùng dải sub-window (1000–1999) với 1002 nên cùng
cơ chế token — dùng nó không có rủi ro gì. Rủi ro nằm ở giả định "số lớn hơn thì
nằm trên": ánh xạ type → layer nằm trong `WindowManagerService`, không có trong
`android.jar`, và `android-stubs-src.jar` đã bị lột javadoc nên không file nào
trong SDK nói ra điều đó. Framework còn giữ riêng một type `@hide` ở **1005**
tên `APPLICATION_ABOVE_SUB_PANEL`, là một lý do để nghi ngờ quy tắc theo số.

Phải xác nhận trên máy thật, và phải kiểm **hai** quan hệ chứ không phải một:

1. ad half vẫn nằm **trên** in-feed (1003 so với 1000) — quan hệ này trước đây
   đúng ở 1002 và đã chạy thật, đổi type là đem nó ra đặt cược lại;
2. ad half nằm **trên** màn che half (1003 so với 1002) — thứ vừa mua được.

Sai ở (1) thì đổi ngược `NON_FULLSCREEN_WINDOW_TYPE` về
`TYPE_APPLICATION_SUB_PANEL` là xong, và quay lại hợp đồng "bật màn che trước,
mở ad sau".

**iOS** không có khái niệm đó. Không Activity, không window type; `UIWindow`
chỉ có `windowLevel`, và `zPosition` thì đổi thứ tự vẽ nhưng **không** đổi thứ
tự nhận chạm (`hitTest:` duyệt `subviews` từ cuối về đầu), nên hai thứ sẽ lệch
nhau. Thứ duy nhất vừa đúng khi vẽ vừa đúng khi chạm là **vị trí trong mảng
`subviews`** — cuối mảng là trên cùng.

Ba trong bốn bề mặt là **subview** của `rootVC.view` (chính là `UnityView`;
game nằm ở lớp *cha*, không phải anh em, nên index 0 vẫn nằm trên game). Luật,
từ dưới lên:

- **In-feed** luôn `insertSubview:atIndex:0` — đáy của phần pack thêm vào.
- **Half-screen** (cả ad lẫn màn che) chèn `aboveSubview:` cái in-feed cao
  nhất, hoặc index 0 nếu không có in-feed nào. Ad chèn trên màn che nếu màn
  che đang đứng.
- **Màn che full-screen** `addSubview:` — cuối mảng, tức trên tất cả những
  thứ trên.

"Cái in-feed cao nhất" là cái **già nhất** còn sống: mỗi cái mới vào index 0 sẽ
đẩy các cái cũ lên. Pack giữ một `NSPointerArray` weak theo thứ tự tạo, phần tử
đầu tiên còn khác `nil` chính là nó — không phải dò `indexOfObject:` (O(n) và
so sánh từng phần tử), và không phải quét `subviews`.

Bề mặt thứ tư, **ad full-screen**, là thứ duy nhất pack `present`, và present
thẳng từ Unity root VC — đúng cách GMA present ad toàn màn của họ. Một VC được
present thì luôn vẽ trên **toàn bộ** subview của VC present nó, nên nó tự động
đứng trên cả ba tầng kia mà không ai phải sắp.

### Vì sao màn che không phải window, cũng không phải present

Bản đầu cho mỗi màn che một `UIWindow` với `windowLevel` riêng. Xếp lớp tuyệt
đối thật, nhưng là tuyệt đối trên **mọi thứ**, kể cả ad mà SDK mediation
present, và biến màn che thành một bề mặt Unity không có quan hệ gì.

Bản thứ hai cho màn che full-screen present từ Unity VC. Tệ hơn: **một VC chỉ
present được đúng một thứ**. Màn che thường được bật lên trong suốt thời gian
một quảng cáo chạy — mà đó đúng là lúc SDK mediation cần cái slot present đó,
nên nó sẽ không mở được ad. Không cứu được bằng cách tắt màn che sớm hơn: mốc
sớm nhất là callback "ad đã hiện", mà callback đó chỉ bắn sau khi present
thành công rồi.

Làm subview thì không lấy của ai cái gì, và vẫn được đúng thứ tự cần.
## 5. Pause: ai tắt game, và tắt đúng một lần

Trên Editor cả hai màn che là **no-op có tiếng**: `Platform/Editor` vẫn đăng ký
platform như Android và iOS, nhưng phần cover của nó chỉ in một warning
"không vẽ trong preview" (một lần cho cả session) rồi thôi. Preview không có
mối nối nào để che, và cũng không có Activity lẫn `UnityPause` để dừng game —
vẽ một hình chữ nhật trông có vẻ đúng thì cũng không kiểm chứng được gì.

Vì vậy `OverlayTransport.Platform` trả null **không** còn nghĩa là "nền tảng
này không hỗ trợ" nữa. Cả ba nền tảng đều đăng ký, nên null chỉ xảy ra khi
build hỏng: hoặc không bootstrap nào chạy (thì ad cũng chết theo), hoặc một
assembly nền tảng quên implement `INativeOverlayPlatform`. Cả hai đều
`Debug.LogError`, không phải warning.

| | Gắn vào đâu | Pause |
|---|---|---|
| Full-screen **overlay** | subview, cuối mảng | có |
| Full-screen **ad** | present từ Unity root VC | chỉ khi không có overlay |
| Half-screen overlay / ad | subview, trên tầng in-feed | không bao giờ |

Tại một thời điểm chỉ có đúng **một** present toàn màn của pack, và trong pack
chỉ có đúng **một** chỗ gọi lệnh pause.

### Kẻ thứ ba xoá cờ của mình

Hai cờ trên chỉ lo được phần trong pack. `UnityPause` là một **cờ**, không phải
bộ đếm, ai ghi cũng được — và SDK mediation có ghi: pause khi ad của họ mở,
**resume khi ad của họ đóng**.

Luồng thật của game là: bật màn che → mở ad mediation → ad đóng → *rồi mới* tắt
màn che. Nên cái resume của SDK rơi vào **giữa**, để lại một màn che kín mít với
game chạy, kêu, tính toán phía sau nó.

Không có sự kiện nào báo cho pack biết chuyện đó, nên cách duy nhất giữ được bất
biến là **nhìn**. `ROPauseGuard` là một `CADisplayLink` chỉ sống trong lúc pack
muốn game đứng: mỗi frame đọc `UnityIsPaused()`, thấy bị xoá thì set lại 1, và
log đúng một lần cho mỗi lượt che. Nó chỉ ép về phía *pause*, và cả hai cờ hạ
xuống thì nó tự tắt — nên không có đường nào kẹt game ở trạng thái pause.

Ngoài ra `Show` gọi lần thứ hai lúc màn che đang đứng cũng set lại cờ chứ không
chỉ đổi màu rồi thôi: gọi `Show` là người dùng đang nói "tôi muốn game đứng".

Android không cần gì trong mục này. Màn che ở đó là Activity, nên khi Activity
ad của SDK finish thì hệ thống quay về màn che — vẫn nằm trên Unity, vẫn pause,
và không có cờ nào cho ai xoá.

Một trường hợp còn hở, cố ý không xử lý: nếu game gọi `Hide` trong lúc ad
mediation *vẫn đang chạy*, pack hạ cờ và game chạy sau lưng ad của họ. Pack
không có cách nào biết SDK còn muốn pause hay không, mà đoán thì tệ hơn — và
triệu chứng chỉ là tốn pin, không sai hình.

**Android** không phải làm gì: full-screen overlay là `NativeOverlayActivity`,
full-screen ad là `OverlayAdActivity`, mà Activity đè lên nhau thì hệ điều hành
tự pause Activity bên dưới. Không dòng nào trong pack gọi `UnityPlayer.pause()`.

**iOS** không có Activity nên phải tự gọi `UnityPause`. Đúng một hàm
`ROApplyPause()` trong `RONativeOverlay.mm` gọi nó, lái bằng đúng hai cờ — một
của overlay, một của ad — rồi OR lại:

    UnityPause(coverWantsPause || adWantsPause ? 1 : 0);

Viết bằng hai cờ có tên chứ không phải bộ đếm là để nói đúng một điều: chỉ hai
thằng này, không ai khác. Ad bật cờ vô điều kiện, nên khi đã có overlay thì cờ
của ad không đổi gì — đúng nghĩa "ad chỉ pause khi không có overlay" — nhưng
nếu overlay tắt trước lúc ad đóng thì game vẫn đứng yên, thay vì chạy tiếp sau
lưng một ad toàn màn.

Không còn trường hợp ngoài thang nào phải xử lý: màn che là subview nên không
đụng tới slot present, và tắt màn che chỉ là `removeFromSuperview` nên không
kéo theo ad nào cả.
