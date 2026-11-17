#!/usr/bin/env bash
# Read-only collection. No permissions/roles/settings are granted or changed.
set -euo pipefail
DEST=${1:?Usage: collect_device.sh NEW_OUTPUT_DIRECTORY}
if [[ -e "$DEST" ]]; then printf 'Refusing to replace existing evidence: %s\n' "$DEST" >&2; exit 1; fi
mkdir -m 700 "$DEST"
adb get-state > "$DEST/adb-state.txt"
for property in ro.product.model ro.product.cpu.abi ro.build.version.release ro.build.version.sdk ro.build.version.security_patch; do
  adb shell getprop "$property" > "$DEST/$property.txt"
done
for role in SMS DIALER CALL_SCREENING; do
  adb shell cmd role get-role-holders "android.app.role.$role" > "$DEST/role-$role.txt"
done
adb shell settings get secure always_on_vpn_app > "$DEST/always-on-vpn.txt"
adb shell settings get secure always_on_vpn_lockdown > "$DEST/vpn-lockdown.txt"
adb shell dumpsys package com.sentinel.quantum.aivalidation > "$DEST/validation-package.txt"
printf 'Collected evidence only; no PASS or production qualification inferred.\n'
