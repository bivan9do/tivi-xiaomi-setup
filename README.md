# Tivi Xiaomi: bộ cài và tuỳ biến qua ADB

Bộ script, mã nguồn và skill cho Claude để biến một tivi Xiaomi / Android TV thành màn hình chính gọn gàng,
điều khiển hoàn toàn từ máy Windows qua ADB Wi-Fi.

- **Launcher** (sửa từ [freeTV](https://github.com/kienmai160598/freeTV), MIT):
  - Ứng dụng được nhóm theo mục: Xem phim, Đọc báo, Xem bóng đá, Gaming, Cài đặt.
  - Thẻ **Lịch Google**: tivi tự tải lịch qua iCal 6 giờ/lần, không cần máy tính bật.
  - Dải **mức dùng Claude Pro** ở góc màn hình.
- **Ô tin tức**: VnExpress, Tuổi Trẻ, Dân Trí, Thanh Niên.
- **Game chơi bằng remote**:
  - Rắn săn mồi.
  - Lướt đường ray: chạy vô tận 3 làn.
  - Sa hình B số sàn: mô phỏng 11 bài thi sa hình hạng B, chấm 100 điểm, xe số sàn có chết máy, rồ ga, tụt dốc.

## Cấu trúc

```
skills/cai-tivi-xiaomi/SKILL.md   skill cho Claude Code: hướng dẫn cài lại từ đầu
scripts/                          deploy, khởi động lại launcher, đẩy mức dùng Claude, giữ Shizuku, cài công cụ build
launcher/                         mã launcher (freeTV đã sửa)
apps/newslinks/                   ô tin tức (mỗi báo một flavor)
games/                            snake, runner, sahinh
apk/                              APK dựng sẵn của game và ô tin tức
```

## Bắt đầu nhanh

1. Bật **Gỡ lỗi ADB qua mạng** trên tivi, rồi đặt biến địa chỉ tivi:
   ```powershell
   $env:TV_ADDR = '192.168.1.50:5555'
   ```
2. Kết nối và cài một game có sẵn:
   ```powershell
   adb connect $env:TV_ADDR
   adb -s $env:TV_ADDR install -r apk\sa-hinh-b.apk
   ```
3. Muốn launcher: chạy `scripts\setup-build-tools.ps1`, sửa `greeting_name` và `weather_city` trong
   `launcher/apps/launcher/app/src/main/res/values/config.xml`, build `launcher/apps/launcher`, rồi chạy `scripts\deploy.ps1`.

Chi tiết từng bước, các lỗi hay gặp và cách thêm app vào từng mục có trong
[`skills/cai-tivi-xiaomi/SKILL.md`](skills/cai-tivi-xiaomi/SKILL.md).

### Dùng như skill của Claude Code

Chép thư mục `skills/cai-tivi-xiaomi` vào `~/.claude/skills/`. Sau đó chỉ cần nói với Claude những câu như
"cài tivi", "thêm app vào mục Xem bóng đá" hay "cài game lên tivi".

## Riêng tư

Repo không chứa IP tivi, tên chủ nhà, toạ độ hay link lịch iCal. Những thứ đó đặt qua biến môi trường,
qua `config.xml` lúc build, hoặc ghi thẳng vào tivi (`ical.txt`). Đừng commit chúng.

## Giấy phép

MIT. Thư mục `launcher/` giữ nguyên giấy phép MIT của tác giả gốc (Gathin) trong `launcher/LICENSE`.
