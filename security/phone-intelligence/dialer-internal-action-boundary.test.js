import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);

test('dialer-owned Phone Core and SMS surfaces use a visible internal launch boundary', () => {
  assert.match(
    dialer,
    /fun launchInternalActivityOrReport\(request: Intent, failureMessage: String\) \{[\s\S]*?context\.startActivity\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'dialer-owned activities must report missing or disabled components'
  );
  assert.doesNotMatch(
    dialer,
    /context\.startActivity\(\s*Intent\(context, PhoneCoreActivationActivity::class\.java\)\s*\)/,
    'the state chip must use the guarded Phone Core activation launch'
  );
  assert.doesNotMatch(
    dialer,
    /startActivity\(\s*Intent\.createChooser|startActivity\(\s*Intent\(\s*Intent\.ACTION_SENDTO[\s\S]*?SmsComposeActivity::class\.java/s,
    'dialer SMS handoffs must use the guarded internal launch path'
  );
});

test('internal dialer launch failures reuse the visible call status', () => {
  assert.match(
    dialer,
    /catch \(_: ActivityNotFoundException\)[\s\S]*?callActionStatus = failureMessage[\s\S]*?catch \(_: RuntimeException\)[\s\S]*?callActionStatus = failureMessage/s,
    'OEM launch failures must be visible in the existing dialer status surface'
  );
  assert.match(
    dialer,
    /launchInternalActivityOrReport\([\s\S]*Android n’a pas pu ouvrir la configuration Phone Core/s,
    'the Phone Core action must provide a French recovery message'
  );
});
