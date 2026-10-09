#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), 'utf8');
const exists = (...parts) => fs.existsSync(path.join(root, ...parts));
const errors = [];

const gate = JSON.parse(read('config', 'phone-core-production-gates.json'));
const workflow = read('.github', 'workflows', 'android-emulation-qualification.yml');
const legacyWorkflowPath = ['.github', 'workflows', 'android-instrumentation.yml'];
const manifest = read('native-android-app', 'app', 'src', 'main', 'AndroidManifest.xml');
const physicalValidation = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security',
  'PhoneCorePhysicalValidation.kt'
);
const callScreeningService = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security',
  'SentinelCallScreeningService.kt'
);
const callBlocklistStore = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security',
  'CallBlocklistStore.kt'
);
const smsDiagnostics = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security',
  'SmsActivationDiagnostics.kt'
);
const smsSender = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security',
  'SentinelSmsSender.kt'
);
const mmsSender = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security',
  'SentinelMmsSender.kt'
);
const setupResumeTest = read(
  'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum',
  'PhoneCoreSetupResumeInstrumentationTest.kt'
);
const setupRebootTest = read(
  'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum',
  'PhoneCoreSetupRebootPreparationInstrumentationTest.kt'
);
const navigationSmoke = read(
  'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui',
  'AllStaticNavigationSurfacesInstrumentationTest.kt'
);
const standaloneSmoke = read(
  'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui',
  'StandaloneActivitySmokeInstrumentationTest.kt'
);
const activationUi = read(
  'native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum',
  'PhoneCoreActivationActivity.kt'
);
const runtimeFlow = read('scripts', 'phone-core-emulator-flow.sh');
const revocationFlow = read('scripts', 'phone-core-emulator-revocation-flow.sh');
const buildGradle = read('native-android-app', 'app', 'build.gradle');

const requireText = (haystack, needle, label) => {
  if (!haystack.includes(needle)) errors.push(`${label} missing marker: ${needle}`);
};
const sameArray = (actual, expected) => JSON.stringify(actual) === JSON.stringify(expected);

if (gate.schema_version !== 3) errors.push('phone-core gate schema_version must be 3');
if (gate.certification_schema_version !== 5) errors.push('Phone Core certification schema must remain v5');
if (gate.rollout_mode !== 'shadow') errors.push('new emulator gate must remain in shadow mode during phase 2');
if ('manual_user_validation_required' in gate) {
  errors.push('ambiguous manual_user_validation_required field is forbidden');
}

const emulation = gate.emulator_qualification;
if (!emulation || emulation.required_for_developer_gate !== true) {
  errors.push('emulator qualification must be required for the developer gate contract');
}
if (emulation?.repository_owner_manual_validation_required_for_emulator_qualification !== false) {
  errors.push('repository owner manual validation must not be required for emulator-only qualification');
}
if (!sameArray(emulation?.required_api_levels, [24, 29, 36, 37])) {
  errors.push('emulator qualification must cover API 24, 29, 36 and 37');
}
if (!sameArray(emulation?.phone_core_runtime_api_levels, [29, 36, 37])) {
  errors.push('Phone Core runtime flows must cover API 29, 36 and 37');
}
if (!sameArray(emulation?.call_screening_decision_api_levels, [36, 37])) {
  errors.push('effective rule-engine screening decisions must remain API 36/37 emulator proofs only');
}
const expectedRequiredChecks = [
  'android_app_unit_tests',
  'wearable_contract_unit_tests',
  'wearable_security_unit_tests',
  'android_lint',
  'phone_number_canonicalization_contract',
  'multi_sim_submit_readiness_contract',
  'connected_instrumentation_api24',
  'connected_instrumentation_api29',
  'connected_instrumentation_api36',
  'connected_instrumentation_api37',
  'all_static_navigation_surfaces_render',
  'standalone_activity_surfaces_render',
  'min_sdk_cold_launch',
  'cold_install_and_relaunch',
  'sdk_api_exact',
  'runtime_package_path',
  'phone_core_setup_resume',
  'synthetic_call_screening_observed',
  'screening_latency_observed',
  'synthetic_call_screening_decision_observed',
  'incoming_call_telecom_flow',
  'outgoing_call_telecom_flow',
  'incoming_sms_and_inline_reply',
  'sms_all_parts_sent_callback',
  'sms_all_parts_delivered_callback',
  'role_revocation_fail_closed',
  'effective_permission_denial_fail_closed',
  'no_crash_or_anr'
];
if (!sameArray(emulation?.required_checks, expectedRequiredChecks)) {
  errors.push('emulator required_checks must exactly match the evidence keys produced by qualification.json');
}
if (emulation?.required_checks?.includes('permission_revocation_fail_closed')) {
  errors.push('ambiguous permission_revocation_fail_closed check is forbidden; role-managed grants must use effective authorization evidence');
}
if (emulation?.passed === true && !(typeof emulation.evidence_ref === 'string' && emulation.evidence_ref.trim())) {
  errors.push('emulator qualification cannot be marked passed without evidence_ref');
}

