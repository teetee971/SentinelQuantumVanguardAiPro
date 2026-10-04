#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const gatePath = path.join(root, 'config', 'phone-core-production-gates.json');
const workflowPath = path.join(root, '.github', 'workflows', 'android-emulation-qualification.yml');
const flowPath = path.join(root, 'scripts', 'phone-core-emulator-flow.sh');
const revocationFlowPath = path.join(root, 'scripts', 'phone-core-emulator-revocation-flow.sh');
const navigationSmokePath = path.join(root, 'native-android-app', 'app', 'src', 'androidTest', 'java', 'com', 'sentinel', 'quantum', 'ui', 'AllStaticNavigationSurfacesInstrumentationTest.kt');
const buildGradlePath = path.join(root, 'native-android-app', 'app', 'build.gradle');
const settingsGradlePath = path.join(root, 'native-android-app', 'settings.gradle');

const errors = [];
const gate = JSON.parse(fs.readFileSync(gatePath, 'utf8'));
const workflow = fs.existsSync(workflowPath) ? fs.readFileSync(workflowPath, 'utf8') : '';
const flow = fs.readFileSync(flowPath, 'utf8');
const revocationFlow = fs.existsSync(revocationFlowPath) ? fs.readFileSync(revocationFlowPath, 'utf8') : '';
const navigationSmoke = fs.existsSync(navigationSmokePath) ? fs.readFileSync(navigationSmokePath, 'utf8') : '';
const buildGradle = fs.readFileSync(buildGradlePath, 'utf8');
const settingsGradle = fs.readFileSync(settingsGradlePath, 'utf8');

const requiredChecks = [
  'android_app_unit_tests',
  'wearable_contract_unit_tests',
  'wearable_security_unit_tests',
  'android_lint',
  'connected_instrumentation_api24',
  'connected_instrumentation_api29',
  'connected_instrumentation_api36',
  'all_static_navigation_surfaces_render',
  'min_sdk_cold_launch',
  'cold_install_and_relaunch',
  'phone_core_setup_resume',
  'incoming_call_telecom_flow',
  'outgoing_call_telecom_flow',
  'incoming_sms_and_inline_reply',
  'role_revocation_fail_closed',
  'permission_revocation_fail_closed',
  'region_canonicalization_matrix',
  'multi_sim_selection_policy_matrix',
  'no_crash_or_anr'
];

if (gate.schema_version !== 2) errors.push('phone-core production gate schema_version must be 2');
if (gate.certification_schema_version !== 5) errors.push('Phone Core certification schema must remain v5');
if (gate.manual_user_validation_required !== false) errors.push('manual user validation must not be required for emulator qualification');

const minSdk = Number(buildGradle.match(/\bminSdk\s+(\d+)/)?.[1]);
if (minSdk !== 24) errors.push(`expected audited Android minSdk 24, found ${String(minSdk)}`);
for (const moduleName of [':app', ':wearable-contract', ':wearable-security']) {
  if (!settingsGradle.includes(`include '${moduleName}'`)) errors.push(`Gradle module missing from settings: ${moduleName}`);
}

const emulation = gate.emulator_qualification;
if (!emulation || emulation.required !== true) errors.push('emulator qualification must be required');
if (JSON.stringify(emulation?.required_api_levels) !== JSON.stringify([24, 29, 36])) errors.push('emulator qualification must cover minSdk API 24, API 29 and API 36');
if (JSON.stringify(emulation?.phone_core_runtime_api_levels) !== JSON.stringify([29, 36])) errors.push('Phone Core role/runtime flows must remain scoped to API 29 and API 36');
if (!Array.isArray(emulation?.required_checks) || new Set(emulation.required_checks).size !== emulation.required_checks.length) {
  errors.push('emulator required_checks must be a unique array');
} else {
  for (const check of requiredChecks) if (!emulation.required_checks.includes(check)) errors.push(`missing emulator qualification check: ${check}`);
}
if (emulation?.passed === true && (typeof emulation.evidence_ref !== 'string' || !emulation.evidence_ref.trim())) errors.push('emulator qualification cannot be marked passed without evidence_ref');

const residual = gate.residual_external_validation;
if (!Array.isArray(residual) || residual.length < 3) {
  errors.push('residual external validation must declare the non-emulatable release checks');
} else {
  const ids = new Set(residual.map(item => item?.id));
  for (const id of ['real_carrier_sms_mms_callbacks', 'samsung_s24_screening_latency', 'physical_dual_sim_oem_compatibility']) {
    if (!ids.has(id)) errors.push(`missing residual physical validation: ${id}`);
  }
  for (const item of residual) {
    if (item.blocking_for_emulator_qualification !== false) errors.push(`${item.id}: residual physical validation must not block emulator qualification`);
    if (item.blocking_for_commercial_release !== true) errors.push(`${item.id}: residual physical validation must still block commercial release`);
    if (item.execution_owner !== 'external_device_lab_or_release_qa') errors.push(`${item.id}: physical validation must be assigned away from manual user validation`);
  }
}

for (const marker of [
  'matrix:',
  'api_level: [24, 29, 36]',
  ':app:testDebugUnitTest',
  ':wearable-contract:test',
  ':wearable-security:test',
  ':app:lintDebug',
  ':app:connectedDebugAndroidTest',
  'phone-core-emulator-flow.sh',
  'phone-core-emulator-revocation-flow.sh',
  'if [[ "$API_LEVEL" -ge 29 ]]',
  'PhoneCore-Emulation-Qualification',
  'FATAL EXCEPTION: main',
  'ANR in com\\.sentinel\\.quantum'
]) {
  if (!workflow.includes(marker)) errors.push(`emulation workflow missing marker: ${marker}`);
}
for (const marker of ['adb emu gsm call', 'adb emu sms send', 'for FLOW_ROLE in DIALER SMS', 'android.app.role.$FLOW_ROLE', 'android.app.role.CALL_SCREENING']) {
  if (!flow.includes(marker)) errors.push(`emulator runtime flow missing marker: ${marker}`);
}
for (const marker of ['remove-role-holder', 'pm revoke', 'rôle SMS disponible mais non accordé', 'android.app.role.CALL_SCREENING', 'assert_no_crash']) {
  if (!revocationFlow.includes(marker)) errors.push(`emulator revocation flow missing marker: ${marker}`);
}
for (const marker of [
  'AllStaticNavigationSurfacesInstrumentationTest',
  'Screen.Home.route',
  'Screen.Search.route',
  'Screen.PhoneSecurity.route',
  'Screen.NetworkSurveillance.route',
  'Screen.CollectiveDefense.route',
  'Screen.SmartHome.route',
  'Screen.Vpn.route',
  'Screen.Settings.route',
  'onRoot(useUnmergedTree = true).assertExists()'
]) {
  if (!navigationSmoke.includes(marker)) errors.push(`application navigation smoke missing marker: ${marker}`);
}

if (errors.length) {
  console.error('PHONE CORE EMULATION GATE: BLOCKED');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}
console.log('PHONE CORE EMULATION GATE: PASS');
