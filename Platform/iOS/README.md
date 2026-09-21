# RiseOn.NativeAdMob — iOS

Bản hiện thực iOS của bộ luật chung ở [README gốc](../../README.md): Obj-C++
trong `Bridge/Native/` (prefix `RO`), C# gọi qua `DllImport("__Internal")`
trong `Bridge/NativeAdBridge.cs`, cùng bộ client `InFeedAdClient` /
`OverlayAdClient` / `CoverPlatform` như hai nền tảng kia. Ba nền tảng ngang
hàng — cùng máy trạng thái, cùng layout engine, cùng ngưỡng chính sách, cùng
format dòng log — nên khi sửa một luật thì sửa cả ba, và bảng đối chiếu dưới
đây chỉ để tìm nhanh file tương ứng bên Java khi cần đối chiếu từng dòng.
File này ghi những gì **riêng iOS**.

## 1. Bảng đối chiếu file với bản Java

| Obj-C++ (`Bridge/Native/`) | Java (`../Android/Bridge/NativeAdMob.androidlib`) |
|---|---|
| `RONativeAdBridge.h/.mm` | (bề mặt `AndroidJavaObject` + các `*Proxy.cs`) |
| `ROBaseAd.h/.mm` | `BaseAd.java` (+ giao thức `NativeAdPresentation`) |
| `RONativeAdStarRatingView.h/.mm` | `NativeAdStarRatingView.java` |
| `ROMeasureLayout.h/.mm` | (mô hình measure của View/LinearLayout/FrameLayout) |
| `ROAdTextLabel.h/.mm` | (TextView + `ApplyTextMode` + marquee) |
| `ROInFeedAd.h/.mm` | `InFeedAd.java` |
| `ROInFeedAdSlot.h/.mm` | `InFeedAdSlot.java` |
| `ROInFeedAdPresentation.h/.mm` | `InFeedAdPresentation.java` |
| `ROInFeedAdLayoutEngine.h/.mm` | `InFeedAdLayoutEngine.java` |
| `ROInFeedAdLayoutValidator.h/.mm` | `InFeedAdLayoutValidator.java` |
| `ROInFeedAdViewFactory.h/.mm` | `InFeedAdViewFactory.java` |
| `ROInFeedAdLayoutTypes.h` | (các struct/enum plan dùng chung) |
| `ROOverlayAd.h/.mm` | `OverlayAd.java` |
| `ROOverlayAdContentView.h/.mm` | `OverlayAdContentView.java` |
| `ROOverlayAdPresentation.h/.mm` | `OverlayAdActivity.java` + `OverlayAdPresentation.java` |
| `ROCover.h/.mm` (+ `../CoverBridge.cs`) | `BaseCover` + `FullScreenCover`(+`Activity`) + `HalfScreenCover` |

## 2. Quy ước riêng iOS

- **Đơn vị**: Unity đưa px màn hình qua bridge; iOS chia cho
  `UIScreen.nativeScale` thành point **một lần duy nhất tại biên**
  (`RONativeAdBridge.mm`, `HBPointsFromPixels`). Nội bộ mọi thứ tính bằng
  point — 1pt đứng đúng chỗ 1dp của luật chung (`Dp(120)` → `120pt`); các
  sàn viết bằng px trong luật chung (`kROMinAttributionSizePx` 15,
  `kROAdChoicesMinWidthPx` 19) đi qua `ro_ptFromPx:`. Dòng log vẫn in cả px
  lẫn "dp" với `density = nativeScale` để đọc chung một cách với Android.
  Point chia hết nên iOS không có "pixel thừa" để phát trong ngân sách
  side-media (README gốc B8).
- **Thread**: lệnh từ Unity hop về `dispatch_get_main_queue()`
  (`+runOnMainThread:`); chỗ luật chung cần "sau khi frame ẩn đã commit"
  (Choreographer bên Android) thì dùng completion của `CATransaction` /
  `dispatch_async` sau commit. Callback về C# rơi trên thread SDK chọn; core
  C# không marshal (README gốc B1). Bảng callback (`RONativeAdListenerCallbacks`)
  được đọc trên thread SDK và ghi từ thread Unity → swap dưới lock để không ai
  thấy nửa bản.
