import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMissedCallReceiver.kt',
  'utf8'
);
const runtimeFacts = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreRuntimeFacts.kt',
  'utf8'
);
const activation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'utf8'
);
const diagnostics = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreLocalDiagnostics.kt',
  'utf8'
);

test('missed-call notification channel participates in Phone Core readiness truth', () => {
  assert.match(receiver, /fun isChannelEnabled\(context: Context\): Boolean/);
  assert.match(receiver, /channel\.importance != NotificationManager\.IMPORTANCE_NONE/);
  assert.match(runtimeFacts, /SentinelMissedCallReceiver\.isChannelEnabled\(context\)/);
  assert.match(activation, /SentinelMissedCallReceiver\.isChannelEnabled\(this\)/);
  assert.match(activation, /SentinelMissedCallReceiver\.ensureChannel\(applicationContext\)/);
  assert.match(diagnostics, /missedCallNotificationChannelEnabled/);
  assert.match(diagnostics, /SentinelMissedCallReceiver\.isChannelEnabled\(context\)/);
});

test('missed-call broadcast failures cannot escape the Telecom receiver', () => {
  assert.match(
    receiver,
    /override fun onReceive\(context: Context, intent: Intent\) \{\s*runCatching \{\s*handleReceive\(context, intent\)/,
    'OEM notification failures must be contained before they escape BroadcastReceiver.onReceive'
  );
  assert.match(
    receiver,
    /private fun handleReceive\(context: Context, intent: Intent\)/,
    'missed-call processing must remain isolated behind the guarded receiver entrypoint'
  );
  assert.match(
    receiver,
    /fun ensureChannel\(context: Context\): Boolean = runCatching \{/,
    'missed-call channel setup must fail closed when Android refuses the operation'
  );
  assert.match(
    receiver,
    /if \(!ensureChannel\(context\)\) return false/,
    'missed-call channel truth must reject when channel setup is not confirmed'
  );
});
