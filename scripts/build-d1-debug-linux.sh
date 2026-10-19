#!/usr/bin/env bash
# Local or hosted Linux build for D.1 debug qualification; no release signing, no deployment.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID="$ROOT/native-android-app"
for tool in java node git sha256sum unzip; do
  command -v "$tool" >/dev/null || { echo "BLOCKED: missing $tool" >&2; exit 2; }
done
if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=normal)" ]]; then
  echo "BLOCKED: source tree contains uncommitted or untracked changes; cannot attest to commit-only provenance" >&2
  exit 2
fi
SOURCE_SHA="$(git -C "$ROOT" rev-parse --verify HEAD)"
SOURCE_REF="$(git -C "$ROOT" symbolic-ref --quiet --short HEAD || true)"
if [[ -n "${EXPECTED_SOURCE_SHA:-}" && "$SOURCE_SHA" != "$EXPECTED_SOURCE_SHA" ]]; then
  echo "BLOCKED: source SHA mismatch (expected $EXPECTED_SOURCE_SHA, got $SOURCE_SHA)" >&2
  exit 2
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" || ! -d "$SDK/platforms/android-37" ]]; then
  echo "BLOCKED: Android SDK platform android-37 not available (ANDROID_HOME / ANDROID_SDK_ROOT)" >&2
  exit 2
fi
if [[ -f "$ANDROID/gradle/wrapper/gradle-wrapper.jar" ]]; then
  GRADLE_CMD=(bash ./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=(gradle)
  GRADLE_VERSION="$(gradle --version | sed -n 's/^Gradle //p' | head -n 1)"
  if [[ "$GRADLE_VERSION" != "9.8.0" ]]; then
    echo "BLOCKED: expected Gradle 9.8.0, found ${GRADLE_VERSION:-unknown}" >&2
    exit 2
  fi
else
  echo "BLOCKED: Gradle wrapper unavailable and no system Gradle installed" >&2
  exit 2
fi
cd "$ROOT"
node scripts/check-android-ui-consistency.js
node scripts/check-d1-activation-source.mjs
cd "$ANDROID"
# Prevent an earlier APK from being mistaken for an output of this run.
APK="$ANDROID/app/build/outputs/apk/debug/app-debug.apk"
rm -f -- "$APK"
"${GRADLE_CMD[@]}" --no-daemon --stacktrace :app:assembleDebug
[[ -s "$APK" ]] || { echo "BLOCKED: APK missing or empty" >&2; exit 3; }
if ! unzip -tqq "$APK"; then
  echo "BLOCKED: generated APK is not a valid ZIP archive" >&2
  exit 3
fi
if ! unzip -Z -1 "$APK" | grep -x 'AndroidManifest.xml' >/dev/null; then
  echo "BLOCKED: APK missing compiled AndroidManifest.xml" >&2
  exit 3
fi
if ! unzip -Z -1 "$APK" | grep -x 'classes.dex' >/dev/null; then
  echo "BLOCKED: APK missing primary DEX code" >&2
  exit 3
fi
AAPT=""
if [[ -d "$SDK/build-tools" ]]; then
  while IFS= read -r candidate; do
    if [[ -x "$candidate" ]]; then AAPT="$candidate"; break; fi
  done < <(find "$SDK/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt -print | sort -Vr)
fi
if [[ -z "$AAPT" || ! -x "$AAPT" ]]; then
  echo "BLOCKED: Android SDK aapt is required to verify APK identity" >&2
  exit 3
fi
APK_BADGING="$("$AAPT" dump badging "$APK")" || {
  echo "BLOCKED: cannot read APK package metadata" >&2
  exit 3
}
PACKAGE_LINE="${APK_BADGING%%
  echo "BLOCKED: generated APK does not declare com.sentinel.quantum" >&2
  exit 3
fi
POST_BUILD_SHA="$(git -C "$ROOT" rev-parse --verify HEAD)"
if [[ "$POST_BUILD_SHA" != "$SOURCE_SHA" ]]; then
  echo "BLOCKED: HEAD changed during compilation" >&2
  exit 3
fi
if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=normal)" ]]; then
  echo "BLOCKED: tracked source or untracked files changed during compilation" >&2
  exit 3
fi
echo "BUILD_OK: $APK"
echo "COMMIT: $SOURCE_SHA"
echo "SOURCE_REF: ${SOURCE_REF:-DETACHED_HEAD}"
echo "BYTES: $(wc -c < "$APK" | tr -d ' ')"
echo "SHA256: $(sha256sum "$APK")"
echo "STATUS: DEBUG_ONLY_NOT_PHYSICALLY_VALIDATED"
\\n'*}"
if [[ ! "$PACKAGE_LINE" =~ ^package:\ name=\'com[.]sentinel[.]quantum\'\  ]]; then
  echo "BLOCKED: generated APK does not declare com.sentinel.quantum" >&2
  exit 3
fi
POST_BUILD_SHA="$(git -C "$ROOT" rev-parse --verify HEAD)"
if [[ "$POST_BUILD_SHA" != "$SOURCE_SHA" ]]; then
  echo "BLOCKED: HEAD changed during compilation" >&2
  exit 3
fi
if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=normal)" ]]; then
  echo "BLOCKED: tracked source or untracked files changed during compilation" >&2
  exit 3
fi
echo "BUILD_OK: $APK"
echo "COMMIT: $SOURCE_SHA"
echo "SOURCE_REF: ${SOURCE_REF:-DETACHED_HEAD}"
echo "BYTES: $(wc -c < "$APK" | tr -d ' ')"
echo "SHA256: $(sha256sum "$APK")"
echo "STATUS: DEBUG_ONLY_NOT_PHYSICALLY_VALIDATED"
