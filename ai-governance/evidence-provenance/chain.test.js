import assert from 'node:assert/strict';
import test from 'node:test';
import { appendEvidence, verifyEvidenceChain } from './chain.js';

test('evidence chain is valid after append', () => {
  let chain = [];
  chain = appendEvidence(chain, { id: 'e1', source: 'synthetic-lab', timestamp: '2026-09-02T00:00:00Z' });
  chain = appendEvidence(chain, { id: 'e2', source: 'synthetic-lab', timestamp: '2026-09-02T00:00:01Z', derived_from: ['e1'] });
  assert.deepEqual(verifyEvidenceChain(chain), { valid: true, reason: 'CHAIN_VALID' });
});

test('tampering is detected', () => {
  let chain = appendEvidence([], { id: 'e1', value: 'original' });
  chain[0].value = 'modified';
  assert.equal(verifyEvidenceChain(chain).valid, false);
});

test('broken parent link is detected', () => {
  let chain = appendEvidence([], { id: 'e1' });
  chain = appendEvidence(chain, { id: 'e2' });
  chain[1].previous_hash = 'bad';
  assert.equal(verifyEvidenceChain(chain).reason, 'CHAIN_LINK_INVALID');
});
