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

import { randomUUID } from 'node:crypto';
import { PostgresVpnLeaseSequenceAuthority } from '../../ops/vpn-gateway/lease-sequence-authority.js';

async function executeVpnSequence(query) {
  assert.match(query.values[0], /^[a-z0-9][a-z0-9-]{1,62}$/);
  const types = query.values.length === 3 ? 'text, bigint, text' : 'text';
  const values = [`'${query.values[0]}'`];
  if (query.values.length === 3) {
    assert.ok(Number.isSafeInteger(query.values[1]));
    values.push(String(query.values[1]));
    assert.match(query.values[2], /^[a-f0-9]{64}$/);
    values.push(`'${query.values[2]}'`);
  }
  const { stdout } = await psql(
    `PREPARE vpn_sequence_query (${types}) AS ${query.text}; EXECUTE vpn_sequence_query (${values.join(',')});`,
    { tuplesOnly: true },
  );
  return {
    rows: stdout
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(Boolean)
      .map(line => {
        const [sequence, state_digest, invalidated] = line.split('|');
        return { sequence, state_digest, invalidated: invalidated === 't' };
      }),
  };
}

before(async () => {
  await psql(await readFile(new URL('../../ops/vpn-gateway/lease-sequence-schema.sql', import.meta.url), 'utf8'));
});

test('real PostgreSQL VPN authority survives adapter recreation, invalidation and rollback attempts', async () => {
  const gateway = `test-${randomUUID()}`;
  try {
    const first = new PostgresVpnLeaseSequenceAuthority({ execute: executeVpnSequence });
    await assert.rejects(first.readMinimumSequence(gateway), /SEQUENCE_UNAVAILABLE/);
    assert.equal(await first.commitSequence(gateway, 7, "a".repeat(64)), 7);
    const restarted = new PostgresVpnLeaseSequenceAuthority({ execute: executeVpnSequence });
    assert.equal(await restarted.readMinimumSequence(gateway), 7);
    await assert.rejects(restarted.commitSequence(gateway, 7, "b".repeat(64)), /COMMIT_UNVERIFIED/);
    await assert.rejects(restarted.assertCommittedSnapshot(gateway, 7, "b".repeat(64)), /SNAPSHOT_UNCONFIRMED/);
    await restarted.assertCommittedSnapshot(gateway, 7, "a".repeat(64));
    assert.equal(await restarted.commitSequence(gateway, 7, "a".repeat(64)), 7);
    await assert.rejects(restarted.commitSequence(gateway, 6, "a".repeat(64)), /COMMIT_UNVERIFIED/);
    await assert.rejects(psql(`UPDATE sentinel_vpn_lease_sequences SET sequence = 6 WHERE gateway_id = '${gateway}'`), /ROLLBACK_REJECTED/);
    assert.equal(await restarted.readMinimumSequence(gateway), 7);

    assert.equal(await restarted.invalidateSnapshot(gateway, 7, "a".repeat(64)), true);
    assert.equal((await restarted.readCommit(gateway)).invalidated, true);
    await assert.rejects(
      restarted.assertCommittedSnapshot(gateway, 7, "a".repeat(64)),
      /SNAPSHOT_UNCONFIRMED/
    );
    await assert.rejects(
      restarted.commitSequence(gateway, 7, "a".repeat(64)),
      /COMMIT_UNVERIFIED/
    );
    await assert.rejects(
      psql(`UPDATE sentinel_vpn_lease_sequences SET invalidated = false WHERE gateway_id = '${gateway}'`),
      /REVALIDATION_REJECTED/
    );
    assert.equal(await restarted.commitSequence(gateway, 8, "c".repeat(64)), 8);
    assert.equal((await restarted.readCommit(gateway)).invalidated, false);
  } finally { await psql(`DELETE FROM sentinel_vpn_lease_sequences WHERE gateway_id = '${gateway}'`); }
});

test('concurrent PostgreSQL VPN authority writers preserve the maximum committed sequence', async () => {
  const gateway = `test-${randomUUID()}`;
  try {
    const authority = new PostgresVpnLeaseSequenceAuthority({ execute: executeVpnSequence });
    await authority.commitSequence(gateway, 1, "a".repeat(64));
    const results = await Promise.allSettled(Array.from({ length: 8 }, (_, index) =>
      new PostgresVpnLeaseSequenceAuthority({ execute: executeVpnSequence }).commitSequence(gateway, index + 2, "a".repeat(64))));
    assert.equal(results[7].status, 'fulfilled');
    for (const result of results) if (result.status === 'rejected') assert.match(result.reason.message, /COMMIT_UNVERIFIED/);
    assert.equal(await authority.readMinimumSequence(gateway), 9);
  } finally { await psql(`DELETE FROM sentinel_vpn_lease_sequences WHERE gateway_id = '${gateway}'`); }
});

import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { VpnGatewayProvisioningCore } from '../../ops/vpn-gateway/provisioning-core.js';
import { VpnLeaseStateStore } from '../../ops/vpn-gateway/lease-state-store.js';
import { persistVpnLeaseState } from '../../ops/vpn-gateway/trusted-state-persist.js';
import { restoreVpnLeaseState } from '../../ops/vpn-gateway/trusted-state-restore.js';

test('real PostgreSQL snapshot binding rejects a differently signed state at the same sequence', async () => {
  const gateway = `test-${randomUUID()}`;
  const dir = await mkdtemp(join(tmpdir(), 'sentinel-vpn-pg-'));
  const path = join(dir, 'leases.json');
  const secret = 'S'.repeat(32);
  const token = 'A'.repeat(32);
  const makeCore = () => new VpnGatewayProvisioningCore({ gateway: {
    id: gateway, endpointHost: 'vpn.example.com', endpointPort: 51820,
    gatewayPublicKey: Buffer.alloc(32, 7).toString('base64'), catalogSequence: 7,
    dnsServers: ['10.73.0.1'], clientIpv6Prefix: '2606:4700:abcd:1234::/64',
  }, accessToken: token, clock: () => 2_000_000_000_000 });
  const makeStore = () => new VpnLeaseStateStore({ path, secret, gatewayId: gateway });
  const authority = new PostgresVpnLeaseSequenceAuthority({ execute: executeVpnSequence });
  try {
    const source = makeCore();
    await persistVpnLeaseState({ core: source, stateStore: makeStore(), sequenceAuthority: authority });
    const target = makeCore();
    await restoreVpnLeaseState({ core: target, stateStore: makeStore(), sequenceAuthority: authority });
    assert.deepEqual(target.exportState(), source.exportState());
    source.provision({ gatewayId: gateway, devicePublicKey: Buffer.alloc(32, 8).toString('base64'), catalogSequence: 7, accessToken: token });
    assert.equal(await makeStore().save(source.exportState()), 1);
    const before = target.exportState();
    await assert.rejects(restoreVpnLeaseState({ core: target, stateStore: makeStore(), sequenceAuthority: authority }), /SNAPSHOT_UNCONFIRMED/);
    assert.deepEqual(target.exportState(), before);
  } finally {
    await psql(`DELETE FROM sentinel_vpn_lease_sequences WHERE gateway_id = '${gateway}'`);
    await rm(dir, { recursive: true, force: true });
  }
});
