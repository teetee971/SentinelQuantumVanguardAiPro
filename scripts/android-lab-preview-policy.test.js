import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const build = readFileSync('native-android-app/app/build.gradle', 'utf8');
const main = readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt', 'utf8');
const manifest = readFileSync('native-android-app/app/src/labPreview/AndroidManifest.xml', 'utf8');

test('lab preview has a separate identity and refuses production instrumentation build types', () => {
  assert.match(build, /applicationIdSuffix\s+'\.labpreview'/);
  assert.match(build, /sentinelInstrumentationBuildType in \['debug', 'labPreview'\]/);
  assert.match(build, /labPreviewImplementation 'androidx\.compose\.ui:ui-test-manifest'/);
});

test('lab preview never automatically offers default-handler setup while production retains it', () => {
  const defaults = build.slice(build.indexOf('defaultConfig {'), build.indexOf('def releaseKeystoreFile'));
  const preview = build.slice(build.indexOf('labPreview {'), build.indexOf('releaseUnsigned {'));
  assert.match(defaults, /buildConfigField "boolean", "PHONE_CORE_SETUP_AUTOLAUNCH_ALLOWED", 'true'/);
  assert.match(preview, /buildConfigField "boolean", "PHONE_CORE_SETUP_AUTOLAUNCH_ALLOWED", 'false'/);
  const entry = main.slice(main.indexOf('private fun maybeOpenPhoneCoreFirstRunSetup()'));
  const guard = entry.indexOf('if (!BuildConfig.PHONE_CORE_SETUP_AUTOLAUNCH_ALLOWED) return');
  assert.ok(guard >= 0 && guard < entry.indexOf('PhoneCoreSetupWizardStore(applicationContext)'),
    'preview must return before touching wizard state or requesting roles');
});

test('lab preview removes privileged Phone Core handlers and declares optional telephony hardware', () => {
  assert.match(manifest, /uses-feature android:name="android.hardware.telephony" android:required="false"/);
  for (const name of ['SentinelCallScreeningService', 'SentinelInCallService', 'SentinelRespondViaMessageService', 'SentinelSmsDeliverReceiver', 'SentinelMmsDeliverReceiver', 'SentinelDialerActivity', 'SmsComposeActivity']) {
    assert.match(manifest, new RegExp(`android:name="[^"\\n]*${name}" tools:node="remove"`));
  }
});
