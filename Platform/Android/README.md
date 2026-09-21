# RiseOn.NativeAdMob — Android

Bridge Java của pack: `Bridge/NativeAdMob.androidlib` (package
`com.riseon.nativeadmob`), gọi từ C# qua `AndroidJavaObject` trong
`InFeedAdClient` / `OverlayAdClient` / `CoverPlatform`, nghe lại qua
`AndroidJavaProxy` trong `Bridge/*Proxy.cs`. Luật chung của pack ở
[README gốc](../../README.md); file này chỉ ghi những gì **riêng Android**.

## 1. androidlib

- `build.gradle`: `compileOnly "com.google.android.gms:play-services-ads:25.3.0"`
  — app đã có GMA qua plugin Unity, `compileOnly` để **không đóng gói trùng**.
  Đổi phiên bản GMA của host thì đổi dòng này theo. `minSdk 24`, `compileSdk
  34`, `sourceCompatibility 1.8` → **không dùng cú pháp sau Java 8** dù cổng
  javac local có thể chạy với `-source 11`.
- `AndroidManifest.xml` khai hai Activity; mỗi thuộc tính đều có lý do ghi
  ngay trong file — đọc comment trước khi đổi. Hai điểm hay bị "sửa cho gọn":
  - **Không có `android:screenOrientation` trên `OverlayAdActivity`.** Android
    8.0 (đúng API 26) ném "Only fullscreen opaque activities can request
    orientation" từ `super.onCreate` cho Activity translucent khai orientation
    cố định — crash trước khi code của ta chạy, 7 người dùng dính (9164a0b0).
    Orientation xin ở runtime trong `onCreate`, **bỏ qua API 26** (ở đó
    Activity translucent theo orientation của Activity đục bên dưới — game
    portrait — nên vẫn portrait).
  - **Không `android:noHistory` trên `FullScreenCoverActivity`.** Cờ đó
    finish Activity ngay khi nó stop, mà ad Activity mở lên trên làm nó stop —
    màn che sẽ chết đúng lúc đang che vết nối và mất khi ad đóng. `finish()`
    quyết vòng đời.
- `styles.xml`: theme của màn che full là **ĐỤC**, không translucent.
  `Theme.Translucent` bảo WindowManager rằng window có thể không phủ hết bên
  dưới → Activity dưới được giữ VISIBLE và composite ở dưới → mọi khoảnh khắc
  window chưa vẽ xong, compositor lấp bằng frame cuối của Unity (game lộ qua
  màn che). Theme này từng thừa hưởng từ ad Activity (ad half cần translucent
  vì không phủ hết màn). `windowBackground` đen để sơn trước khi content lay
  out; `windowAnimationStyle @null` để lần trở lại foreground không fade.

## 2. Luồng

- Mọi lệnh từ C# hop về main thread qua `BaseAd.RunOnMainThread` /
  `BaseCover.MAIN` (Handler main looper, không phải `Activity.runOnUiThread`
  — cái đó không có gì để post khi Activity null và bản cũ chạy action trên
  thread gọi: thao tác window trên thread Unity là crash chờ sẵn).
- **Mọi `Notify*` đi qua `AdEventDispatcher.Post`** — một `HandlerThread`
  `RiseOnNativeAdMobEvents`. Lý do: listener là C# sau `AndroidJavaProxy`,
  gọi nó là bước vào IL2CPP; bước vào IL2CPP có thể **block chừng nào player
  còn pause** (handshake stop-the-world của GC không xong khi Unity ngủ dưới
  màn che / ad). Đo trên production 2026-08: năm cụm ANR, mỗi cụm là ảnh
  chụp main thread parked ở GC suspend point trong `OnAdCompleted` /
  `OnStateChanged` ("Input dispatching timed out"). Không sửa được cái stall
  từ tầng này; sửa được **ai bị stall**: main thread không bao giờ được đứng ở
  cửa đó. Tham số chụp vào local **trước** khi post; một queue giữ thứ tự.
  **Luật cho code mới: không gọi listener trực tiếp từ bất kỳ thread nào —
  luôn `AdEventDispatcher.Post`.**
