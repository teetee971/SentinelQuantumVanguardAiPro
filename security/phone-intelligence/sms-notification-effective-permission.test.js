import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const helper = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsNotificationHelper.kt',
  'utf8'
);

test('SMS notification evidence requires effective Android notification permission', () => {
  assert.match(helper, /PermissionChecker\.checkSelfPermission\([\s\S]*?Manifest\.permission\.POST_NOTIFICATIONS/);
  assert.match(helper, /!= PermissionChecker\.PERMISSION_GRANTED/);
  assert.doesNotMatch(
    helper,
    /ContextCompat\.checkSelfPermission/,
    'grant-only permission checks must not drive notification truth'
  );
});

test('SMS notification submission fails closed when app or channel notifications are disabled', () => {
  assert.match(helper, /NotificationManagerCompat\.from\(context\)\.areNotificationsEnabled\(\)/);
  assert.match(helper, /if \(!isChannelEnabled\(context\)\) return@runCatching false/);
  assert.match(helper, /channel != null && channel\.importance != NotificationManager\.IMPORTANCE_NONE/);
});

test('SMS notification helper only returns true after notify is submitted without exception', () => {
  assert.match(
    helper,
    /: Boolean = runCatching \{[\s\S]*?manager\.notify\(notificationId, builder\.build\(\)\)[\s\S]*?true[\s\S]*?\}\.getOrDefault\(false\)/
  );
});

test('SMS notification setup contains channel and PendingIntent failures', () => {
  assert.match(
    helper,
    /fun notifyMessage\([\s\S]*?\): Boolean = runCatching \{/,
    'notification construction must not escape the SMS callback path'
  );
  assert.match(
    helper,
    /fun ensureChannel\(context: Context\): Boolean = runCatching \{/,
    'SMS channel creation must fail closed when Android refuses the operation'
  );
  assert.match(
    helper,
    /if \(!ensureChannel\(context\)\) return@runCatching false/,
    'SMS notification submission must stop when channel setup is not confirmed'
  );
});
