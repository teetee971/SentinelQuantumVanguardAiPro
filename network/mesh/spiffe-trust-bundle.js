import { createHash, X509Certificate } from "node:crypto";

const MAX_ANCHORS = 32;
const MAX_PEM_CHARS = 64 * 1024;
const MAX_TRUST_DOMAINS = 32;
const SCHEMA_VERSION = 1;

function normalizeTrustDomain(value) {
  const domain = String(value || "").trim();
  if (
    !domain ||
    domain.length > 255 ||
    domain !== domain.toLowerCase() ||
    !domain.split(".").every(label =>
      /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label)
    )
  ) {
    throw new Error("trust domain invalid");
  }
  return domain;
}

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
  };
}

function digestBundle(trustDomain, sequence, anchors) {
  const canonical = JSON.stringify({
    schemaVersion: SCHEMA_VERSION,
    trustDomain,
    sequence,
    anchors: anchors.map(anchor => anchor.fingerprint256).sort(),
  });
  return createHash("sha256").update(canonical, "utf8").digest("hex");
}

function normalizeBundle({ trustDomain, sequence, anchorsPem }) {
  const domain = normalizeTrustDomain(trustDomain);
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
  const digest = digestBundle(domain, sequence, parsed);
  return Object.freeze({
    schemaVersion: SCHEMA_VERSION,
    trustDomain: domain,
    sequence,
    digest,
    anchorsPem: Object.freeze(parsed.map(anchor => anchor.pem)),
    fingerprints256: Object.freeze(parsed.map(anchor => anchor.fingerprint256)),
  });
}

export class SpiffeTrustBundleManager {
  #bundles = new Map();

  current(trustDomain) {
    const domain = normalizeTrustDomain(trustDomain);
    const bundle = this.#bundles.get(domain);
    return bundle ? structuredClone(bundle) : null;
  }

  listMetadata() {
    return [...this.#bundles.values()]
      .sort((a, b) => a.trustDomain.localeCompare(b.trustDomain))
      .map(bundle => Object.freeze({
        trustDomain: bundle.trustDomain,
        sequence: bundle.sequence,
        digest: bundle.digest,
        fingerprints256: Object.freeze([...bundle.fingerprints256]),
      }));
  }

  install(bundle) {
    const normalized = normalizeBundle(bundle);
    if (!this.#bundles.has(normalized.trustDomain) && this.#bundles.size >= MAX_TRUST_DOMAINS) {
      throw new Error("trust domain capacity exceeded");
    }
    const current = this.#bundles.get(normalized.trustDomain);
    if (current && normalized.sequence <= current.sequence) {
      throw new Error("trust bundle rollback or replay detected");
    }
    this.#bundles.set(normalized.trustDomain, normalized);
    return structuredClone(normalized);
  }

  exportState() {
    return Object.freeze({
      schemaVersion: SCHEMA_VERSION,
      bundles: Object.freeze(
        [...this.#bundles.values()]
          .sort((a, b) => a.trustDomain.localeCompare(b.trustDomain))
          .map(bundle => structuredClone(bundle))
      ),
    });
  }

  restoreState(state) {
    if (!state || typeof state !== "object" || state.schemaVersion !== SCHEMA_VERSION) {
      throw new Error("trust bundle state schema invalid");
    }
    if (!Array.isArray(state.bundles) || state.bundles.length > MAX_TRUST_DOMAINS) {
      throw new Error("trust bundle state invalid");
    }

    const restored = new Map();
    for (const raw of state.bundles) {
      const normalized = normalizeBundle({
        trustDomain: raw?.trustDomain,
        sequence: raw?.sequence,
        anchorsPem: raw?.anchorsPem,
      });
      if (raw.digest !== normalized.digest) throw new Error("trust bundle state digest mismatch");
      if (restored.has(normalized.trustDomain)) throw new Error("duplicate trust domain in bundle state");

      const existing = this.#bundles.get(normalized.trustDomain);
      if (existing && normalized.sequence < existing.sequence) {
        throw new Error("trust bundle rollback detected");
      }
      restored.set(normalized.trustDomain, normalized);
    }

    this.#bundles = restored;
    return this.exportState();
  }

  verifierConfig(trustDomain) {
    const bundle = this.current(trustDomain);
    if (!bundle) throw new Error("trust bundle not installed");
    return Object.freeze({
      expectedTrustDomain: bundle.trustDomain,
      trustBundlePem: Object.freeze([...bundle.anchorsPem]),
      sequence: bundle.sequence,
      digest: bundle.digest,
    });
  }
}

export const spiffeTrustBundleLimits = Object.freeze({
  maxAnchors: MAX_ANCHORS,
  maxPemChars: MAX_PEM_CHARS,
  maxTrustDomains: MAX_TRUST_DOMAINS,
  schemaVersion: SCHEMA_VERSION,
});