- Callback SDK (paid event, `onAdClicked`, load) rơi trên thread SDK; cấu
  trúc mutable của unit sống trên main → membership test dùng
  `ConcurrentHashMap.newKeySet()` (`OwnsAd`), không walk deque.
- Mọi lệnh từ C# **mang `AndroidApplication.currentActivity`** (Unity giữ
  static, gọi được từ thread nào cũng rẻ). Activity đổi = window cũ chết:
  in-feed bỏ mọi thứ đã materialize, chỉ cache thô sống sót.
- `AndroidJavaProxy` dispatch theo **tên method**: proxy C# phải đặt đúng tên
  method của interface Java (`InFeedAdListener`, `NativeAdLoadListener`,
  `NativeAdCompletedListener`). Sai tên là nuốt lặng, không có lỗi.

## 3. Ad full-screen là Activity — yêu cầu cứng

- **Không được đổi sang Dialog.** Bản Dialog (31bcbd2a) đã bị revert
  (dfc1cab2) theo yêu cầu chủ dự án. Điều tra delay cho thấy thứ phình ra
  không phải Activity switch mà là **bind creative trong `onCreate`** (video
  end card khởi tạo player). Fix giữ Activity (4e4d73d4): `OverlayAdContentView`
  **dựng sẵn lúc LOAD**, truyền qua `Session` (kèm `CloseRelay` và
  `closeOnLeft` đã roll), `onCreate` chỉ attach; restore path dựng tươi. Log
  `Show timeline: content attached/presented +Xms (prebuilt: yes/no)` tag
  `OverlayAd` để đo (đã đo: launch ~167ms).
- **Background activity launch (Android 10+)**: `startActivity` từ app đang ở
  nền **thành công như một lời gọi và bị hệ thống vứt lặng**. Kịch bản thật
  (76fa0612): người chơi tap xuyên ad vào trình duyệt → interstitial tự đóng
  sau lưng trình duyệt → start end card đi vào hư không → session treo sau
  màn che đen vĩnh viễn ("game đen sau khi quay lại"). Luật: **một session đã
  start thì hoặc attach, hoặc được restore khi resume, hoặc complete với lỗi —
  không bao giờ treo.** `StartSession` post `VerifyInitialAttach` sau
  `RESTORE_ATTACH_TIMEOUT_MS` (1200ms): chưa có Activity → `restorePending`,
  giao cho máy restore (host ở nền → watcher `onActivityResumed` mở lại end
  card khi người chơi quay về; host đang trước → vòng restore thử lại
  `MAX_RESTORE_START_ATTEMPTS` 4 lần cách 180ms, chờ focus tối đa 300×100ms,
  rồi complete lỗi). Chủ dự án chốt **mở lại end card khi quay về** (không
  bỏ qua).
- Back: hai cơ chế vì hai thời kỳ — `onBackPressed` (cũ) và callback no-op
  đăng ký cho predictive back (`enableOnBackInvokedCallback`). Màn che nuốt
  back **có chủ ý**; chỉ caller quyết khi nào nó hạ.
- `excludeFromRecents`, `launchMode standard` (ad) / `singleTop` (màn che:
  đã lên thì repaint chứ không xếp cái thứ hai), `configChanges` đầy đủ để
  xoay/resize không recreate.
- Pause: Activity trên cùng làm hệ thống pause Unity phía dưới. **Không dòng
  nào trong pack gọi `UnityPlayer.pause()`.** `Hide` của màn che là JNI post
  sang main looper — sống khi Unity dừng; điều kiện là lời gọi đến từ thread
  còn chạy (README gốc B4).

## 4. Window type và thang xếp lớp

| Tầng | Cơ chế | Type |
|---|---|---|
| in-feed | window trên Unity Activity (`InFeedAdPresentation`) | `TYPE_APPLICATION_PANEL` (1000) |
| half-screen ad | Dialog window (`OverlayAdPresentation`) | `TYPE_APPLICATION_SUB_PANEL` (1002) |
| half-screen cover | Dialog window (`HalfScreenCover`) | `TYPE_APPLICATION_SUB_PANEL` (1002) |
| full-screen ad / cover | Activity | — |

