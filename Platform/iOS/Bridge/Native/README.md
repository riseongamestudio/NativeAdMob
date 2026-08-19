# Native ad – iOS port

Port 1:1 của `../../../Android/Bridge/NativeAdMob.androidlib`
(`com.riseon.nativeadmob`) sang Objective-C++, hành vi đồng nhất với bản
Android: cùng máy trạng thái, cùng layout engine, cùng ngưỡng chính sách,
cùng format dòng log.

## Bản đồ file (Java → Obj-C++)

| Android | iOS |
|---|---|
| (bề mặt AndroidJavaObject) | `RONativeAdBridge.h/.mm` |
| (C# wrapper) | `../NativeAdBridge.cs`, assembly `RiseOn.NativeAdMob.iOS` |
| `BaseAd.java` | `ROBaseAd.h/.mm` |
| `NativeAdPresentation.java` | (giao thức trong `ROBaseAd.h`) |
| `NativeAdStarRatingView.java` | `RONativeAdStarRatingView.h/.mm` |
| (View/LinearLayout/FrameLayout measure model) | `ROMeasureLayout.h/.mm` |
| (TextView + ApplyTextMode + marquee) | `ROAdTextLabel.h/.mm` |
| `InFeedAd.java` | `ROInFeedAd.h/.mm` |
| `InFeedAdSlot.java` | `ROInFeedAdSlot.h/.mm` |
| `InFeedAdPresentation.java` | `ROInFeedAdPresentation.h/.mm` |
| `InFeedAdLayoutEngine.java` | `ROInFeedAdLayoutEngine.h/.mm` |
| `InFeedAdLayoutValidator.java` | `ROInFeedAdLayoutValidator.h/.mm` |
| `InFeedAdViewFactory.java` | `ROInFeedAdViewFactory.h/.mm` |
| `OverlayAd.java` | `ROOverlayAd.h/.mm` |
| `OverlayAdContentView.java` | `ROOverlayAdContentView.h/.mm` |
| `OverlayAdActivity.java` + `OverlayAdPresentation.java` | `ROOverlayAdPresentation.h/.mm` |
| `NativeOverlay.java` + `NativeOverlayActivity.java` | `RONativeOverlay.h/.mm` (+ `../NativeOverlayBridge.cs`) |

Hai file cuối là **màn che** (`FullScreenOverlay`, `HalfScreenOverlay`) — không
có quảng cáo nào trong đó, chỉ một mảng màu. Chúng ở chung pack vì phải xếp
lớp cùng một chỗ với ad; xem mục "Xếp lớp" trong README của pack.

## Quy ước chuyển đổi

- **Đơn vị**: Unity đưa px màn hình qua bridge; iOS chia cho
  `UIScreen.nativeScale` thành point ngay tại biên. Nội bộ mọi thứ tính bằng
  point — 1pt đứng đúng chỗ 1dp của Android (`Dp(120)` → `120pt`). Dòng log
  vẫn in cả px lẫn "dp" với `density = nativeScale` để đọc chung một cách.
- **Thread**: `Handler main` → `dispatch_get_main_queue()`;
  `Choreographer.postFrameCallback` (hoãn swap sau frame ẩn) →
  `CATransaction` completion / `dispatch_async` sau commit.
- **Cửa sổ**: iOS không có Activity, cũng không có window type. Ba tầng
  Android (panel → sub-panel → Activity) đổi thành thứ tự trong mảng
  `subviews` của `UnityGetGLViewController().view`: in-feed vào index 0,
  half-screen chèn trên in-feed, màn che full-screen vào cuối mảng. Riêng ad
  full-screen thì `present` từ chính VC đó, nên tự động nằm trên cả ba. Xem
  mục "Xếp lớp" và "Pause" trong README của pack.
- **Measure**: toàn bộ engine viết theo mô hình measure-spec của Android —
  `ROMeasureLayout` tái tạo đúng mô hình đó (kể cả quy tắc weight+height-0
  trong UNSPECIFIED bị đổi thành WRAP_CONTENT mà icon filler phụ thuộc).
- **Khác biệt SDK phải nhớ**: `GADAdValue.value` là đơn vị tiền tệ, KHÔNG
  phải micros như Android (không chia 1e6); mã lỗi no-fill của iOS là
  `GADErrorNoFill=1` (Android là 3) — C# chỉ phân biệt 0/khác-0 nên không sao;
  paid-event source lấy từ `responseInfo.loadedAdNetworkResponseInfo`.

## Kiểm chứng

Chưa compile được trên máy Windows này — cần Mac/Xcode (hoặc export Unity
iOS + `pod install`). Trước khi tin bất kỳ hành vi nào: build, chạy, đọc log
`InFeed`/`Overlay` như trên Android.
