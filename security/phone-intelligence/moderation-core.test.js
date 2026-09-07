import test from 'node:test';
import assert from 'node:assert/strict';
import { AbuseScoreGate, SlidingWindowRateLimiter, createModerationService } from './moderation-core.js';

function ids() {
  let value = 0;
  return () => `id-${++value}`;
}

const baseReport = {
  actor_id: 'actor-1',
  number: '+33612345678',
  reason: 'phishing',
  note: 'SMS demandant une authentification bancaire.',
  evidence_refs: ['local-analysis:sample-1'],
};

test('rate limiter returns a bounded retry interval after the limit', () => {
  const limiter = new SlidingWindowRateLimiter({ limit: 2, windowMs: 10_000, maxKeys: 10 });
  assert.equal(limiter.consume('actor', 1_000).allowed, true);
  assert.equal(limiter.consume('actor', 2_000).allowed, true);
  const blocked = limiter.consume('actor', 3_000);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, 'RATE_LIMITED');
  assert.equal(blocked.retryAfterMs, 8_000);
  assert.equal(limiter.consume('actor', 12_000).allowed, true);
});

test('rate limiter prunes expired subjects before rejecting capacity', () => {
  const limiter = new SlidingWindowRateLimiter({ limit: 2, windowMs: 1_000, maxKeys: 1 });
  assert.equal(limiter.consume('actor-a', 1_000).allowed, true);
  const admitted = limiter.consume('actor-b', 2_001);
  assert.equal(admitted.allowed, true);
  assert.equal(admitted.reason, 'RATE_LIMIT_OK');
});

test('rate limiter fails closed when an actor clock moves backwards', () => {
  const limiter = new SlidingWindowRateLimiter({ limit: 2, windowMs: 10_000, maxKeys: 10 });
  assert.equal(limiter.consume('actor', 2_000).allowed, true);
  const regressed = limiter.consume('actor', 1_999);
  assert.equal(regressed.allowed, false);
  assert.equal(regressed.reason, 'RATE_LIMIT_TIME_REGRESSION');
});

test('rate limiter clock regression cannot prune another actor history', () => {
  const limiter = new SlidingWindowRateLimiter({ limit: 1, windowMs: 10_000, maxKeys: 1 });
  assert.equal(limiter.consume('actor-a', 2_000).allowed, true);
  assert.equal(limiter.consume('actor-b', 1_999).reason, 'RATE_LIMIT_TIME_REGRESSION');
  assert.equal(limiter.consume('actor-a', 2_001).reason, 'RATE_LIMITED');
});

test('abuse score gate blocks explicitly signaled actors and expires the signal', () => {
  const gate = new AbuseScoreGate({ threshold: 100, ttlMs: 10_000, maxSubjects: 10 });
  assert.equal(gate.record('actor-1', 60, 1_000).recorded, true);
  assert.equal(gate.check('actor-1', 2_000).allowed, true);
  assert.equal(gate.record('actor-1', 40, 3_000).score, 100);
  assert.equal(gate.check('actor-1', 4_000).reason, 'ABUSE_GUARD_BLOCKED');
  assert.equal(gate.check('actor-1', 13_001).allowed, true);
});

test('report submission is bounded and duplicate reports are suppressed', () => {
  const service = createModerationService({ idFactory: ids(), reportLimiter: new SlidingWindowRateLimiter({ limit: 5 }) });
  const first = service.submitReport(baseReport, 1_000);
  assert.equal(first.accepted, true);
  assert.equal(first.report.status, 'pending');

  const duplicate = service.submitReport(baseReport, 2_000);
  assert.equal(duplicate.accepted, false);
  assert.equal(duplicate.reason, 'REPORT_DUPLICATE');

  assert.equal(service.submitReport({ ...baseReport, number: '0612345678' }, 3_000).reason, 'REPORT_NUMBER_INVALID');
  assert.equal(service.submitReport({ ...baseReport, reason: 'fraud' }, 3_000).reason, 'REPORT_REASON_INVALID');
});

