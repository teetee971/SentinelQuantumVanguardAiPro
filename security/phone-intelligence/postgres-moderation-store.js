function unavailableStore(reason = 'DURABLE_MODERATION_EXECUTOR_REQUIRED') {
  const fail = async () => ({ persisted: false, reason });
  return Object.freeze({ createAppeal: fail, createReport: fail, decideAppeal: fail, moderateReport: fail, pruneExpired: fail });
}

const ID = /^[A-Za-z0-9._:-]{1,128}$/;
const E164 = /^\+[1-9]\d{6,14}$/;
const REASON = /^[A-Z][A-Z0-9_]{1,119}$/;
const REPORT_REASONS = new Set(['phishing', 'impersonation', 'payment', 'spam', 'other']);
const finalReportStates = new Set(['accepted', 'rejected', 'escalated']);
const finalAppealStates = new Set(['accepted', 'rejected']);
const finiteTime = (value) => Number.isFinite(value) && value >= 0;
const invalid = (reason) => Promise.resolve({ persisted: false, reason });

async function executeMutation(execute, query, successReason) {
  try {
    const result = await execute(query);
    if (!Array.isArray(result?.rows) || result.rows.length !== 1) {
      return { persisted: false, reason: 'DURABLE_MODERATION_CONFLICT' };
    }
    return { persisted: true, reason: successReason, record: Object.freeze({ ...result.rows[0] }) };
  } catch {
    return { persisted: false, reason: 'DURABLE_MODERATION_UNAVAILABLE' };
  }
}

