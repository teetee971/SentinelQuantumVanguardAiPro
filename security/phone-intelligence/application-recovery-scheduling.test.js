import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const application = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt',
  'utf8'
);

test('startup recovery scheduling failures remain observable without crashing Application', () => {
  assert.match(
    application,
    /private fun scheduleRecovery\(label: String, action: \(\) -> Unit\)[\s\S]*?LocalLogger\(this\)\.logAsync\(/,
    'startup recovery failures must be recorded through the bounded local logger'
  );
  assert.match(
    application,
    /scheduleRecovery\("réparation provider SMS"\)\s*\{[\s\S]*?SentinelSmsStatusReceiver\.queueProviderRepair\(this@SentinelApplication\)/,
    'provider repair must share the startup failure boundary'
  );
  assert.match(
    application,
    /scheduleRecovery\("préchargement des règles de filtrage"\)[\s\S]*?CallBlocklistStore\(this@SentinelApplication\)[\s\S]*?prepareScreeningSnapshot\(\)/,
    'screening preload failures must fail open without crashing Application'
  );
  for (const label of [
    'watchdog soumission SMS',
    'récupération SMS entrant',
    'récupération WAP Push MMS',
    'nettoyage MMS sortant',
    'watchdog soumission MMS'
  ]) {
    assert.match(
      application,
      new RegExp(`scheduleRecovery\\("${label}"\\)`),
      `${label} must remain observable when scheduling fails`
    );
  }
  assert.doesNotMatch(
    application,
    /runCatching \{\s*(?:SmsSubmissionWatchdogWorker|IncomingSmsRecoveryWorker|IncomingMmsWapIngressRecoveryWorker|MmsSendCleanupWorker|MmsSubmissionWatchdogWorker)\.schedule\(/,
    'startup schedulers must not hide failures in isolated runCatching blocks'
  );
});
