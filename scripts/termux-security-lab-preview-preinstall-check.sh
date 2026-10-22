#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-androidtest/native-android-app"
APK="$ROOT/app/build/outputs/apk/labPreview/app-labPreview.apk"
[[ -s "$APK" ]] || { echo "BLOQUE: APK preview absent"; exit 2; }
proot-distro login ubuntu -- bash -c '
set -euo pipefail
APK="$1"
SDK=/root/android-sdk
AAPT="$(find "$SDK/build-tools" -type f -name aapt -perm /111 | sort -V | tail -1)"
APKSIGNER="$(find "$SDK/build-tools" -type f -name apksigner | sort -V | tail -1)"
test -n "$AAPT" && test -n "$APKSIGNER" || { echo "BLOQUE: outils Android absents"; exit 2; }
BADGING="$("$AAPT" dump badging "$APK")"
printf "%s\n" "$BADGING" | grep -m1 "^package:"
printf "%s\n" "$BADGING" | grep -m1 "^application-label:"
printf "%s\n" "$BADGING" | grep -m1 "^launchable-activity:"
printf "%s\n" "$BADGING" | grep -q "^package: name=\x27com.sentinel.quantum.labpreview\x27" || { echo "BLOQUE: ID paquet incorrect"; exit 3; }
"$APKSIGNER" verify --verbose "$APK"
MANIFEST="$("$AAPT" dump xmltree "$APK" AndroidManifest.xml)"
printf "%s\n" "$MANIFEST" | grep -E "SentinelCallScreeningService|SentinelInCallService|SentinelSmsDeliverReceiver|SentinelMmsDeliverReceiver|SentinelRespondViaMessageService|SentinelDialerActivity|SmsComposeActivity" && { echo "BLOQUE: composant Téléphone/SMS présent"; exit 4; } || true
printf "%s\n" "$BADGING" | grep -E "uses-permission: name=\x27android.permission.(CALL_PHONE|READ_CALL_LOG|READ_SMS|SEND_SMS|RECEIVE_SMS|RECEIVE_MMS|RECEIVE_WAP_PUSH)\x27" && { echo "BLOQUE: permission Téléphone/SMS présente"; exit 5; } || true
echo "VERDICT=PREVIEW_ID_SIGNATURE_MANIFEST_VERIFIES"
' _ "$APK"
sha256sum "$APK"
echo "APK_NON_INSTALLE=$APK"
