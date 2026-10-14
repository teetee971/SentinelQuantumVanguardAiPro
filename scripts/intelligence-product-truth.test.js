import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { auditProductTruth, loadProductTruthSources } from './check-android-product-truth.js';

function source() {
  const s = loadProductTruthSources();
  s.screeningProjection = readFileSync(resolve('native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/ScreeningProjection.kt'), 'utf8');
  s.intelligenceStore = readFileSync(resolve('native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceSnapshotStore.kt'), 'utf8');
  s.intelligenceRuntime = readFileSync(resolve('native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceCoreRuntime.kt'), 'utf8');
  return s;
}

test('rejects general intelligence access before respondToCall', () => {
  const s = source();
  s.callScreening = s.callScreening.replace(
    'respondToCall(callDetails, response.build())',
    'IntelligenceCoreRuntime.snapshotForNumber(this, "+33123456789")\n        respondToCall(callDetails, response.build())'
  );

  assert.ok(auditProductTruth(s).some((error) => error.includes('critical path')));
});

test('rejects community or UI risk fields entering ScreeningProjection', () => {
  const s = source();
  s.screeningProjection = s.screeningProjection.replace(
    'val intelligenceRevision: Long?',
    'val intelligenceRevision: Long?,\n    val communityScore: Int?,\n    val contactDisplayName: String?'
  );

  assert.ok(auditProductTruth(s).some((error) => error.includes('ScreeningProjection')));
});

test('rejects raw PII storage slots in the intelligence persistence boundary', () => {
  const s = source();
  s.intelligenceStore = s.intelligenceStore.replace(
    'private val maxAgeMs: Long',
    'private val maxAgeMs: Long,\n    private val rawPhoneNumber: String?,\n    private val messageBody: String?'
  );

  assert.ok(auditProductTruth(s).some((error) => error.includes('raw PII')));
});

test('rejects a general snapshot being used as direct automatic blocking authority', () => {
  const s = source();
  s.intelligenceRuntime += '\nfun autoBlock(snapshot: IntelligenceSnapshot) = snapshot.riskAssessment.enforcementAuthorized\n';

  assert.ok(auditProductTruth(s).some((error) => error.includes('automatic-blocking authority')));
});
