am start -n com.gathin.tvhome/.LauncherSettingsActivity 2>&1 | tail -1
sleep 3
uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
grep -o '<node[^>]*text="[^"]\+"[^>]*bounds="[^"]*"' /data/local/tmp/ui.xml | sed 's/.*text="\([^"]*\)".*bounds="\([^"]*\)"/\1 @ \2/' | head -40
