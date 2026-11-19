$d = "$env:LOCALAPPDATA\TiviTuCapNhat"
$adb = "$d\platform-tools\adb.exe"
$tv = if ($env:TV_ADDR) { $env:TV_ADDR } else { throw 'Hãy đặt biến TV_ADDR, ví dụ: $env:TV_ADDR = ''192.168.1.50:5555''' }
$f = '/sdcard/Android/data/com.gathin.tvhome/files'
$tz = [TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
function Local($iso) { [TimeZoneInfo]::ConvertTimeFromUtc([datetime]::Parse($iso).ToUniversalTime(), $tz).ToString('HH:mm dd/MM') }

# Claude usage is no longer baked into the wallpaper: the launcher draws it from usage.json (push-usage.ps1).

& $adb connect $tv | Out-Null
& $adb -s $tv install -r "$d\km-launcher-adb.apk"
foreach ($a in Get-ChildItem "$d\build\newslinks\app\build\outputs\apk" -Recurse -Filter *.apk) { "$($a.Name): " + ((& $adb -s $tv install -r $a.FullName) -join ' ') }
& $adb -s $tv push "$d\wallpaper-rung-thong.jpg" /data/local/tmp/wallpaper.jpg | Out-Null
& $adb -s $tv shell "pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS; cp /data/local/tmp/wallpaper.jpg $f/wallpaper.jpg; rm /data/local/tmp/wallpaper.jpg"
$s = ([IO.File]::ReadAllText("$d\restart-launcher.sh")) -replace "`r", ''
$s | & $adb -s $tv shell sh
Start-Sleep -Seconds 20
& $adb -s $tv shell "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 3; settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1; sleep 4; dumpsys accessibility | grep -E 'Bound services' | cut -c1-50; input keyevent KEYCODE_DPAD_UP; input keyevent KEYCODE_DPAD_UP; sleep 2; screencap -p /data/local/tmp/s.png"
& $adb -s $tv pull /data/local/tmp/s.png "$d\home-new.png" 2>&1 | Out-Null
