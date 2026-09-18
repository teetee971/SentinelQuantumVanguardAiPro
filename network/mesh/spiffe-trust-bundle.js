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

function digestContent(trustDomain, anchors) {
  return createHash("sha256")
    .update(JSON.stringify({
      trustDomain,
      anchors: anchors.map(anchor => anchor.fingerprint256).sort(),
    }), "utf8")
    .digest("hex");
}

function digestBundle(trustDomain, sequence, contentDigest) {
  return createHash("sha256")
    .update(JSON.stringify({
      schemaVersion: SCHEMA_VERSION,
      trustDomain,
      sequence,
      contentDigest,
    }), "utf8")
    .digest("hex");
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

  const contentDigest = digestContent(domain, parsed);
  const digest = digestBundle(domain, sequence, contentDigest);
  return Object.freeze({
    schemaVersion: SCHEMA_VERSION,
    trustDomain: domain,
    sequence,
    contentDigest,
    digest,
    anchorsPem: Object.freeze(parsed.map(anchor => anchor.pem)),
    fingerprints256: Object.freeze(parsed.map(anchor => anchor.fingerprint256)),
  });
}

export class SpiffeTrustBundleManager {
  #bundles = new Map();
  #lastSequences = new Map();

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
        contentDigest: bundle.contentDigest,
        fingerprints256: Object.freeze([...bundle.fingerprints256]),
      }));
  }

  install(bundle) {
    const normalized = normalizeBundle(bundle);
    if (!this.#bundles.has(normalized.trustDomain) && this.#bundles.size >= MAX_TRUST_DOMAINS) {
      throw new Error("trust domain capacity exceeded");
    }

    const lastSequence = this.#lastSequences.get(normalized.trustDomain) || 0;
    if (normalized.sequence <= lastSequence) {
      throw new Error("trust bundle rollback or replay detected");
    }
    const current = this.#bundles.get(normalized.trustDomain);
    if (current && normalized.contentDigest === current.contentDigest) {
      throw new Error("trust bundle content replay detected");
    }

    this.#bundles.set(normalized.trustDomain, normalized);
    this.#lastSequences.set(normalized.trustDomain, normalized.sequence);
    return structuredClone(normalized);
  }

  observe({ trustDomain, anchorsPem }) {
    const domain = normalizeTrustDomain(trustDomain);
    const current = this.#bundles.get(domain);
    const sequence = (this.#lastSequences.get(domain) || 0) + 1;
    const candidate = normalizeBundle({ trustDomain: domain, sequence, anchorsPem });

    if (current && candidate.contentDigest === current.contentDigest) {
      return Object.freeze({ changed: false, bundle: structuredClone(current) });
    }
    if (!current && this.#bundles.size >= MAX_TRUST_DOMAINS) {
      throw new Error("trust domain capacity exceeded");
    }

    this.#bundles.set(domain, candidate);
    this.#lastSequences.set(domain, candidate.sequence);
    return Object.freeze({ changed: true, bundle: structuredClone(candidate) });
  }

  observeSet(bundles) {
    if (!Array.isArray(bundles) || !bundles.length || bundles.length > MAX_TRUST_DOMAINS) {
      throw new Error("trust bundle observed set invalid");
    }

    const normalizedInputs = new Map();
    for (const raw of bundles) {
      const domain = normalizeTrustDomain(raw?.trustDomain);
      if (normalizedInputs.has(domain)) throw new Error("duplicate trust domain in observed set");
      normalizedInputs.set(domain, raw?.anchorsPem);
    }

    const nextBundles = new Map();
    const nextSequences = new Map(this.#lastSequences);
    const changedDomains = [];

    for (const [domain, anchorsPem] of normalizedInputs.entries()) {
      const current = this.#bundles.get(domain);
      const nextSequence = (nextSequences.get(domain) || 0) + 1;
      const candidate = normalizeBundle({
        trustDomain: domain,
        sequence: nextSequence,
        anchorsPem,
      });

      if (current && candidate.contentDigest === current.contentDigest) {
        nextBundles.set(domain, current);
        continue;
      }

      nextBundles.set(domain, candidate);
      nextSequences.set(domain, candidate.sequence);
      changedDomains.push(domain);
    }

    const removedDomains = [...this.#bundles.keys()]
      .filter(domain => !normalizedInputs.has(domain))
      .sort();

    this.#bundles = nextBundles;
    this.#lastSequences = nextSequences;

    return Object.freeze({
      changedDomains: Object.freeze(changedDomains.sort()),
      removedDomains: Object.freeze(removedDomains),
      bundles: Object.freeze(this.listMetadata()),
    });
  }

  exportState() {
    return Object.freeze({
      schemaVersion: SCHEMA_VERSION,
      bundles: Object.freeze(
        [...this.#bundles.values()]
          .sort((a, b) => a.trustDomain.localeCompare(b.trustDomain))
          .map(bundle => structuredClone(bundle))
      ),
      lastSequences: Object.freeze(
        [...this.#lastSequences.entries()]
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([trustDomain, sequence]) => Object.freeze({ trustDomain, sequence }))
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
    if (!Array.isArray(state.lastSequences) || state.lastSequences.length > MAX_TRUST_DOMAINS) {
      throw new Error("trust bundle sequence state invalid");
    }

    const restoredSequences = new Map();
    for (const raw of state.lastSequences) {
      const domain = normalizeTrustDomain(raw?.trustDomain);
      const sequence = raw?.sequence;
      if (!Number.isSafeInteger(sequence) || sequence < 1) {
        throw new Error("trust bundle sequence state invalid");
      }
      if (restoredSequences.has(domain)) throw new Error("duplicate trust domain in sequence state");
      restoredSequences.set(domain, sequence);
    }

    const restoredBundles = new Map();
    for (const raw of state.bundles) {
      const normalized = normalizeBundle({
        trustDomain: raw?.trustDomain,
        sequence: raw?.sequence,
        anchorsPem: raw?.anchorsPem,
      });
      if (raw.contentDigest !== normalized.contentDigest || raw.digest !== normalized.digest) {
        throw new Error("trust bundle state digest mismatch");
      }
      if (restoredBundles.has(normalized.trustDomain)) {
        throw new Error("duplicate trust domain in bundle state");
      }
      if (restoredSequences.get(normalized.trustDomain) !== normalized.sequence) {
        throw new Error("trust bundle sequence state mismatch");
      }

      const existingSequence = this.#lastSequences.get(normalized.trustDomain) || 0;
      if (existingSequence > 0 && normalized.sequence <= existingSequence) {
        throw new Error("trust bundle rollback or replay detected");
      }
      restoredBundles.set(normalized.trustDomain, normalized);
    }

    for (const [domain, sequence] of restoredSequences.entries()) {
      const existingSequence = this.#lastSequences.get(domain) || 0;
      if (existingSequence > 0 && sequence <= existingSequence) {
        throw new Error("trust bundle rollback or replay detected");
      }
    }

    this.#bundles = restoredBundles;
    this.#lastSequences = restoredSequences;
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
      contentDigest: bundle.contentDigest,
    });
  }
}

export const spiffeTrustBundleLimits = Object.freeze({
  maxAnchors: MAX_ANCHORS,
  maxPemChars: MAX_PEM_CHARS,
  maxTrustDomains: MAX_TRUST_DOMAINS,
  schemaVersion: SCHEMA_VERSION,
});