if (gate.commercial_release?.physical_validation_required !== true) {
  errors.push('physical validation must remain required for commercial release');
}
if (gate.commercial_release?.emulator_only_is_sufficient !== false) {
  errors.push('emulator-only evidence must never be sufficient for commercial release');
}

const physicalRequiredCount = Number(
  physicalValidation.match(/val requiredCount:\s*Int\s*get\(\)\s*=\s*(\d+)/)?.[1]
);
if (physicalRequiredCount !== 14) {
  errors.push(`Phone Core v5 local technical contract must expose exactly 14 criteria, found ${String(physicalRequiredCount)}`);
}
if (gate.local_technical_certificate?.required_count !== physicalRequiredCount) {
  errors.push('configured local technical certificate count must match PhoneCorePhysicalValidation');
}

const requiredResidualIds = [
  'real_carrier_sms_mms_callbacks',
  'samsung_s24_screening_latency',
  'android10_call_screening_decision',
  'physical_dual_sim_oem_compatibility'
];
const residual = Array.isArray(gate.residual_external_validation) ? gate.residual_external_validation : [];
const residualIds = new Set(residual.map((item) => item?.id));
for (const id of requiredResidualIds) {
  if (!residualIds.has(id)) errors.push(`missing residual physical validation: ${id}`);
}
for (const item of residual) {
  if (item?.blocking_for_emulator_qualification !== false) {
    errors.push(`${item?.id}: physical validation must not block emulator qualification`);
  }
  if (item?.blocking_for_commercial_release !== true) {
    errors.push(`${item?.id}: physical validation must block commercial release`);
  }
  if (item?.execution_owner !== 'external_device_lab_or_release_qa') {
    errors.push(`${item?.id}: physical validation must be assigned to release QA/device lab, not repository-owner manual approval`);
  }
}

if (!exists(...legacyWorkflowPath)) {
  errors.push('shadow rollout must keep android-instrumentation.yml until the replacement is proven green');
}
if (!exists('.github', 'workflows', 'android-emulation-qualification.yml')) {
  errors.push('shadow emulator qualification workflow is missing');
}

const minSdk = Number(buildGradle.match(/\bminSdk\s+(\d+)/)?.[1]);
if (minSdk !== 24) errors.push(`expected audited minSdk 24, found ${String(minSdk)}`);

if (!/<activity\s+android:name="\.PhoneCoreActivationActivity"\s+android:exported="false"\s*\/>/m.test(manifest)) {
  errors.push('PhoneCoreActivationActivity must remain private (android:exported=false)');
}

