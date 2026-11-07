#!/usr/bin/env bash
set -uo pipefail
expected=${1:?Expected source SHA required}
repo=$(git rev-parse --show-toplevel)
out=${2:?Outside evidence directory required}
[[ $(git rev-parse HEAD) == "$expected" && -z $(git status --porcelain) ]] || exit 2
out=$(python3 -c 'from pathlib import Path;import sys;print(Path(sys.argv[1]).resolve())' "$out")
case "$out" in "$repo"|"$repo"/*) exit 2;; esac
mkdir -p "$out"
git rev-parse HEAD > "$out/SOURCE_SHA"
git rev-parse HEAD:native-android-app > "$out/ANDROID_TREE"
git ls-files -z native-android-app | xargs -0 sha256sum > "$out/BUILD_INPUTS.sha256"
common=(--offline --no-daemon --max-workers=1 --console=plain)
if [[ -n "${SENTINEL_AAPT2_ARM64:-}" ]]; then common+=("-Pandroid.aapt2FromMavenOverride=$SENTINEL_AAPT2_ARM64"); fi
gradle=(bash "$repo/native-android-app/gradlew" -p "$repo/native-android-app")
: > "$out/COMMANDS.txt"; : > "$out/STATUS.tsv"
run() {
 local label=$1; shift
 printf '%q ' "$@" >> "$out/COMMANDS.txt"; printf '\n' >> "$out/COMMANDS.txt"
 "$@" > "$out/$label.log" 2>&1
 local code=$?; printf '%s\t%s\n' "$label" "$code" >> "$out/STATUS.tsv"
 echo "$label: $code"
}
units() {
 local label=$1; shift
 python3 -c 'import shutil,sys;shutil.rmtree(sys.argv[1],ignore_errors=True)' "$repo/native-android-app/app/build/test-results/testDebugUnitTest"
 run "$label" "${gradle[@]}" :app:testDebugUnitTest --rerun "$@" "${common[@]}"
 cp -R "$repo/native-android-app/app/build/test-results/testDebugUnitTest" "$out/$label-junit" 2>/dev/null || true
}
run java "$JAVA_HOME/bin/java" -version
run gradle-version "${gradle[@]}" --version
run kotlin "${gradle[@]}" :app:compileDebugKotlin "${common[@]}"
units unit
run wearable "${gradle[@]}" :wearable-contract:test --rerun :wearable-security:test --rerun "${common[@]}"
for module in wearable-contract wearable-security; do
 cp -R "$repo/native-android-app/$module/build/test-results/test" "$out/$module-junit" 2>/dev/null || true
done
run lint "${gradle[@]}" :app:lintDebug "${common[@]}"
run assemble "${gradle[@]}" :app:assembleDebug :app:assembleDebugAndroidTest "${common[@]}"
apk="$repo/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
testapk="$repo/native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if awk -F '\t' '$1=="assemble" {exit $2}' "$out/STATUS.tsv"; then
 for kind in app test; do
  file=$apk; [[ $kind == test ]] && file=$testapk
  run "$kind-signature" "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$file"
  run "$kind-align" "$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 "$file"
  run "$kind-badging" "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$file"
  cp "$file" "$out/$kind.apk"
 done
 units unit-fixture "-PsentinelSignatureFixture=$apk"
fi
cp "$repo/native-android-app/app/build/reports/lint-results-debug.xml" "$out/lint.xml" 2>/dev/null || true
run policy node --test scripts/android-release-policy.test.js
run products npm run test:android-product-truth
run credentials npm run test:credential-hygiene
run git-check git diff --check
python3 "$repo/qualification/cleaner/summarize.py" "$out"
