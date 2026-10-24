/** Transport-neutral encrypted-envelope router. Does not encrypt or transmit messages. */
export class EncryptedEnvelopeRouter {
  #seen = new Map();
  constructor({now = () => Date.now(), maxEntries = 2048, maxHops = 7, maxEnvelopeBytes = 4096} = {}) {
    if (![maxEntries, maxHops, maxEnvelopeBytes].every(n => Number.isSafeInteger(n) && n > 0)) throw new Error('INVALID_LIMITS');
    this.now = now; this.maxEntries = maxEntries; this.maxHops = maxHops; this.maxEnvelopeBytes = maxEnvelopeBytes;
  }
  accept(envelope) {
    const now = this.now();
    if (!Number.isSafeInteger(now) || now < 0) return {action: 'REJECT', reason: 'INVALID_CLOCK'};
    for (const [id, expiry] of this.#seen) if (expiry <= now) this.#seen.delete(id);
    if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) return {action:'REJECT', reason:'INVALID_ENVELOPE'};
    const {id, ciphertext, expiresAt, remainingHops} = envelope;
    if (typeof id !== 'string' || !/^[a-f0-9]{32}$/i.test(id)) return {action:'REJECT',reason:'INVALID_ID'};
    if (!(ciphertext instanceof Uint8Array) || ciphertext.byteLength === 0 || ciphertext.byteLength > this.maxEnvelopeBytes) return {action:'REJECT',reason:'INVALID_CIPHERTEXT'};
    if (!Number.isSafeInteger(expiresAt) || expiresAt <= now || expiresAt > now + 86_400_000) return {action:'REJECT',reason:'INVALID_EXPIRY'};
    if (!Number.isSafeInteger(remainingHops) || remainingHops < 0 || remainingHops > this.maxHops) return {action:'REJECT',reason:'INVALID_HOPS'};
    if (this.#seen.has(id)) return {action:'DROP',reason:'DUPLICATE'};
    if (this.#seen.size >= this.maxEntries) this.#seen.delete(this.#seen.keys().next().value);
    this.#seen.set(id, expiresAt);
    if (remainingHops === 0) return {action:'DELIVER_ONLY',reason:'HOP_LIMIT'};
    // Never return plaintext or a mutable ciphertext reference.
    return {action:'RELAY_CANDIDATE', envelope:{id, ciphertext:new Uint8Array(ciphertext), expiresAt, remainingHops: remainingHops - 1}};
  }
}
