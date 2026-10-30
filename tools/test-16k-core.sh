#!/usr/bin/env bash
set -uo pipefail
test "$(adb shell getconf PAGE_SIZE | tr -d '\r')" = "16384" || exit 1
adb logcat -c
if ./gradlew connectedDebugAndroidTest --console=plain --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=com.pingwin.vpn.CoreStartupTest#actualCoreStartsAndroidDnsAndProxyConfiguration -PciVersionCode=$((1000 + GITHUB_RUN_NUMBER)); then
  exit 0
else
  adb logcat -d -v brief > app/build/outputs/androidTest-results/16kb-logcat.txt
  tail -200 app/build/outputs/androidTest-results/16kb-logcat.txt
  exit 1
fi
