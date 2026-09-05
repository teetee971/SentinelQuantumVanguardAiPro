CREATE TABLE IF NOT EXISTS sentinel_replay_consumptions (
  replay_key TEXT PRIMARY KEY,
  consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
