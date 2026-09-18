import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

const MAX_PENDING = 10000;
const MAX_TTL_MS = 15 * 60 * 1000;
const MAX_ATTEMPTS = 5;

function boundedNodeId(value) {
  const id = String(value || "").trim();
  if (!/^[A-Za-z0-9:_./-]{2,256}$/.test(id)) throw new Error("invalid node id");
  return id;
}

function hashCode(code) {
  return createHash("sha256").update(String(code), "utf8").digest();
}

function safeEqual(expected, code) {
  if (!expected || typeof code !== "string" || code.length < 32 || code.length > 128) return false;
  const actual = hashCode(code);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

export class MeshEnrollmentBroker {
  #pending = new Map();
  #clock;

  constructor({ clock = () => Date.now() } = {}) {
    this.#clock = clock;
  }

  createInvitation({ nodeId, publicKeyFingerprint, ttlMs = 10 * 60 * 1000 }) {
    if (this.#pending.size >= MAX_PENDING) throw new Error("enrollment capacity exceeded");
    const id = boundedNodeId(nodeId);
    const fingerprint = String(publicKeyFingerprint || "").trim().toLowerCase();
    if (!/^[a-f0-9]{64}$/.test(fingerprint)) throw new Error("invalid public key fingerprint");
    if (!Number.isInteger(ttlMs) || ttlMs < 30000 || ttlMs > MAX_TTL_MS) {
      throw new Error("invalid enrollment ttl");
    }

    const code = randomBytes(32).toString("base64url");
    const now = this.#clock();
    const invitation = {
      nodeId: id,
      publicKeyFingerprint: fingerprint,
      codeHash: hashCode(code),
      attempts: 0,
      createdAt: now,
      expiresAt: now + ttlMs,
      consumed: false,
    };
    this.#pending.set(id, invitation);
    return {
      nodeId: id,
      code,
      expiresAt: invitation.expiresAt,
      publicKeyFingerprint: fingerprint,
    };
  }

  claim({ nodeId, code, publicKeyFingerprint }) {
    const id = boundedNodeId(nodeId);
    const invitation = this.#pending.get(id);
    if (!invitation || invitation.consumed) return { accepted: false, reason: "INVITATION_NOT_FOUND" };
    if (invitation.expiresAt <= this.#clock()) {
      this.#pending.delete(id);
      return { accepted: false, reason: "INVITATION_EXPIRED" };
    }

    invitation.attempts += 1;
    if (invitation.attempts > MAX_ATTEMPTS) {
      this.#pending.delete(id);
      return { accepted: false, reason: "INVITATION_ATTEMPTS_EXCEEDED" };
    }

    const fingerprint = String(publicKeyFingerprint || "").trim().toLowerCase();
    if (!/^[a-f0-9]{64}$/.test(fingerprint) || fingerprint !== invitation.publicKeyFingerprint) {
      return { accepted: false, reason: "PUBLIC_KEY_FINGERPRINT_MISMATCH" };
    }
    if (!safeEqual(invitation.codeHash, code)) {
      return { accepted: false, reason: "INVITATION_CODE_INVALID" };
    }

    invitation.consumed = true;
    this.#pending.delete(id);
    return {
      accepted: true,
      reason: "INVITATION_CLAIMED",
      nodeId: id,
      publicKeyFingerprint: invitation.publicKeyFingerprint,
    };
  }

  revoke(nodeId) {
    return this.#pending.delete(boundedNodeId(nodeId));
  }

  pruneExpired() {
    const now = this.#clock();
    let removed = 0;
    for (const [nodeId, invitation] of this.#pending.entries()) {
      if (invitation.expiresAt <= now || invitation.consumed) {
        this.#pending.delete(nodeId);
        removed += 1;
      }
    }
    return removed;
  }

  pendingCount() {
    return this.#pending.size;
  }
}

export const meshEnrollmentLimits = Object.freeze({
  maxPending: MAX_PENDING,
  maxTtlMs: MAX_TTL_MS,
  maxAttempts: MAX_ATTEMPTS,
});
