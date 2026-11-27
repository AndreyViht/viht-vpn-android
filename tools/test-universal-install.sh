#!/usr/bin/env bash
set -euo pipefail
# Never uninstall or seed fixtures on a physical/user device.
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = "1"
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "33"
mkdir -p app/build/reports/installability
exec > >(tee app/build/reports/installability/android13-install-upgrade.log) 2>&1
abis=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')
echo "Android 13 device ABIs: $abis"
abi=""
case ",$abis," in
  *,x86,*) abi=x86 ;;
  *,armeabi-v7a,*) abi=armeabi-v7a ;;
esac
echo "Installation ABI override: ${abi:-system-default (no 32-bit ABI advertised)}"
apk=app/build/outputs/apk/debug/app-debug.apk
previous=app/build/previous-universal-22.apk
curl --fail --location --retry 3 --max-time 180 -o "$previous" https://anviht.ru/download/VihtVPN-Android-2.0.0.22.apk
echo "1d4aaa88daa298141047739191b136135b1104a72a713dad31190d875bcaa125  $previous" | sha256sum --check
install_apk() {
  local mode=$1 file=$2
  local args=(adb install)
  if [ "$mode" = update ]; then args+=(-r); fi
  if [ -n "$abi" ]; then args+=(--abi "$abi"); fi
  args+=("$file")
  "${args[@]}"
}
run_test() {
  local class=$1
  local out
  local args=(adb shell am instrument -w -r)
  if [ -n "$abi" ]; then args+=(--abi "$abi"); fi
  args+=(-e installScenario true -e class "$class" ru.anviht.vpn.test.test/androidx.test.runner.AndroidJUnitRunner)
  out=$("${args[@]}")
  printf '%s\n' "$out"
  grep -F 'OK (1 test)' <<< "$out"
  if grep -F 'INSTRUMENTATION_STATUS_CODE: -3' <<< "$out"; then return 1; fi
}
adb uninstall ru.anviht.vpn.test
install_apk fresh "$apk"
run_test com.pingwin.vpn.InstallationUpgradeTest#finalApkInstallsAndActivityStarts
if [ -n "$abi" ]; then
  run_test com.pingwin.vpn.Native32BitInstallTest
fi
adb uninstall ru.anviht.vpn.test
install_apk fresh "$previous"
run_test com.pingwin.vpn.InstallationUpgradeTest#writeOldVersionMarker
install_apk update "$apk"
run_test com.pingwin.vpn.InstallationUpgradeTest#upgradePreservesExistingAppData
run_test com.pingwin.vpn.InstallationUpgradeTest#finalApkInstallsAndActivityStarts
echo 'PASS: final universal APK fresh install, native startup and signed update with preserved data'
