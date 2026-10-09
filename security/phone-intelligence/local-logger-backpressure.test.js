import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const logger = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalLogger.kt',
  'utf8'
);

test('asynchronous security logging uses bounded backpressure', () => {
  assert.match(logger, /ThreadPoolExecutor\(/);
  assert.match(logger, /ArrayBlockingQueue< Runnable >|ArrayBlockingQueue<Runnable>/);
  assert.match(logger, /ThreadPoolExecutor\.AbortPolicy\(\)/);
  assert.doesNotMatch(logger, /Executors\.newSingleThreadExecutor/);
});
