#!/usr/bin/env bash
# Local only: never dispatches CI, installs an APK, merges, or publishes a release.
set -euo pipefail
expected_sha=${1:?Usage: bash qualification/REPRODUCE_FINAL_SHA.sh EXPECTED_SHA OUTPUT_DIRECTORY}
evidence_dir=${2:?Provide an evidence directory outside the repository}
repo_root=$(git rev-parse --show-toplevel)
[[ $(git -C "$repo_root" rev-parse HEAD) == "$expected_sha" ]] || { echo 'Wrong source SHA'; exit 2; }
[[ -z $(git -C "$repo_root" status --porcelain) ]] || { echo 'Source tree must be clean'; exit 2; }
: "${JAVA_HOME:?Provide JDK 17}"
: "${ANDROID_HOME:?Provide Android SDK with platforms;android-37.0 and build-tools;36.0.0}"
"$JAVA_HOME/bin/java" -version 2>&1 | rg '^openjdk version "17\.'
mkdir -p "$evidence_dir"
evidence_dir=$(realpath "$evidence_dir")
cd "$repo_root/native-android-app"
run() {
  local label=$1; shift
  printf '%q ' "$@" >> "$evidence_dir/commands.txt"
  printf '\n' >> "$evidence_dir/commands.txt"
  "$@" 2>&1 | tee "$evidence_dir/$label.log"
}
git rev-parse HEAD > "$evidence_dir/SOURCE_SHA"
git ls-files -z -- . | xargs -0 sha256sum > "$evidence_dir/android-sources.sha256"
run gradle-version bash gradlew --version
common=(--no-daemon --console=plain --max-workers=2)
run targeted bash gradlew :app:testDebugUnitTest --rerun --tests com.sentinel.quantum.ui.screens.PhoneSecurityVisualDashboardSourceContractTest "${common[@]}"
cp -R app/build/test-results/testDebugUnitTest "$evidence_dir/targeted-junit"
run unit bash gradlew :app:testDebugUnitTest --rerun "${common[@]}"
cp -R app/build/test-results/testDebugUnitTest "$evidence_dir/unit-junit"
run lint bash gradlew :app:lintDebug "${common[@]}"
run assemble bash gradlew :app:assembleDebug "${common[@]}"
apk=$(realpath app/build/outputs/apk/debug/app-debug.apk)
run signature "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk"
run badging "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$apk"
run unit-with-fixture bash gradlew :app:testDebugUnitTest --rerun "-PsentinelSignatureFixture=$apk" "${common[@]}"
cp -R app/build/test-results/testDebugUnitTest "$evidence_dir/unit-with-fixture-junit"
cp app/build/reports/lint-results-debug.* "$evidence_dir/"
cp "$apk" "$evidence_dir/app-debug.apk"
sha256sum "$evidence_dir/app-debug.apk" > "$evidence_dir/APK_SHA256SUMS"
git diff --check
echo 'Local commands completed. Inspect test counts/skips, lint, artifact metadata and device/Dependabot gates before declaring readiness.'