- **Measure**: layout engine chung được viết theo mô hình measure-spec
  (UNSPECIFIED / AT_MOST / EXACTLY, weight, wrap/match); `ROMeasureLayout`
  là hiện thực của mô hình đó trên UIKit (`HBLinearLayoutView`,
  `HBFrameLayoutView`, `ro_layoutWidth/Height/Gravity/Margins/Padding`) —
  kể cả quy tắc "weight + height 0 trong UNSPECIFIED đọc thành WRAP_CONTENT"
  mà icon filler phụ thuộc. Sửa engine thì sửa cả `ROInFeedAdLayoutEngine.mm`
  và `InFeedAdLayoutEngine.java`; đừng "tối ưu" model đo cho giống UIKit hơn.
- Method riêng tư prefix `ro_`, hằng `kRO*`; tham số nhiều dòng dấu phẩy đầu
  dòng như hai bên kia.

## 3. Khác biệt SDK phải nhớ

- `GADAdValue.value` là **đơn vị tiền tệ**, không phải micros (không chia
  1e6, nếu không doanh thu co 10^6 lần).
- No-fill là `GADErrorNoFill = 1` (Android là 3); C# chỉ phân biệt 0/khác 0.
- Paid-event source lấy từ
  `responseInfo.loadedAdNetworkResponseInfo.adNetworkClassName`.
- `GADNativeAd` **không có** `performClickOnAssetWithKey:` (đó là của
  `GADCustomNativeAd`) → cú chạm close của RedirectOnClose được thả xuyên
  xuống `GADNativeAdView` từ `hitTest:` — hook duy nhất chạy lúc touch-down và
  được phép từ chối target. Mất foreground đọc từ
  `UIApplicationWillResignActive` (`onPaused` của presentation); countdown
  giữ thời gian còn lại qua resign-active.
- AdChoices in-feed **chưa sửa trên iOS**: code vẫn như HEAD — SDK tự vẽ dấu
  ở góc ưu tiên (`GADAdChoicesPositionTopRightCorner` trong `ROBaseAd`), ta
  chỉ có ô giữ chỗ `adChoicesReserve`; với ô bo góc thì dấu có thể bị cung
  cắt y như Android trước đây. Cách Android đang dùng (padding
  `NativeAdView`) **không chép sang được**: UIKit không có padding cho
  subview đặt bằng frame, và chưa biết SDK iOS đặt dấu vào view nào. Gán
  `GADNativeAdView.adChoicesView` là API có sẵn, nhưng đúng cách đó trên
  Android đã không dời được dấu (README Android §7) — nên **không đoán nữa**:
  lấy cây view trên iPhone (Xcode View Debugger) rồi mới quyết.
- MAX trên iOS với `InvokeEventsOnUnityMainThread = false` bắn callback trên
  một `NSOperationQueue` nền, không phải main queue → **iOS không được miễn**
  luật "hide màn che phải ở ngoài player loop" (README gốc B4), và cũng
  không được miễn luật "không đụng API Unity trong callback".

## 4. Xếp lớp bằng vị trí trong `subviews`

iOS không có Activity, không có window type; `UIWindow` chỉ có `windowLevel`,
và `zPosition` thì đổi thứ tự vẽ nhưng **không** đổi thứ tự nhận chạm
(`hitTest:` duyệt `subviews` từ cuối về đầu), nên hai thứ sẽ lệch nhau. Thứ
duy nhất vừa đúng khi vẽ vừa đúng khi chạm là **vị trí trong mảng
`subviews`** — cuối mảng là trên cùng.

Ba trong bốn bề mặt là **subview** của `rootVC.view` (chính là `UnityView`;
game nằm ở lớp *cha*, không phải anh em, nên index 0 vẫn nằm trên game). Luật,
từ dưới lên:

- **In-feed** luôn `insertSubview:atIndex:0` — đáy của phần pack thêm vào.
- **Half-screen** (cả ad lẫn màn che) chèn `aboveSubview:` cái in-feed cao
  nhất, hoặc index 0 nếu không có in-feed nào. Ad chèn trên màn che nếu màn
  che đang đứng — khớp hợp đồng "màn che trước, ad sau" của README gốc B3.
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

### Vì sao màn che là subview, không phải window cũng không phải present

Bản đầu cho mỗi màn che một `UIWindow` với `windowLevel` riêng. Xếp lớp tuyệt
đối thật, nhưng là tuyệt đối trên **mọi thứ**, kể cả ad mà SDK mediation
present, và biến màn che thành một bề mặt Unity không có quan hệ gì.