for (const marker of [
  'Log.i(LIFECYCLE_TAG, CALLBACK_MARKER)',
  'const val LIFECYCLE_TAG = "SentinelLifecycle"',
  'const val CALLBACK_MARKER = "CallScreeningService:onScreenCall"',
  'const val RESPONSE_LATENCY_MARKER = "CallScreeningService:response_elapsed_ms="',
  'const val RESPONSE_SENT_MARKER = "CallScreeningService:response_sent="',
  'const val MAX_PRE_RESPONSE_MS = 450L',
  'SystemClock.elapsedRealtime()',
  'logResponseLatency(startedAtElapsedMs, responseSent)',
  'getSystemService(TelephonyManager::class.java).isEmergencyNumber',
  'if (emergency != false)',
  'CallBlocklistStore.cachedSnapshotForScreening()',
  'SCREENING_FINGERPRINTER::cachedCandidates',
  'private fun respondAndLog(',
  'respondAndLog(callDetails, CallResponse.Builder().build(), startedAtElapsedMs)'
]) requireText(callScreeningService, marker, 'CallScreeningService truth');
const screeningBudgetMs = Number(
  callScreeningService.match(/const val MAX_PRE_RESPONSE_MS = (\d+)L/)?.[1] ?? 0
);
if (screeningBudgetMs <= 0 || screeningBudgetMs >= 500) {
  errors.push('CallScreeningService pre-response budget must remain strictly below the 500 ms physical gate');
}
requireText(callBlocklistStore, 'internal fun cachedSnapshotForScreening', 'memory-only screening cache');
if (!/val verificationCode = if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.R\)\s*\{\s*when \(callDetails\.callerNumberVerificationStatus\)/.test(callScreeningService)) {
  errors.push('callerNumberVerificationStatus requires Android 11/API 30; Android 10 must retain UNKNOWN without calling the accessor');
}
if (callScreeningService.includes('CallBlocklistStore(this)')) {
  errors.push('CallScreeningService must not construct CallBlocklistStore before respondToCall; SharedPreferences stay outside the screening critical path');
}

const incomingGuard = callScreeningService.indexOf('callDetails.callDirection != Call.Details.DIRECTION_INCOMING');
const callbackMarker = callScreeningService.indexOf('Log.i(LIFECYCLE_TAG, CALLBACK_MARKER)');
const emergencyLookup = callScreeningService.indexOf('getSystemService(TelephonyManager::class.java).isEmergencyNumber');
const roleAuthorization = callScreeningService.indexOf('isRoleHeld(RoleManager.ROLE_CALL_SCREENING)');
if (!(callbackMarker < roleAuthorization && roleAuthorization < emergencyLookup)) {
  errors.push('CallScreeningService must recheck the Android 10+ screening role before emergency lookup and rule-engine decisions');
}
if (incomingGuard < 0 || callbackMarker < 0 || emergencyLookup < 0 || !(incomingGuard < callbackMarker && callbackMarker < emergencyLookup)) {
  errors.push('PII-free callback marker must be after the incoming-call guard and before emergency classification');
}

for (const permissionBoundary of [smsDiagnostics, smsSender, mmsSender]) {
  requireText(permissionBoundary, 'PermissionChecker', 'effective SMS/MMS permission boundary');
}
requireText(smsDiagnostics, 'PermissionChecker.checkSelfPermission', 'SMS activation AppOp-aware truth');
requireText(smsSender, 'PermissionChecker.checkSelfPermission', 'SMS send AppOp-aware truth');
requireText(mmsSender, 'PermissionChecker.checkSelfPermission', 'MMS send AppOp-aware truth');

