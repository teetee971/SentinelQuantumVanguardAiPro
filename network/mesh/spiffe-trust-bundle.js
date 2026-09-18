import { createHash, X509Certificate } from "node:crypto";

const MAX_ANCHORS = 32;
const MAX_PEM_CHARS = 64 * 1024;
const SCHEMA_VERSION = 1;

function parseAnchor(pem, index) {
  if (typeof pem !== "string" || pem.length < 64 || pem.length > MAX_PEM_CHARS) {
    throw new Error(`trust anchor ${index} invalid`);
  }
  let cert;
  try {
    cert = new X509Certificate(pem);
  } catch {
    throw new Error(`trust anchor ${index} invalid`);
  }
  if (!cert.ca) throw new Error(`trust anchor ${index} must be a CA`);
  return {
    pem,
    fingerprint256: cert.fingerprint256.replaceAll(":", "").toLowerCase(),
    validFrom: Date.parse(cert.validFrom),
    validTo: Date.parse(cert.validTo),
  };
}

function digestBundle(sequence, anchors) {
  const canonical = JSON.stringify({
    schemaVersion: SCHEMA_VERSION,
    sequence,
    anchors: anchors
      .map(anchor => anchor.fingerprint256)
      .sort(),
  });
  return createHash("sha256").update(canonical, "utf8").digest("hex");
}

function normalizeBundle({ sequence, anchorsPem }) {
  if (!Number.isSafeInteger(sequence) || sequence < 1) {
    throw new Error("trust bundle sequence invalid");
  }
  if (!Array.isArray(anchorsPem) || !anchorsPem.length || anchorsPem.length > MAX_ANCHORS) {
    throw new Error("trust bundle anchors invalid");
  }
  const parsed = anchorsPem.map(parseAnchor);
  const seen = new Set();
  for (const anchor of parsed) {
    if (seen.has(anchor.fingerprint256)) throw new Error("duplicate trust anchor");
    seen.add(anchor.fingerprint256);
  }
  const digest = digestBundle(sequence, parsed);
  return Object.freeze({
    schemaVersion: SCHEMA_VERSION,
    sequence,
    digest,
    anchorsPem: Object.freeze(parsed.map(anchor => anchor.pem)),
    fingerprints256: Object.freeze(parsed.map(anchor => anchor.fingerprint256)),
  });
}

export class SpiffeTrustBundleManager {
  #current = null;

  current() {
    return this.#current ? structuredClone(this.#current) : null;
  }

  install(bundle) {
    const normalized = normalizeBundle(bundle);
    if (this.#current && normalized.sequence <= this.#current.sequence) {
      throw new Error("trust bundle rollback or replay detected");
    }
    this.#current = normalized;
    return this.current();
  }

  exportState() {
    if (!this.#current) {
      return Object.freeze({
        schemaVersion: SCHEMA_VERSION,
        sequence: 0,
        current: null,
      });
    }
    return Object.freeze({
      schemaVersion: SCHEMA_VERSION,
      sequence: this.#current.sequence,
      current: this.current(),
    });
  }

  restoreState(state) {
    if (!state || typeof state !== "object" || state.schemaVersion !== SCHEMA_VERSION) {
      throw new Error("trust bundle state schema invalid");
    }
    if (state.current === null) {
      if (state.sequence !== 0) throw new Error("trust bundle empty state invalid");
      this.#current = null;
      return null;
    }
    if (!Number.isSafeInteger(state.sequence) || state.sequence < 1) {
      throw new Error("trust bundle state sequence invalid");
    }
    const normalized = normalizeBundle({
      sequence: state.current.sequence,
      anchorsPem: state.current.anchorsPem,
    });
    if (normalized.sequence !== state.sequence) {
      throw new Error("trust bundle state sequence mismatch");
    }
    if (state.current.digest !== normalized.digest) {
      throw new Error("trust bundle state digest mismatch");
    }
    if (this.#current && normalized.sequence < this.#current.sequence) {
      throw new Error("trust bundle rollback detected");
    }
    this.#current = normalized;
    return this.current();
  }

  verifierConfig() {
    if (!this.#current) throw new Error("trust bundle not installed");
    return Object.freeze({
      trustBundlePem: Object.freeze([...this.#current.anchorsPem]),
      sequence: this.#current.sequence,
      digest: this.#current.digest,
    });
  }
}

export const spiffeTrustBundleLimits = Object.freeze({
  maxAnchors: MAX_ANCHORS,
  maxPemChars: MAX_PEM_CHARS,
  schemaVersion: SCHEMA_VERSION,
});
