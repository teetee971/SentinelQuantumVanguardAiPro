import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { rejectDuplicateJsonKeys } from './strict-json-duplicate-keys.js';

const verifier = path.resolve('scripts/verify-phone-core-physical-session.js');

function runVerifier(rawManifest) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-duplicate-json-'));
  try {
    const manifestPath = path.join(tmp, 'session.json');
    const publicKeyPath = path.join(tmp, 'trusted-public-key.pem');
    fs.writeFileSync(manifestPath, rawManifest);
    fs.writeFileSync(publicKeyPath, 'not-used-before-strict-json-validation\n');
    return spawnSync(process.execPath, [
      verifier,
      manifestPath,
      '--trusted-public-key', publicKeyPath,
      '--expected-source-sha', 'a'.repeat(40),
      '--expected-apk-sha256', 'b'.repeat(64),
      '--expected-certificate-sha256', 'c'.repeat(64)
    ], { encoding: 'utf8' });
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

test('strict JSON guard accepts unique keys at every object depth', () => {
  assert.doesNotThrow(() => rejectDuplicateJsonKeys('{"a":1,"nested":{"a":2},"list":[{"b":3}]}'));
});

test('strict JSON guard rejects duplicate root keys', () => {
  assert.throws(
    () => rejectDuplicateJsonKeys('{"verdict":"FAIL","verdict":"PASS"}'),
    /duplicate object key.*verdict/i
  );
});

test('strict JSON guard rejects escaped-equivalent nested duplicate keys', () => {
  assert.throws(
    () => rejectDuplicateJsonKeys('{"entry":{"status":"FAIL","st\\u0061tus":"PASS"}}'),
    /duplicate object key.*status/i
  );
});

test('strict JSON guard permits the same key name in distinct objects', () => {
  assert.doesNotThrow(() => rejectDuplicateJsonKeys('[{"status":"PASS"},{"status":"FAIL"}]'));
});

test('physical session verifier fails closed on duplicate manifest keys before qualification logic', () => {
  const result = runVerifier('{"schema_version":1,"schema_version":1}');
  assert.equal(result.status, 1, `stdout=${result.stdout}\nstderr=${result.stderr}`);
  assert.match(result.stderr, /duplicate object key.*schema_version/i);
});
