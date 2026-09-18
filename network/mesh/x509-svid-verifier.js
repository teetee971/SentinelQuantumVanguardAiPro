import { X509Certificate } from "node:crypto";

const MAX_CERT_PEM_CHARS = 64 * 1024;
const MAX_TRUST_BUNDLE_CERTS = 32;
const DEFAULT_CLOCK_SKEW_MS = 60_000;
const EVIDENCE_SECRET = Symbol("verified-svid-evidence");
const verifiedEvidence = new WeakSet();

export class VerifiedSvidEvidence {
  constructor(secret, payload) {
    if (secret !== EVIDENCE_SECRET) throw new Error("verified SVID evidence cannot be constructed directly");
    this.spiffeId = payload.spiffeId;
    this.trustDomain = payload.trustDomain;
    this.path = payload.path;
    this.notBefore = payload.notBefore;
    this.notAfter = payload.notAfter;
    this.signerFingerprint256 = payload.signerFingerprint256;
    this.leafFingerprint256 = payload.leafFingerprint256;
    Object.freeze(this);
    verifiedEvidence.add(this);
  }
}

export function isVerifiedSvidEvidence(value) {
  return value instanceof VerifiedSvidEvidence && verifiedEvidence.has(value);
}

function validTrustDomain(value) {
  if (typeof value !== "string" || value.length < 1 || value.length > 255) return false;
  if (value !== value.toLowerCase()) return false;
  return value.split(".").every(label =>
    /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label)
  );
}

function parseSpiffeId(raw) {
  let url;
  try {
    url = new URL(String(raw || ""));
  } catch {
    throw new Error("x509 svid SPIFFE ID invalid");
  }
  if (url.protocol !== "spiffe:" || url.username || url.password || url.port || url.search || url.hash) {
    throw new Error("x509 svid SPIFFE ID invalid");
  }
  const trustDomain = url.hostname;
  if (!validTrustDomain(trustDomain)) throw new Error("x509 svid trust domain invalid");
  const pathname = url.pathname;
  if (!pathname.startsWith("/") || pathname.includes("%") || pathname.includes("//")) {
    throw new Error("x509 svid path not canonical");
  }
  const segments = pathname.split("/").slice(1);
  if (segments.some(segment =>
    segment === "." ||
    segment === ".." ||
    (segment && !/^[A-Za-z0-9._-]{1,128}$/.test(segment))
  )) {
    throw new Error("x509 svid path invalid");
  }
  const path = pathname === "/" ? "/" : `/${segments.filter(Boolean).join("/")}`;
  const id = `spiffe://${trustDomain}${path}`;
  if (id !== String(raw)) throw new Error("x509 svid SPIFFE ID not canonical");
  return Object.freeze({ id, trustDomain, path });
}

function parseCertificate(pem, name) {
  if (typeof pem !== "string" || pem.length < 64 || pem.length > MAX_CERT_PEM_CHARS) {
    throw new Error(`${name} invalid`);
  }
  try {
    return new X509Certificate(pem);
  } catch {
    throw new Error(`${name} invalid`);
  }
}

function extractUriSans(cert) {
  const alt = cert.subjectAltName || "";
  const matches = [];
  const re = /URI:([^,]+)(?:,|$)/g;
  let match;
  while ((match = re.exec(alt)) !== null) {
    matches.push(match[1].trim());
  }
  return matches;
}

function certificateTimeMs(value, name) {
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) throw new Error(`x509 svid ${name} invalid`);
  return ms;
}

export class X509SvidVerifier {
  #trustAnchors;
  #clock;
  #clockSkewMs;

  constructor({
    trustBundlePem,
    clock = () => Date.now(),
    clockSkewMs = DEFAULT_CLOCK_SKEW_MS,
  }) {
    if (!Array.isArray(trustBundlePem) || !trustBundlePem.length || trustBundlePem.length > MAX_TRUST_BUNDLE_CERTS) {
      throw new Error("x509 svid trust bundle invalid");
    }
    if (typeof clock !== "function") throw new Error("x509 svid clock invalid");
    if (!Number.isInteger(clockSkewMs) || clockSkewMs < 0 || clockSkewMs > 5 * 60_000) {
      throw new Error("x509 svid clock skew invalid");
    }

    this.#trustAnchors = trustBundlePem.map((pem, index) =>
      parseCertificate(pem, `x509 svid trust anchor ${index}`)
    );
    this.#clock = clock;
    this.#clockSkewMs = clockSkewMs;
  }

  verify({ leafPem, expectedTrustDomain = null }) {
    const leaf = parseCertificate(leafPem, "x509 svid leaf");
    const now = this.#clock();
    const notBefore = certificateTimeMs(leaf.validFrom, "notBefore");
    const notAfter = certificateTimeMs(leaf.validTo, "notAfter");

    if (now + this.#clockSkewMs < notBefore) throw new Error("x509 svid not yet valid");
    if (now - this.#clockSkewMs >= notAfter) throw new Error("x509 svid expired");

    const uriSans = extractUriSans(leaf);
    if (uriSans.length !== 1) {
      throw new Error("x509 svid must contain exactly one URI SAN");
    }
    const identity = parseSpiffeId(uriSans[0]);
    if (expectedTrustDomain !== null && identity.trustDomain !== expectedTrustDomain) {
      throw new Error("x509 svid trust domain mismatch");
    }

    const signer = this.#trustAnchors.find(anchor => {
      try {
        return leaf.checkIssued(anchor) && leaf.verify(anchor.publicKey);
      } catch {
        return false;
      }
    });
    if (!signer) throw new Error("x509 svid signature not trusted");

    const signerNotBefore = certificateTimeMs(signer.validFrom, "anchor notBefore");
    const signerNotAfter = certificateTimeMs(signer.validTo, "anchor notAfter");
    if (now + this.#clockSkewMs < signerNotBefore || now - this.#clockSkewMs >= signerNotAfter) {
      throw new Error("x509 svid trust anchor outside validity");
    }

    return new VerifiedSvidEvidence(EVIDENCE_SECRET, {
      spiffeId: identity.id,
      trustDomain: identity.trustDomain,
      path: identity.path,
      notBefore,
      notAfter,
      signerFingerprint256: signer.fingerprint256.replaceAll(":", "").toLowerCase(),
      leafFingerprint256: leaf.fingerprint256.replaceAll(":", "").toLowerCase(),
    });
  }
}

export const x509SvidLimits = Object.freeze({
  maxCertificatePemChars: MAX_CERT_PEM_CHARS,
  maxTrustBundleCerts: MAX_TRUST_BUNDLE_CERTS,
  defaultClockSkewMs: DEFAULT_CLOCK_SKEW_MS,
});
