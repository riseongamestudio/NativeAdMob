# Lịch sử thay đổi

Mọi thay đổi đáng kể của `com.riseon.nativeadmob` được ghi ở đây. Định dạng theo
[Keep a Changelog](https://keepachangelog.com/vi/1.1.0/), đánh số theo
[Semantic Versioning](https://semver.org/lang/vi/).

## [1.0.3] - 2026-09-25

### Sửa

- Assembly nền tảng `RiseOn.NativeAdMob.Android` và `RiseOn.NativeAdMob.iOS`
  bị UnityLinker bỏ khỏi bản build khi pack được cài dạng package, vì không
  assembly nào tham chiếu tới chúng. Bootstrap của nền tảng không bao giờ chạy
  nên không ad nào hiện được. Cả hai giờ khai `[assembly: AlwaysLinkAssembly]`.
  Bản nằm trong `Assets/` không bị, vì Unity không cắt assembly của project.

## [1.0.2] - 2026-09-23

### Thay đổi

- Tổ chức lại thư mục theo bố cục chuẩn của package Unity: `Core/` thành
  `Runtime/`, `Platform/<Nền tảng>` thành `Runtime/Platforms/<Nền tảng>`
  (Android, iOS, Editor). Tên assembly, namespace và GUID không đổi, nên project
  đang dùng không phải sửa gì.
- `LICENSE` đổi tên thành `LICENSE.md` theo bố cục chuẩn của package Unity (nút
  Licenses của Package Manager tìm file này).
- README gốc rút gọn còn giới thiệu, cài đặt và hướng dẫn nhanh; tài liệu chi
  tiết (kiến trúc, luật chung, vận hành) chuyển sang `Runtime/README.md`.

### Thêm

- `CHANGELOG.md`.
- `documentationUrl` trong `package.json`, trỏ tới README của đúng version trên GitHub.

## [1.0.1] - 2026-09-21

### Thay đổi

- `package.json`: thêm `author` "RiseOn", `displayName` đổi thành "Native AdMob".

## [1.0.0] - 2026-09-21

Bản phát hành đầu tiên dưới dạng package UPM.

### Thêm

- `FullScreenAd`, `HalfScreenAd` (collapsible) và `InFeedAd` cho native ad của
  AdMob, cùng hai màn che `FullScreenCover`, `HalfScreenCover`.
- Hiện thực cho Android (Java) và iOS (Obj-C++), bản xem trước trong Editor.
- Giấy phép MIT.