Window không có index như subview — `WindowManager` không có API đổi z-order.
Chỉ hai đòn bẩy: **type**, và thứ tự `addView` trong cùng type. Đòn bẩy thứ
hai vô dụng vì muốn leo lên phải gỡ window ra thêm lại (flicker, mà cũng chỉ
tới được *đỉnh* của type chứ không chèn xuống dưới được). Ad half và màn che
half **cùng** type 1002 → thứ tự giữa chúng là **hợp đồng của người gọi: bật
màn che trước, mở ad sau**.

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

### Hợp đồng system UI

Mọi window của pack **lay out DƯỚI navigation bar và cutout, ẩn bar kiểu
sticky** — đúng hợp đồng bề mặt của game. Window ad half từng là thứ duy nhất
trên màn hình tôn trọng bar: vuốt bar ra khỏi auto-hide, quay từ Recents →
WindowManager đậu panel LÊN TRÊN bar, dải trắng chỗ nút, cả ad bị xô lên, tới
lần chạm sau bar ẩn lại thì panel rơi xuống. Đo trên Samsung ba nút,
2026-08-21. Slot in-feed đặt bằng px tuyệt đối game tính trên bề mặt full
màn; một window né bar là một ô không còn nằm chỗ game đặt.

### Chiều cao panel half

**Một** nơi quyết: `resolveHalfScreenPanelHeightForRatio` trong
`OverlayAdContentView`; `HalfScreenCover` gọi đúng hàm đó. Đo riêng bằng
`decorView.getHeight()` bằng `displayMetrics.heightPixels` trên giả lập
1080×1920 và **khác** trên máy có cutout/gesture bar — cả hai window ở
`Gravity.BOTTOM`, cái cao hơn thừa ra thành dải đen trên ad. Window half là
**short window**, không phải window full với đỉnh trong suốt: dải trên không
nằm trong input region nên game vẫn nhận chạm.

## 5. RedirectOnClose: hook nào bắn ở đâu

- Cú chạm được **thả xuyên** xuống `NativeAdView` (touch listener trả `false`
  ở DOWN) — SDK thấy ngón tay thật trên view của họ, tự đếm, tự redirect.
  `performClick()` từng ngồi đó và không làm gì: nó bắn `OnClickListener`
  không có `MotionEvent`, SDK đếm touch chứ không đếm lời gọi.
- `onWindowFocusChanged(false)` chỉ phủ **đường Activity** (full-screen).
  Từng tin rằng "view nhận focus change dù nằm window nào" — **sai** cho đúng
  window quan trọng: `OverlayAdPresentation` đặt `FLAG_NOT_FOCUSABLE` cho
  window half, mà window không bao giờ nhận focus thì không bao giờ báo mất
  focus. Đo: cả phiên show half, hook đó bắn đúng một lần, cho một ad
  full-screen. Đường half do `onWindowVisibilityChanged` gánh: trình duyệt
  chiếm màn → host Activity stop → mọi window trên token đó invisible.
- Chọn hook view chứ không phải `Activity.onPause` vì collapsible sống trong
  Dialog trên Unity Activity, không có onPause riêng. Nhánh `OnPaused` vẫn
  giữ — thừa chứ không thay thế, phòng full-screen được bật `RedirectOnClose`.
- Countdown đóng băng khi window mất visibility (Dialog không có Activity
  pause riêng; visibility thì window nào cũng có).
- Latch chống double-click (`SingleClickNativeAdContainer`) nhả theo
  **visibility**, không theo focus, cùng lý do.

## 6. Presentation in-feed

- Giữ tham chiếu `ViewTreeObserver` đã đăng ký: `getViewTreeObserver()` trả
  observer của window khi còn attach và một observer nổi **khác** sau khi
  detach; remove nhầm instance là no-op lặng → presentation, ad, view tree và
  Activity sống mãi trong callback list của window.
- Vòng quan sát hình học chỉ tiến trên **draw pass**, mà Android bỏ
  `invalidate()` trên view chưa VISIBLE → phải drive decor view của window
  host, không thì vòng lặp đứng tới khi thứ gì đó tình cờ vẽ lại.
