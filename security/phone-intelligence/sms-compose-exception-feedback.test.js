import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const compose = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

function submissionBlock(marker, nextMarker) {
  const start = compose.indexOf(marker);
  const end = compose.indexOf(nextMarker, start);
  assert.ok(start >= 0 && end > start, `${marker} must remain inspectable`);
  return compose.slice(start, end);
}

test('SMS submission exceptions leave a visible and auditable outcome', () => {
  const block = submissionBlock('fun submitSms', 'fun submitMms');
  assert.match(
    block,
    /catch \(cancelled: CancellationException\) \{[\s\S]*throw cancelled[\s\S]*catch \(error: Exception\)/,
    'coroutine cancellation must propagate while unexpected send errors are contained'
  );
  assert.match(
    block,
    /localLogger\.logAsync\([\s\S]*LocalLogger\.LogLevel\.ERROR[\s\S]*SmsCompose/,
    'unexpected SMS failures must leave a bounded local audit entry'
  );
  assert.match(
    block,
    /status = "Impossible de terminer l’envoi SMS[^"]*"/,
    'unexpected SMS failures must be visible to the user'
  );
  assert.match(block, /finally \{[\s\S]*submissionInFlight = false/);
});

test('MMS submission exceptions leave a visible and auditable outcome', () => {
  const block = submissionBlock('fun submitMms', '\n\n                DisposableEffect');
  assert.match(
    block,
    /catch \(cancelled: CancellationException\) \{[\s\S]*throw cancelled[\s\S]*catch \(error: Exception\)/,
    'coroutine cancellation must propagate while unexpected MMS errors are contained'
  );
  assert.match(
    block,
    /localLogger\.logAsync\([\s\S]*LocalLogger\.LogLevel\.ERROR[\s\S]*MmsCompose/,
    'unexpected MMS failures must leave a bounded local audit entry'
  );
  assert.match(
    block,
    /status = "Impossible de terminer l’envoi MMS[^"]*"/,
    'unexpected MMS failures must be visible to the user'
  );
  assert.match(block, /finally \{[\s\S]*submissionInFlight = false/);
});