test('moderation service consults the abuse gate before accepting a report', () => {
  const abuseGate = new AbuseScoreGate({ threshold: 10, ttlMs: 10_000 });
  abuseGate.record('actor-1', 10, 1_000);
  const service = createModerationService({ idFactory: ids(), abuseGate });
  const blocked = service.submitReport(baseReport, 2_000);
  assert.equal(blocked.accepted, false);
  assert.equal(blocked.reason, 'ABUSE_GUARD_BLOCKED');
});

test('report store is bounded and expired reports free capacity', () => {
  const service = createModerationService({
    idFactory: ids(),
    retentionMs: 60_000,
    appealSlaMs: 60_000,
    maxReports: 1,
    duplicateWindowMs: 1_000,
    reportLimiter: new SlidingWindowRateLimiter({ limit: 10 }),
  });
  assert.equal(service.submitReport(baseReport, 1_000).accepted, true);
  assert.equal(service.submitReport({ ...baseReport, actor_id: 'actor-2', number: '+33612345679' }, 2_000).reason, 'REPORT_CAPACITY_REACHED');
  assert.equal(service.submitReport({ ...baseReport, actor_id: 'actor-2', number: '+33612345679' }, 61_001).accepted, true);
});

test('pruning an expired report preserves a newer duplicate index entry', () => {
  const service = createModerationService({
    idFactory: ids(),
    retentionMs: 120_000,
    appealSlaMs: 60_000,
    duplicateWindowMs: 60_000,
    maxReports: 3,
    reportLimiter: new SlidingWindowRateLimiter({ limit: 20, windowMs: 1_000 }),
  });

  assert.equal(service.submitReport(baseReport, 1_000).accepted, true);
  const newer = service.submitReport(baseReport, 61_001);
  assert.equal(newer.accepted, true);
  assert.equal(service.submitReport({ ...baseReport, actor_id: 'actor-2', number: '+33612345679' }, 120_500).accepted, true);

  const capacityTrigger = service.submitReport({ ...baseReport, actor_id: 'actor-3', number: '+33612345680' }, 121_000);
  assert.equal(capacityTrigger.accepted, true);

  const duplicate = service.submitReport(baseReport, 121_000);
  assert.equal(duplicate.accepted, false);
  assert.equal(duplicate.reason, 'REPORT_DUPLICATE');
  assert.equal(duplicate.report_id, newer.report.id);
});

test('moderation rejects a duplicate submission whose clock moves backwards', () => {
  const service = createModerationService({
    idFactory: ids(),
    reportLimiter: { consume: () => ({ allowed: true }) },
  });
  assert.equal(service.submitReport(baseReport, 2_000).accepted, true);
  const regressed = service.submitReport(baseReport, 1_999);
  assert.equal(regressed.accepted, false);
  assert.equal(regressed.reason, 'REPORT_TIME_REGRESSION');
});

test('retention is enforced on reads and cascades to appeals', () => {
  const service = createModerationService({ idFactory: ids(), retentionMs: 60_000, duplicateWindowMs: 60_000, appealSlaMs: 60_000 });
  const submitted = service.submitReport(baseReport, 1_000);
  service.moderateReport({
    report_id: submitted.report.id,
    decision: 'rejected',
    moderator_id: 'moderator-1',
    reason_code: 'INSUFFICIENT_EVIDENCE',
    decision_source: 'human',
  }, 2_000);
  const opened = service.fileAppeal({
    report_id: submitted.report.id,
    actor_id: 'actor-1',
    message: 'Merci de réexaminer les éléments.',
  }, 3_000);

  assert.ok(service.getReport(submitted.report.id, { at: 60_999 }));
  assert.equal(service.getReport(submitted.report.id, { at: 61_000 }), null);
  assert.equal(service.getAppeal(opened.appeal.id, { at: 61_000 }), null);
});

test('duplicate suppression cannot outlive report retention', () => {
  assert.throws(
    () => createModerationService({ retentionMs: 60_000, duplicateWindowMs: 60_001, appealSlaMs: 60_000 }),
    /MODERATION_CONFIG_INVALID/,
  );
});

