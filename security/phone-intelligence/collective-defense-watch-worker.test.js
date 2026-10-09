import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const worker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/background/CollectiveDefenseWatchWorker.kt',
  'utf8'
);

test('collective-defense watch worker retries unpersisted refreshes', () => {
  assert.match(
    worker,
    /if \(!store\.markAttempted\([\s\S]*?\)\) \{[\s\S]*?failures\+\+/,
    'a failed attempt timestamp must remain retryable'
  );
  assert.match(
    worker,
    /if \(!store\.upsert\(refreshed\)\) \{[\s\S]*?failures\+\+[\s\S]*?return@forEach/,
    'a refresh that was not persisted must not be reported as processed'
  );

  const persistIndex = worker.indexOf('store.upsert(refreshed)');
  const escalationIndex = worker.indexOf('escalated += refreshed');
  assert.ok(
    persistIndex >= 0 && escalationIndex > persistIndex,
    'risk notifications must be derived only after durable refresh success'
  );
  assert.match(
    worker,
    /failures > 0 && runAttemptCount < MAX_RETRY_ATTEMPTS/,
    'partial worker failures must schedule a bounded retry'
  );
  assert.doesNotMatch(
    worker,
    /failures === watches\.size && runAttemptCount < MAX_RETRY_ATTEMPTS/,
    'retrying only when every watch fails loses partial failures'
  );
});
