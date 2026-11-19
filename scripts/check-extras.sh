am start -n com.android.tv.settings/.MainSettings >/dev/null 2>&1
input keyevent KEYCODE_HOME
sleep 1.4
screencap -p /data/local/tmp/petal.png
for i in 1 2 3 4 5 6; do input keyevent KEYCODE_DPAD_DOWN; sleep 0.4; done
sleep 1
screencap -p /data/local/tmp/grid.png
uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
grep -o 'text="\(VnExpress\|Tuổi Trẻ\|Dân Trí\|Thanh Niên\)"' /data/local/tmp/ui.xml
input keyevent KEYCODE_HOME
