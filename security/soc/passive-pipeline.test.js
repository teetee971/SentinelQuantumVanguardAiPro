import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { createInMemoryReplayGuard } from '../../decision-plane/action-verification/anti-replay.js';
import { signingPayload } from '../../decision-plane/policy/proof-authenticity.js';
import { MAX_EVENTS, MAX_PAYLOAD_BYTES, processPassiveSocEvents, processPassiveSocEventsAsync } from './passive-pipeline.js';

const KEYS = generateKeyPairSync('ed25519');
const NOW = Date.parse('2026-09-15T12:00:00.000Z');
const TRUST = Object.freeze({
  authorizedIssuers: Object.freeze({ soc_event: new Set(['sensor-a', 'sensor-b']) }),
  revokedKeyIds: new Set(),
  resolvePublicKey: ({ keyId }) => keyId === 'soc-key-1' ? KEYS.publicKey : null,
});

function event(overrides = {}) {
  const value = {
    event_id: 'event-1',
    event_type: 'indicator_observed',
    source_id: 'source-a',
    subject: 'sha256:' + 'a'.repeat(64),
    payload: { verdict: 'suspicious', confidence: 70 },
    issued_at: '2026-09-15T11:59:00.000Z',
    expires_at: '2026-09-15T12:01:00.000Z',
    issuer_id: 'sensor-a',
    key_id: 'soc-key-1',
    signature_alg: 'ed25519',
    ...overrides,
  };
  value.signature = sign(null, signingPayload(value, 'soc_event'), KEYS.privateKey).toString('base64');
  return value;
}

function run(events, replayGuard = createInMemoryReplayGuard()) {
  return processPassiveSocEvents(events, { trust: TRUST, replayGuard, now: NOW });
}

test('accepts a signed bounded event and creates a passive case', () => {
  const result = run([event()]);
  assert.equal(result.valid, true);
  assert.equal(result.reason, 'PASSIVE_PIPELINE_COMPLETE');
  assert.equal(result.cases.length, 1);
  assert.equal(result.cases[0].confidence_class, 'SINGLE_SOURCE');
  assert.equal(result.cases[0].autonomous_action, false);
  assert.equal(result.side_effect_performed, false);
});

test('rejects forged, expired and unsupported events', () => {
  const forged = event();
  forged.payload.confidence = 99;
  assert.equal(run([forged]).rejected[0].reason, 'PROOF_SIGNATURE_INVALID');

  assert.match(
    run([event({ expires_at: '2026-09-15T11:59:59.000Z' })]).rejected[0].reason,
    /^EVENT_WINDOW_PROOF_EXPIRED$/,
  );
  assert.equal(
    run([event({ event_type: 'contain_endpoint' })]).rejected[0].reason,
    'EVENT_TYPE_UNSUPPORTED',
  );
});

test('consumes event identifiers once and rejects replay', () => {
  const guard = createInMemoryReplayGuard();
  assert.equal(run([event()], guard).valid, true);
  const replay = run([event()], guard);
  assert.equal(replay.valid, false);
  assert.equal(replay.rejected[0].reason, 'REPLAY_DETECTED');
});

test('deduplicates identical observations and records independent sources', () => {
  const first = event();
  const second = event({ event_id: 'event-2', source_id: 'source-b', issuer_id: 'sensor-b' });
  const result = run([first, second]);
  assert.equal(result.valid, true);
  assert.equal(result.cases.length, 1);
  assert.equal(result.cases[0].source_count, 2);
  assert.equal(result.cases[0].confidence_class, 'MULTI_SOURCE');
  assert.deepEqual(result.cases[0].source_ids, ['source-a', 'source-b']);
});

test('enforces batch and payload limits', () => {
  assert.equal(run([]).reason, 'EVENT_BATCH_INVALID');
  assert.equal(
    run(Array.from({ length: MAX_EVENTS + 1 }, (_, i) => event({ event_id: `e-${i}` }))).reason,
    'EVENT_BATCH_INVALID',
  );
  const oversized = event({ payload: { text: 'x'.repeat(MAX_PAYLOAD_BYTES + 1) } });
  assert.equal(run([oversized]).rejected[0].reason, 'EVENT_PAYLOAD_TOO_LARGE');
});

test('fails closed without a durable anti-replay contract', () => {
  const result = processPassiveSocEvents([event()], { trust: TRUST, now: NOW });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ANTI_REPLAY_GUARD_REQUIRED');
});

test('uses the asynchronous pipeline for durable replay guards', async () => {
  const consumed = new Set();
  const replayGuard = {
    async consumeAtomically(key) {
      if (consumed.has(key)) return { valid: false, reason: 'REPLAY_DETECTED' };
      consumed.add(key);
      return { valid: true, reason: 'REPLAY_KEY_CONSUMED' };
    },
  };
  const first = await processPassiveSocEventsAsync([event()], { trust: TRUST, replayGuard, now: NOW });
  assert.equal(first.valid, true);
  const replay = await processPassiveSocEventsAsync([event()], { trust: TRUST, replayGuard, now: NOW });
  assert.equal(replay.valid, false);
  assert.equal(replay.rejected[0].reason, 'REPLAY_DETECTED');

  const wrongEntryPoint = processPassiveSocEvents([event({ event_id: 'event-sync' })], {
    trust: TRUST,
    replayGuard,
    now: NOW,
  });
  assert.equal(wrongEntryPoint.valid, false);
  assert.equal(wrongEntryPoint.rejected[0].reason, 'ASYNC_REPLAY_GUARD_REQUIRES_ASYNC_PIPELINE');
});
