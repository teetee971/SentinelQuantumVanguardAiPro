import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const callerId = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt',
  'utf8'
);

test('Caller ID WhatsApp handoff keeps OEM failures visible', () => {
  assert.match(
    callerId,
    /var whatsappStatus by remember\(number\) \{ mutableStateOf<String\?>\(null\) \}/,
    'the caller card must retain an observable WhatsApp handoff status'
  );
  assert.match(
    callerId,
    /context\.startActivity\([\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: SecurityException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'the WhatsApp handoff must contain framework and OEM runtime failure boundaries'
  );
  assert.match(
    callerId,
    /whatsappStatus\?\.let\s*\{[\s\S]*?liveRegion = LiveRegionMode\.Polite/,
    'WhatsApp handoff failures must remain visible and accessible'
  );
});