- Chờ root sẵn sàng có giới hạn (`MAX_ROOT_WAIT_MS` 10s) và kết thúc như một
  dismissal bình thường, không được park slot (park là chặn mọi load sau).
  Layout cuối phải ổn định 3 pass, quan sát 80–500ms.
- Backdrop bo góc kiêm clip (`setOutlineProvider(BACKGROUND)` +
  `setClipToOutline(true)`); màu trong suốt vẫn clip.
- **Không measure-thăm-dò + `requestLayout` giữa layout pass** mà không có
  khoá idempotent: Android vứt `requestLayout` giữa pass và GMA lay cột ở cỡ
  thăm dò (c335fdc4). Bản sửa `post()` từ `onSizeChanged` sau đó bị thay:
  overlay giờ lập kế hoạch né control **trong `onMeasure`** theo kích thước
  thật, guard bằng `plannedPanelWidth/Height` + `avoidancePlanDirty`, và
  `planningInMeasure` chặn `requestLayout` thừa — vì `displayMetrics.heightPixels`
  có OEM đã trừ cutout, có OEM chưa, nên kế hoạch đoán trước rồi sửa sau frame
  đầu là một cú nhảy nhìn thấy (README gốc B6).

## 7. AdChoices

**Dấu AdChoices là của SDK và nằm trong lớp của SDK.** Cây view của ô in-feed
đo trên máy (2026-09-21, Oppo CPH2121, ô 602×234px):

```
NativeAdView
├─ FrameLayout            nội dung của ta (ảnh nền, veil, chữ, nút, badge "Ad", reserve)
└─ FrameLayout (zza)      lớp của SDK, full-size
   └─ RelativeLayout
      └─ "Ad Choices Icon" 45×45px, ghim sát góc trên-phải của lớp này
```

Bytecode `play-services-ads-api` 25.3.0 xác nhận: `NativeAdView extends
FrameLayout`, **không** override `onMeasure`/`onLayout`/`setPadding`; lớp
`zza` được tạo trong `zze(Context)` là `FrameLayout` thường với
`LayoutParams(MATCH_PARENT, MATCH_PARENT)` không margin; override `addView`
và `bringChildToFront` chỉ để giữ `zza` luôn ở trên cùng.

**Đã thử và sai — đừng thử lại:** đăng ký `AdChoicesView` của ta qua
`setAdChoicesView` (trước `setNativeAd`). Bytecode chỉ cho thấy hàm đó đăng ký
view dưới asset "3011"; không có gì nói SDK sẽ vẽ dấu vào đó. Trên máy: SDK
nhận view (gắn listener, view thành bấm được) nhưng **vẫn vẽ dấu ở lớp `zza`,
sát góc thô**; view của ta trống, thành một ô vô hình bấm được. Đã gỡ.

**Cách đang dùng** — `InFeedAdViewFactory.InsetSdkOverlay`, gọi trong
`BuildNativeAdView` trước khi `addView` nội dung:
- `nativeAdView.setPadding(inset ×4)` với `inset = BadgeEdgeInsetPx()` — đúng
  giá trị badge "Ad" dùng, nên hai dấu góc luôn thụt bằng nhau. Lớp `zza`
  (MATCH_PARENT) co vào `inset`, dấu theo vào.
- Nội dung của ta: `LayoutParams` MATCH_PARENT với **margin âm = −inset** →
  FrameLayout đặt nó ở `padding + margin = 0`, đo với `padding + margin = 0`
  → phủ kín ô **đúng từng pixel như cũ**: cùng bounds, cùng layout, validator
  đo cùng số (nó đo tương đối `NativeAdView` bằng
  `offsetDescendantRectToMyCoords`).
- `setClipToPadding(false)`: không thì dải giữa padding và mép ô (mép ảnh
  nền scrim) bị cắt. Cung bo vẫn do outline của presentation cắt như cũ.
- `inset == 0` (ô vuông) → return sớm, không đổi gì.

Rủi ro duy nhất: cách này dựa vào việc `zza` là con MATCH_PARENT của
`NativeAdView`. Nâng GMA thì kiểm lại bytecode `zze` và cây view.

