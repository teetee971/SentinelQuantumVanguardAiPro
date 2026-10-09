import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const service = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt',
  'utf8'
);
const activity = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt',
  'utf8'
);

test('Telecom UI actions share a fail-closed race boundary', () => {
  assert.match(
    service,
    /private fun performCallAction\([\s\S]*?runCatching \{\s*action\(call\)/,
    'framework call commands must be contained before they reach Telecom'
  );
  assert.match(
    service,
    /Commande Telecom refusée ou devenue obsolète: \$actionName/,
    'stale or rejected commands must remain observable without crashing the service'
  );
  for (const action of [
    'disconnect',
    'hold',
    'unhold',
    'answer',
    'reject',
    'mergeConference',
    'swapConference',
    'startDtmf',
    'stopDtmf',
  ]) {
    assert.match(
      service,
      new RegExp(`performCallAction\\([\\s\\S]*?actionName = "${action}"`),
      `${action} must use the shared Telecom command boundary`
    );
  }
});

test('microphone capability reads stay inside the same race boundary', () => {
  assert.match(
    service,
    /private fun requestMicrophoneMuted\([\s\S]*?performCallAction\([\s\S]*?allowed = \{[\s\S]*?call\.details\.can\(Call\.Details\.CAPABILITY_MUTE\)/,
    'mute capability reads must be contained before setMuted reaches Telecom'
  );
});

test('foreground activity launch cannot crash the Telecom service', () => {
  assert.match(
    service,
    /private fun showInCallActivity\(\) \{\s*runCatching \{[\s\S]*?startActivity\(/,
    'background or OEM activity launch failures must be contained at the service boundary'
  );
  assert.match(
    service,
    /private fun showInCallActivity\(\)[\s\S]*?onFailure \{[\s\S]*?Interface d[’']appel indisponible/s,
    'a failed UI launch must remain observable without crashing Telecom'
  );
});

test('Telecom action failure reporting cannot rethrow through the service boundary', () => {
  assert.match(
    service,
    /private fun performCallAction\([\s\S]*?\.onFailure \{[\s\S]*?runCatching \{[\s\S]*?onFailure\(\)\s*\}/,
    'failure-state publication must be contained after an OEM rejects a Telecom action'
  );
});

test('critical in-call controls expose rejected Telecom commands to the customer UI', () => {
  assert.match(
    activity,
    /onReject\s*=\s*\{[\s\S]*!SentinelInCallService\.reject\((?:snapshot|currentSnapshot)\.id\)/,
    'reject failures must not disappear in the incoming-call UI'
  );
  assert.match(
    activity,
    /onAnswer\s*=\s*\{[\s\S]*!SentinelInCallService\.answer\((?:snapshot|currentSnapshot)\.id\)/,
    'answer failures must not disappear in the incoming-call UI'
  );
  assert.match(
    activity,
    /onHangup\s*=\s*\{[\s\S]*!SentinelInCallService\.disconnect\((?:snapshot|currentSnapshot)\.id\)/,
    'hang-up failures must not disappear in the in-call UI'
  );
  assert.match(
    activity,
    /onActionFailure\s*=\s*\{[\s\S]*!SentinelInCallService\.(hold|unhold)\(/,
    'hold and resume failures must remain visible to the customer'
  );
  assert.match(
    activity,
    /actionStatus\?\.let \{[\s\S]*errorContainer[\s\S]*Text\(\s*message/s,
    'the rejected command must be rendered in the pinned in-call surface'
  );
});
