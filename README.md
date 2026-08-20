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
kích thước (`HalfScreenCover`, `FullScreenCover`).

Màn che (`FullScreenCover`, `HalfScreenCover`) nằm chung pack chính vì
điều này: chúng phải chen vào đúng thang đó, không thể xếp lớp từ một pack
đứng ngoài.

**Android** có sẵn thang: in-feed là window `TYPE_APPLICATION_PANEL` (1000),
half-screen là `TYPE_APPLICATION_SUB_PANEL` (1002), full-screen là Activity —
mà Activity thì luôn lên trên cùng. Hệ điều hành lo, không phải mình lo.

Window không có index như subview — `WindowManager` không có API đổi z-order.
Chỉ hai đòn bẩy: **type**, và thứ tự `addView` trong cùng type. Đòn bẩy thứ hai
vô dụng ở đây vì muốn leo lên phải gỡ window ra thêm lại (flicker, mà cũng chỉ
tới được *đỉnh* của type chứ không chèn xuống dưới được).

Mà ad half và màn che half **cùng** type 1002. Nên trên Android thứ tự giữa hai
cái đó là **hợp đồng của người gọi**: bật màn che *trước*, mở ad *sau*. Thứ tự
đó đúng ở cả hai nền tảng và là thứ tự game dùng. Trường hợp ngược lại — bật
màn che khi ad half đang hiện — Android sẽ cho màn che đè lên ad, iOS thì không.

### Đã thử tách type và đã sai — đừng thử lại

Ý tưởng: đẩy ad half lên `TYPE_APPLICATION_ATTACHED_DIALOG` (1003) để thang tự
đúng, dựa trên giả định "số type lớn hơn thì vẽ trên". **Giả định đó sai.** Đo
bằng `adb shell dumpsys window windows` (liệt kê từ trên xuống) trên bản build
đã chứa 1003:

    ty=APPLICATION_SUB_PANEL          <- màn che
    ty=APPLICATION_ATTACHED_DIALOG    <- ad half, bị chôn
    ty=APPLICATION_PANEL              <- in-feed
    ty=BASE_APPLICATION               <- Unity

Triệu chứng trên máy: collapsible ra một mảng đen nửa dưới màn hình, không có
gì trong đó. Sub-window type được ánh xạ sang layer bên trong
`WindowManagerService`, và ánh xạ đó **không** theo thứ tự số. Type duy nhất
xếp trên `SUB_PANEL` là `ABOVE_SUB_PANEL` (1005), mà nó `@hide` nên không gọi
tên được. Vậy 1002 là bậc cao nhất dùng được, phần còn lại do hợp đồng gọi hàm
gánh.

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

### Vì sao trên iOS màn che là subview, không phải window cũng không phải present

(Chỉ nói về iOS. Trên Android màn che full-screen là Activity — xem mục 5.)

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
## 5. Pause: ai tắt game, và luật phải trả cho nó

| | Gắn vào đâu | Pause |
|---|---|---|
| Màn che full | **Activity** / subview cuối mảng | **có** |
| Màn che half | window `SUB_PANEL` / subview trên tầng in-feed | không |
| **Ad** full-screen | Activity / present từ Unity root VC | có |
| Ad half | window `SUB_PANEL` / subview | không |

**Android** không phải gọi gì: cả hai thứ pause đều là Activity, hệ điều hành
tự dừng Activity bên dưới. Không dòng nào trong pack gọi `UnityPlayer.pause()`.

**iOS** không có Activity nên tự gọi `UnityPause`. Đúng một hàm
`ROApplyPause()` trong `ROCover.mm` gọi nó, lái bằng hai cờ có tên — một
của màn che, một của ad — rồi OR lại.

### Luật phải trả cho cái pause đó

Unity dừng thì **C# không chạy**, mà `Hide()` là một lời gọi C#. Nên:

> Thứ gỡ màn che full **không được** xếp lịch qua player loop của Unity.

Bản thân lời gọi thì thread nào cũng được — nó là JNI/DllImport, rồi post sang
main looper của Android; cả hai đều còn sống khi Unity dừng. Cái chết là **chỗ
xếp lịch**: `Update`, coroutine, `UniTask.Post`, hay callback SDK đã bị marshal
về main thread — tất cả đều nằm sau player loop.

