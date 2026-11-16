#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.sentinel.quantum"
APK_PATH="${1:-}"
OUTPUT_ROOT="${PHONE_CORE_EVIDENCE_DIR:-artifacts/phone-core-physical-evidence}"
TIMESTAMP_UTC="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${OUTPUT_ROOT}/${TIMESTAMP_UTC}"
MANIFEST_PATH="${OUTPUT_DIR}/physical-evidence.json"
commercial_release_claim=false

if [[ -z "${APK_PATH}" || ! -f "${APK_PATH}" ]]; then
  echo "Usage: $0 /path/to/sentinel.apk" >&2
  exit 2
fi

for command in adb git; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 2
  fi
done

if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA256="$(sha256sum "${APK_PATH}" | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  APK_SHA256="$(shasum -a 256 "${APK_PATH}" | awk '{print $1}')"
else
  echo "Neither sha256sum nor shasum is available" >&2
  exit 2
fi

SOURCE_HEAD_SHA="${SOURCE_HEAD_SHA:-$(git rev-parse HEAD)}"
mkdir -p "${OUTPUT_DIR}"

if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "No authorized Android device is available through adb" >&2
  exit 3
fi

sanitize_stream() {
  sed -E \
    -e 's#(tel:|sip:)[^[:space:]]+#\1[REDACTED]#gI' \
    -e 's#[+]?([0-9][ .()-]*){7,}#[REDACTED_NUMBER]#g' \
    -e 's#[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}#[REDACTED_EMAIL]#g'
}

status_for_file() {
  local file="$1"
  if [[ -s "${file}" ]]; then
    printf 'OBSERVED'
  else
    printf 'MISSING'
  fi
}

# Canonical commands from the physical validation protocol. Output is sanitized before persistence.
adb shell getprop ro.build.fingerprint 2>/dev/null | sanitize_stream > "${OUTPUT_DIR}/build-fingerprint.txt" || true
BUILD_FINGERPRINT_STATUS="$(status_for_file "${OUTPUT_DIR}/build-fingerprint.txt")"

adb shell dumpsys package com.sentinel.quantum 2>/dev/null \
  | grep -E 'versionName=|versionCode=|firstInstallTime=|lastUpdateTime=|granted=true|requested permissions:|signatures=|Signing|pkg=Package' \
  | sanitize_stream > "${OUTPUT_DIR}/package.txt" || true
PACKAGE_STATUS="$(status_for_file "${OUTPUT_DIR}/package.txt")"

adb shell dumpsys role 2>/dev/null \
  | grep -E 'android\.app\.role\.(DIALER|SMS|CALL_SCREENING)|com\.sentinel\.quantum' \
  | sanitize_stream > "${OUTPUT_DIR}/roles.txt" || true
ROLE_STATUS="$(status_for_file "${OUTPUT_DIR}/roles.txt")"

adb shell dumpsys telecom 2>/dev/null \
  | grep -E 'CallScreening|InCallService|DefaultDialer|PhoneAccount|CallAudio|State|mIsInCall|com\.sentinel\.quantum' \
  | sanitize_stream > "${OUTPUT_DIR}/telecom.txt" || true
TELECOM_STATUS="$(status_for_file "${OUTPUT_DIR}/telecom.txt")"

adb logcat -b crash -d 2>/dev/null \
  | grep -E 'com\.sentinel\.quantum|FATAL EXCEPTION|AndroidRuntime|ANR' \
  | sanitize_stream > "${OUTPUT_DIR}/crash-buffer.txt" || true
CRASH_STATUS="$(status_for_file "${OUTPUT_DIR}/crash-buffer.txt")"

adb shell dumpsys batterystats 2>/dev/null \
  | grep -E 'com\.sentinel\.quantum|Estimated power use|Uid u0a|Battery Time Remaining' \
  | sanitize_stream > "${OUTPUT_DIR}/batterystats.txt" || true
BATTERY_STATUS="$(status_for_file "${OUTPUT_DIR}/batterystats.txt")"

cat > "${MANIFEST_PATH}" <<EOF
{
  "schema_version": 1,
  "evidence_type": "PHONE_CORE_PHYSICAL_DEVICE_PRECHECK",
  "created_at_utc": "${TIMESTAMP_UTC}",
  "source_head_sha": "${SOURCE_HEAD_SHA}",
  "apk_sha256": "${APK_SHA256}",
  "package_name": "${PACKAGE_NAME}",
  "commercial_release_claim": false,
  "automatic_physical_validation": false,
  "collection": {
    "build_fingerprint": "${BUILD_FINGERPRINT_STATUS}",
    "package": "${PACKAGE_STATUS}",
    "roles": "${ROLE_STATUS}",
    "telecom": "${TELECOM_STATUS}",
    "crash_buffer": "${CRASH_STATUS}",
    "batterystats": "${BATTERY_STATUS}"
  },
  "physical_criteria": {
    "incoming_call_connected": "NOT_EXECUTED",
    "outgoing_call_connected": "NOT_EXECUTED",
    "call_screening_observed": "NOT_EXECUTED",
    "contacts_provider_ready": "NOT_EXECUTED",
    "call_history_provider_ready": "NOT_EXECUTED",
    "incoming_sms_received": "NOT_EXECUTED",
    "outgoing_sms_submitted": "NOT_EXECUTED",
    "outgoing_sms_delivered": "NOT_EXECUTED",
    "incoming_mms_safe_preview": "NOT_EXECUTED",
    "outgoing_mms_sent": "NOT_EXECUTED",
    "incoming_call_notification": "NOT_EXECUTED",
    "incoming_sms_notification": "NOT_EXECUTED",
    "caller_id_ui_shown": "NOT_EXECUTED",
    "in_call_ui_shown": "NOT_EXECUTED"
  },
  "status_vocabulary": ["OBSERVED", "MISSING", "NOT_EXECUTED"]
}
EOF

printf 'Phone Core physical evidence precheck written to %s\n' "${OUTPUT_DIR}"
printf 'SOURCE_HEAD_SHA=%s\n' "${SOURCE_HEAD_SHA}"
printf 'APK_SHA256=%s\n' "${APK_SHA256}"
printf 'This collector does not establish physical or commercial release readiness.\n'
