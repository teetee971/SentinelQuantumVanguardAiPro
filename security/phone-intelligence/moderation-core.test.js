import test from 'node:test';
import assert from 'node:assert/strict';
import { SlidingWindowRateLimiter, createModerationService } from './moderation-core.js';

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

test('accepted reports require an explicit moderation decision', () => {
  const service = createModerationService({ idFactory: ids() });
  const submitted = service.submitReport(baseReport, 1_000);
  assert.equal(service.acceptedReports({ at: 2_000 }).length, 0);

  const moderated = service.moderateReport({
    report_id: submitted.report.id,
    decision: 'accepted',
    moderator_id: 'moderator-1',
    reason_code: 'EVIDENCE_REVIEWED',
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
  }, 4_000);
  assert.equal(decided.updated, true);
  assert.equal(decided.appeal.status, 'accepted');
});
