import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);

test('API 24-28 emergency numbers use Android legacy emergency oracle before ordinary call policy', () => {
  assert.match(
    dialer,
    /import android\.telephony\.PhoneNumberUtils/,
    'legacy supported Android versions must use the platform emergency-number oracle'
  );
  assert.match(
    dialer,
    /PhoneNumberUtils\.isLocalEmergencyNumber\(this, safeNumber\)/,
    'API 24-28 must not hard-code platformConfirmsEmergency=false'
  );
  assert.doesNotMatch(
    dialer,
    /\}\s*else false\s*\n\s*if \(!EmergencyCallGuard\.requiresExplicitPhoneAccountSelection/,
    'legacy Android must not send possible emergency numbers through ordinary SIM/risk gates'
  );
});
