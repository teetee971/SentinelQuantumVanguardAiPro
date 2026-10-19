import test from 'node:test';
import assert from 'node:assert/strict';
import { MailOAuthTransactionGate } from './oauth-transaction-gate.js';

const input = {
  accountId: 'user-1', provider: 'gmail',
  callbackUri: 'https://sentinel.example/oauth/gmail/callback',
  scopes: ['https://www.googleapis.com/auth/gmail.readonly']
};

test('valid account-bound OAuth state can be consumed only once', () => {
  const gate = new MailOAuthTransactionGate({ now: () => 1_000 });
  const flow = gate.start(input);
  assert.equal(flow.codeChallengeMethod, 'S256');
  assert.equal(flow.codeChallenge.length, 43);
  const result = gate.consume({ ...input, state: flow.state });
  assert.ok(result?.codeVerifier);
  assert.deepEqual(result.expectedScopes, input.scopes);
  assert.equal(gate.consume({ ...input, state: flow.state }), null);
});

test('a callback for a different account consumes and rejects the state', () => {
  const gate = new MailOAuthTransactionGate({ now: () => 1_000 });
  const flow = gate.start(input);
  assert.equal(gate.consume({ ...input, accountId: 'user-2', state: flow.state }), null);
  assert.equal(gate.consume({ ...input, state: flow.state }), null);
});

test('expiration and replay are blocked', () => {
  let now = 1_000;
  const gate = new MailOAuthTransactionGate({ now: () => now });
  const flow = gate.start(input);
  now += 300_001;
  assert.equal(gate.consume({ ...input, state: flow.state }), null);
});

test('a mutated callback URL or provider is rejected', () => {
  const gate = new MailOAuthTransactionGate({ now: () => 1_000 });
  const flow = gate.start(input);
  assert.equal(gate.consume({ ...input, callbackUri: input.callbackUri + '/evil', state: flow.state }), null);
  const another = gate.start(input);
  assert.equal(gate.consume({ ...input, provider: 'microsoft', state: another.state }), null);
});

test('only least privilege read scopes are allowed', () => {
  const gate = new MailOAuthTransactionGate();
  assert.throws(() => gate.start({ ...input, scopes: ['https://mail.google.com/'] }), /SCOPE_UNAUTHORIZED/);
  assert.throws(() => gate.start({ ...input, scopes: ['Mail.Send'] }), /SCOPE_UNAUTHORIZED/);
  assert.throws(() => gate.start({ ...input, callbackUri: 'http://sentinel.example' }), /CALLBACK_INVALID/);
  assert.throws(() => gate.start({ ...input, provider: 'imap' }), /PROVIDER_UNSUPPORTED/);
});

test('Microsoft read scope passes but no write privileges are returned', () => {
  const gate = new MailOAuthTransactionGate();
  const ms = { ...input, provider: 'microsoft', scopes: ['Mail.Read'] };
  const flow = gate.start(ms);
  assert.deepEqual(gate.consume({ ...ms, state: flow.state })?.expectedScopes, ['Mail.Read']);
});