export function createPostgresModerationStore({ execute } = {}) {
  if (typeof execute !== 'function') return unavailableStore();

  return Object.freeze({
    createReport(report) {
      if (!ID.test(report?.id || '') || !ID.test(report?.actor_id || '') || !E164.test(report?.number || '') ||
          !REPORT_REASONS.has(report?.reason) || typeof report?.note !== 'string' || report.note.length > 500 ||
          !Array.isArray(report?.evidence_refs) || report.evidence_refs.length > 8 ||
          report.evidence_refs.some((value) => typeof value !== 'string' || value.length < 1 || value.length > 240) ||
          !finiteTime(report?.created_at_ms) || !finiteTime(report?.expires_at_ms) || report.expires_at_ms <= report.created_at_ms) {
        return invalid('DURABLE_REPORT_INPUT_INVALID');
      }
      return executeMutation(execute, {
        text: `WITH inserted AS (
          INSERT INTO sentinel_phone_reports
            (report_id, actor_id, number_e164, reason_code, note, evidence_refs, created_at, updated_at, expires_at)
          VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $7, $8)
          ON CONFLICT DO NOTHING RETURNING *
        ), audited AS (
          SELECT sentinel_phone_append_audit('REPORT_SUBMITTED', actor_id, report_id, reason_code, created_at) AS audit_hash
          FROM inserted
        ) SELECT inserted.*, audited.audit_hash FROM inserted CROSS JOIN audited`,
        values: [report?.id, report?.actor_id, report?.number, report?.reason, report?.note,
          JSON.stringify(report?.evidence_refs ?? []), new Date(report?.created_at_ms), new Date(report?.expires_at_ms)],
      }, 'REPORT_PERSISTED');
    },

    moderateReport(report) {
      if (!ID.test(report?.id || '') || !finalReportStates.has(report?.status) || !REASON.test(report?.moderation_reason || '') ||
          !ID.test(report?.moderator_id || '') || !['human', 'automated'].includes(report?.decision_source) ||
          (report.decision_source === 'automated' && report.status !== 'escalated') || !finiteTime(report?.updated_at_ms)) {
        return invalid('DURABLE_MODERATION_INPUT_INVALID');
      }
      return executeMutation(execute, {
        text: `WITH updated AS (
          UPDATE sentinel_phone_reports SET status = $2, moderation_reason = $3, moderator_id = $4,
            decision_source = $5, updated_at = $6
          WHERE report_id = $1 AND status IN ('pending', 'escalated') AND updated_at <= $6
            AND ($5 <> 'automated' OR $2 = 'escalated') RETURNING *
        ), audited AS (
          SELECT sentinel_phone_append_audit('REPORT_' || upper(status), moderator_id, report_id, moderation_reason, updated_at) AS audit_hash
          FROM updated
        ) SELECT updated.*, audited.audit_hash FROM updated CROSS JOIN audited`,
        values: [report?.id, report?.status, report?.moderation_reason, report?.moderator_id,
          report?.decision_source, new Date(report?.updated_at_ms)],
      }, 'REPORT_MODERATION_PERSISTED');
    },

    createAppeal(appeal) {
      if (!ID.test(appeal?.id || '') || !ID.test(appeal?.report_id || '') || !ID.test(appeal?.actor_id || '') ||
          typeof appeal?.message !== 'string' || appeal.message.length < 1 || appeal.message.length > 1000 ||
          !finiteTime(appeal?.created_at_ms) || !finiteTime(appeal?.due_at_ms) || appeal.due_at_ms <= appeal.created_at_ms) {
        return invalid('DURABLE_APPEAL_INPUT_INVALID');
      }
      return executeMutation(execute, {
        text: `WITH inserted AS (
          INSERT INTO sentinel_phone_appeals
            (appeal_id, report_id, actor_id, message, created_at, updated_at, due_at)
          SELECT $1, report_id, $3, $4, $5, $5, $6 FROM sentinel_phone_reports
          WHERE report_id = $2 AND actor_id = $3 AND status IN ('accepted', 'rejected', 'escalated')
          ON CONFLICT DO NOTHING RETURNING *
        ), audited AS (
          SELECT sentinel_phone_append_audit('APPEAL_OPENED', actor_id, appeal_id, 'USER_APPEAL', created_at) AS audit_hash
          FROM inserted
        ) SELECT inserted.*, audited.audit_hash FROM inserted CROSS JOIN audited`,
        values: [appeal?.id, appeal?.report_id, appeal?.actor_id, appeal?.message,
          new Date(appeal?.created_at_ms), new Date(appeal?.due_at_ms)],
      }, 'APPEAL_PERSISTED');
    },

    decideAppeal(appeal) {
      if (!ID.test(appeal?.id || '') || !finalAppealStates.has(appeal?.status) || !ID.test(appeal?.reviewer_id || '') ||
          !REASON.test(appeal?.decision_reason || '') || typeof appeal?.decision_note !== 'string' ||
          appeal.decision_note.length < 1 || appeal.decision_note.length > 1000 || !finiteTime(appeal?.updated_at_ms)) {
        return invalid('DURABLE_APPEAL_DECISION_INPUT_INVALID');
      }
      return executeMutation(execute, {
        text: `WITH updated AS (
          UPDATE sentinel_phone_appeals SET status = $2, reviewer_id = $3, decision_reason = $4,
            decision_note = $5, decision_source = 'human', sla_status = CASE WHEN $6 <= due_at THEN 'met' ELSE 'breached' END,
            updated_at = $6 WHERE appeal_id = $1 AND status IN ('open', 'in_review') AND updated_at <= $6 RETURNING *
        ), audited AS (
          SELECT sentinel_phone_append_audit('APPEAL_' || upper(status), reviewer_id, appeal_id, decision_reason, updated_at) AS audit_hash
          FROM updated
        ) SELECT updated.*, audited.audit_hash FROM updated CROSS JOIN audited`,
        values: [appeal?.id, appeal?.status, appeal?.reviewer_id, appeal?.decision_reason,
          appeal?.decision_note, new Date(appeal?.updated_at_ms)],
      }, 'APPEAL_DECISION_PERSISTED');
    },

    async pruneExpired(at = Date.now()) {
      if (!finiteTime(at)) return { persisted: false, reason: 'DURABLE_PRUNE_TIME_INVALID' };
      try {
        const result = await execute({
          text: 'DELETE FROM sentinel_phone_reports WHERE expires_at <= $1 RETURNING report_id',
          values: [new Date(at)],
        });
        return { persisted: true, reason: 'EXPIRED_REPORTS_PRUNED', count: result?.rows?.length ?? 0 };
      } catch {
        return { persisted: false, reason: 'DURABLE_MODERATION_UNAVAILABLE' };
      }
    },
  });
}
