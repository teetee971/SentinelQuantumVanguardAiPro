/**
 * Transport-neutral, non-cryptographic relay policy.
 * Caller MUST authenticate an envelope before accept(); this code is not an E2EE implementation.
 * Snapshot is sensitive routing metadata: persist using encrypted, access-controlled storage.
 */
export class EncryptedEnvelopeRouter {
  #seen = new Map();
  #windowStart = null;
  #windowCount = 0;
  constructor({now = () => Date.now(), maxEntries = 2048, maxHops = 7, maxEnvelopeBytes = 4096, maxPerMinute = 120} = {}) {
    if (![maxEntries, maxHops, maxEnvelopeBytes, maxPerMinute].every(n => Number.isSafeInteger(n) && n > 0)) throw new Error('INVALID_LIMITS');
    if (typeof now !== 'function') throw new Error('INVALID_CLOCK');
    Object.assign(this, {now, maxEntries, maxHops, maxEnvelopeBytes, maxPerMinute});
  }
  #clock() {
    const time = this.now();
    return Number.isSafeInteger(time) && time >= 0 ? time : null;
  }
  #prune(now) {
    for (const [id, expiry] of this.#seen) if (expiry <= now) this.#seen.delete(id);
  }
  accept(envelope) {
    const now = this.#clock();
    if (now === null) return {action:'REJECT',reason:'INVALID_CLOCK'};
    this.#prune(now);
    if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) return {action:'REJECT',reason:'INVALID_ENVELOPE'};
    const {id, ciphertext, expiresAt, remainingHops} = envelope;
    if (typeof id !== 'string' || !/^[a-f0-9]{32}$/i.test(id)) return {action:'REJECT',reason:'INVALID_ID'};
    if (!(ciphertext instanceof Uint8Array) || ciphertext.byteLength === 0 || ciphertext.byteLength > this.maxEnvelopeBytes) return {action:'REJECT',reason:'INVALID_CIPHERTEXT'};
    if (!Number.isSafeInteger(expiresAt) || expiresAt <= now || expiresAt - now > 86_400_000) return {action:'REJECT',reason:'INVALID_EXPIRY'};
    if (!Number.isSafeInteger(remainingHops) || remainingHops < 0 || remainingHops > this.maxHops) return {action:'REJECT',reason:'INVALID_HOPS'};
    if (this.#seen.has(id)) return {action:'DROP',reason:'DUPLICATE'};
    if (this.#seen.size >= this.maxEntries) return {action:'REJECT',reason:'DEDUP_CAPACITY'};
    if (this.#windowStart === null || now < this.#windowStart || now - this.#windowStart >= 60_000) {
      this.#windowStart = now; this.#windowCount = 0;
    }
    if (this.#windowCount >= this.maxPerMinute) return {action:'REJECT',reason:'RATE_LIMIT'};
    this.#windowCount++;
    this.#seen.set(id, expiresAt);
    if (remainingHops === 0) return {action:'DELIVER_ONLY',reason:'HOP_LIMIT'};
    return {action:'RELAY_CANDIDATE', envelope:{id,ciphertext:new Uint8Array(ciphertext),expiresAt,remainingHops:remainingHops-1}};
  }
  snapshot() {
    const now = this.#clock();
    if (now === null) throw new Error('INVALID_CLOCK');
    this.#prune(now);
    return {version:1, capturedAt:now, entries:[...this.#seen.entries()].map(([id,expiresAt])=>({id,expiresAt}))};
  }
  restore(snapshot) {
    const now = this.#clock();
    if (now === null || !snapshot || snapshot.version !== 1 || !Array.isArray(snapshot.entries) || snapshot.entries.length > this.maxEntries || !Number.isSafeInteger(snapshot.capturedAt) || snapshot.capturedAt > now) throw new Error('INVALID_SNAPSHOT');
    const recovered = new Map();
    for (const row of snapshot.entries) {
      if (!row || typeof row.id !== 'string' || !/^[a-f0-9]{32}$/i.test(row.id) || !Number.isSafeInteger(row.expiresAt) || row.expiresAt - snapshot.capturedAt > 86_400_000 || recovered.has(row.id)) throw new Error('INVALID_SNAPSHOT');
      if (row.expiresAt > now) recovered.set(row.id,row.expiresAt);
    }
    this.#seen = recovered;
    this.#windowStart = now;
    this.#windowCount = 0;
  }
}
