import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const osint = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/OsintDetailScreen.kt',
  'utf8'
);

test('OSINT browser handoff keeps OEM runtime failures visible', () => {
  assert.match(
    osint,
    /context\.startActivity\([\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: SecurityException\)[\s\S]*?catch \(_: IllegalArgumentException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'external browser handoff must contain framework and OEM runtime failures'
  );
  assert.match(
    osint,
    /if \(openFailed\)[\s\S]*?R\.string\.osint_detail_open_failed/,
    'external browser failures must remain visible'
  );
});
