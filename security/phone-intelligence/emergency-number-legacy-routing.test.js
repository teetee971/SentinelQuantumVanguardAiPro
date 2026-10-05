import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);
const oracle = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/EmergencyNumberOracle.kt',
  'utf8'
);
const placeCall = dialer.match(
  /private fun placeCallIfReady\(number: String\) \{[\s\S]*?\n    private fun sanitizeDialNumber/
)?.[0] ?? '';

test('cross-version emergency oracle remains Android-owned on API 24+', () => {
  assert.match(oracle, /TelephonyManager::class\.java\)\.isEmergencyNumber\(number\)/);
  assert.match(oracle, /PhoneNumberUtils\.isLocalEmergencyNumber\(context, number\)/);
  assert.match(
    oracle,
    /if \(modern != null\) return modern[\s\S]*return legacyIsEmergency\(context, number\)/,
    'modern oracle failure must fall back to Android legacy emergency lookup'
  );
});

test('dialer delegates emergency routing to the cross-version oracle before ordinary call policy', () => {
  assert.ok(placeCall, 'placeCallIfReady must remain inspectable');
  assert.match(
    dialer,
    /import com\.sentinel\.quantum\.security\.EmergencyNumberOracle/,
    'dialer must use the shared cross-version emergency oracle'
  );
  assert.match(
    placeCall,
    /val platformConfirmsEmergency = EmergencyNumberOracle\.isEmergency\(this, safeNumber\)/,
    'API 24-28 must not hard-code platformConfirmsEmergency=false'
  );
  assert.doesNotMatch(
    placeCall,
    /\}\s*else false\s*\n\s*if \(!EmergencyCallGuard\.requiresExplicitPhoneAccountSelection/,
    'legacy Android must not send possible emergency numbers through ordinary SIM/risk gates'
  );
  const emergencyOracleIndex = placeCall.indexOf('val platformConfirmsEmergency');
  const roleGateIndex = placeCall.indexOf('if (!holdsDialerRole())');
  assert.ok(emergencyOracleIndex >= 0, 'emergency oracle must be evaluated');
  assert.ok(roleGateIndex >= 0, 'ordinary dialer role gate must remain explicit');
  assert.ok(
    emergencyOracleIndex < roleGateIndex,
    'emergency detection must happen before an ordinary ROLE_DIALER recovery prompt can intercept the call'
  );
});