test('accepted reports require an explicit moderation decision', () => {
  const service = createModerationService({ idFactory: ids() });
  const submitted = service.submitReport(baseReport, 1_000);
  assert.equal(service.acceptedReports({ at: 2_000 }).length, 0);

  const moderated = service.moderateReport({
    report_id: submitted.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: 'EVIDENCE_REVIEWED',
    decision_source: 'human',
  }, 2_000);
  assert.equal(moderated.updated, true);
  assert.equal(service.acceptedReports({ at: 3_000 }).length, 1);
});

test('appeal requires ownership and a completed moderation decision', () => {
  const service = createModerationService({ idFactory: ids() });
  const submitted = service.submitReport(baseReport, 1_000);
  assert.equal(service.fileAppeal({ report_id: submitted.report.id, actor_id: 'actor-1', message: 'review' }, 2_000).reason, 'APPEAL_PREMATURE');

  service.moderateReport({
    report_id: submitted.report.id,
    decision: 'rejected',
    moderator_id: 'moderator-1',
    reason_code: 'INSUFFICIENT_EVIDENCE',
    decision_source: 'human',
  }, 2_000);
  assert.equal(service.fileAppeal({ report_id: submitted.report.id, actor_id: 'actor-2', message: 'review' }, 3_000).reason, 'APPEAL_ACTOR_MISMATCH');

  const opened = service.fileAppeal({ report_id: submitted.report.id, actor_id: 'actor-1', message: 'Merci de réexaminer les éléments.' }, 3_000);
  assert.equal(opened.accepted, true);
  assert.equal(opened.appeal.status, 'open');

  const decided = service.decideAppeal({
    appeal_id: opened.appeal.id,
    decision: 'accepted',
    reviewer_id: 'reviewer-1',
    decision_note: 'Recours accepté après contrôle.',
    reason_code: 'EVIDENCE_CONFIRMED',
    decision_source: 'human',
  }, 4_000);
  assert.equal(decided.updated, true);
  assert.equal(decided.appeal.status, 'accepted');
});

test('automation may escalate but cannot accept or reject reports', () => {
  const service = createModerationService({ idFactory: ids() });
  const first = service.submitReport(baseReport, 1_000);
  assert.equal(service.moderateReport({
    report_id: first.report.id,
    decision: 'accepted',
    moderator_id: 'automation-1',
    reason_code: 'MODEL_SCORE',
    decision_source: 'automated',
  }, 2_000).reason, 'AUTOMATION_DECISION_FORBIDDEN');

  const escalated = service.moderateReport({
    report_id: first.report.id,
    decision: 'escalated',
    moderator_id: 'automation-1',
    reason_code: 'HUMAN_REVIEW_REQUIRED',
    decision_source: 'automated',
  }, 2_000);
  assert.equal(escalated.updated, true);
  assert.equal(escalated.report.decision_source, 'automated');
  assert.equal(service.acceptedReports({ at: 2_001 }).length, 0);
});

test('final moderation decisions cannot be rewritten or backdated', () => {
  const service = createModerationService({ idFactory: ids() });
  const submitted = service.submitReport(baseReport, 1_000);
  const accepted = service.moderateReport({
    report_id: submitted.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: 'EVIDENCE_REVIEWED',
    decision_source: 'human',
  }, 3_000);
  assert.equal(accepted.updated, true);
  assert.equal(service.moderateReport({
    report_id: submitted.report.id,
    decision: 'rejected',
    moderator_id: 'moderator-2',
    reason_code: 'LATE_OVERRIDE',
    decision_source: 'human',
  }, 4_000).reason, 'MODERATION_ALREADY_FINAL');

  const escalatedReport = service.submitReport({ ...baseReport, number: '+33612345679' }, 4_000);
  service.moderateReport({
    report_id: escalatedReport.report.id,
    decision: 'escalated',
    moderator_id: 'automation-1',
    reason_code: 'HUMAN_REVIEW_REQUIRED',
    decision_source: 'automated',
  }, 6_000);
  assert.equal(service.moderateReport({
    report_id: escalatedReport.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: 'EVIDENCE_REVIEWED',
    decision_source: 'human',
  }, 5_999).reason, 'MODERATION_TIME_INVALID');
});

