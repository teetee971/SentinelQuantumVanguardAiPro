import assert from 'node:assert/strict';
import { checkSentinelIsolation } from './check-sentinel-isolation.js';

const result = await checkSentinelIsolation();
assert.equal(result.passed, true, JSON.stringify(result.violations));
assert.equal(result.violations.length, 0);

console.log(`sentinel isolation test: PASS (${result.files_scanned} files scanned)`);
