import { randomUUID } from 'node:crypto';

const REPORT_STATUSES = Object.freeze(['pending', 'accepted', 'rejected', 'escalated']);
const APPEAL_STATUSES = Object.freeze(['open', 'in_review', 'accepted', 'rejected']);
const REASONS = Object.freeze(['phishing', 'impersonation', 'payment', 'spam', 'other']);
const E164 = /^\+[1-9]\d{6,14}$/;
const ACTOR_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const MAX_NOTE_CHARS = 500;
const MAX_EVIDENCE_REFS = 8;
const MAX_EVIDENCE_REF_CHARS = 240;
const DEFAULT_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;

function boundedText(value, max) {
  const text = String(value ?? '').trim();
  return text.length <= max ? text : null;
}

class SlidingWindowRateLimiter {
  constructor({ limit = 20, windowMs = 60_000, maxKeys = 10_000 } = {}) {
    if (!Number.isInteger(limit) || limit < 1 || !Number.isInteger(windowMs) || windowMs < 1000 ||
        !Number.isInteger(maxKeys) || maxKeys < 1) throw new Error('RATE_LIMIT_CONFIG_INVALID');
    this.limit = limit;
    this.windowMs = windowMs;
    this.maxKeys = maxKeys;
    this.events = new Map();
  }

  consume(key, at = Date.now()) {
    if (!ACTOR_ID.test(String(key || '')) || !Number.isFinite(at) || at < 0) {
      return { allowed: false, reason: 'RATE_LIMIT_INPUT_INVALID', retryAfterMs: this.windowMs };
    }
    if (!this.events.has(key) && this.events.size >= this.maxKeys) {
      return { allowed: false, reason: 'RATE_LIMIT_CAPACITY_REACHED', retryAfterMs: this.windowMs };
    }
    const cutoff = at - this.windowMs;
    const recent = (this.events.get(key) || []).filter((value) => value > cutoff && value <= at);
    if (recent.length >= this.limit) {
      const retryAfterMs = Math.max(1, recent[0] + this.windowMs - at);
      this.events.set(key, recent);
      return { allowed: false, reason: 'RATE_LIMITED', retryAfterMs };
    }
    recent.push(at);
    this.events.set(key, recent);
    return { allowed: true, reason: 'RATE_LIMIT_OK', remaining: this.limit - recent.length };
  }
}

