---
name: cai-tivi-xiaomi
description: Cài và tuỳ biến tivi Xiaomi / Android TV qua ADB Wi-Fi từ máy Windows — launcher freeTV (km-launcher) có widget lịch Google, dải mức dùng Claude, nhóm ứng dụng theo mục (Xem phim, Đọc báo, Xem bóng đá, Gaming, Cài đặt), ô báo tin tức, và các game chơi bằng remote (Rắn săn mồi, Lướt đường ray, Sa hình B số sàn). Dùng khi người dùng nói "cài tivi", "cài launcher cho tivi", "thêm app vào mục ... trên tivi", "đưa lịch lên tivi", "cài game lên tivi", "tivi Xiaomi", hoặc cần sửa/cài lại bộ này.
---

# Cài tivi Xiaomi bằng ADB

Bộ này nằm trong repo `tivi-xiaomi-setup`. Mọi đường dẫn bên dưới tính từ gốc repo.
Máy làm việc là Windows. Mặc định thư mục làm việc trên máy là `%LOCALAPPDATA%\TiviTuCapNhat`
(adb ở `platform-tools\adb.exe`, công cụ build ở `build\`), các script trong `scripts/` dùng đường dẫn đó.

## 0. Chuẩn bị

1. Trên tivi: Cài đặt → Giới thiệu → bấm "Bản dựng" 7 lần → Tuỳ chọn nhà phát triển → bật **Gỡ lỗi USB** và **Gỡ lỗi ADB qua mạng**.
2. Lấy IP tivi (Cài đặt → Mạng). Đặt biến cho phiên PowerShell: `$env:TV_ADDR = '192.168.x.y:5555'`.
3. Tải Android platform-tools (trang chính thức của Google) vào `%LOCALAPPDATA%\TiviTuCapNhat\platform-tools`.
4. `adb connect $env:TV_ADDR` — lần đầu tivi hiện hộp thoại cho phép, chọn **Luôn cho phép**.
5. Công cụ build (JDK 17 + Android SDK 34): chạy `scripts/setup-build-tools.ps1` một lần.
   Khi build: `JAVA_HOME=build\jdk`, `ANDROID_HOME=build\sdk`, `GRADLE_USER_HOME=build\gradle`, rồi `& .\gradlew.bat assembleRelease` (thêm `--offline` sau lần đầu).

Không bao giờ đưa IP tivi, tên người dùng, link iCal bí mật hay toạ độ nhà vào repo hay vào tin nhắn gửi dịch vụ ngoài.

## 1. Launcher (`launcher/`, gốc là freeTV, giấy phép MIT)

- Mã app ở `launcher/apps/launcher`, package `com.gathin.tvhome`.
- Cá nhân hoá trước khi build: `res/values/config.xml` → `greeting_name` (lời chào), `weather_city` (`@vĩ độ,kinh độ|Tên nơi`).
- Build rồi cài: `adb -s $env:TV_ADDR install -r <apk>`.
- **Sau mỗi lần cài lại launcher** (bản release không debuggable, `run-as` không dùng được):
  1. `adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS`
  2. chạy `scripts/restart-launcher.sh` qua `adb shell sh` (đổi CRLF thành LF trước)
  3. bật lại dịch vụ trợ năng nút Home:
     `settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 3; settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1`
     Kiểm tra `dumpsys accessibility | grep "Bound services"` phải có "Home button".
- `scripts/deploy.ps1` gói cả quy trình (cài launcher, ô tin tức, hình nền, khởi động lại).
- Lỗi R8 "classes.dex being used by another process": chạy `gradlew --stop` rồi build lại.
  Build có thể hỏng âm thầm nếu nuốt output — luôn kiểm tra `BUILD SUCCESSFUL` và giờ sửa của APK.

### Nhóm ứng dụng theo mục

`launcher/apps/launcher/app/src/main/java/com/gathin/tvhome/Groups.java`: `TITLES` là tên mục, `PACKAGES` là danh sách
package theo đúng thứ tự. App không có trong mục nào rơi vào "Khác". Mỗi hàng 6 ô (1 ô tiêu đề + 5 app);
hàng tràn để trống cột tiêu đề để phím xuống trên remote không bị kẹt.
Tìm package: `adb shell pm list packages -3`; tên hiển thị: kéo APK về rồi `aapt dump badging` (trong `build\sdk\build-tools`).

### Widget lịch Google (thẻ "Lịch Google" trên màn hình chính)

Tivi tự tải lịch, không cần máy tính bật:
1. Lấy "Địa chỉ bí mật ở định dạng iCal" trong cài đặt Google Calendar (người dùng tự dán, coi là bí mật).
2. Ghi đúng một dòng URL đó vào `ical.txt`, đẩy qua `/data/local/tmp/` rồi `cp` sang
   `/sdcard/Android/data/com.gathin.tvhome/files/ical.txt`.
3. `AgendaFeed.java` tải 6 giờ/lần (thử lại 30 phút nếu lỗi), mở rộng lịch lặp 7 ngày tới.
4. Thêm thẻ trên tivi: giữ OK trên dãy widget → "Thêm widget" → Lịch Google.

### Dải mức dùng Claude Pro (góc trên phải)

Launcher đọc `files/usage.json`. `scripts/push-usage.ps1 -Five <%> -FiveReset <ISO> -Weekly <%> -WeeklyReset <ISO>` ghi và đẩy file.
Lên lịch mỗi 3 giờ bằng một scheduled task của Claude: lấy số liệu bằng công cụ get_usage rồi gọi script.
Không có file thì dải tự ẩn.

## 2. Ô tin tức (`apps/newslinks/`)

Một app nhiều flavor (vnexpress, tuoitre, dantri, thanhnien), mỗi flavor là một ô mở trang báo.
Banner tạo bằng `make-banners.ps1`. Thêm báo: thêm flavor trong `app/build.gradle` và một dòng trong `make-banners.ps1`.

## 3. Game chơi bằng remote (`games/`)

| Thư mục | Package | Game |
|---|---|---|
| `games/snake` | `vn.trochoi.ransan` | Rắn săn mồi |
| `games/runner` | `vn.trochoi.luotray` | Lướt đường ray (chạy vô tận 3 làn, tự thiết kế) |
| `games/sahinh` | `vn.trochoi.sahinh` | Sa hình B số sàn (11 bài thi, chấm 100 điểm) |

Có sẵn APK trong `apk/`. Mỗi game có `make-banner.ps1` — **lưu file .ps1 dạng UTF-8 có BOM**,
nếu không PowerShell 5.1 đọc sai chữ tiếng Việt. Thêm package vào mục "Gaming" trong `Groups.java`.
Không làm lại game có bản quyền (tên, nhân vật, hình ảnh); làm game cùng thể loại với thiết kế riêng.

## 4. Giữ Shizuku sống (Obtainium tự cập nhật app)

`scripts/giu-shizuku.ps1` kiểm tra và bật lại `shizuku_server`. Chạy 30 phút/lần bằng Task Scheduler.

## Mẹo và lỗi hay gặp

- `adb shell input keyevent` trên tivi này trễ khoảng 2 giây mỗi lệnh: kiểm tra bằng `screencap`, `uiautomator dump`, logcat,
  đừng thử chơi game qua adb.
- Chụp màn hình: `screencap -p /data/local/tmp/s.png` rồi `adb pull`.
- Cốc Cốc bỏ qua intent VIEW: muốn mở link thì gõ vào thanh địa chỉ (`input text`).
- Trong Git Bash, `adb pull /data/...` bị đổi đường dẫn: thêm `MSYS_NO_PATHCONV=1`.
- `scripts/diag-boot.sh` chẩn đoán ADB/Shizuku sau khi tivi khởi động lại; `scripts/open-settings.sh` mở trang cài đặt launcher.