for (const api of [24, 29, 36, 37]) {
  if (!new RegExp(`api_level:\\s*${api}\\b`).test(workflow)) {
    errors.push(`emulation workflow missing API ${api}`);
  }
}
for (const marker of [
  "sdk_package_level: '37.0'",
  ':app:testDebugUnitTest',
  ':wearable-contract:test',
  ':wearable-security:test',
  ':app:lintDebug',
  'CallRuleEngineTest',
  'SmsSubmitReadinessTest',
  ':app:connectedDebugAndroidTest',
  'id: instrumentation',
  'HOST_CONTRACT_RESULT: ${{ needs.contract-and-host-tests.result }}',
  'INSTRUMENTATION_OUTCOME: ${{ steps.instrumentation.outcome }}',
  'id: setup_reboot',
  'adb shell reboot',
  'timeout --signal=INT --kill-after=5s 30s',
  'SETUP_REBOOT_OUTCOME: ${{ steps.setup_reboot.outcome }}',
  'setupRebootObserved',
  'run-as com.sentinel.quantum cat shared_prefs/phone_core_setup_wizard_v2.xml',
  "process.env.HOST_CONTRACT_RESULT === 'success'",
  "process.env.INSTRUMENTATION_OUTCOME === 'success'",
  'sentinel-instrumentation-api${api}-tests.log',
  '^OK \\([1-9][0-9]* tests?\\)$',
  'instrumentationEvidenceText',
  "instrumentationEvidenceText.includes('AllStaticNavigationSurfacesInstrumentationTest')",
  "instrumentationEvidenceText.includes('StandaloneActivitySmokeInstrumentationTest')",
  "instrumentationEvidenceText.includes('PhoneCoreSetupResumeInstrumentationTest')",
  'raw adb instrumentation log',
  'Gradle connected test XML',
  'connected_instrumentation_api${api}',
  'phone_core_setup_resume',
  'synthetic_call_screening_decision_observed',
  'no_crash_or_anr',
  'android_app_unit_tests',
  'wearable_contract_unit_tests',
  'wearable_security_unit_tests',
  'android_lint',
  'phone-core-emulator-flow.sh',
  'phone-core-emulator-revocation-flow.sh',
  'pm path com.sentinel.quantum',
  'runtime-package-path.txt',
  'runtimePackagePathConfirmed',
  'runtime_package_path',
  'setup-reboot-sdk.txt',
  'sdkApiExact',
  'sdk_api_exact',
  'ACTUAL_API=',
  'PhoneCore-Emulation-Qualification',
  'FATAL EXCEPTION:',
  'ANR in com\\.sentinel\\.quantum',
  'schema_version: 3',
  'physical_modem_claim: false',
  'commercial_release_claim: false',
  'effective_permission_denial_fail_closed'
]) requireText(workflow, marker, 'shadow emulation workflow');

if (workflow.includes('set-bypassing-role-qualification')) {
  errors.push('emulator gate must never bypass Android role qualification');
}
if (workflow.includes('android-instrumentation.yml')) {
  errors.push('shadow workflow must not disable or mutate the existing instrumentation gate');
}
if (workflow.includes('hostPrerequisitesPassed = true')) {
  errors.push('emulator qualification must not hardcode host/instrumentation prerequisites to true');
}
if (/^\s*connected_instrumentation:\s*true\b/m.test(workflow) || /^\s*setup_resume:\s*modernPhoneCore\b/m.test(workflow)) {
  errors.push('qualification.json checks must be derived from actual workflow/test evidence, not static truthy literals');
}
if (/^\s*connected_instrumentation\s*:/m.test(workflow) || /^\s*setup_resume\s*:/m.test(workflow) || /^\s*crash_or_anr_absent\s*:/m.test(workflow)) {
  errors.push('legacy qualification check keys are forbidden; report keys must match phone-core-production-gates.json exactly');
}
if (workflow.includes('cmd role get-role-holders --user 0 android.app.role.SMS')) {
  errors.push('workflow must not use get-role-holders as an Android 10/API 29 oracle; runtime scripts own the dumpsys-compatible role proof');
}

for (const marker of [
  'PhoneCoreSetupResumeInstrumentationTest',
  'PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP',
  'ActivityScenario.launch<PhoneCoreActivationActivity>',
  'attempted_target',
  'completed',
  'interruptedFirstRunResumesWithoutFalseCompletion',
  'executeShellCommand("am force-stop',
  'LifecycleState.IN_PROGRESS'
]) requireText(setupResumeTest, marker, 'setup resume instrumentation');
if (setupResumeTest.includes('sendKeyDownUpSync')) {
  errors.push('setup-resume instrumentation must not require privileged key injection');
}
const resumedScenarioIndex = setupResumeTest.indexOf('val resumedScenario = ActivityScenario.launch');
if (resumedScenarioIndex >= 0) {
  const resumedBlock = setupResumeTest.slice(resumedScenarioIndex);
  if (resumedBlock.indexOf('dismissSystemSetupDialog()') > resumedBlock.indexOf('resumedScenario.moveToState')) {
    errors.push('setup resume must dismiss Android-owned dialogs before forcing the resumed lifecycle');
  }
}

