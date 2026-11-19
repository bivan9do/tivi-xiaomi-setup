echo "=== uptime"; cut -d' ' -f1 /proc/uptime
echo "=== props"; getprop persist.adb.tcp.port; getprop service.adb.tcp.port; getprop sys.usb.config; getprop init.svc.adbd; settings get global adb_enabled
echo "=== shizuku"; ps -A | grep shizuku_server || echo SHIZUKU_DEAD
echo "=== launcher start"; logcat -d -b events | grep -E "am_proc_start.*gathin|wm_set_resumed_activity.*gathin" | head -5 | cut -c1-160
echo "=== adb log"; logcat -d | grep -iE "adb_enabled|AdbService|AdbDebugging|adbd|ADB_ENABLED|keep-adb|setAdbEnabled|persist.adb|tcp.port" | head -40 | cut -c1-200
echo "=== settings writers"; logcat -d | grep -iE "SettingsProvider.*adb|adb.*Settings" | head -10 | cut -c1-200
