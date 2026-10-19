import test from 'node:test';
import assert from 'node:assert/strict';
import { MailOAuthTransactionGate } from './oauth-transaction-gate.js';

const options = { approvedCallbacks: { gmail: ['https://sentinel.example/oauth/gmail/callback'], microsoft: ['https://sentinel.example/oauth/gmail/callback'] } };
const gateAt = (now) => new MailOAuthTransactionGate({ ...options, now });

const input = {
  accountId: 'user-1', provider: 'gmail',
  callbackUri: 'https://sentinel.example/oauth/gmail/callback',
  scopes: ['https://www.googleapis.com/auth/gmail.readonly']
};

test('valid account-bound OAuth state can be consumed only once', () => {
  const gate = gateAt(() => 1_000);
  const flow = gate.start(input);
  assert.equal(flow.codeChallengeMethod, 'S256');
  assert.equal(flow.codeChallenge.length, 43);
  const result = gate.consume({ ...input, state: flow.state });
  assert.ok(result?.codeVerifier);
  assert.deepEqual(result.expectedScopes, input.scopes);
  assert.equal(gate.consume({ ...input, state: flow.state }), null);
});

test('a callback for a different account consumes and rejects the state', () => {
  const gate = gateAt(() => 1_000);
  const flow = gate.start(input);
  assert.equal(gate.consume({ ...input, accountId: 'user-2', state: flow.state }), null);
  assert.equal(gate.consume({ ...input, state: flow.state }), null);
});

test('expiration and replay are blocked', () => {
  let now = 1_000;
  const gate = gateAt(() => now);
  const flow = gate.start(input);
  now += 300_001;
  assert.equal(gate.consume({ ...input, state: flow.state }), null);
});

test('a mutated callback URL or provider is rejected', () => {
  const gate = gateAt(() => 1_000);
  const flow = gate.start(input);
  assert.equal(gate.consume({ ...input, callbackUri: input.callbackUri + '/evil', state: flow.state }), null);
  const another = gate.start(input);
  assert.equal(gate.consume({ ...input, provider: 'microsoft', state: another.state }), null);
});

test('only least privilege read scopes are allowed', () => {
  const gate = new MailOAuthTransactionGate(options);
  assert.throws(() => gate.start({ ...input, scopes: ['https://mail.google.com/'] }), /SCOPE_UNAUTHORIZED/);
  assert.throws(() => gate.start({ ...input, scopes: ['Mail.Send'] }), /SCOPE_UNAUTHORIZED/);
  assert.throws(() => gate.start({ ...input, callbackUri: 'http://sentinel.example' }), /CALLBACK_INVALID/);
  assert.throws(() => gate.start({ ...input, provider: 'imap' }), /PROVIDER_UNSUPPORTED/);
});

test('Microsoft read scope passes but no write privileges are returned', () => {
  const gate = new MailOAuthTransactionGate(options);
  const ms = { ...input, provider: 'microsoft', scopes: ['Mail.Read'] };
  const flow = gate.start(ms);
  assert.deepEqual(gate.consume({ ...ms, state: flow.state })?.expectedScopes, ['Mail.Read']);
});

test('a valid HTTPS URL not on the provider allowlist is rejected', () => {
  const gate = new MailOAuthTransactionGate(options);
  assert.throws(
    () => gate.start({ ...input, callbackUri: 'https://attacker.example/receive' }),
    /CALLBACK_NOT_ALLOWLISTED/
  );
});

test('starting more than the bounded pending transaction limit is blocked', () => {
  const gate = new MailOAuthTransactionGate({ ...options, maxPending: 1, now: () => 1_000 });
  gate.start(input);
  assert.throws(() => gate.start(input), /PENDING_LIMIT/);
});

test('expired pending transactions are pruned before enforcing the limit', () => {
  let now = 1_000;
  const gate = new MailOAuthTransactionGate({ ...options, maxPending: 1, now: () => now });
  gate.start(input);
  now += 300_001;
  const replacement = gate.start(input);
  assert.equal(typeof replacement.state, 'string');
});

test('accidentally allowlisted insecure or credential-bearing redirect still fails closed', () => {
  const badUris = [
    'http://sentinel.example/oauth/callback',
    'https://user:password@sentinel.example/oauth/callback',
    'https://sentinel.example/oauth/callback#fragment',
    'https://sentinel.example:444/oauth/callback'
  ];
  for (const callbackUri of badUris) {
    const gate = new MailOAuthTransactionGate({
      approvedCallbacks: { gmail: [callbackUri] }
    });
    assert.throws(
      () => gate.start({ ...input, callbackUri }),
      /CALLBACK_INVALID/
    );
  }
});