for (const marker of [
  'PhoneCoreSetupRebootPreparationInstrumentationTest',
  'preserve_state',
  'waitForAttemptedTarget',
  'cleanUpUnlessWorkflowWillReboot()',
  'LifecycleState.IN_PROGRESS'
]) requireText(setupRebootTest, marker, 'setup reboot preparation instrumentation');
if (setupRebootTest.includes('markAttemptedTarget(')) {
  errors.push('setup reboot preparation must observe the UI target, not seed an artificial target');
}
if (/executeShellCommand\("am force-stop/.test(setupRebootTest)) {
  errors.push('setup reboot preparation must leave process termination to the external reboot workflow');
}
if (!/fun dismissSystemSetupDialog\(\)/.test(setupRebootTest) ||
    !/cleanUpUnlessWorkflowWillReboot\(\)[\s\S]*dismissSystemSetupDialog\(\)/.test(setupRebootTest)) {
  errors.push('setup reboot preparation must dismiss any Android role/permission dialog before releasing ActivityScenario');
}
if (setupRebootTest.includes('scenario.moveToState(Lifecycle.State.RESUMED)')) {
  errors.push('setup reboot preparation must observe the real paused/resumed state instead of forcing RESUMED behind an Android-owned dialog');
}

for (const marker of [
  'AllStaticNavigationSurfacesInstrumentationTest',
  'Screen.Home.route',
  'Screen.PhoneSecurity.route',
  'Screen.NetworkSurveillance.route',
  'Screen.Vpn.route',
  'Screen.Settings.route',
  'fetchSemanticsNode()'
]) requireText(navigationSmoke, marker, 'navigation smoke');

for (const marker of [
  'StandaloneActivitySmokeInstrumentationTest',
  'PhoneCoreActivationActivity::class.java',
  'PhoneCoreDiagnosticActivity::class.java',
  'VoiceStudioActivity::class.java',
  'ActivityScenario.launch'
]) requireText(standaloneSmoke, marker, 'standalone activity smoke');

for (const marker of [
  '!mmsSafePreviewValidated',
  'Phone Core bloqué · aperçu MMS sécurisé indisponible'
]) requireText(activationUi, marker, 'MMS locked Phone Core banner');

for (const marker of [
  'wait_role_held android.app.role.CALL_SCREENING',
  'ADB_COMMAND_TIMEOUT_SECONDS="${ADB_COMMAND_TIMEOUT_SECONDS:-30}"',
  'ADB_COMMAND_KILL_GRACE_SECONDS="${ADB_COMMAND_KILL_GRACE_SECONDS:-5}"',
  'command timeout',
  'adb "$@"',
  'dumpsys role',
  'adb emu gsm call',
  'wait_logcat_marker "CallScreeningService:onScreenCall"',
  'wait_logcat_marker "CallScreeningService:response_elapsed_ms="',
  'wait_logcat_marker "CallScreeningService:response_sent=true"',
  'wait_incoming_sentinel_surface',
  'wait_private_timeline_signal_prefix "CALL_SCREENED:"',
  'wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"',
  'wait_private_timeline_event "OUTGOING" "INCALL_ACTIVE"',
  'adb emu sms send',
  'Synthetic Telecom callback/calls, cold relaunch, and inline SMS reply verified'
]) requireText(runtimeFlow, marker, 'Phone Core emulator runtime flow');

for (const marker of [
  'remove-role-holder',
  'pm revoke',
  'appops set',
  'set_send_sms_appop ignore',
  'adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS "$mode"',
  'assert_send_sms_appop_denied',
  'assert_sms_role_held',
  'assert_action_disabled "phone_core_sms_send"',
  'effective_permission_denial_fail_closed',
  'assert_modem_call_absent',
  'SCREENING_CALLBACK_BEFORE=',
  'SCREENING_DECISION_BEFORE=',
  'wait_role_absent android.app.role.CALL_SCREENING',
  'assert_no_crash'
]) requireText(revocationFlow, marker, 'Phone Core revocation flow');

if (errors.length) {
  console.error('PHONE CORE EMULATION SHADOW GATE: BLOCKED');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}
console.log('PHONE CORE EMULATION SHADOW GATE: PASS');
