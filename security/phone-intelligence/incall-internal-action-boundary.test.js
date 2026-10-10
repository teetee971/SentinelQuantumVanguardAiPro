import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const activity = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt',
  'utf8'
);

test('missing in-call session configuration launch failures return to the visible card', () => {
  assert.match(
    activity,
    /onConfigure = \{[\s\S]*?try \{[\s\S]*?startActivity\([\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'the in-call diagnostic launch must contain OEM/framework failures'
  );
  assert.match(
    activity,
    /onConfigure: \(\) -> String\?/, 
    'the configuration action must return an observable failure instead of discarding it'
  );
  assert.match(
    activity,
    /onConfigure\(\)\?\.let \{ actionStatus = it \}/,
    'the missing-session card must render a failed configuration launch'
  );
});
