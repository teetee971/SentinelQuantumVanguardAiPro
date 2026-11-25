#!/bin/sh
# Preflight lecture seule pour Termux + Ubuntu proot ARM64.
# N'installe rien, ne compile pas, n'écrit pas dans les données de l'application.
# Ne lance pas Gradle : un wrapper peut télécharger et modifier .gradle.
set -u

repo=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$repo" || exit 2

fail=0
say() { printf '%s\n' "$1"; }

say "PREFLIGHT_SENTINEL v1"
say "date=$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date)"
say "pwd=$repo"
say "uname=$(uname -a 2>/dev/null || echo unknown)"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  say "BLOCK git: ce répertoire n'est pas un dépôt"
  exit 2
fi

head=$(git rev-parse HEAD)
branch=$(git rev-parse --abbrev-ref HEAD)
say "HEAD=$head"
say "branch=$branch"
say "origin_c2c970d8_expected=c2c970d8bd960d3b7ff387ead86aa1f1e41a4d97"
say "origin_1775_expected=c4f4d981da8637166b7a25c7fe5b811bbf3ddcae"
say "integration_merge_expected=42a57f19d45237e6263b2594fd6083528b591b2e"

say "--- parents of HEAD ---"
git rev-list --parents -n 1 HEAD
merge=42a57f19d45237e6263b2594fd6083528b591b2e
if git cat-file -t "$merge" >/dev/null 2>&1; then
  say "integration_merge=$merge"
  say "integration_parents=$(git rev-list --parents -n 1 "$merge")"
else
  say "integration_merge=absent"
fi
say "--- contains ---"
for sha in \
  c2c970d8bd960d3b7ff387ead86aa1f1e41a4d97 \
  c4f4d981da8637166b7a25c7fe5b811bbf3ddcae \
  42a57f19d45237e6263b2594fd6083528b591b2e \
  75a9e7dd6e31a08cac79d08347ae961d3831e543 \
  3fd8aa1dc2b5fee43922b91790b623f2ad96f2b5 \
  d5e9a69b181f42859dfa9de83d6e0818c1f71a74 \
  f036b2b79372f7402143b97730687ee5497351d6 \
  8211d15271404b9c6b12ebaf05671bf5ca15ee14 \
  28bac353e40e4bb3cc77620d7e0817973f3b25af
do
  if git cat-file -t "$sha" >/dev/null 2>&1; then
    if git merge-base --is-ancestor "$sha" HEAD 2>/dev/null; then
      say "ancestor $sha yes"
    else
      say "present_not_ancestor $sha"
    fi
  else
    say "absent $sha"
  fi
done

if git cat-file -t b2e2eebe >/dev/null 2>&1; then
  say "local_b2e2eebe=$(git rev-parse b2e2eebe)"
else
  say "local_b2e2eebe=absent_not_on_github"
fi

say "--- required preservation commits ---"
for sha in \
  d5e9a69b181f42859dfa9de83d6e0818c1f71a74 \
  3fd8aa1dc2b5fee43922b91790b623f2ad96f2b5 \
  75a9e7dd6e31a08cac79d08347ae961d3831e543
do
  if git merge-base --is-ancestor "$sha" HEAD 2>/dev/null; then
    say "required_ancestor $sha yes"
  else
    say "BLOCK required preservation commit missing: $sha"
    fail=1
  fi
done

say "--- local modifications (read-only) ---"
git status --porcelain
say "stash_count=$(git stash list | wc -l | tr -d ' ')"
say "--- disk ---"
df -h "$repo" /data 2>/dev/null || df -h "$repo"

say "--- toolchain ---"
if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | sed 's/^/java /'
else
  say "java=ABSENT"
  fail=1
fi
say "ANDROID_HOME=${ANDROID_HOME:-}"
say "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -n "$sdk" ] && [ -d "$sdk" ]; then
  say "sdk_dir=present"
  if [ -d "$sdk/platforms" ]; then
    say "platforms=$(ls "$sdk/platforms" 2>/dev/null | tr '\n' ' ')"
  else
    say "platforms=ABSENT"
    fail=1
  fi
  if [ -d "$sdk/build-tools" ]; then
    say "build_tools=$(ls "$sdk/build-tools" 2>/dev/null | tr '\n' ' ')"
  else
    say "build_tools=ABSENT"
    fail=1
  fi
else
  say "sdk_dir=ABSENT"
  fail=1
fi

if [ -f native-android-app/gradlew ]; then
  say "gradlew=present"
  if [ -x native-android-app/gradlew ]; then
    say "gradlew_executable=yes"
  else
    say "gradlew_executable=no"
    say "NOTE chmod +x native-android-app/gradlew is local metadata only. Do it before Gradle, not during this preflight."
    fail=1
  fi
else
  say "gradlew=ABSENT"
  fail=1
fi
if [ -d "$HOME/.gradle/wrapper/dists" ]; then
  say "gradle_dists_cached=yes"
else
  say "gradle_dists_cached=no"
fi

gradle_file="native-android-app/app/build.gradle"
if [ -f "$gradle_file" ]; then
  say "--- version and signing (read-only grep) ---"
  grep -n -E "versionCode|versionName|applicationId|signingConfig|storeFile|KEYSTORE" "$gradle_file" || true
  version_code=$(grep -E "versionCode[[:space:]]+[0-9]+" "$gradle_file" | head -1 | tr -cd '0-9')
  say "parsed_versionCode=${version_code:-unknown}"
  if [ "${version_code:-}" != "10" ]; then
    say "BLOCK versionCode n'est pas 10. Ne pas compiler pour une installation."
    fail=1
  fi
else
  say "BLOCK build.gradle absent"
  fail=1
fi

say "--- instrumentation must not touch the installed package ---"
inst="native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/V25RoomIdentityUpgradeInstrumentationTest.kt"
if [ -f "$inst" ] && grep -q "REFUSING_TO_TOUCH_INSTALLED_V25_PACKAGE" "$inst"; then
  say "instrumentation_package_guard=present"
else
  say "BLOCK the synthetic Room test can delete sentinel-local.db. Refuse unless the package is com.sentinel.quantum.v1validation."
  fail=1
fi

say "--- forbidden actions not performed ---"
say "no_gradle no_adb no_pm no_install no_uninstall no_app_data_write"

if [ "$fail" -ne 0 ]; then
  say "PREFLIGHT=BLOCKED"
  exit 1
fi
say "PREFLIGHT=READY_FOR_EXPLICIT_JVM_LINT_ONLY"
say "Next, only if this output was kept, and as two separate commands:"
say "cd native-android-app && ./gradlew :app:testDebugUnitTest -PsentinelCommercialV1=true --console=plain"
say "cd native-android-app && ./gradlew :app:lintDebug -PsentinelCommercialV1=true --console=plain"
say "Do not assemble a releasable APK. Do not adb install. Do not pm clear. Do not uninstall com.sentinel.quantum."
say "Do not run connectedAndroidTest on com.sentinel.quantum. The synthetic Room test deletes sentinel-local.db of whatever package it is installed into."
exit 0
