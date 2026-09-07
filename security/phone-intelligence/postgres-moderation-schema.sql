CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION sentinel_phone_valid_evidence_refs(p_value JSONB) RETURNS BOOLEAN
LANGUAGE sql IMMUTABLE AS $$
  SELECT jsonb_typeof(p_value) = 'array'
    AND jsonb_array_length(p_value) <= 8
    AND NOT EXISTS (
      SELECT 1 FROM jsonb_array_elements(p_value) AS entries(item)
      WHERE jsonb_typeof(item) <> 'string' OR length(item #>> '{}') NOT BETWEEN 1 AND 240
    );
$$;

CREATE TABLE IF NOT EXISTS sentinel_phone_reports (
  report_id TEXT PRIMARY KEY CHECK (length(report_id) BETWEEN 1 AND 128),
  actor_id TEXT NOT NULL CHECK (actor_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
  number_e164 TEXT NOT NULL CHECK (number_e164 ~ '^\+[1-9][0-9]{6,14}$'),
  reason_code TEXT NOT NULL CHECK (reason_code IN ('phishing', 'impersonation', 'payment', 'spam', 'other')),
  note TEXT NOT NULL CHECK (length(note) <= 500),
  evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (sentinel_phone_valid_evidence_refs(evidence_refs)),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected', 'escalated')),
  moderation_reason TEXT CHECK (moderation_reason ~ '^[A-Z][A-Z0-9_]{1,119}$'),
  moderator_id TEXT CHECK (moderator_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
  decision_source TEXT CHECK (decision_source IN ('human', 'automated')),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  CHECK (updated_at >= created_at),
  CHECK (expires_at > created_at),
  CHECK (decision_source <> 'automated' OR status = 'escalated'),
  CHECK (status = 'pending' OR (moderation_reason IS NOT NULL AND moderator_id IS NOT NULL AND decision_source IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS sentinel_phone_reports_active_duplicate_idx
  ON sentinel_phone_reports (actor_id, number_e164)
  WHERE status IN ('pending', 'escalated');
CREATE INDEX IF NOT EXISTS sentinel_phone_reports_expiry_idx ON sentinel_phone_reports (expires_at);
CREATE INDEX IF NOT EXISTS sentinel_phone_reports_accepted_idx ON sentinel_phone_reports (updated_at)
  WHERE status = 'accepted';

CREATE TABLE IF NOT EXISTS sentinel_phone_appeals (
  appeal_id TEXT PRIMARY KEY CHECK (length(appeal_id) BETWEEN 1 AND 128),
  report_id TEXT NOT NULL REFERENCES sentinel_phone_reports(report_id) ON DELETE CASCADE,
  actor_id TEXT NOT NULL CHECK (actor_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'in_review', 'accepted', 'rejected')),
  message TEXT NOT NULL CHECK (length(message) BETWEEN 1 AND 1000),
  reviewer_id TEXT CHECK (reviewer_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
  decision_reason TEXT CHECK (decision_reason ~ '^[A-Z][A-Z0-9_]{1,119}$'),
  decision_note TEXT CHECK (length(decision_note) BETWEEN 1 AND 1000),
  decision_source TEXT CHECK (decision_source = 'human'),
  sla_status TEXT CHECK (sla_status IN ('met', 'breached')),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  due_at TIMESTAMPTZ NOT NULL,
  CHECK (updated_at >= created_at),
  CHECK (due_at > created_at),
  CHECK (status IN ('open', 'in_review') OR
    (reviewer_id IS NOT NULL AND decision_reason IS NOT NULL AND decision_note IS NOT NULL AND decision_source = 'human' AND sla_status IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS sentinel_phone_appeals_report_idx ON sentinel_phone_appeals (report_id);
CREATE INDEX IF NOT EXISTS sentinel_phone_appeals_overdue_idx ON sentinel_phone_appeals (due_at)
  WHERE status IN ('open', 'in_review');
CREATE UNIQUE INDEX IF NOT EXISTS sentinel_phone_appeals_one_open_idx ON sentinel_phone_appeals (report_id)
  WHERE status IN ('open', 'in_review');

CREATE TABLE IF NOT EXISTS sentinel_phone_moderation_audit (
  sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  previous_hash TEXT NOT NULL CHECK (previous_hash ~ '^[a-f0-9]{64}$'),
  event_hash TEXT NOT NULL UNIQUE CHECK (event_hash ~ '^[a-f0-9]{64}$'),
  action TEXT NOT NULL CHECK (action ~ '^[A-Z][A-Z0-9_]{1,63}$'),
  actor_id TEXT NOT NULL CHECK (actor_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
  subject_id TEXT NOT NULL CHECK (length(subject_id) BETWEEN 1 AND 128),
  reason_code TEXT NOT NULL CHECK (reason_code ~ '^[A-Za-z][A-Za-z0-9_]{1,119}$'),
  occurred_at TIMESTAMPTZ NOT NULL
);

CREATE OR REPLACE FUNCTION sentinel_phone_append_audit(
  p_action TEXT, p_actor_id TEXT, p_subject_id TEXT, p_reason_code TEXT, p_occurred_at TIMESTAMPTZ
) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
  v_previous TEXT;
  v_hash TEXT;
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext('sentinel_phone_moderation_audit'));
  SELECT event_hash INTO v_previous FROM sentinel_phone_moderation_audit ORDER BY sequence DESC LIMIT 1;
  v_previous := COALESCE(v_previous, repeat('0', 64));
  v_hash := encode(digest(convert_to(
    jsonb_build_object('previous_hash', v_previous, 'action', p_action, 'actor_id', p_actor_id,
      'subject_id', p_subject_id, 'reason_code', p_reason_code, 'occurred_at', p_occurred_at)::text,
    'UTF8'), 'sha256'), 'hex');
  INSERT INTO sentinel_phone_moderation_audit
    (previous_hash, event_hash, action, actor_id, subject_id, reason_code, occurred_at)
  VALUES (v_previous, v_hash, p_action, p_actor_id, p_subject_id, p_reason_code, p_occurred_at);
  RETURN v_hash;
END;
$$;

CREATE OR REPLACE FUNCTION sentinel_phone_reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'sentinel_phone_moderation_audit is append-only';
END;
$$;

DROP TRIGGER IF EXISTS sentinel_phone_audit_append_only ON sentinel_phone_moderation_audit;
CREATE TRIGGER sentinel_phone_audit_append_only
  BEFORE UPDATE OR DELETE ON sentinel_phone_moderation_audit
  FOR EACH ROW EXECUTE FUNCTION sentinel_phone_reject_audit_mutation();

DROP TRIGGER IF EXISTS sentinel_phone_audit_no_truncate ON sentinel_phone_moderation_audit;
CREATE TRIGGER sentinel_phone_audit_no_truncate
  BEFORE TRUNCATE ON sentinel_phone_moderation_audit
  FOR EACH STATEMENT EXECUTE FUNCTION sentinel_phone_reject_audit_mutation();

REVOKE ALL ON sentinel_phone_moderation_audit FROM PUBLIC;
REVOKE ALL ON FUNCTION sentinel_phone_append_audit(TEXT, TEXT, TEXT, TEXT, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION sentinel_phone_valid_evidence_refs(JSONB) FROM PUBLIC;