Hộp dấu 45px (cố định 15dp) cao hơn dải trên cùng ta chừa cho badge (~31px)
nên lấn vào dòng headline — có từ trước fix; validator không thấy vì nó đo
reserve (23px) chứ không đo dấu thật. `OverlayAdContentView` vẫn dùng ô giữ
chỗ trơn (overlay không bo góc nên chưa lộ).

## 8. Cổng javac — bắt buộc sau mọi sửa Java

Hai lần build Android của chủ dự án vỡ ở javac (illegal forward reference +
definite assignment trong `InFeedAdSlot`; escape `\|` bị shell nuốt một
backslash). Công thức, chạy được không cần Android Studio:

```
javac  = <Unity>/Editor/Data/PlaybackEngines/AndroidPlayer/OpenJDK/bin/javac.exe
cp     = <Unity>/Editor/Data/PlaybackEngines/AndroidPlayer/SDK/platforms/android-36/android.jar
       ; ~/.gradle/caches/**/jetified-play-services-ads-api-2*/jars/classes.jar   (bản mới nhất)
       ; ~/.gradle/caches/**/jetified-play-services-ads-2*/jars/classes.jar
javac -source 11 -target 11 -Xlint:-options -cp <cp> -d <out> <mọi .java trong androidlib>
```

Phiên bản trong gradle cache đổi theo host (25.4.0 từng bị evict) → glob,
đừng ghim. Gradle thật biên dịch với `1.8` nên code phải là Java 8. Sau khi
build, kiểm build đã chứa code mới:

```
grep -a "<symbol>" Library/Bee/Android/Prj/IL2CPP/Gradle/unityLibrary/NativeAdMob.androidlib/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes/com/riseon/nativeadmob/*.class
```

## 9. Debug trên máy

- `adb` **không** có trong PATH của shell nền → đường dẫn tuyệt đối
  `%LOCALAPPDATA%/Android/platform-tools/adb`. Hai thiết bị thường cắm cùng
  lúc (máy thật density 3.0, giả lập 1.5) → **luôn truyền `-s <serial>`**,
  thiếu là adb trả rỗng. Ô 288×361px = 96×120dp trên máy thật, 192×241dp
  trên giả lập: khác biệt layout giữa hai máy thường là dp, không phải bug.
- Log: `logcat -v time -s OverlayAd:V InFeedAd:V NativeAd:V` chạy **nền ra
  file** — buffer bị AppLovin spam đẩy trôi trong 1–2 phút nên `logcat -d`
  hay trượt. Dòng cần tìm: `In-feed layout ready <plan>` (template/tier/
  media/icon/body/advertiser/rating/textx/marq), `Scrim geometry:`,
  `Side-media layout:`, `padding a/b/c of x`, `Show timeline:`, lý do reject
  của validator (`registered assets overlap`, `... is clipped`).
- `adb shell dumpsys window windows` — thứ tự window từ trên xuống và khung
  (`mFrame`) của từng window; ô in-feed là window `ty=APPLICATION_PANEL`,
  ad/cover half là `APPLICATION_SUB_PANEL`.
- **Cây view thật của ad đang hiện — ăn đứt screenshot:**
  `adb shell uiautomator dump --windows /data/local/tmp/ui.xml` rồi `adb pull`.
  Chỉ đọc, không chạm gì. Cho class, `bounds` px tuyệt đối, text,
  `content-desc` (dấu AdChoices của SDK có desc `Ad Choices Icon`) của
  **mọi window**.
  **Không dùng `dumpsys activity top` cho in-feed hay half-screen**: nó chỉ
  dump cây view của chính Activity, không có window con — và trên máy Oppo
  này còn không dump cả Activity của game. Nó chỉ dùng được cho ad full-screen
  (vì đó là Activity).
- Git Bash đổi đường dẫn dạng `/data/...` thành `C:/Program Files/Git/data/...`
  trước khi đưa cho adb → đặt `MSYS_NO_PATHCONV=1` khi gọi adb với đường dẫn
  phía máy.
- `dumpsys activity activities` cho `mResumedActivity` khi nghi màn đen.
- Không bao giờ `adb shell input` lên thiết bị; không bao giờ tap vào nội
  dung quảng cáo.
