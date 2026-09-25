# RiseOn.NativeAdMob

Native ad của AdMob hiển thị theo ba kiểu: toàn màn hình, nửa màn hình
(collapsible) và ô trong danh sách (in-feed), kèm hai màn che trơn xếp cùng thang
với ad. Chạy trên Android và iOS, có bản xem trước trong Editor.

Package `com.riseon.nativeadmob`, namespace `RiseOn.NativeAdMob`.

## Mục lục

- [Yêu cầu](#yêu-cầu)
- [Cài đặt](#cài-đặt)
- [Tổng quan](#tổng-quan)
- [Hướng dẫn nhanh](#hướng-dẫn-nhanh)
  - [Tạo placement](#tạo-placement)
  - [Callback đến trên thread native](#callback-đến-trên-thread-native)
  - [Màn che](#màn-che)
  - [Ô in-feed](#ô-in-feed)
  - [Trong Editor](#trong-editor)
- [Tài liệu chi tiết](#tài-liệu-chi-tiết)
- [Lịch sử thay đổi](#lịch-sử-thay-đổi)
- [Giấy phép](#giấy-phép)

## Yêu cầu

| Phụ thuộc | Cách có | Dùng cho |
|---|---|---|
| Unity 6000.3 | | Bản đang dùng để phát triển |
| `com.google.ads.mobile` 11.5.0 | Tự cài theo `package.json` (trên OpenUPM cần scope `com.google`) | Plugin Google Mobile Ads cho Unity. Plugin này mang theo SDK native (Android `play-services-ads`, iOS pod `Google-Mobile-Ads-SDK`) |
| `com.unity.ugui` 2.0.0 | Tự cài theo `package.json` | Bản xem trước trong Editor |

## Cài đặt

**OpenUPM** (khuyên dùng): thêm registry OpenUPM với scope `com.riseon` và
`com.google` (để kéo được plugin Google Mobile Ads) vào `Packages/manifest.json`,
rồi thêm package:

```json
{
  "scopedRegistries": [
    {
      "name": "package.openupm.com",
      "url": "https://package.openupm.com",
      "scopes": ["com.google", "com.riseon"]
    }
  ],
  "dependencies": {
    "com.riseon.nativeadmob": "1.0.3"
  }
}
```

**Git URL**: *Package Manager → + → Add package from git URL*:

```
https://github.com/riseongamestudio/NativeAdMob.git#v1.0.3
```

Cài bằng git thì plugin Google Mobile Ads vẫn phải lấy được từ một registry (vd.
OpenUPM như trên), vì Package Manager không tự kéo phụ thuộc qua git.

## Tổng quan

| Kiểu | Lớp | Dùng cho |
|---|---|---|
| Toàn màn hình | `FullScreenAd` | App open, interstitial, end card |
| Nửa màn hình | `HalfScreenAd` | Collapsible ở nửa dưới màn hình; `HeightRatio` là phần bị che |
| In-feed | `InFeedAd` | Ô trong danh sách; một instance cho mỗi ad unit, nhiều ô |
| Màn che | `FullScreenCover`, `HalfScreenCover` | Màn trơn che khoảng giữa các ad |

Ad được vẽ bằng view native của hệ điều hành, đè lên Unity. Assembly core giữ API
và máy trạng thái; ba assembly nền tảng (Android, iOS, Editor) mỗi cái chỉ biên
dịch cho nền tảng của nó và tự cắm vào core lúc khởi động. Pack chỉ lo nạp và
hiển thị; chính sách của game (gói bỏ quảng cáo, tần suất, remote config) nằm ở
phía game, trước khi gọi pack.

## Hướng dẫn nhanh

### Tạo placement

Mỗi placement là một instance sống trọn đời app, tạo một lần lúc khởi động, sau
khi Google Mobile Ads đã khởi tạo:

```csharp
using RiseOn.NativeAdMob;
using UnityEngine;

var interstitial = new FullScreenAd(new FullScreenAd.Settings {
    AdUnitId        = "ca-app-pub-xxxxxxxx/yyyyyyyy",
    BackgroundColor = Color.black,
    Format          = "interstitial",                 // tên placement, đi kèm mọi OnAdPaid
    Close           = new CloseSettings { Cooldown = 3 },
});
interstitial.OnAdPaid   += info => LogRevenue(info.Format, info.Value, info.Currency);
interstitial.OnAdHidden += OnInterstitialClosed;
interstitial.Load();      // gọi một lần; pack tự nạp tiếp và tự thử lại

// khi cần hiện
if (interstitial.IsReady()) interstitial.Show();
```

- Kết quả hiển thị về qua `OnAdDisplayed`, `OnAdDisplayFailed`, `OnAdHidden`.
- `HalfScreenAd` dùng y như vậy, thêm `HeightRatio` trong `Settings`.
- Placement nào hiện ad thứ hai ngay khi ad đầu đóng thì đặt `CacheSize = 2`.
- `CloseSettings` quyết nút đóng: thời gian chờ (`Cooldown`), cạnh đặt nút
  (`CloseSide`), vị trí đồng hồ (`TimerSide`), bấm đóng có mở quảng cáo không
  (`RedirectOnClose`); đổi sau được bằng `SetClose`.

### Callback đến trên thread native

Mọi event của pack đến trên thread native, **không** phải thread Unity. Việc gì
đụng tới Unity trong handler phải tự đưa về main thread (`UniTask.Post`,
`SynchronizationContext`...). Giá trị cần đọc trong callback thì tính sẵn từ
trước, đừng để nó được ghi qua một lệnh post sang main thread.

### Màn che

- Nửa màn hình: `HalfScreenCover.Show(color, heightRatio)` **trước**, rồi mới
  `Show()` một `HalfScreenAd` cùng `HeightRatio`; hạ màn che khi ad xong.
- Toàn màn hình: `FullScreenCover.Show()` trước, thường để che khoảng giữa hai
  quảng cáo nối nhau. Màn che toàn màn hình làm Unity dừng, nên `Hide()` phải
  được gọi từ một thread không chạy qua player loop (thực tế là callback của
  chính SDK quảng cáo), và mọi đường kết thúc, kể cả hiển thị thất bại, đều phải
  hạ.

### Ô in-feed

```csharp
var feed = new InFeedAd(new InFeedAd.Settings {
    AdUnitId  = "ca-app-pub-xxxxxxxx/zzzzzzzz",
    SlotCount = 3,
});

feed.Initialize(new Vector2Int(widthPx, heightPx), roundCornerPx: 24);  // khi đã đo được ô, gọi lại khi ô đổi cỡ
feed[0].SetPosition(new Vector2Int(xPx, yPxFromTop));                   // tọa độ pixel, gốc trên-trái
feed[0].Show();
feed[0].Hide();                                                          // khi ô rời màn hình
```

- In-feed tự nạp từ lúc tạo; tạo sớm để ad kịp sẵn trước khi ô đầu tiên hiện.
- Chỉ show khi ô nằm trọn trong khung nhìn. Pack không vẽ gì khi chưa có ad, nên
  giữ một placeholder của game bên dưới ô.
- Cách đo ô từ uGUI và đổi bán kính bo góc sang pixel:
  [tài liệu core, mục A11](Runtime/README.md#a11-tích-hợp-vào-game).

### Trong Editor

Editor vẽ bản xem trước bằng uGUI. Canvas của game phải nằm dưới cả thang sorting
order của pack (`-5` trở xuống). Chi tiết: [Runtime/Platforms/Editor/README.md](Runtime/Platforms/Editor/README.md).

## Tài liệu chi tiết

| Tài liệu | Nội dung |
|---|---|
| [Runtime/README.md](Runtime/README.md) | Kiến trúc, vòng đời ad, engine layout, template, luật chung (cache, xếp lớp, pause, layout, media, AdChoices...), quy trình và sổ sự cố |
| [Runtime/Platforms/Android/README.md](Runtime/Platforms/Android/README.md) | Riêng Android: Activity, window type, luồng giao sự kiện, cổng javac, adb |
| [Runtime/Platforms/iOS/README.md](Runtime/Platforms/iOS/README.md) | Riêng iOS: bảng đối chiếu file, quy ước pt/px, subview, UnityPause |
| [Runtime/Platforms/Editor/README.md](Runtime/Platforms/Editor/README.md) | Bản xem trước trong Editor: ảnh chụp thay vì mô phỏng, sorting order, pause |

## Lịch sử thay đổi

Xem [CHANGELOG.md](CHANGELOG.md).

## Giấy phép

MIT, xem [LICENSE.md](LICENSE.md).