test('appeal SLA is observable and only a human can decide an appeal', () => {
  const service = createModerationService({ idFactory: ids(), appealSlaMs: 60_000 });
  const submitted = service.submitReport(baseReport, 1_000);
  service.moderateReport({
    report_id: submitted.report.id,
    decision: 'rejected',
    moderator_id: 'moderator-1',
    reason_code: 'INSUFFICIENT_EVIDENCE',
    decision_source: 'human',
  }, 2_000);
  const opened = service.fileAppeal({
    report_id: submitted.report.id,
    actor_id: baseReport.actor_id,
    message: 'Merci de réexaminer les éléments.',
  }, 3_000);
  assert.equal(opened.appeal.due_at_ms, 63_000);
  assert.equal(service.overdueAppeals({ at: 63_000 }).length, 0);
  assert.equal(service.overdueAppeals({ at: 63_001 }).length, 1);

  const automated = service.decideAppeal({
    appeal_id: opened.appeal.id,
    decision: 'rejected',
    reviewer_id: 'automation-1',
    decision_note: 'Décision automatique.',
    reason_code: 'MODEL_SCORE',
    decision_source: 'automated',
  }, 63_001);
  assert.equal(automated.reason, 'APPEAL_HUMAN_REVIEW_REQUIRED');

  const human = service.decideAppeal({
    appeal_id: opened.appeal.id,
    decision: 'accepted',
    reviewer_id: 'reviewer-1',
    decision_note: 'Éléments confirmés après réexamen.',
    reason_code: 'EVIDENCE_CONFIRMED',
    decision_source: 'human',
  }, 63_001);
  assert.equal(human.updated, true);
  assert.equal(human.appeal.sla_status, 'breached');
});

test('audit trail records who what when and why in a verifiable bounded chain', () => {
  const service = createModerationService({ idFactory: ids() });
  const submitted = service.submitReport(baseReport, 1_000);
  service.moderateReport({
    report_id: submitted.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: 'EVIDENCE_REVIEWED',
    decision_source: 'human',
  }, 2_000);

  const trail = service.auditTrail();
  assert.equal(Object.isFrozen(trail), true);
  assert.equal(Object.isFrozen(trail[0]), true);
  assert.equal(trail.length, 2);
  assert.equal(trail[1].actor_id, 'moderator-1');
  assert.equal(trail[1].action, 'REPORT_ACCEPTED');
  assert.equal(trail[1].reason, 'EVIDENCE_REVIEWED');
  assert.equal(trail[1].occurred_at_ms, 2_000);
  assert.equal(trail[1].previous_hash, trail[0].hash);
  assert.equal(service.verifyAuditTrail(), true);
  assert.equal(JSON.stringify(trail).includes(baseReport.number), false);
  assert.equal(JSON.stringify(trail).includes(baseReport.note), false);
});

test('audit reason fields reject free text and sensitive values', () => {
  const service = createModerationService({ idFactory: ids() });
  const submitted = service.submitReport(baseReport, 1_000);
  const rejected = service.moderateReport({
    report_id: submitted.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: `token=${'a'.repeat(32)}`,
    decision_source: 'human',
  }, 2_000);
  assert.equal(rejected.updated, false);
  assert.equal(rejected.reason, 'MODERATION_REASON_REQUIRED');
  assert.equal(service.auditTrail().length, 1);
});

test('mutations fail closed when the audit trail reaches capacity', () => {
  const service = createModerationService({ idFactory: ids(), maxAuditEvents: 1 });
  const submitted = service.submitReport(baseReport, 1_000);
  assert.equal(submitted.accepted, true);
  const moderation = service.moderateReport({
    report_id: submitted.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: 'EVIDENCE_REVIEWED',
    decision_source: 'human',
  }, 2_000);
  assert.equal(moderation.updated, false);
  assert.equal(moderation.reason, 'AUDIT_CAPACITY_REACHED');
  assert.equal(service.getReport(submitted.report.id, { at: 2_001 }).status, 'pending');
});
