import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const manifest = fs.readFileSync(
  'native-android-app/app/src/main/AndroidManifest.xml',
  'utf8'
);
const respondService = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelRespondViaMessageService.kt',
  'utf8'
);

function componentBlock(tag, androidName) {
  const escapedName = androidName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const pattern = new RegExp(
    `<${tag}\\b(?=[^>]*android:name="${escapedName}")[^>]*>[\\s\\S]*?<\\/${tag}>`,
    'm'
  );
  return manifest.match(pattern)?.[0] ?? '';
}

test('default SMS respond-via-message service handles SMS and MMS schemes', () => {
  const service = componentBlock('service', '.security.SentinelRespondViaMessageService');
  assert.ok(service, 'SentinelRespondViaMessageService must be declared');
  assert.match(service, /android:exported="true"/);
  assert.match(service, /android:permission="android\.permission\.SEND_RESPOND_VIA_MESSAGE"/);
  assert.match(service, /android\.intent\.action\.RESPOND_VIA_MESSAGE/);

  const schemes = new Set(
    [...service.matchAll(/<data\s+android:scheme="([^"]+)"\s*\/>/g)].map((match) => match[1])
  );
  assert.deepEqual(
    [...schemes].sort(),
    ['mms', 'mmsto', 'sms', 'smsto'],
    'RESPOND_VIA_MESSAGE must expose every default-SMS scheme, not only SMS schemes'
  );
});

test('quick replies preserve the requested SMS or MMS transport', () => {
  assert.match(respondService, /val scheme = intent\.data\?\.scheme\?\.lowercase\(\)\.orEmpty\(\)/);
  assert.match(respondService, /"sms", "smsto"\s*->\s*\{/);
  assert.match(respondService, /SentinelSmsSender\(appContext\)\.send\(destination, body\)/);
  assert.match(respondService, /"mms", "mmsto"\s*->\s*\{/);
  assert.match(respondService, /SentinelMmsSender\(appContext\)\.send\(/);
  assert.match(respondService, /UNSUPPORTED_RESPOND_VIA_MESSAGE_SCHEME/);
  assert.match(
    respondService,
    /schemeSpecificPart\.orEmpty\(\)\.substringBefore\('\?'\)/,
    'destination must exclude URI query parameters before transport validation'
  );
});

test('default SMS SENDTO activity handles the same four schemes', () => {
  const activity = componentBlock('activity', '.SmsComposeActivity');
  assert.ok(activity, 'SmsComposeActivity must be declared');
  assert.match(activity, /android\.intent\.action\.SENDTO/);
  const schemes = new Set(
    [...activity.matchAll(/<data\s+android:scheme="([^"]+)"\s*\/>/g)].map((match) => match[1])
  );
  assert.deepEqual([...schemes].sort(), ['mms', 'mmsto', 'sms', 'smsto']);
});
