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
const navigationSmoke = read(
  'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui',
  'AllStaticNavigationSurfacesInstrumentationTest.kt'
);
const standaloneSmoke = read(
  'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui',
  'StandaloneActivitySmokeInstrumentationTest.kt'
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
if (!Array.isArray(emulation?.required_checks) || !emulation.required_checks.includes('effective_permission_denial_fail_closed')) {
  errors.push('emulator contract must require effective_permission_denial_fail_closed');
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
  errors.push(`Phone Core v5 physical contract must expose exactly 14 criteria, found ${String(physicalRequiredCount)}`);
}
if (gate.local_technical_certificate?.required_count !== physicalRequiredCount) {
  errors.push('configured physical certificate count must match PhoneCorePhysicalValidation');
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
  'getSystemService(TelephonyManager::class.java).isEmergencyNumber',
  'if (emergency != false)',
  'CallBlocklistStore.cachedSnapshotForScreening()',
  'SCREENING_FINGERPRINTER::cachedCandidates',
  'respondToCall(callDetails, CallResponse.Builder().build())'
]) requireText(callScreeningService, marker, 'CallScreeningService truth');
requireText(callBlocklistStore, 'internal fun cachedSnapshotForScreening', 'memory-only screening cache');
if (callScreeningService.includes('CallBlocklistStore(this)')) {
  errors.push('CallScreeningService must not construct CallBlocklistStore before respondToCall; SharedPreferences stay outside the screening critical path');
}

const incomingGuard = callScreeningService.indexOf('callDetails.callDirection != Call.Details.DIRECTION_INCOMING');
const callbackMarker = callScreeningService.indexOf('Log.i(LIFECYCLE_TAG, CALLBACK_MARKER)');
const emergencyLookup = callScreeningService.indexOf('getSystemService(TelephonyManager::class.java).isEmergencyNumber');
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
  "process.env.HOST_CONTRACT_RESULT === 'success'",
  "process.env.INSTRUMENTATION_OUTCOME === 'success'",
  "instrumentationXml.includes('AllStaticNavigationSurfacesInstrumentationTest')",
  "instrumentationXml.includes('StandaloneActivitySmokeInstrumentationTest')",
  "instrumentationXml.includes('PhoneCoreSetupResumeInstrumentationTest')",
  'phone-core-emulator-flow.sh',
  'phone-core-emulator-revocation-flow.sh',
  'ACTUAL_API=',
  'PhoneCore-Emulation-Qualification',
  'FATAL EXCEPTION: main',
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
if (/connected_instrumentation:\s*true/.test(workflow) || /setup_resume:\s*modernPhoneCore/.test(workflow)) {
  errors.push('qualification.json checks must be derived from actual workflow/test evidence, not static truthy literals');
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
  'interruptedFirstRunResumesWithoutFalseCompletion'
]) requireText(setupResumeTest, marker, 'setup resume instrumentation');
if (setupResumeTest.includes('sendKeyDownUpSync')) {
  errors.push('setup-resume instrumentation must not require privileged key injection');
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
  'wait_role_held android.app.role.CALL_SCREENING',
  'dumpsys role',
  'adb emu gsm call',
  'wait_logcat_marker "CallScreeningService:onScreenCall"',
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
  'adb shell appops set "$PACKAGE" SEND_SMS "$mode"',
  'assert_send_sms_appop_denied',
  'assert_sms_role_held',
  'assert_action_disabled "Envoyer"',
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
