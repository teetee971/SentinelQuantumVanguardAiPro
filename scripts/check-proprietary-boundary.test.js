import test from 'node:test';
import assert from 'node:assert/strict';
import { validateDistributedText } from './check-proprietary-boundary.js';

test('ordinary public client code is allowed', () => {
  assert.deepEqual(validateDistributedText('fetch("/v1/intelligence/lookup")', 'client.js'), []);
});

test('server-only secret identifiers are rejected', () => {
  const errors = validateDistributedText('const key = process.env.MODERATION_API_KEY;', 'client.js');
  assert.ok(errors.some((error) => error.includes('server-only identifier exposed')));
});

test('server-only authentication headers are rejected', () => {
  const errors = validateDistributedText('headers: { "X-Report-Key": token }', 'client.js');
  assert.ok(errors.some((error) => error.includes('server-only authentication header exposed')));
});

test('internal moderation route is rejected', () => {
  const errors = validateDistributedText('fetch("/v1/intelligence/moderation/pending")', 'client.js');
  assert.ok(errors.some((error) => error.includes('internal/admin API route exposed')));
});

test('public low-trust reporting route remains allowed', () => {
  assert.deepEqual(validateDistributedText('fetch("/v1/intelligence/report-public")', 'client.js'), []);
});