Trong game này đường sống là `onCompletedAnyThread` của `AdMaxProvider`: nó bắn
trên chính luồng SDK, ngoài `UniTask.Post`. **Mọi** đường kết thúc một lượt show
phải mang nó, kể cả nhánh `FailedToDisplay` — nhánh đó từng thiếu và hậu quả là
màn che không bao giờ được gỡ.

Đã trả giá một lần rồi: khi màn che full là Activity còn `Hide` đi qua callback
đã marshal về main thread, đo được `mResumedActivity` là màn che,
`UnityPlayerActivity` `state=STOPPED`, log Unity im hẳn — màn đen vĩnh viễn,
back bị nuốt có chủ ý, chỉ force-stop mới thoát.

### `ROPauseGuard`

`UnityPause` là **cờ**, không phải bộ đếm, và SDK mediation cũng ghi nó — pause
khi ad của họ mở, **resume khi ad của họ đóng**. Cú resume đó có thể rơi vào
lúc màn che hoặc ad toàn màn của pack đang đứng. Không có sự kiện nào báo, nên
guard là một `CADisplayLink` chỉ sống trong lúc pack muốn game đứng: mỗi frame
đọc `UnityIsPaused()`, thấy bị xoá thì set lại, log một lần mỗi lượt. Nó chỉ ép
về phía pause, và hai cờ hạ xuống thì tự tắt.

## 6. RedirectOnClose: đóng ad đúng lúc redirect chiếm màn hình

`CloseSettings.RedirectOnClose` biến nút close thành một cú click thật vào ad:
người chơi bấm close, SDK ghi nhận click, trình duyệt mở, và ad biến mất.

Chỗ tinh tế là **đóng vào lúc nào**. Mốc hiển nhiên — SDK báo đã ghi nhận click
— là **sai**: "đã ghi nhận" không phải "trình duyệt đã lên". Giữa hai mốc đó ad
đã biến mất còn trình duyệt chưa tới, và người chơi nhìn thấy game trần trong
khoảnh khắc ấy.

Nên mốc đóng là lúc **cửa sổ ad mất foreground** — vì thứ duy nhất đẩy nó xuống
ngay sau một cú click do chính người chơi tạo ra là cái vừa được mở. Đóng ở đó
thì trình duyệt đã che kín, không ai thấy sự thay thế.

Máy trạng thái, giống nhau hai nền tảng:

    IDLE
     │ chạm nút close
     ▼
    ARMED ──────────── hết CLOSE_REPORT_TIMEOUT (1s) ──► đóng
     │ SDK báo click                                     (cú chạm không trúng
     ▼                                                    asset nào, sẽ không
    REDIRECT_PENDING                                      có redirect nào cả)
     │ mất foreground ──► đóng   ← redirect đã chiếm màn hình
     └ hết REDIRECT_TIMEOUT (3s) ──► đóng
       (click đã được ghi nhưng không gì lên trên: mạng chậm, hoặc SDK
        mở overlay ngay trong app)

Android không có bước ARMED riêng cho iOS: bên Android cú chạm được **thả rơi**
xuống `NativeAdView` rồi chờ `onAdClicked`, còn bên iOS gọi thẳng
`performClickOnAssetWithKey:`. Khác cách lấy click, giống nhau từ
`REDIRECT_PENDING` trở đi.

| | Android | iOS |
|---|---|---|
| mất foreground | `OverlayAdContentView.onWindowFocusChanged(false)` | `onPaused`, do presentation gọi từ `UIApplicationWillResignActive` |

Chọn `onWindowFocusChanged` trên Android chứ không phải `Activity.onPause` vì
ad **duy nhất** dùng `RedirectOnClose` là collapsible — mà collapsible là
half-screen, sống trong một Dialog trên Unity Activity nên không có onPause của
riêng nó. Một View thì nhận được thay đổi focus dù nằm trong window nào, nên
một hook phủ cả hai đường. Nhánh trong `OnPaused` vẫn giữ, thừa chứ không thay
thế, phòng khi mai này một ad toàn màn được bật `RedirectOnClose`.

Home, cuộc gọi đến, kéo notification shade cũng chạm vào những hook này — nhưng
chỉ có tác dụng khi đang `REDIRECT_PENDING`, tức là vừa bấm close **và** SDK
vừa báo click trong cùng một nhịp. Rơi trúng cửa sổ đó do tình cờ thì kết cục
vẫn là đóng ad, đúng thứ người chơi vừa bấm.

Chỉ cú chạm **bắt đầu trên nút close** mới vũ trang được. Bấm đúng CTA là click
thật, ad phải còn nguyên khi người chơi quay lại.
