import assert from 'node:assert/strict';
import { runGovernanceFuzz } from './governance-fuzz.js';

const first = runGovernanceFuzz({ cases: 500, seed: 0x517e11 });
const second = runGovernanceFuzz({ cases: 500, seed: 0x517e11 });

assert.equal(first.passed, true);
assert.equal(first.crashes, 0);
assert.deepEqual(first, second);

console.log('governance fuzz tests: PASS');
