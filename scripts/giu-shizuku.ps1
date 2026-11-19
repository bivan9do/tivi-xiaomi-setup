# Giữ Shizuku chạy trên tivi để Obtainium tự cập nhật VTV Go.
# Chạy mỗi 30 phút bằng Task Scheduler (tác vụ "TiviTuCapNhat").
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$adb = Join-Path $dir 'platform-tools\adb.exe'
$tv = if ($env:TV_ADDR) { $env:TV_ADDR } else { throw 'Hãy đặt biến TV_ADDR, ví dụ: $env:TV_ADDR = ''192.168.1.50:5555''' }
$log = Join-Path $dir 'nhat-ky.txt'

function Log($msg) {
  $line = "{0:yyyy-MM-dd HH:mm} {1}" -f (Get-Date), $msg
  Add-Content -Path $log -Value $line -Encoding UTF8
  $lines = Get-Content $log -Encoding UTF8
  if ($lines.Count -gt 500) { $lines | Select-Object -Last 300 | Set-Content $log -Encoding UTF8 }
}

& $adb connect $tv | Out-Null
$state = (& $adb -s $tv get-state 2>$null)
if ($state -ne 'device') { Log "Khong ket noi duoc tivi (tat nguon, khac mang, hoac ADB bi tat)"; exit 0 }

$alive = (& $adb -s $tv shell "ps -A | grep -c shizuku_server").Trim()
if ($alive -ne '0') { Log "Shizuku dang chay"; exit 0 }

& $adb -s $tv shell 'P=$(pm path moe.shizuku.privileged.api | sed "s/package://;s/base.apk//"); ${P}lib/arm/libshizuku.so' | Out-Null
Start-Sleep -Seconds 3
$alive = (& $adb -s $tv shell "ps -A | grep -c shizuku_server").Trim()
if ($alive -ne '0') { Log "Da bat lai Shizuku" } else { Log "Bat lai Shizuku THAT BAI" }
