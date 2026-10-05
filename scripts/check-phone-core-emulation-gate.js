#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), 'utf8');
const existsAndRead = (...parts) => {
  const file = path.join(root, ...parts);
  return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
};

const gate = JSON.parse(read('config', 'phone-core-production-gates.json'));
const workflow = existsAndRead('.github', 'workflows', 'android-emulation-qualification.yml');
const codeqlWorkflow = existsAndRead('.github', 'workflows', 'codeql-analysis.yml');
const manifest = read('native-android-app', 'app', 'src', 'main', 'AndroidManifest.xml');
const physicalValidation = read('native-android-app', 'app', 'src', 'main', 'java', 'com', 'sentinel', 'quantum', 'security', 'PhoneCorePhysicalValidation.kt');
const setupResumeTest = existsAndRead('native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'PhoneCoreSetupResumeInstrumentationTest.kt');
const runtimeFlow = read('scripts', 'phone-core-emulator-flow.sh');
const revocationFlow = existsAndRead('scripts', 'phone-core-emulator-revocation-flow.sh');
const navigationSmoke = existsAndRead('native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui', 'AllStaticNavigationSurfacesInstrumentationTest.kt');
const standaloneSmoke = existsAndRead('native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui', 'StandaloneActivitySmokeInstrumentationTest.kt');
const canonicalizationTest = existsAndRead('native-android-app', 'app', 'src', 'test', 'java', 'com', 'sentinel', 'quantum', 'security', 'CallRuleEngineTest.kt');
const multiSimReadinessTest = existsAndRead('native-android-app', 'app', 'src', 'test', 'java', 'com', 'sentinel', 'quantum', 'security', 'SmsSubmitReadinessTest.kt');
const buildGradle = read('native-android-app', 'app', 'build.gradle');
const settingsGradle = read('native-android-app', 'settings.gradle');

const errors = [];
const requiredApis = [24, 29, 36, 37];
const runtimeApis = [29, 36, 37];
const requiredChecks = [
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
  'phone_core_setup_resume',
  'synthetic_call_screening_observed',
  'incoming_call_telecom_flow',
  'outgoing_call_telecom_flow',
  'incoming_sms_and_inline_reply',
  'role_revocation_fail_closed',
  'permission_revocation_fail_closed',
  'no_crash_or_anr'
];

const sameArray = (actual, expected) => JSON.stringify(actual) === JSON.stringify(expected);
const requireText = (haystack, needle, label) => {
  if (!haystack.includes(needle)) errors.push(`${label} missing marker: ${needle}`);
};

if (gate.schema_version !== 2) errors.push('phone-core production gate schema_version must be 2');
if (gate.certification_schema_version !== 5) errors.push('Phone Core certification schema must remain v5');
if (gate.manual_user_validation_required !== false) errors.push('manual user validation must not be required for emulator qualification');
const physicalRequiredCount = Number(physicalValidation.match(/val requiredCount:\s*Int\s*get\(\)\s*=\s*(\d+)/)?.[1]);
if (physicalRequiredCount !== 14) errors.push(`Phone Core v5 physical contract must expose exactly 14 criteria, found ${String(physicalRequiredCount)}`);
if (gate.local_technical_certificate?.required_count !== physicalRequiredCount) {
  errors.push(`physical gate count ${String(gate.local_technical_certificate?.required_count)} does not match PhoneCorePhysicalValidation ${String(physicalRequiredCount)}`);
}
requireText(physicalValidation, 'const val SIGNAL_CALL_ACTIVE = "INCALL_ACTIVE"', 'Phone Core physical active-call evidence');
requireText(physicalValidation, 'const val SIGNAL_CALL_SCREENED_PREFIX = "CALL_SCREENED:"', 'Phone Core call-screening evidence');

const minSdk = Number(buildGradle.match(/\bminSdk\s+(\d+)/)?.[1]);
if (minSdk !== 24) errors.push(`expected audited Android minSdk 24, found ${String(minSdk)}`);
for (const moduleName of [':app', ':wearable-contract', ':wearable-security']) {
  if (!settingsGradle.includes(`include '${moduleName}'`)) errors.push(`Gradle module missing from settings: ${moduleName}`);
}

if (!/<activity\s+android:name="\.PhoneCoreActivationActivity"\s+android:exported="false"\s*\/>/m.test(manifest)) {
  errors.push('PhoneCoreActivationActivity must remain private (android:exported="false")');
}
if (fs.existsSync(path.join(root, 'scripts', 'phone-core-emulator-setup-resume-flow.sh'))) {
  errors.push('retired adb setup-resume script must not exist; private setup is instrumentation-only');
}
if (workflow.includes('phone-core-emulator-setup-resume-flow.sh')) {
  errors.push('emulation workflow must not shell-launch the private PhoneCoreActivationActivity');
}
if (setupResumeTest.includes('sendKeyDownUpSync')) {
  errors.push('setup-resume instrumentation must not require privileged cross-application key injection');
}
if (runtimeFlow.includes('wait_text "Décrocher"') || runtimeFlow.includes('tap_text "Décrocher"')) {
  errors.push('incoming-call emulation must not depend on an OS-owned localized Answer label');
}
if (runtimeFlow.includes('wait_text "Appel autorisé"')) {
  errors.push('incoming-call emulation must not require CallerIdActivity to win the foreground race');
}
const dialerColdLaunchCommand = 'adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"';
if (runtimeFlow.split(dialerColdLaunchCommand).length - 1 < 2) {
  errors.push('modern Phone Core runtime must prove first launch plus process relaunch after fresh install');
}
if (revocationFlow.includes('SMS_SIGNAL_BEFORE=') || revocationFlow.includes("timeline_signal_prefix_count 'SMS_ALL_PARTS_SENT'")) {
  errors.push('SMS role revocation must not depend on asynchronous callbacks from an earlier legitimate send');
}

const emulation = gate.emulator_qualification;
if (!emulation || emulation.required !== true) errors.push('emulator qualification must be required');
if (!sameArray(emulation?.required_api_levels, requiredApis)) errors.push('emulator qualification must cover API 24, 29, 36 and 37');
if (!sameArray(emulation?.phone_core_runtime_api_levels, runtimeApis)) errors.push('Phone Core runtime flows must cover API 29, 36 and 37');
if (!Array.isArray(emulation?.required_checks) || new Set(emulation.required_checks).size !== emulation.required_checks.length) {
  errors.push('emulator required_checks must be a unique array');
} else {
  for (const check of requiredChecks) if (!emulation.required_checks.includes(check)) errors.push(`missing emulator qualification check: ${check}`);
  for (const check of emulation.required_checks) if (!requiredChecks.includes(check)) errors.push(`undeclared emulator qualification check contract: ${check}`);
}
if (emulation?.passed === true && (typeof emulation.evidence_ref !== 'string' || !emulation.evidence_ref.trim())) {
  errors.push('emulator qualification cannot be marked passed without evidence_ref');
}

const residual = gate.residual_external_validation;
if (!Array.isArray(residual) || residual.length < 3) {
  errors.push('residual external validation must declare non-emulatable release checks');
} else {
  const expectedIds = ['real_carrier_sms_mms_callbacks', 'samsung_s24_screening_latency', 'physical_dual_sim_oem_compatibility'];
  const ids = new Set(residual.map(item => item?.id));
  for (const id of expectedIds) if (!ids.has(id)) errors.push(`missing residual physical validation: ${id}`);
  for (const item of residual) {
    if (item.blocking_for_emulator_qualification !== false) errors.push(`${item.id}: physical validation must not block emulator qualification`);
    if (item.blocking_for_commercial_release !== true) errors.push(`${item.id}: physical validation must block commercial release`);
    if (item.execution_owner !== 'external_device_lab_or_release_qa') errors.push(`${item.id}: physical validation must not be assigned to the repository owner manually`);
  }
}

for (const api of requiredApis) {
  if (!new RegExp(`api_level:\\s*${api}\\b`).test(workflow)) errors.push(`emulation workflow missing API ${api}`);
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
  'phone-core-emulator-flow.sh',
  'phone-core-emulator-revocation-flow.sh',
  'ACTUAL_API=',
  'PhoneCore-Emulation-Qualification',
  'phone_number_canonicalization_contract',
  'multi_sim_submit_readiness_contract',
  'setup_resume: modernPhoneCore',
  'synthetic_call_screening_observed: modernPhoneCore',
  'cold_install_and_relaunch: true',
  'min-sdk-first-launch.txt',
  'min-sdk-second-launch.txt',
  'FATAL EXCEPTION: main',
  'ANR in com\\.sentinel\\.quantum'
]) requireText(workflow, marker, 'emulation workflow');

