import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const SECURITY_SOURCE_ROOT = resolve(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security'
);
const SERVICE_PATH = resolve(SECURITY_SOURCE_ROOT, 'SentinelCallScreeningService.kt');
const BLOCKLIST_PATH = resolve(SECURITY_SOURCE_ROOT, 'CallBlocklistStore.kt');
const FINGERPRINTER_PATH = resolve(SECURITY_SOURCE_ROOT, 'CallNumberFingerprinter.kt');
const RULE_ENGINE_PATH = resolve(SECURITY_SOURCE_ROOT, 'CallRuleEngine.kt');
const ARCEP_CATALOG_PATH = resolve(SECURITY_SOURCE_ROOT, 'ArcepVerifiedPrefixCatalog.kt');
const ROLE_POLICY_PATH = resolve(SECURITY_SOURCE_ROOT, 'AndroidRoleReadPolicy.kt');

const CALLBACK_START = '    override fun onScreenCall(callDetails: Call.Details) {';
const MANDATORY_RESPONSE = [
  '        responseBoundary.deliverResponse {',
  '            respondToCall(callDetails, response.build())',
  '        }'
].join('\n');
const INSERTION_ANCHOR = '        val rawCallerNumber = callDetails.handle?.schemeSpecificPart';

const forbiddenPreResponseIo = [
  /\bgetSharedPreferences\s*\(/,
  /\bPreferenceManager\s*\.\s*getDefaultSharedPreferences\s*\(/,
  /\bcontentResolver\s*\.\s*(?:query|insert|update|delete|open\w*)\s*\(/,
  /\bgetContentResolver\s*\(\s*\)\s*\.\s*(?:query|insert|update|delete|open\w*)\s*\(/,
  /\bRoom\s*\.\s*(?:databaseBuilder|inMemoryDatabaseBuilder)\s*\(/,
  /\bSQLiteDatabase\s*\.\s*(?:openDatabase|openOrCreateDatabase)\s*\(/,
  /\bFile(?:InputStream|OutputStream)?\s*\(/,
  /\bKeyStore\s*\.\s*getInstance\s*\(/,
  /\bKeyGenerator\s*\.\s*getInstance\s*\(/,
  /\bURL\s*\(/,
  /\bHttpURLConnection\b/,
  /\bOkHttpClient\b/,
  /\bRetrofit\b/,
  /\bstartActivity\s*\(/
];

const blockingIoMutations = [
  '        getSharedPreferences("sentinel-screening", MODE_PRIVATE)',
  '        PreferenceManager.getDefaultSharedPreferences(this)',
  '        contentResolver.query(uri, null, null, null, null)',
  '        getContentResolver().openInputStream(uri)',
  '        Room.databaseBuilder(this, ScreeningDb::class.java, "screening.db")',
  '        SQLiteDatabase.openDatabase("screening.db", null, 0)',
  '        File(filesDir, "screening.tmp").readText()',
  '        FileInputStream("screening.tmp")',
  '        KeyStore.getInstance("AndroidKeyStore")',
  '        KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore")',
  '        startActivity(Intent())'
];

const uncoveredBlockingIoMutations = [
  '        RandomAccessFile("screening.tmp", "r")',
  '        Files.readAllBytes(path)',
  '        openFileInput("screening.tmp")',
  '        openFileOutput("screening.tmp", MODE_PRIVATE)',
  '        Socket("127.0.0.1", 443)',
  '        future.get()'
];

function assertNoForbiddenIo(label, source) {
  for (const forbidden of forbiddenPreResponseIo) {
    assert.doesNotMatch(
      source,
      forbidden,
      `${label} must stay free of blocking, storage, network, Keystore or UI I/O: ${forbidden}`
    );
  }
}

function sliceBetween(source, startMarker, endMarker, label) {
  const start = source.indexOf(startMarker);
  assert.ok(start >= 0, `${label} start marker is missing`);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(end > start, `${label} end marker is missing`);
  return source.slice(start, end);
}

function injectAfter(source, sectionStartMarker, anchor, mutation, label) {
  const sectionStart = source.indexOf(sectionStartMarker);
  assert.ok(sectionStart >= 0, `${label} section is missing`);
  const anchorIndex = source.indexOf(anchor, sectionStart);
  assert.ok(anchorIndex >= sectionStart, `${label} mutation anchor is missing`);
  return `${source.slice(0, anchorIndex)}${mutation}\n${source.slice(anchorIndex)}`;
}

function assertPreResponseIoBoundary(source) {
  const callbackStart = source.indexOf(CALLBACK_START);
  const responseStart = source.indexOf(MANDATORY_RESPONSE, callbackStart);

  assert.ok(callbackStart >= 0, 'CallScreening callback boundary is missing');
  assert.ok(responseStart > callbackStart, 'mandatory CallScreening response boundary is missing');

  const preResponse = source.slice(
    callbackStart,
    responseStart + MANDATORY_RESPONSE.length
  );

  assertNoForbiddenIo('CallScreening pre-response callback', preResponse);
}

function assertTransitiveHelperPurity({ blocklist, fingerprinter, ruleEngine, arcepCatalog, rolePolicy }) {
  const cachedSnapshot = sliceBetween(
    blocklist,
    '        internal fun cachedSnapshotForScreening(now: Long = System.currentTimeMillis()): Snapshot =',
    '\n    }\n}',
    'CallBlocklistStore.cachedSnapshotForScreening'
  );
  const activeSnapshot = sliceBetween(
    blocklist,
    '        fun activeAt(now: Long): Snapshot = copy(',
    '\n\n        val effectiveBlockedPrefixes',
    'CallBlocklistStore.Snapshot.activeAt'
  );
  const effectivePrefixes = sliceBetween(
    blocklist,
    '        val effectiveBlockedPrefixes: Set<String>',
    '\n    }\n\n    private fun encodeMetadata',
    'CallBlocklistStore.Snapshot.effectiveBlockedPrefixes'
  );
  const cachedCandidates = sliceBetween(
    fingerprinter,
    '    fun cachedCandidates(normalizedNumber: String): Set<String> {',
    '\n\n    /**\n     * Loads existing Keystore keys before CallScreeningService can run.',
    'CallNumberFingerprinter.cachedCandidates'
  );
  const hmacStart = fingerprinter.indexOf('internal fun computeHmacFingerprint(');
  assert.ok(hmacStart >= 0, 'computeHmacFingerprint helper is missing');
  const pureHmac = fingerprinter.slice(hmacStart);

  assertNoForbiddenIo('CallBlocklistStore.cachedSnapshotForScreening', cachedSnapshot);
  assertNoForbiddenIo('CallBlocklistStore.Snapshot.activeAt', activeSnapshot);
  assertNoForbiddenIo('CallBlocklistStore.Snapshot.effectiveBlockedPrefixes', effectivePrefixes);
  assertNoForbiddenIo('CallNumberFingerprinter.cachedCandidates', cachedCandidates);
  assertNoForbiddenIo('computeHmacFingerprint', pureHmac);
  assertNoForbiddenIo('CallRuleEngine', ruleEngine);
  assertNoForbiddenIo('ArcepVerifiedPrefixCatalog', arcepCatalog);
  assertNoForbiddenIo('AndroidRoleReadPolicy', rolePolicy);
}

function readHelperSources() {
  return {
    blocklist: readFileSync(BLOCKLIST_PATH, 'utf8'),
    fingerprinter: readFileSync(FINGERPRINTER_PATH, 'utf8'),
    ruleEngine: readFileSync(RULE_ENGINE_PATH, 'utf8'),
    arcepCatalog: readFileSync(ARCEP_CATALOG_PATH, 'utf8'),
    rolePolicy: readFileSync(ROLE_POLICY_PATH, 'utf8')
  };
}

test('current CallScreening callback remains free of generic blocking or UI I/O before response', () => {
  assertPreResponseIoBoundary(readFileSync(SERVICE_PATH, 'utf8'));
});

test('contract rejects generic blocking or UI I/O before the mandatory call-screening response', () => {
  const source = readFileSync(SERVICE_PATH, 'utf8');
  assert.ok(source.includes(INSERTION_ANCHOR));

  for (const mutation of blockingIoMutations) {
    const mutated = source.replace(
      INSERTION_ANCHOR,
      `${mutation}\n${INSERTION_ANCHOR}`
    );

    assert.throws(
      () => assertPreResponseIoBoundary(mutated),
      /must stay free of blocking, storage, network, Keystore or UI I\/O/,
      `expected pre-response I/O rejection for: ${mutation}`
    );
  }
});

test('contract rejects additional blocking primitives before the mandatory call-screening response', () => {
  const source = readFileSync(SERVICE_PATH, 'utf8');
  assert.ok(source.includes(INSERTION_ANCHOR));

  for (const mutation of uncoveredBlockingIoMutations) {
    const mutated = source.replace(
      INSERTION_ANCHOR,
      `${mutation}\n${INSERTION_ANCHOR}`
    );

    assert.throws(
      () => assertPreResponseIoBoundary(mutated),
      /must stay free of blocking, storage, network, Keystore or UI I\/O/,
      `expected uncovered pre-response I/O rejection for: ${mutation}`
    );
  }
});

test('transitive helpers used before respondToCall remain memory-only or CPU-only', () => {
  assertTransitiveHelperPurity(readHelperSources());
});

test('transitive helper contract catches hidden storage or Keystore regressions', () => {
  const sources = readHelperSources();

  const mutatedBlocklist = injectAfter(
    sources.blocklist,
    '        internal fun cachedSnapshotForScreening(now: Long = System.currentTimeMillis()): Snapshot =',
    '            SCREENING_SNAPSHOT.activeAt(now)',
    '            getSharedPreferences("screening-regression", 0)',
    'cached snapshot helper'
  );
  assert.throws(
    () => assertTransitiveHelperPurity({ ...sources, blocklist: mutatedBlocklist }),
    /CallBlocklistStore\.cachedSnapshotForScreening must stay free/,
    'hidden SharedPreferences access in a pre-response helper must fail closed'
  );

  const mutatedFingerprinter = injectAfter(
    sources.fingerprinter,
    '    fun cachedCandidates(normalizedNumber: String): Set<String> {',
    '        val values = linkedSetOf<String>()',
    '        KeyStore.getInstance("AndroidKeyStore")',
    'cached fingerprint helper'
  );
  assert.throws(
    () => assertTransitiveHelperPurity({ ...sources, fingerprinter: mutatedFingerprinter }),
    /CallNumberFingerprinter\.cachedCandidates must stay free/,
    'hidden AndroidKeyStore access in a pre-response helper must fail closed'
  );
});
