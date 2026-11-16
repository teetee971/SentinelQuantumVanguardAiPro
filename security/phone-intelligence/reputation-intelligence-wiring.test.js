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
  assert.match(runtime, /fun prepareRemoteReputation\s*\(/);
  assert.match(runtime, /fun publishPreparedRemoteReputation\s*\(/);
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
    /IntelligenceCoreRuntime\.publishPreparedRemoteReputation/,
    'Caller ID must enqueue only the consented prepared result'
  );
});

test('caller reputation persistence and publication share the settings acceptance transaction', () => {
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');
  const settings = readFileSync(SETTINGS_PATH, 'utf8');
  const prepare = callerId.indexOf('IntelligenceCoreRuntime.prepareRemoteReputation(');
  const guardStart = callerId.indexOf('settingsStore.acceptCallerReputationResult(');
  const persist = callerId.indexOf('.persist(number, result');
  const publish = callerId.indexOf('IntelligenceCoreRuntime.publishPreparedRemoteReputation(');

  assert.ok(prepare >= 0 && prepare < guardStart, 'KeyStore fingerprint preparation must finish before the consent lock');
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
    /publish\s*=\s*\{[\s\S]*?IntelligenceCoreRuntime\.publishPreparedRemoteReputation\(preparedReputation\)/,
    'Intelligence Core publication must run inside the same acceptance transaction'
  );
  assert.match(
    settings,
    /fun\s*<T\s*:\s*Any>\s+acceptCallerReputationResult\s*\(/,
    'SettingsStore must own the acceptance boundary'
  );
});

test('prepared Core publication only enqueues and cannot initialize the Android Keystore under consent lock', () => {
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');
  const preparation = runtime.indexOf('fun prepareRemoteReputation(');
  const publication = runtime.indexOf('fun publishPreparedRemoteReputation(');
  const nextMember = runtime.indexOf('\n    /**', publication);

  assert.ok(preparation >= 0 && publication > preparation, 'separate preparation and publication APIs are required');
  assert.match(
    runtime.slice(preparation, publication),
    /stateFor\([\s\S]*?fingerprinter\.fingerprint\([\s\S]*?ReputationEvidenceAdapter\.fromRemoteResult/,
    'Core runtime and fingerprint work belong to preparation'
  );
  assert.match(
    runtime.slice(publication, nextMember > publication ? nextMember : undefined),
    /prepared\?\.enqueue\(\)/,
    'publication inside the lock must use the already prepared event'
  );
  assert.match(
    runtime,
    /fun enqueue\(\): PublishResult = sink\.publish\(event\)/,
    'prepared publication must be only a bounded sink enqueue'
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
