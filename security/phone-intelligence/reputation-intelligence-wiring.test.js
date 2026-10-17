import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const SOURCE_ROOT = resolve(
  'native-android-app/app/src/main/java/com/sentinel/quantum'
);
const RUNTIME_PATH = resolve(
  SOURCE_ROOT,
  'intelligence/IntelligenceCoreRuntime.kt'
);
const ADAPTER_PATH = resolve(
  SOURCE_ROOT,
  'intelligence/ReputationEvidenceAdapter.kt'
);
const CALLER_ID_PATH = resolve(SOURCE_ROOT, 'CallerIdActivity.kt');
const SETTINGS_PATH = resolve(SOURCE_ROOT, 'data/SettingsStore.kt');

test('consented caller reputation is published into Intelligence Core after UI enrichment', () => {
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');
  const adapter = readFileSync(ADAPTER_PATH, 'utf8');
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');

  assert.match(
    runtime,
    /fun publishRemoteReputation\s*\(/,
    'Intelligence Core needs a bounded reputation publication entry point'
  );
  assert.match(
    runtime,
    /ReputationEvidenceAdapter\.fromRemoteResult/,
    'remote reputation must use the existing evidence adapter'
  );
  assert.match(
    adapter,
    /TrustClass\.UNVERIFIED_REMOTE/,
    'remote reputation must remain explicitly unverified'
  );
  assert.match(
    callerId,
    /IntelligenceCoreRuntime\.publishRemoteReputation/,
    'Caller ID must publish the consented result into the shared core'
  );
});

test('caller reputation persistence and publication share the settings acceptance transaction', () => {
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');
  const settings = readFileSync(SETTINGS_PATH, 'utf8');
  const guardStart = callerId.indexOf('settingsStore.acceptCallerReputationResult(');
  const persist = callerId.indexOf('.persist(number, result');
  const publish = callerId.indexOf('IntelligenceCoreRuntime.publishRemoteReputation(');

  assert.ok(guardStart >= 0, 'Caller ID must enter the shared consent transaction');
  assert.ok(persist >= 0, 'caller reputation must persist the bounded result');
  assert.ok(publish > persist && persist > guardStart, 'publication must follow persistence inside acceptance');

  const acceptanceEnd = callerId.indexOf('}.getOrNull()', guardStart);
  assert.ok(acceptanceEnd > publish, 'acceptance must produce the UI result');
  const publicationWindow = callerId.slice(guardStart, acceptanceEnd);
  assert.match(
    publicationWindow,
    /result\s*=\s*result[\s\S]*?persist\s*=\s*\{[\s\S]*?\.persist\(number,\s*result\s*\)/,
    'the transaction must receive the result and bounded persistence operation'
  );
  assert.match(
    publicationWindow,
    /publish\s*=\s*\{[\s\S]*?IntelligenceCoreRuntime\.publishRemoteReputation\(/,
    'Intelligence Core publication must run inside the same acceptance transaction'
  );
  assert.match(
    settings,
    /fun\s*<T\s*:\s*Any>\s+acceptCallerReputationResult\s*\(/,
    'SettingsStore must own the acceptance boundary'
  );
});

test('caller reputation revocation setters and backup restore share the acceptance lock', () => {
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');
  const settings = readFileSync(SETTINGS_PATH, 'utf8');

  assert.match(
    callerId,
    /remoteResult\s*=\s*withContext\(Dispatchers\.IO\)[\s\S]*?settingsStore\.acceptCallerReputationResult\(/,
    'Caller ID must display only the transaction result'
  );
  assert.equal(
    (settings.match(/set\(value\)\s*\{\s*CALLER_REPUTATION_BOUNDARY\.change\s*\{/g) ?? []).length,
    2,
    'consent and protection mode setters must acquire the shared boundary'
  );
  assert.match(
    settings,
    /fun\s+applyRestorablePreferences\([\s\S]*?CALLER_REPUTATION_BOUNDARY\.change\s*\{/,
    'backup restore must not bypass revocation serialization'
  );
});
