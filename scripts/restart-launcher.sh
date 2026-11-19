am force-stop com.gathin.tvhome
settings put secure enabled_accessibility_services ''
settings put secure accessibility_enabled 0
am start -n com.gathin.tvhome/.MainActivity >/dev/null
sleep 5
settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
settings put secure accessibility_enabled 1
sleep 3
dumpsys accessibility | grep -E 'Bound services' | cut -c1-50
uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
grep -o 'text="Good[^"]*"' /data/local/tmp/ui.xml | head -3
grep -o 'text="[^"]*Kien[^"]*"' /data/local/tmp/ui.xml | head -3
