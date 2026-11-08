#!/usr/bin/env bash
# Host qualification only. No adb installation, transport, role changes or CI dispatch.
set -uo pipefail
expected=${1:?Expected source SHA required}
out=${2:?Outside evidence directory required}
repo=$(git rev-parse --show-toplevel)
[[ $(git rev-parse HEAD) == "$expected" && -z $(git status --porcelain) ]] || { echo 'Wrong SHA or dirty source'; exit 2; }
out=$(realpath -m "$out")
case "$out" in "$repo"|"$repo"/*) echo 'Output must be outside the source worktree'; exit 2;; esac
: "${JAVA_HOME:?JDK17 required}" "${ANDROID_HOME:?SDK37 required}" "${ROBOLECTRIC_SDK_DIR:?Offline SDK24/27/28/35 jars required}"
cmake=${SENTINEL_HOST_CMAKE:-cmake}
build=${SENTINEL_BUILD_ROOT:-$repo/native-android-app}
mkdir -p "$out"
git rev-parse HEAD > "$out/SOURCE_SHA"
git rev-parse HEAD:native-android-app > "$out/ANDROID_TREE"
common=(--offline --no-daemon --no-build-cache --max-workers=1 --console=plain "-PsentinelHostCmake=$cmake" "-PsentinelRobolectricSdkDir=$ROBOLECTRIC_SDK_DIR")
if [[ -n "${SENTINEL_BUILD_ROOT:-}" ]]; then
    mkdir -p "$build"
    python3 - "$out/ram-build.gradle" "$build" <<'PY'
import json,sys
open(sys.argv[1],'w').write('allprojects { p -> p.layout.buildDirectory.set(new File('+json.dumps(sys.argv[2])+', p.name)) }\n')
PY
    common+=(-I "$out/ram-build.gradle")
else
    # Standard per-module build folders, unlike the optional shared build root.
    build="$repo/native-android-app"
fi
: > "$out/COMMANDS.txt"; : > "$out/STATUS.tsv"
run() {
    local label=$1; shift
    printf '%q ' "$@" >> "$out/COMMANDS.txt"; printf '\n' >> "$out/COMMANDS.txt"
    "$@" > "$out/$label.log" 2>&1
    local status=$?
    printf '%s\t%s\n' "$label" "$status" >> "$out/STATUS.tsv"
    echo "$label: exit $status"
}
module_build() {
    if [[ -n "${SENTINEL_BUILD_ROOT:-}" ]]; then printf '%s/%s' "$build" "$1"; else printf '%s/%s/build' "$build" "$1"; fi
}
gradle=(bash "$repo/native-android-app/gradlew" -p "$repo/native-android-app")
run build "${gradle[@]}" "${common[@]}" :app:assembleDebug :app:assembleDebugAndroidTest :mms-compression-lab:assembleDebug :mms-compression-lab:assembleDebugAndroidTest
app=$(module_build app)
run regression "${gradle[@]}" "${common[@]}" "-PsentinelSignatureFixture=$app/outputs/apk/debug/app-debug.apk" :app:testDebugUnitTest :mms-compression-lab:testDebugUnitTest :wearable-contract:test :wearable-security:test :app:lintDebug :mms-compression-lab:lintDebug
for module in app mms-compression-lab wearable-contract wearable-security; do
    path=$(module_build "$module")
    mkdir -p "$out/$module"
    cp -R "$path/test-results" "$out/$module/" 2>/dev/null || true
    cp "$path/reports/"lint-results-debug.* "$out/$module/" 2>/dev/null || true
done
for module in app mms-compression-lab; do
    path=$(module_build "$module")
    for kind in debug androidTest/debug; do
        for apk in "$path/outputs/apk/$kind/"*.apk; do
            [[ -f "$apk" ]] || continue
            label=$(basename "$apk" .apk)
            run "$label-signature" "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk"
            run "$label-align" "$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 "$apk"
            run "$label-badging" "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$apk"
            cp "$apk" "$out/"
        done
    done
done
run diff-check git -C "$repo" diff --check
(cd "$out" && sha256sum ./*.apk > SHA256SUMS)
awk '$2 != 0 { failed=1 } END { exit failed }' "$out/STATUS.tsv"
