# Writes usage.json (Claude Pro usage) and pushes it to the TV launcher, which redraws the top-right strip within a minute.
# Example: push-usage.ps1 -Five 22 -FiveReset 2026-11-19T02:00:00Z -Weekly 22 -WeeklyReset 2026-11-25T11:00:00Z
param(
  [Parameter(Mandatory)][int]$Five,
  [string]$FiveReset = '',
  [Parameter(Mandatory)][int]$Weekly,
  [string]$WeeklyReset = ''
)
$d = "$env:LOCALAPPDATA\TiviTuCapNhat"
$adb = "$d\platform-tools\adb.exe"
$tv = if ($env:TV_ADDR) { $env:TV_ADDR } else { throw 'Hãy đặt biến TV_ADDR, ví dụ: $env:TV_ADDR = ''192.168.1.50:5555''' }
$f = '/sdcard/Android/data/com.gathin.tvhome/files'
$json = [ordered]@{
  five = $Five; fiveReset = $FiveReset; weekly = $Weekly; weeklyReset = $WeeklyReset
  updated = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
} | ConvertTo-Json -Compress
[IO.File]::WriteAllText("$d\usage.json", $json, (New-Object Text.UTF8Encoding $false))
& $adb connect $tv | Out-Null
& $adb -s $tv push "$d\usage.json" /data/local/tmp/usage.json | Out-Null
& $adb -s $tv shell "cp /data/local/tmp/usage.json $f/usage.json && rm /data/local/tmp/usage.json && cat $f/usage.json"
