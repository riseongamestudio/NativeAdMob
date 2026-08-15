# Native ad – iOS port

Port 1:1 của `Assets/Plugins/Android/NativeAdMob.androidlib` (`com.riseon.nativeadmob`)
sang Objective-C++, hành vi đồng nhất với bản Android: cùng máy trạng thái,
cùng layout engine, cùng ngưỡng chính sách, cùng format dòng log.

## Bản đồ file (Java → Obj-C++)

| Android | iOS | Trạng thái |
|---|---|---|
| (bề mặt AndroidJavaObject) | `RONativeAdMobBridge.h/.mm` | ✅ |
| (C# wrapper) | `NativeAdMobIOSBridge.cs` + nhánh `#if UNITY_IOS` trong NativeAdMob/InFeed/Overlay.cs | ✅ |
| `NativeAdMob.java` | `RONativeAdMob.h/.mm` | ✅ |
| (View/LinearLayout/FrameLayout measure model) | `ROMeasureLayout.h/.mm` | ✅ |
| (TextView + ApplyTextMode + marquee) | `ROAdTextLabel.h/.mm` | ✅ |
| `NativeAdMobStarRatingView.java` | `RONativeAdMobStarRatingView.h/.mm` | ✅ |
| `InFeed.java` | `RONativeAdMobInFeed.h/.mm` | ✅ |
| `NativeAdMobInFeedPresentation.java` | `RONativeAdMobInFeedPresentation.h/.mm` | ✅ |
| `NativeAdMobInFeedLayoutEngine.java` | `RONativeAdMobInFeedLayoutEngine.h/.mm` | ✅ |
| `NativeAdMobInFeedLayoutValidator.java` | `RONativeAdMobInFeedLayoutValidator.h/.mm` | ✅ |
| `NativeAdMobInFeedViewFactory.java` | `RONativeAdMobInFeedViewFactory.h/.mm` | ✅ |
| `Overlay.java` | `RONativeAdMobOverlay.h/.mm` | ✅ |
| `NativeAdMobOverlayContentView.java` | `RONativeAdMobOverlayContentView.h/.mm` | ✅ |
| `NativeAdMobOverlayActivity/Presentation` | gộp vào presentation (UIViewController) | chưa |

## Quy ước chuyển đổi

- **Đơn vị**: Unity đưa px màn hình qua bridge; iOS chia cho
  `UIScreen.nativeScale` thành point ngay tại biên. Nội bộ mọi thứ tính bằng
  point — 1pt đứng đúng chỗ 1dp của Android (`Dp(120)` → `120pt`). Dòng log
  vẫn in cả px lẫn "dp" với `density = nativeScale` để đọc chung một cách.
- **Thread**: `Handler main` → `dispatch_get_main_queue()`;
  `Choreographer.postFrameCallback` (hoãn swap sau frame ẩn) →
  `CATransaction` completion / `dispatch_async` sau commit.
- **Cửa sổ**: dialog panel in-feed → UIView con của
  `UnityGetGLViewController().view` đặt frame theo slot; Activity full-screen
  → `UIViewController` present không animation; collapsible → view ghim đáy.
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
