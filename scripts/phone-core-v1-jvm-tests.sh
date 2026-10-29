#!/usr/bin/env bash
# Runs Android-independent production policies and their actual JUnit tests.
# Requires the Kotlin compiler jars bundled with Gradle and JUnit 4.13.2 + Hamcrest.
set -euo pipefail
: "${GRADLE_LIB_DIR:?Set to Gradle lib directory}"
: "${JUNIT_JAR:?Set to junit-4.13.2.jar}"
: "${HAMCREST_JAR:?Set to hamcrest-core-1.3.jar}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${PHONE_CORE_TEST_OUT:-"$ROOT/native-android-app/build/phone-core-v1-jvm"}
mkdir -p "$OUT"
MAIN="$ROOT/native-android-app/app/src/main/java/com/sentinel/quantum"
TEST="$ROOT/native-android-app/app/src/test/java/com/sentinel/quantum"
CLASSPATH="$JUNIT_JAR:$HAMCREST_JAR"
for jar in "$GRADLE_LIB_DIR"/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
sources=("$MAIN/PhoneCoreRoleRequestPolicy.kt" "$TEST/PhoneCoreRoleRequestPolicyTest.kt")
classes=(com.sentinel.quantum.PhoneCoreRoleRequestPolicyTest)
for name in IncomingCallAlertPolicy InCallAudioRequestGate InCallEvidenceTracker DtmfTonePlayer InCallSessionRegistry InCallDirectionPolicy InCallTruthPolicy InCallAudioUiPolicy InCallPresencePolicy PhoneCorePhysicalValidation PhoneCoreCertificationProvenance PhonePrivateTimeline; do
    sources+=("$MAIN/security/$name.kt" "$TEST/security/${name}Test.kt")
    classes+=("com.sentinel.quantum.security.${name}Test")
done
sources+=("$TEST/security/InCallActivityLaunchPolicyTest.kt")
classes+=(com.sentinel.quantum.security.InCallActivityLaunchPolicyTest)
java -cp "$GRADLE_LIB_DIR/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$CLASSPATH" -d "$OUT" "${sources[@]}"
java -cp "$OUT:$CLASSPATH" org.junit.runner.JUnitCore "${classes[@]}"
