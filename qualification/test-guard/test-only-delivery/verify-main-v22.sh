#!/usr/bin/env bash
# Reads installed APK identity only; never installs, starts an Activity or runs tests.
set -u -o pipefail
SENTINEL_DEVICE=${1:-192.168.1.16:42175}
[[ "$SENTINEL_DEVICE" =~ ^[A-Za-z0-9._:-]+$ ]] || { echo 'MAIN_QUERY_FAILED: invalid device'; exit 2; }
[[ $(adb -s "$SENTINEL_DEVICE" get-state 2>/dev/null) == device ]] || { echo 'MAIN_QUERY_FAILED: device unavailable'; exit 2; }
if ! SENTINEL_MAIN_PATHS=$(adb -s "$SENTINEL_DEVICE" shell pm path com.sentinel.quantum); then
    echo 'MAIN_QUERY_FAILED'; exit 2
fi
mapfile -t SENTINEL_MAIN_PATHS_ARRAY < <(printf '%s\n' "$SENTINEL_MAIN_PATHS" | tr -d '\r' | sed -n 's/^package://p')
[[ ${#SENTINEL_MAIN_PATHS_ARRAY[@]} == 1 && ${SENTINEL_MAIN_PATHS_ARRAY[0]} == /data/app/*/base.apk ]] || { echo 'MAIN_QUERY_FAILED: unsupported APK layout'; exit 2; }
if ! SENTINEL_MAIN_HASH_OUTPUT=$(adb -s "$SENTINEL_DEVICE" shell sha256sum "${SENTINEL_MAIN_PATHS_ARRAY[0]}"); then
    echo 'MAIN_QUERY_FAILED'; exit 2
fi
SENTINEL_MAIN_HASH=$(printf '%s\n' "$SENTINEL_MAIN_HASH_OUTPUT" | tr -d '\r' | awk '{print $1}')
printf 'Main APK SHA256: %s\n' "$SENTINEL_MAIN_HASH"
if [[ "$SENTINEL_MAIN_HASH" != 10e70d21409daabcee0ba898703a1cb60897d127d22afffc84b6ca79f94390c8 ]]; then
    echo 'MAIN_APK_DIFFERENT_DO_NOT_RUN'; exit 2
fi
echo 'REFERENCE_MAIN_V22_MATCH: no installation or test performed'