requireText(codeqlWorkflow, '"android-emulation-qualification.yml"', 'required CodeQL gate coupling');
requireText(codeqlWorkflow, '"production-merge-gate.yml"', 'required CodeQL gate coupling');
if (codeqlWorkflow.includes('"android-instrumentation.yml"')) {
  errors.push('required CodeQL status still depends on retired android-instrumentation.yml');
}

for (const marker of ['CallRuleEngineTest', 'frenchAndInternationalPrefixesCanonicalizeConsistently', 'normalizeNumber']) {
  requireText(canonicalizationTest, marker, 'phone-number canonicalization contract');
}
for (const marker of ['SmsSubmitReadinessTest', 'blocksMultiSimUntilExplicitLineSelected', 'canSubmit']) {
  requireText(multiSimReadinessTest, marker, 'multi-SIM submit readiness contract');
}
for (const marker of [
  'PhoneCoreSetupResumeInstrumentationTest',
  'PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP',
  'ActivityScenario.launch<PhoneCoreActivationActivity>',
  'attempted_target',
  'completed',
  'performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)',
  'interruptedFirstRunResumesWithoutFalseCompletion'
]) requireText(setupResumeTest, marker, 'private setup resume instrumentation');
for (const marker of [
  'adb emu gsm call',
  'adb emu gsm accept "$FLOW_NUMBER"',
  'wait_incoming_sentinel_surface',
  "n.get('package') == package_name",
  "'Appel autorisé' in text",
  "'Appel entrant' in text and 'Sonnerie' in text",
  '01-dialer-first-launch',
  '01b-dialer-relaunch',
  'wait_private_timeline_signal_prefix "CALL_SCREENED:"',
  'wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"',
  'wait_private_timeline_event "OUTGOING" "INCALL_ACTIVE"',
  'shared_prefs/phone_private_timeline.xml',
  'adb emu sms send',
  'for FLOW_ROLE in DIALER SMS',
  'android.app.role.$FLOW_ROLE',
  'android.app.role.CALL_SCREENING'
]) requireText(runtimeFlow, marker, 'emulator runtime flow');
for (const marker of [
  'remove-role-holder',
  'pm revoke',
  'assert_sms_role_held',
  'Envoi SMS : autorisation Android requise.',
  'Détection SIM : accès à l’état téléphonique requis.',
  'rôle SMS disponible mais non accordé',
  'assert_button_disabled "Envoyer"',
  'tap_ui_text "Envoyer"',
  'assert_modem_call_absent "$DIALER_PROBE_NUMBER"',
  'tap_ui_text "Appeler"',
  'SCREENING_BEFORE=',
  "timeline_signal_prefix_count 'CALL_SCREENED:'",
  'wait_modem_call_present "$SCREENING_PROBE_NUMBER"',
  'android.app.role.CALL_SCREENING',
  'assert_no_crash'
]) requireText(revocationFlow, marker, 'emulator protected-action revocation flow');
for (const marker of ['AllStaticNavigationSurfacesInstrumentationTest', 'Screen.Home.route', 'Screen.Search.route', 'Screen.PhoneSecurity.route', 'Screen.NetworkSurveillance.route', 'Screen.CollectiveDefense.route', 'Screen.SmartHome.route', 'Screen.Vpn.route', 'Screen.Settings.route', 'fetchSemanticsNode()']) {
  requireText(navigationSmoke, marker, 'application navigation smoke');
}
for (const marker of ['StandaloneActivitySmokeInstrumentationTest', 'PhoneCoreActivationActivity::class.java', 'PhoneCoreDiagnosticActivity::class.java', 'VoiceStudioActivity::class.java', 'ActivityScenario.launch']) {
  requireText(standaloneSmoke, marker, 'standalone activity smoke');
}

if (errors.length) {
  console.error('PHONE CORE EMULATION GATE: BLOCKED');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}
console.log('PHONE CORE EMULATION GATE: PASS');