function createModerationService({
  reportLimiter = new SlidingWindowRateLimiter(),
  idFactory = randomUUID,
  retentionMs = DEFAULT_RETENTION_MS,
  duplicateWindowMs = 24 * 60 * 60 * 1000,
} = {}) {
  if (typeof idFactory !== 'function' || !Number.isInteger(retentionMs) || retentionMs < 60_000 ||
      !Number.isInteger(duplicateWindowMs) || duplicateWindowMs < 1000) throw new Error('MODERATION_CONFIG_INVALID');

  const reports = new Map();
  const appeals = new Map();

  function submitReport(input = {}, at = Date.now()) {
    if (!Number.isFinite(at) || at < 0) return { accepted: false, reason: 'REPORT_TIME_INVALID' };
    const actorId = String(input.actor_id || '').trim();
    const number = String(input.number || '').trim();
    const reason = String(input.reason || '').trim();
    const note = boundedText(input.note, MAX_NOTE_CHARS);
    const evidenceRefs = Array.isArray(input.evidence_refs) ? input.evidence_refs.map((value) => boundedText(value, MAX_EVIDENCE_REF_CHARS)) : [];

    if (!ACTOR_ID.test(actorId)) return { accepted: false, reason: 'REPORT_ACTOR_INVALID' };
    if (!E164.test(number)) return { accepted: false, reason: 'REPORT_NUMBER_INVALID' };
    if (!REASONS.includes(reason)) return { accepted: false, reason: 'REPORT_REASON_INVALID' };
    if (note === null || evidenceRefs.length > MAX_EVIDENCE_REFS || evidenceRefs.some((value) => value === null || !value)) {
      return { accepted: false, reason: 'REPORT_EVIDENCE_INVALID' };
    }

    const rate = reportLimiter.consume(actorId, at);
    if (!rate.allowed) return { accepted: false, reason: rate.reason, retry_after_ms: rate.retryAfterMs };

    const duplicate = [...reports.values()].find((report) =>
      report.actor_id === actorId && report.number === number && at - report.created_at_ms < duplicateWindowMs && at >= report.created_at_ms);
    if (duplicate) return { accepted: false, reason: 'REPORT_DUPLICATE', report_id: duplicate.id };

    const id = String(idFactory());
    if (!id || id.length > 128 || reports.has(id)) return { accepted: false, reason: 'REPORT_ID_INVALID' };
    const report = Object.freeze({
      id,
      actor_id: actorId,
      number,
      reason,
      note,
      evidence_refs: Object.freeze([...evidenceRefs]),
      status: 'pending',
      moderation_reason: null,
      created_at_ms: at,
      updated_at_ms: at,
      expires_at_ms: at + retentionMs,
    });
    reports.set(id, report);
    return { accepted: true, reason: 'REPORT_ACCEPTED_FOR_REVIEW', report };
  }

  function moderateReport({ report_id, decision, moderator_id, reason_code } = {}, at = Date.now()) {
    const report = reports.get(String(report_id || ''));
    if (!report) return { updated: false, reason: 'REPORT_NOT_FOUND' };
    if (!['accepted', 'rejected', 'escalated'].includes(decision)) return { updated: false, reason: 'MODERATION_DECISION_INVALID' };
    if (!ACTOR_ID.test(String(moderator_id || ''))) return { updated: false, reason: 'MODERATOR_ID_INVALID' };
    const reasonCode = boundedText(reason_code, 120);
    if (!reasonCode) return { updated: false, reason: 'MODERATION_REASON_REQUIRED' };
    if (!Number.isFinite(at) || at < report.created_at_ms) return { updated: false, reason: 'MODERATION_TIME_INVALID' };

    const updated = Object.freeze({ ...report, status: decision, moderation_reason: reasonCode, moderator_id, updated_at_ms: at });
    reports.set(report.id, updated);
    return { updated: true, reason: 'REPORT_MODERATED', report: updated };
  }

  function fileAppeal({ report_id, actor_id, message } = {}, at = Date.now()) {
    const report = reports.get(String(report_id || ''));
    if (!report) return { accepted: false, reason: 'REPORT_NOT_FOUND' };
    if (report.status === 'pending') return { accepted: false, reason: 'APPEAL_PREMATURE' };
    if (report.actor_id !== actor_id) return { accepted: false, reason: 'APPEAL_ACTOR_MISMATCH' };
    const text = boundedText(message, 1000);
    if (!text) return { accepted: false, reason: 'APPEAL_MESSAGE_INVALID' };
    if (!Number.isFinite(at) || at < report.updated_at_ms) return { accepted: false, reason: 'APPEAL_TIME_INVALID' };
    if ([...appeals.values()].some((appeal) => appeal.report_id === report.id && ['open', 'in_review'].includes(appeal.status))) {
      return { accepted: false, reason: 'APPEAL_ALREADY_OPEN' };
    }

    const id = String(idFactory());
    if (!id || id.length > 128 || appeals.has(id)) return { accepted: false, reason: 'APPEAL_ID_INVALID' };
    const appeal = Object.freeze({
      id,
      report_id: report.id,
      actor_id,
      status: 'open',
      message: text,
      decision_note: null,
      created_at_ms: at,
      updated_at_ms: at,
    });
    appeals.set(id, appeal);
    return { accepted: true, reason: 'APPEAL_OPENED', appeal };
  }

  function decideAppeal({ appeal_id, decision, reviewer_id, decision_note } = {}, at = Date.now()) {
    const appeal = appeals.get(String(appeal_id || ''));
    if (!appeal) return { updated: false, reason: 'APPEAL_NOT_FOUND' };
    if (!['accepted', 'rejected'].includes(decision)) return { updated: false, reason: 'APPEAL_DECISION_INVALID' };
    if (!ACTOR_ID.test(String(reviewer_id || ''))) return { updated: false, reason: 'APPEAL_REVIEWER_INVALID' };
    const note = boundedText(decision_note, 1000);
    if (!note) return { updated: false, reason: 'APPEAL_DECISION_NOTE_REQUIRED' };
    if (!Number.isFinite(at) || at < appeal.created_at_ms) return { updated: false, reason: 'APPEAL_TIME_INVALID' };

    const updated = Object.freeze({ ...appeal, status: decision, reviewer_id, decision_note: note, updated_at_ms: at });
    appeals.set(appeal.id, updated);
    return { updated: true, reason: 'APPEAL_DECIDED', appeal: updated };
  }

  function getReport(id) { return reports.get(id) || null; }
  function getAppeal(id) { return appeals.get(id) || null; }
  function acceptedReports({ at = Date.now() } = {}) {
    return [...reports.values()].filter((report) => report.status === 'accepted' && report.expires_at_ms > at);
  }

  return Object.freeze({ acceptedReports, decideAppeal, fileAppeal, getAppeal, getReport, moderateReport, submitReport });
}

export {
  APPEAL_STATUSES,
  DEFAULT_RETENTION_MS,
  MAX_EVIDENCE_REFS,
  MAX_NOTE_CHARS,
  REPORT_STATUSES,
  SlidingWindowRateLimiter,
  createModerationService,
};
