import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const composer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

test('reused SMS composer consumes a fresh SENDTO intent without accepting ACTION_SEND', () => {
  assert.match(
    composer,
    /override fun onNewIntent\(intent: Intent\)[\s\S]*?Intent\.ACTION_SENDTO[\s\S]*?setIntent\(intent\)/s,
    'the composer must process a new SENDTO request instead of keeping the old recipient'
  );
  assert.match(
    composer,
    /externalDestination\s*=\s*payload\.destination[\s\S]*?externalBody\s*=\s*payload\.body[\s\S]*?externalMmsIntent\s*=\s*payload\.mmsIntent[\s\S]*?externalComposeRequestEpoch\+\+/s,
    'recipient, body and MMS mode must cross the activity-to-Compose state bridge'
  );
  assert.match(
    composer,
    /LaunchedEffect\(externalComposeRequestEpoch\)[\s\S]*?destination\s*=\s*externalDestination[\s\S]*?body\s*=\s*externalBody[\s\S]*?mmsComposeMode\s*=\s*externalMmsIntent/s,
    'Compose must replace the visible draft after Android reuses the activity'
  );
  assert.match(
    composer,
    /LaunchedEffect\(externalComposeRequestEpoch\)[\s\S]*?submissionInFlight\s*=\s*false[\s\S]*?activeSendToken\s*=\s*null[\s\S]*?selectedThreadId\s*=\s*null/s,
    'a new external compose request must discard stale UI submission and conversation state'
  );
  assert.match(
    composer,
    /val requestEpoch = externalComposeRequestEpoch[\s\S]*?if \(requestEpoch != externalComposeRequestEpoch\) return@launch/s,
    'a late result from the previous compose request must not overwrite the new draft'
  );
  assert.match(
    composer,
    /val openConversations = source\?\.getBooleanExtra\(EXTRA_OPEN_CONVERSATIONS, false\) == true[\s\S]*?!mmsIntent/s,
    'blank internal launches must still open conversations while MMS remains an explicit composer mode'
  );
  assert.doesNotMatch(
    composer,
    /intent\.action\s*==\s*Intent\.ACTION_SEND\s*\|\|\s*intent\.action\s*==\s*Intent\.ACTION_SENDTO/,
    'generic ACTION_SEND must remain excluded from the SMS composer entry point'
  );
});
