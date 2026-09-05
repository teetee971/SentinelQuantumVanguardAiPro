import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createPostgresReplayGuard } from './postgres-replay-guard.js';

const execFileAsync = promisify(execFile);
const DATABASE_URL = process.env.DATABASE_URL || 'postgresql://postgres@127.0.0.1:5432/sentinel_test';

async function psql(sql, { tuplesOnly = false } = {}) {
  const args = ['--no-psqlrc', '--set', 'ON_ERROR_STOP=1'];
  if (tuplesOnly) args.push('--tuples-only', '--no-align', '--quiet');
  args.push(DATABASE_URL, '--command', sql);
  return execFileAsync('psql', args, { env: { ...process.env } });
}

async function execute(query) {
  const value = query.values?.[0];
  const escaped = String(value).replaceAll("'", "''");
  const { stdout } = await psql(
    `INSERT INTO sentinel_replay_consumptions (replay_key) VALUES ('${escaped}') ON CONFLICT (replay_key) DO NOTHING RETURNING replay_key;`,
    { tuplesOnly: true },
  );
  const returnedKeys = stdout
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  return { rows: returnedKeys.map((replay_key) => ({ replay_key })) };
}

before(async () => {
  const schema = await readFile(new URL('./postgres-replay-schema.sql', import.meta.url), 'utf8');
  await psql(schema);
  await psql('TRUNCATE sentinel_replay_consumptions;');
});

after(async () => {
  await psql('TRUNCATE sentinel_replay_consumptions;');
});

test('uses the real PostgreSQL uniqueness constraint for replay prevention', async () => {
  const guard = createPostgresReplayGuard({ execute });
  const first = await guard.consumeAtomically('authorization:integration-1');
  const second = await guard.consumeAtomically('authorization:integration-1');

  assert.deepEqual(first, { valid: true, reason: 'REPLAY_KEY_CONSUMED' });
  assert.deepEqual(second, { valid: false, reason: 'REPLAY_DETECTED' });
});

test('concurrent consumers can consume a replay key only once', async () => {
  const guard = createPostgresReplayGuard({ execute });
  const results = await Promise.all(
    Array.from({ length: 8 }, () => guard.consumeAtomically('authorization:integration-concurrent')),
  );

  assert.equal(results.filter((result) => result.valid).length, 1);
  assert.equal(results.filter((result) => result.reason === 'REPLAY_DETECTED').length, 7);
});
