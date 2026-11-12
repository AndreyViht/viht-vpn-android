#!/usr/bin/env bash
set -euo pipefail
apk=admin-app/build/outputs/apk/debug/admin-app-debug.apk
adb install "$apk"
adb shell am start -W -n ru.anviht.admin.test/ru.anviht.admin.AdminActivity
for attempt in {1..15}; do
  adb shell uiautomator dump /sdcard/viht-admin.xml >/dev/null
  if adb shell cat /sdcard/viht-admin.xml | grep -Fq 'Вход администратора'; then exit 0; fi
done
echo 'Admin login screen did not appear'
exit 1
