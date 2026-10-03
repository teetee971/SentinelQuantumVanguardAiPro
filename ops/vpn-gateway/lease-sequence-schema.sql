-- Provision independently of the gateway snapshot volume. Runtime credentials must not own
-- the table or have DELETE/TRUNCATE/DDL rights; custody and backups require external review.
BEGIN;
CREATE TABLE IF NOT EXISTS sentinel_vpn_lease_sequences (
  gateway_id text PRIMARY KEY CHECK (gateway_id ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
  sequence bigint NOT NULL CHECK (sequence BETWEEN 1 AND 9007199254740991),
  state_digest text NOT NULL CHECK (state_digest ~ '^[a-f0-9]{64}$'),
  invalidated boolean NOT NULL DEFAULT false
);
ALTER TABLE sentinel_vpn_lease_sequences
  ADD COLUMN IF NOT EXISTS invalidated boolean NOT NULL DEFAULT false;

CREATE OR REPLACE FUNCTION sentinel_vpn_reject_sequence_rollback() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.gateway_id <> OLD.gateway_id OR NEW.sequence < OLD.sequence THEN
    RAISE EXCEPTION 'VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED';
  END IF;
  IF NEW.sequence = OLD.sequence AND NEW.state_digest <> OLD.state_digest THEN
    RAISE EXCEPTION 'VPN_SEQUENCE_AUTHORITY_DIGEST_REWRITE_REJECTED';
  END IF;
  IF NEW.sequence = OLD.sequence AND OLD.invalidated AND NOT NEW.invalidated THEN
    RAISE EXCEPTION 'VPN_SEQUENCE_AUTHORITY_REVALIDATION_REJECTED';
  END IF;
  IF NEW.sequence > OLD.sequence AND NEW.invalidated THEN
    RAISE EXCEPTION 'VPN_SEQUENCE_AUTHORITY_NEW_SEQUENCE_INVALIDATED';
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS sentinel_vpn_monotonic_sequence ON sentinel_vpn_lease_sequences;
CREATE TRIGGER sentinel_vpn_monotonic_sequence BEFORE UPDATE ON sentinel_vpn_lease_sequences
FOR EACH ROW EXECUTE FUNCTION sentinel_vpn_reject_sequence_rollback();

COMMIT;
