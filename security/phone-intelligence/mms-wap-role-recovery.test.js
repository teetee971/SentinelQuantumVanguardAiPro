import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsDeliverReceiver.kt',
  'utf8'
);

test('temporary SMS-role loss keeps an already accepted WAP MMS recoverable', () => {
  assert.match(
    receiver,
    /override fun onReceive\(context: Context, intent: Intent\)[\s\S]*?if \(!holdsSmsRole\(context\)\) return/s,
    'new WAP broadcasts must still be rejected when Sentinel is not the SMS role holder'
  );

  const captureStart = receiver.indexOf('private fun captureAndScheduleRecovery');
  const processStart = receiver.indexOf('private fun processDelivery');
  const roleHelperStart = receiver.indexOf('private fun holdsSmsRole');
  assert.ok(captureStart >= 0 && processStart > captureStart && roleHelperStart > processStart);

  const capture = receiver.slice(captureStart, processStart);
  const process = receiver.slice(processStart, roleHelperStart);
  assert.doesNotMatch(
    capture,
    /if \(!holdsSmsRole\(context\)\) return/,
    'recovery capture must not discard a broadcast that was already admitted before role revocation'
  );
  assert.match(
    process,
    /if \(!holdsSmsRole\(context\)\) return DeliveryOutcome\.RETRY/,
    'a role revocation during recovery must retain the bounded WAP journal for a later retry'
  );
});
