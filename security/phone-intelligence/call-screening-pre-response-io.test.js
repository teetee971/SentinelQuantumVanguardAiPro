import test from 'node:test';
import assert from 'node:assert/strict';
import { auditProductTruth, loadProductTruthSources } from '../../scripts/check-android-product-truth.js';

const PRE_RESPONSE_ANCHOR = '        val rawCallerNumber = callDetails.handle?.schemeSpecificPart';

const blockingIoMutations = [
  '        getSharedPreferences("sentinel-screening", MODE_PRIVATE)',
  '        contentResolver.query(uri, null, null, null, null)',
  '        Room.databaseBuilder(this, ScreeningDb::class.java, "screening.db")',
  '        SQLiteDatabase.openDatabase("screening.db", null, 0)',
  '        File(filesDir, "screening.tmp").readText()',
  '        startActivity(Intent())'
];

test('rejects generic blocking or UI I/O before the mandatory call-screening response', () => {
  for (const mutation of blockingIoMutations) {
    const sources = loadProductTruthSources();
    assert.ok(sources.callScreening.includes(PRE_RESPONSE_ANCHOR));
    sources.callScreening = sources.callScreening.replace(
      PRE_RESPONSE_ANCHOR,
      `${mutation}\n${PRE_RESPONSE_ANCHOR}`
    );

    assert.ok(
      auditProductTruth(sources).some((error) => error.includes('pre-response I/O')),
      `expected pre-response I/O rejection for: ${mutation}`
    );
  }
});
