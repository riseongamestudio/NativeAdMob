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

## 3. Cache: bao nhiêu, nạp khi nào, hết hạn khi nào

Overlay và in-feed dùng chung một mô hình: giữ sẵn `CacheSize` ad ấm.

- **Mặc định 1** (`CacheSize = 0` nghĩa là 1 với overlay; với in-feed là
  `SlotCount + 1`). Trần là 5 (`MAX_CACHE_SIZE` / `kROMaxCacheSize`).
- Chỉnh ở C#: `FullScreenAd.Settings.CacheSize`, `HalfScreenAd.Settings.CacheSize`,
  `InFeedAd.Settings.CacheSize`. Collapsible để **2** vì plan chained bày ad
  thứ hai ngay khi ad đầu đóng.
- **Ba nơi kích hoạt nạp**: provider gọi `Load()`; một lượt load **thành công**
  tự nối lượt sau cho tới khi đầy; và một `Show` vừa rút ad ra khỏi cache.
- **Load hỏng thì dừng hẳn** — không retry, không backoff bên trong pack.
  Quyền retry thuộc về provider (`AdMobProvider` đang dùng backoff luỹ thừa 2,
  trần 32 giây).
- Chỉ **ad ở đầu hàng** được dựng sẵn giao diện; ad phía sau dựng khi lên đầu.

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
