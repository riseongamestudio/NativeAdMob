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
- **AdChoices in-feed.** Đo trên iPhone (2026-09-21, GMA 13.9.0):
  - SDK tạo `GADNativeAdAttributionView` làm **con trực tiếp** của
    `GADNativeAdView`, ghim bằng Auto Layout vào **mép** của nó:
    `V:|-(0)-[GADNativeAdAttributionView]` và
    `GADNativeAdAttributionView.right == GADNativeAdView.right` — inset 0,
    `translatesAutoresizingMaskIntoConstraints = NO`. Cung bo do
    `ROInFeedAdPresentation` cắt (`clipsToBounds`, `cornerRadius`), nên dấu
    hiện trong view này sẽ bị cắt như Android trước đây.
  - SDK còn thêm `GADOverlayView` (ẩn) và một `UIView` trơn, cả hai phủ kín
    `GADNativeAdView` bằng constraint center/width/height.
  - Header SDK **hứa rõ**: gán `GADAdChoicesView` vào
    `GADNativeAdView.adChoicesView` trước `setNativeAd:` thì AdChoices "will
    render inside" view đó. Android không có câu hứa này và trên máy đã không
    làm vậy. Khi gán, SDK có nhận view (log: nó tắt `userInteractionEnabled`
    trên view đó như mọi asset view) nhưng **vẫn tạo** attribution view riêng;
    vì test ad không có AdChoices nên chưa biết nội dung thật đi vào đâu.
  - **Cách Android không chép sang được**: attribution view ghim vào mép
    `GADNativeAdView`, không phải con MATCH_PARENT tôn trọng padding. Thu nhỏ
    `GADNativeAdView` rồi cho nội dung tràn ra thì MediaView phủ cả ô của
    template scrim sẽ nằm ngoài nó — SDK coi đó là lỗi tích hợp ("Not all
    asset views lie inside the native ad view") — và chạm ở viền sẽ không tới
    được ad. Đừng làm.
  - **Cách đang dùng** — `ROInFeedAdViewFactory
    insetSdkAdChoicesInNativeAdView:logResult:`, gọi cuối mỗi
    `ro_layoutBoundContent` (không log) và một lần lúc layout ready (có log):
    tìm subview class `GADNativeAdAttributionView`, tìm hai constraint trên
    `GADNativeAdView` ghim nó `top == top`, `right/trailing == right/trailing`
    (hệ số 1, quan hệ bằng, viết xuôi hay ngược đều nhận), rồi đổi hằng số
    thành ±`ro_badgeEdgeInset` — đúng giá trị badge "Ad" dùng, nên hai dấu góc
    luôn thụt bằng nhau. Chỉ gán khi khác; khung giữ nguyên cỡ.
  - **Không ép gì khi SDK khác đi**: không thấy class, không thấy đủ hai
    constraint, hay hằng số không phải 0/giá trị mình đặt → để nguyên (dấu về
    góc như trước) và log một lần mỗi lần chạy app, dạng
    `InFeed: In-feed AdChoices inset SKIPPED (GMA x.y.z): <thấy gì> - update
    insetSdkAdChoicesInNativeAdView: for this SDK`. Thành công: `... inset
    applied (GMA x.y.z): SDK container inset by 3pt`. SDK tự đặt lại hằng số
    về 0 sau khi mình sửa: log `RESET` rồi sửa lại. Phiên bản GMA đọc qua
    runtime (`valueForKey:@"versionNumber"`), không gọi hàm SDK theo tên —
    file này chỉ biên dịch trên Mac, đoán sai tên hàm là vỡ build.
  - **Vòng layout của pack bỏ qua view do Auto Layout quản**
    (`translatesAutoresizingMaskIntoConstraints == NO`): trước đây
    `ro_layoutBoundContent` gán frame cả ô cho **mọi** con của
    `GADNativeAdView`, kể cả khung AdChoices của SDK, chờ Auto Layout sửa lại.
    Mọi view của pack đều đặt bằng frame (không chỗ nào tắt tamic), nên chỉ
    view của SDK bị bỏ qua.
  - Chưa thấy với ad thật (test ad không có AdChoices).
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

**Build sạch lần đầu ngày 2026-09-21** trên máy Mac của chủ dự án: Xcode 26.6,
iPhoneOS SDK 26.5, Google-Mobile-Ads-SDK 13.9.0 (CocoaPods), Unity
6000.3.19f1, chạy trên iPhone 14 Pro (scale 3). 0 lỗi; một warning có sẵn
(`contentEdgeInsets` deprecated từ iOS 15, trong `ROInFeedAdViewFactory.mm`).
Ô in-feed đã hiện và layout đúng với test ad.

Máy Windows vẫn không compile được — cổng local chỉ kiểm ngoặc `{}()[]` cân
sau khi bỏ comment và chuỗi, và chữ ký ABI khớp ba nơi. Sửa code iOS xong thì
vẫn phải build trên Mac mới biết.

**Cách đọc cây view trên iPhone** (đã dùng ngày 2026-09-21): chèn tạm một
hàm dump đệ quy vào **bản copy** `ROInFeedAdViewFactory.mm` trong project Xcode
đã export (Unity copy plugin chứ không symlink — không đụng repo), gọi sau
`nativeAdView.nativeAd = _nativeAd;` bằng `dispatch_after` 3/8/15/30s, in class,
`frame`, rect trong window, `hidden`, `autoresizingMask`,
`translatesAutoresizingMaskIntoConstraints`, `clipsToBounds`, `cornerRadius`,
và **mọi constraint mỗi view đang giữ**; lọc console theo một tiền tố. Chụp
ảnh bằng `drawViewHierarchyInRect:` vào `tmp/` của app.

**Test ad trên iOS không mang AdChoices** (unit test `3986624511`: container
AdChoices của SDK luôn `hidden`, 0×0, rỗng). Mọi thứ liên quan AdChoices phải
kiểm bằng ad thật.
