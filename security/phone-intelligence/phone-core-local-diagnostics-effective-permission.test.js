import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const source = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreLocalDiagnostics.kt',
  'utf8'
);

test('Phone Core local diagnostics report effective Android permission state', () => {
  assert.match(
    source,
    /fun granted\(permission: String\): Boolean =\s*PermissionChecker\.checkSelfPermission\(context, permission\) == PermissionChecker\.PERMISSION_GRANTED/
  );
  assert.doesNotMatch(
    source,
    /ContextCompat\.checkSelfPermission/,
    'grant-only permission state must not be exposed as local diagnostic truth'
  );
});
