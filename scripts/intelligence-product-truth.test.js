import test from 'node:test';
import assert from 'node:assert/strict';
import {
  auditIntelligenceProductTruth,
  loadIntelligenceProductTruthSources
} from './intelligence-product-truth.js';
import { auditPhysicalEvidenceCollector } from './check-phone-core-physical-evidence.js';

const source = () => loadIntelligenceProductTruthSources();

test('current intelligence integration respects product truth boundaries', () => {
  assert.deepEqual(auditIntelligenceProductTruth(source()), []);
});

test('rejects general intelligence access before respondToCall', () => {
  const s = source();
  s.callScreening = s.callScreening.replace(
    'respondToCall(callDetails, response.build())',
    'IntelligenceCoreRuntime.snapshotForNumber(this, "+33123456789")\n        respondToCall(callDetails, response.build())'
  );

  assert.ok(auditIntelligenceProductTruth(s).some((error) => error.includes('critical path')));
});

test('rejects community or UI risk fields entering ScreeningProjection', () => {
  const s = source();
  s.screeningProjection = s.screeningProjection.replace(
    'val intelligenceRevision: Long?',
    'val intelligenceRevision: Long?,\n    val communityScore: Int?,\n    val contactDisplayName: String?'
  );

  assert.ok(auditIntelligenceProductTruth(s).some((error) => error.includes('ScreeningProjection')));
});

test('rejects raw PII storage slots in the intelligence persistence boundary', () => {
  const s = source();
  s.intelligenceStore = s.intelligenceStore.replace(
    'private val maxAgeMs: Long',
    'private val maxAgeMs: Long,\n    private val rawPhoneNumber: String?,\n    private val messageBody: String?'
  );

  assert.ok(auditIntelligenceProductTruth(s).some((error) => error.includes('raw PII')));
});

test('rejects a general snapshot being used as direct automatic blocking authority', () => {
  const s = source();
  s.intelligenceRuntime += '\nfun autoBlock(snapshot: IntelligenceSnapshot) = snapshot.riskAssessment.enforcementAuthorized\n';

  assert.ok(auditIntelligenceProductTruth(s).some((error) => error.includes('automatic-blocking authority')));
});

test('physical evidence collector rejects PII sources and automatic physical validation', () => {
  const safe = [
    '#!/usr/bin/env bash',
    'SOURCE_HEAD_SHA=test',
    'APK_SHA256=test',
    'adb shell getprop ro.build.fingerprint',
    'adb shell dumpsys package com.sentinel.quantum',
    'adb shell dumpsys role',
    'adb shell dumpsys telecom',
    'adb logcat -b crash -d',
    'adb shell dumpsys batterystats',
    'OBSERVED MISSING NOT_EXECUTED',
    'physical-evidence.json',
    'commercial_release_claim=false',
  ].join('\n');
  assert.deepEqual(auditPhysicalEvidenceCollector(safe), []);

  for (const forbidden of [
    'content://sms',
    'content://mms',
    'content://contacts',
    'content://call_log',
    'subscription_id',
    'subscriber_id',
    'physically_validated=true',
    'commercial_release_claim=true',
  ]) {
    const errors = auditPhysicalEvidenceCollector(`${safe}\n${forbidden}`);
    assert.ok(errors.length > 0, `expected rejection for ${forbidden}`);
  }
});
