import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const service = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt',
  'utf8'
);

test('Telecom callback registration failures do not crash the in-call service', () => {
  assert.match(
    service,
    /private fun trackCall\(call: Call\): Boolean \{[\s\S]*?runCatching \{\s*call\.registerCallback\(/,
    'call registration must be contained at the service boundary'
  );
  assert.match(
    service,
    /\.onFailure \{[\s\S]*?LocalLogger\(this\)\.logAsync\(/,
    'registration failures must be observable without crashing Telecom'
  );
  assert.match(
    service,
    /private val callbacksRegistered = java\.util\.Collections\.synchronizedSet\(/,
    'unregister must be limited to callbacks that actually registered'
  );
  assert.match(
    service,
    /if \(!callbacksRegistered\.remove\(call\)\) return[\s\S]*?call\.unregisterCallback\(callback\)/,
    'callback teardown must not invoke unregister after failed registration'
  );
});