Bản thứ hai cho màn che full-screen present từ Unity VC. Tệ hơn: **một VC chỉ
present được đúng một thứ**. Màn che thường được bật lên trong suốt thời gian
một quảng cáo chạy — mà đó đúng là lúc SDK mediation cần cái slot present đó,
nên nó sẽ không mở được ad. Không cứu được bằng cách tắt màn che sớm hơn: mốc
sớm nhất là callback "ad đã hiện", mà callback đó chỉ bắn sau khi present
thành công rồi.

Làm subview thì không lấy của ai cái gì, và vẫn được đúng thứ tự cần. Màn che
full nuốt chạm có chủ ý; màn che half là container phủ host nhưng `hitTest:`
trả chạm ở dải trên về game. Cả hai theo bounds của host (xoay, split-screen)
thay vì kích thước chụp lúc show; chiều cao half lấy từ resolver của ad
(`resolveHalfScreenPanelHeightForRatio:`), không tự tính.

## 5. Pause: `UnityPause` và `ROPauseGuard`

Không có Activity nên pack tự gọi `UnityPause`. Đúng một hàm `ROApplyPause()`
trong `ROCover.mm` gọi nó, lái bằng **hai cờ có tên** — một của màn che full,
một của ad full-screen — rồi OR lại: hai người này và không ai khác, và không
thứ tự đến nào để game đứng mà không có gì ở trên. Show lại màn che đang đứng
thì **áp lại** pause chứ không giả định — cờ có thể đã bị người khác xoá.

`UnityPause` là **cờ**, không phải bộ đếm, và SDK mediation cũng ghi nó — pause
khi ad của họ mở, **resume khi ad của họ đóng**. Cú resume đó có thể rơi vào
lúc màn che hoặc ad toàn màn của pack đang đứng. Không có sự kiện nào báo, nên
guard là một `CADisplayLink` chỉ sống trong lúc pack muốn game đứng: mỗi frame
đọc `UnityIsPaused()`, thấy bị xoá thì set lại, log một lần mỗi lượt. Chạy ở
common run-loop modes để một vòng tracking ở đâu đó không làm câm nó. Nó chỉ
ép về phía pause, và hai cờ hạ xuống thì tự tắt.

Luật trả giá cho pause (hide phải đến từ thread còn chạy) ở README gốc B4 —
áp cho iOS y như Android.

## 6. ABI với C#

- `RONativeAdBridge.h` và `NativeAdBridge.cs` là **một hợp đồng hai bản
  chép**: cùng tên hàm, cùng thứ tự tham số, cùng ngữ nghĩa với bề mặt Java
  mà C# đã dùng — để tầng C# là một đường code với hai transport. Đổi chữ ký
  một bên là đổi cả ba nơi (`.h`, `.mm`, `.cs`); gate `balance.py` kiểm
  `ROInFeedAd_Create` / `ROInFeedAd_Configure` khớp giữa ba file.
- Callback về C# là **trampoline static** `[MonoPInvokeCallback]` (yêu cầu
  IL2CPP), tra client theo `instanceId` mà C# chọn và native echo lại; late
  callback không khớp generation thì rơi. `bool` marshal `[MarshalAs(I1)]`;
  chuỗi UTF-8 chỉ sống trong thời gian callback — copy nếu cần giữ.
- Handle: `Create` trả handle đã retain; `Release` vừa tháo ad vừa balance
  retain; không lệnh nào hợp lệ sau đó.
- Comment đầu `RONativeAdBridge.h` còn nói "C# proxies marshal qua
  `MobileAdsEventExecutor`" — **đã cũ**: core C# không marshal (README gốc
  B1). Sửa dòng đó khi chạm file.

## 7. Kiểm chứng

**Chưa compile được trên máy Windows này** — toàn bộ Obj-C++ cần Mac/Xcode
(hoặc export Unity iOS trên Windows → `xcodebuild` trên runner macOS /
Codemagic, build Simulator không cần Apple account; chạy thử bằng TestFlight).
Cổng local chỉ kiểm: ngoặc `{}()[]` cân sau khi bỏ comment và string, và chữ
ký ABI khớp ba nơi. Vì thế **mọi hành vi iOS đều là "chưa kiểm chứng"** cho
tới khi build được; trước khi tin bất kỳ hành vi nào: build, chạy, đọc log
theo cùng format với Android (`layout ready <plan>`, `rect=[..]px = [..]dp
(density=..)`, `Show timeline:`).
