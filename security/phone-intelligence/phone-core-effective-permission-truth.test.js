import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const activation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'utf8'
);
const runtime = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreRuntimeFacts.kt',
  'utf8'
);

test('activation center and canonical runtime facts use effective Android permission truth', () => {
  assert.match(activation, /import androidx\.core\.content\.PermissionChecker/);
  assert.match(runtime, /import androidx\.core\.content\.PermissionChecker/);
  assert.match(
    activation,
    /private fun hasPermission\(permission: String\): Boolean =\s*PermissionChecker\.checkSelfPermission\(this, permission\) == PermissionChecker\.PERMISSION_GRANTED/
  );
  assert.match(
    runtime,
    /private fun hasEffectivePermission\(context: Context, permission: String\): Boolean =\s*PermissionChecker\.checkSelfPermission\(context, permission\) == PermissionChecker\.PERMISSION_GRANTED/
  );
  assert.doesNotMatch(
    activation,
    /ContextCompat\.checkSelfPermission/,
    'activation UI must not report a runtime grant as READY when the associated AppOp is denied'
  );
});
