#!/usr/bin/env bash
# Builds and host tests only: never installs, sends MMS, changes roles, or dispatches CI.
set -uo pipefail
expected=${1:?Usage: reproduce.sh EXPECTED_SHA OUTSIDE_OUTPUT_DIRECTORY}
repo=$(git rev-parse --show-toplevel)
out=$(python3 -c 'from pathlib import Path;import sys;print(Path(sys.argv[1]).resolve())' "${2:?Output required}")
[[ $(git rev-parse HEAD) == "$expected" && -z $(git status --porcelain) ]] || { echo 'Wrong SHA or dirty sources'; exit 2; }
case "$out" in "$repo"|"$repo"/*) echo 'Evidence must be outside worktree'; exit 2;; esac
: "${JAVA_HOME:?JDK17 required}" "${ANDROID_HOME:?SDK37/build-tools36 required}" "${ROBOLECTRIC_SDK_DIR:?Pre-cached SDK27/35 jars directory required}"
mkdir -p "$out"
cd "$repo"
git rev-parse HEAD > "$out/SOURCE_SHA"
git rev-parse HEAD:native-android-app > "$out/ANDROID_TREE"
git ls-files -z -- native-android-app scripts | xargs -0 sha256sum > "$out/SOURCE_FILES.sha256"
common=(--offline --no-daemon --max-workers=1 --console=plain "-PsentinelRobolectricSdkDir=$ROBOLECTRIC_SDK_DIR")
if [[ -n "${SENTINEL_AAPT2_ARM64:-}" ]]; then common+=("-Pandroid.aapt2FromMavenOverride=$SENTINEL_AAPT2_ARM64"); fi
gradle=(bash "$repo/native-android-app/gradlew" -p "$repo/native-android-app")
declare -A statuses
: > "$out/COMMANDS.txt"
: > "$out/STATUS.tsv"
run() {
    local label=$1; shift
    printf '%q ' "$@" >> "$out/COMMANDS.txt"; printf '\n' >> "$out/COMMANDS.txt"
    "$@" > "$out/$label.log" 2>&1
    local result=$?
    statuses[$label]=$result
    printf '%s\t%s\n' "$label" "$result" >> "$out/STATUS.tsv"
    echo "$label: exit $result"
}
run_test() {
    local label=$1 module=$2; shift 2
    # Only owned Gradle-generated test output; preserve each run in the outside evidence folder.
    python3 -c 'import shutil,sys;shutil.rmtree(sys.argv[1],ignore_errors=True)' "$repo/native-android-app/$module/build/test-results/testDebugUnitTest"
    run "$label" "${gradle[@]}" ":$module:testDebugUnitTest" --rerun "$@" "${common[@]}"
    if [[ -d "$repo/native-android-app/$module/build/test-results/testDebugUnitTest" ]]; then
        cp -R "$repo/native-android-app/$module/build/test-results/testDebugUnitTest" "$out/$label-junit"
    fi
}
run java "$JAVA_HOME/bin/java" -version
run gradle-version "${gradle[@]}" --version
run compile-kotlin "${gradle[@]}" :app:compileDebugKotlin :mms-compression-lab:compileDebugKotlin "${common[@]}"
run_test targeted app --tests com.sentinel.quantum.ui.screens.PhoneSecurityVisualDashboardSourceContractTest
run_test app-unit app
run_test lab-unit mms-compression-lab
run wearable-unit "${gradle[@]}" :wearable-contract:test --rerun :wearable-security:test --rerun "${common[@]}"
for module in wearable-contract wearable-security; do
    if [[ -d "$repo/native-android-app/$module/build/test-results/test" ]]; then
        cp -R "$repo/native-android-app/$module/build/test-results/test" "$out/$module-junit"
    fi
done
run lint "${gradle[@]}" :app:lintDebug :mms-compression-lab:lintDebug "${common[@]}"
run assemble "${gradle[@]}" :app:assembleDebug :mms-compression-lab:assembleDebug "${common[@]}"
run android-test "${gradle[@]}" :app:assembleDebugAndroidTest :mms-compression-lab:assembleDebugAndroidTest "${common[@]}"
files=(app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk mms-compression-lab/build/outputs/apk/debug/mms-compression-lab-debug.apk mms-compression-lab/build/outputs/apk/androidTest/debug/mms-compression-lab-debug-androidTest.apk)
labels=(app app-test lab lab-test)
for index in "${!files[@]}"; do
    apk="$repo/native-android-app/${files[$index]}"
    label=${labels[$index]}
    if [[ -f "$apk" && ${statuses[assemble]} == 0 && ${statuses[android-test]} == 0 ]]; then
        run "$label-signature" "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk"
        run "$label-badging" "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$apk"
        run "$label-align" "$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 "$apk"
        cp "$apk" "$out/$label.apk"
    fi
done
if [[ ${statuses[assemble]} == 0 ]]; then
    run_test app-unit-fixture app "-PsentinelSignatureFixture=$repo/native-android-app/${files[0]}"
fi
for module in app mms-compression-lab; do
    mkdir -p "$out/$module-lint"
    cp "$repo/native-android-app/$module/build/reports/"lint-results-debug.* "$out/$module-lint/" 2>/dev/null || true
done
run node-policy node --test scripts/android-release-policy.test.js
run node-products npm run test:android-product-truth
run node-credentials npm run test:credential-hygiene
run diff-check git diff --check
python3 "$repo/qualification/v23/summarize.py" "$out"
exit $?
