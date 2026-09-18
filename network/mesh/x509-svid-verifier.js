import { X509Certificate } from "node:crypto";
import { parseSpiffeId } from "./spiffe-identity.js";

const MAX_CERT_PEM_CHARS = 64 * 1024;
const MAX_TRUST_BUNDLE_CERTS = 32;
const DEFAULT_CLOCK_SKEW_MS = 60_000;

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

    return Object.freeze({
      verified: true,
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
