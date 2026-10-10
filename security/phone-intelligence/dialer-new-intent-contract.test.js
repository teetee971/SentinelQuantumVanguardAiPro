import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);

test('reused dialer activity consumes a fresh ACTION_DIAL intent', () => {
  assert.match(
    dialer,
    /override fun onNewIntent\(intent: Intent\)[\s\S]*?setIntent\(intent\)[\s\S]*?ACTION_DIAL/s,
    'the dialer must process a new ACTION_DIAL instead of keeping the old intent'
  );
  assert.match(
    dialer,
    /externalDialNumber\s*=\s*dialNumberFromIntent\(intent\)\.orEmpty\(\)[\s\S]*?externalDialRequestEpoch\+\+/s,
    'the fresh number must be published to the Compose state bridge'
  );
  assert.match(
    dialer,
    /if \(intent\.action != Intent\.ACTION_DIAL\) return[\s\S]*?pendingNumber = null[\s\S]*?assistedConfirmationNumber = null/s,
    'a fresh dial request must supersede a pending role or permission callback'
  );
  assert.match(
    dialer,
    /LaunchedEffect\(externalDialRequestEpoch\)[\s\S]*?number\s*=\s*externalDialNumber/s,
    'Compose must replace the visible number after Android reuses the activity'
  );
  assert.match(
    dialer,
    /private fun dialNumberFromIntent\(source: Intent\?\): String\?/s,
    'ACTION_DIAL parsing must be shared by cold and warm activity launches'
  );
});
