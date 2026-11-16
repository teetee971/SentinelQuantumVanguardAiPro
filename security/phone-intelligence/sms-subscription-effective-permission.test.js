import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const source = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsSubscriptionState.kt',
  'utf8'
);

test('SMS SIM selection requires effective READ_PHONE_STATE authorization', () => {
  assert.match(
    source,
    /PermissionChecker\.checkSelfPermission\(context, Manifest\.permission\.READ_PHONE_STATE\)/
  );
  assert.match(source, /!=\s*PermissionChecker\.PERMISSION_GRANTED/);
  assert.doesNotMatch(
    source,
    /ContextCompat\.checkSelfPermission/,
    'grant-only permission checks must not drive SIM availability truth'
  );
});
