import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);
const policy = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/OutgoingCallPermissionPolicy.kt',
  'utf8'
);

test('ordinary PSTN routing accepts only Android framework SIM accounts', () => {
  assert.match(dialer, /PhoneAccount\.CAPABILITY_SIM_SUBSCRIPTION/);
  assert.match(dialer, /val frameworkSimHandles = buildList/);
  assert.match(dialer, /frameworkSimHandles\.mapIndexed/);
  assert.match(
    dialer,
    /val selectedAccount = try \{[\s\S]*?telecom\.getPhoneAccount\(selectedLine\.handle\)[\s\S]*?OutgoingCallPermissionPolicy\.classify/
  );
  assert.match(policy, /AccountAuthority \{ FRAMEWORK_SIM, UNVERIFIED \}/);
  assert.match(policy, /authority == AccountAuthority\.FRAMEWORK_SIM/);
  assert.doesNotMatch(
    dialer,
    /telecom\.isOutgoingCallPermitted\(/,
    'default dialer must not use the self-managed PhoneAccount oracle for framework SIM routing'
  );
});

test('temporary self-writing SIM patch workflow must not remain in the repository', () => {
  assert.equal(
    fs.existsSync('.github/workflows/phone-core-framework-sim-patch-once.yml'),
    false,
    'one-shot contents:write workflow must be removed after the code change is committed'
  );
});
