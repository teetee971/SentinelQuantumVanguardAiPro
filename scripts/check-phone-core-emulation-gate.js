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
const setupResumeFlow = existsAndRead('scripts', 'phone-core-emulator-setup-resume-flow.sh');
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
if (gate.local_technical_certificate?.required_count !== 13) errors.push('physical certificate must reflect the current 13-proof contract');

const minSdk = Number(buildGradle.match(/\bminSdk\s+(\d+)/)?.[1]);
if (minSdk !== 24) errors.push(`expected audited Android minSdk 24, found ${String(minSdk)}`);
for (const moduleName of [':app', ':wearable-contract', ':wearable-security']) {
  if (!settingsGradle.includes(`include '${moduleName}'`)) errors.push(`Gradle module missing from settings: ${moduleName}`);
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
  'phone-core-emulator-setup-resume-flow.sh',
  'phone-core-emulator-flow.sh',
  'phone-core-emulator-revocation-flow.sh',
  'ACTUAL_API=',
  'PhoneCore-Emulation-Qualification',
  'phone_number_canonicalization_contract',
  'multi_sim_submit_readiness_contract',
  'FATAL EXCEPTION: main',
  'ANR in com\\.sentinel\\.quantum'
]) requireText(workflow, marker, 'emulation workflow');

for (const marker of ['CallRuleEngineTest', 'frenchAndInternationalPrefixesCanonicalizeConsistently', 'normalizeNumber']) {
  requireText(canonicalizationTest, marker, 'phone-number canonicalization contract');
}
for (const marker of ['SmsSubmitReadinessTest', 'blocksMultiSimUntilExplicitLineSelected', 'canSubmit']) {
  requireText(multiSimReadinessTest, marker, 'multi-SIM submit readiness contract');
}
for (const marker of ['FIRST_RUN_PHONE_CORE_SETUP', 'Configuration initiale', 'Assistant séquentiel', 'attempted_target', 'completed', 'am force-stop', 'setup-resume-launch.txt']) {
  requireText(setupResumeFlow, marker, 'setup resume flow');
}
for (const marker of ['adb emu gsm call', 'adb emu sms send', 'for FLOW_ROLE in DIALER SMS', 'android.app.role.$FLOW_ROLE', 'android.app.role.CALL_SCREENING']) {
  requireText(runtimeFlow, marker, 'emulator runtime flow');
}
for (const marker of ['remove-role-holder', 'pm revoke', 'assert_sms_role_held', 'Envoi SMS : autorisation Android requise.', 'Détection SIM : accès à l’état téléphonique requis.', 'rôle SMS disponible mais non accordé', 'android.app.role.CALL_SCREENING', 'assert_no_crash']) {
  requireText(revocationFlow, marker, 'emulator revocation flow');
}
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
