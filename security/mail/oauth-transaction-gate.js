import { createHash, randomBytes } from 'node:crypto';

/**
 * Server-side OAuth transaction guard. Not a deployed broker or token exchange.
 * Transactions remain process-local: production MUST replace this with a
 * shared TTL store supporting atomic consume and authenticated account binding.
 */
export class MailOAuthTransactionGate {
  #transactions = new Map();
  #now;
  constructor({ now = () => Date.now() } = {}) {
    this.#now = now;
  }

  start({ accountId, provider, callbackUri, scopes }) {
    if (typeof accountId !== 'string' || !accountId.trim()) throw new Error('ACCOUNT_REQUIRED');
    if (!['gmail', 'microsoft'].includes(provider)) throw new Error('PROVIDER_UNSUPPORTED');
    if (typeof callbackUri !== 'string' || !/^https:\/\/[^/?#@]+(?:\/[^?#]*)?$/.test(callbackUri))
      throw new Error('CALLBACK_INVALID');
    // Scopes are server-assigned. The caller may not request mutation scopes.
    const expectedScopes = provider === 'gmail'
      ? ['https://www.googleapis.com/auth/gmail.readonly']
      : ['Mail.Read'];
    if (!Array.isArray(scopes) || scopes.length !== expectedScopes.length ||
        !scopes.every((scope, i) => scope === expectedScopes[i]))
      throw new Error('SCOPE_UNAUTHORIZED');

    const state = randomBytes(32).toString('base64url');
    const codeVerifier = randomBytes(32).toString('base64url');
    const codeChallenge = createHash('sha256').update(codeVerifier).digest('base64url');
    // Store a digest rather than the state value. Never log the verifier.
    const key = createHash('sha256').update(state).digest('hex');
    this.#transactions.set(key, {
      accountId, provider, callbackUri, expectedScopes,
      codeVerifier, createdAtMs: this.#now()
    });
    return Object.freeze({ state, codeChallenge, codeChallengeMethod: 'S256' });
  }

  /**
   * Returns sensitive codeVerifier ONLY to the trusted backend, never a client.
   * An authorization code must still be redeemed against the provider token
   * endpoint with strict client settings and validated response.
   */
  consume({ accountId, provider, callbackUri, state }) {
    if (typeof state !== 'string' || state.length > 256) return null;
    const key = createHash('sha256').update(state).digest('hex');
    const transaction = this.#transactions.get(key);
    if (!transaction) return null;
    this.#transactions.delete(key);
    const age = this.#now() - transaction.createdAtMs;
    if (age < 0 || age > 300_000) return null;
    if (transaction.accountId !== accountId ||
        transaction.provider !== provider ||
        transaction.callbackUri !== callbackUri) return null;
    return {
      codeVerifier: transaction.codeVerifier,
      expectedScopes: [...transaction.expectedScopes]
    };
  }

  /** Cleanup is strictly defensive; correctness relies on atomic consume. */
  pruneExpired() {
    const now = this.#now();
    for (const [key, transaction] of this.#transactions) {
      if (now < transaction.createdAtMs || now - transaction.createdAtMs > 300_000)
        this.#transactions.delete(key);
    }
  }
}
