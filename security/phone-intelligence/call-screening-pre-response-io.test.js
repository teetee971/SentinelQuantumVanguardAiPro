import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const SERVICE_PATH = resolve(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt'
);
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
  '        startActivity(Intent())'
];

function assertPreResponseIoBoundary(source) {
  const callbackStart = source.indexOf(CALLBACK_START);
  const responseStart = source.indexOf(MANDATORY_RESPONSE, callbackStart);

  assert.ok(callbackStart >= 0, 'CallScreening callback boundary is missing');
  assert.ok(responseStart > callbackStart, 'mandatory CallScreening response boundary is missing');

  const preResponse = source.slice(
    callbackStart,
    responseStart + MANDATORY_RESPONSE.length
  );

  for (const forbidden of forbiddenPreResponseIo) {
    assert.doesNotMatch(
      preResponse,
      forbidden,
      `pre-response I/O is forbidden in CallScreening: ${forbidden}`
    );
  }
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
      /pre-response I\/O is forbidden/,
      `expected pre-response I/O rejection for: ${mutation}`
    );
  }
});
