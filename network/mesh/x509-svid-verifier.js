import { X509Certificate } from "node:crypto";
import { certificateAuthorityMetadata, certificateX509Metadata, isSerialRevoked, verifyX509Crl } from "./x509-crl.js";

const MAX_CERT_PEM_CHARS = 64 * 1024;
const MAX_TRUST_BUNDLE_CERTS = 32;
const MAX_INTERMEDIATE_CERTS = 8;
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

export function parseSubjectAltNameEntries(raw) {
  const text = String(raw || "");
  if (!text) return [];

  const entries = [];
  let start = 0;
  let inQuotes = false;
  let escaped = false;

  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (inQuotes) {
      if (escaped) {
        escaped = false;
      } else if (ch === "\\") {
        escaped = true;
      } else if (ch === "\"") {
        inQuotes = false;
      }
      continue;
    }
    if (ch === "\"") {
      inQuotes = true;
      continue;
    }
    if (ch === "," && text[i + 1] === " ") {
      entries.push(text.slice(start, i));
      start = i + 2;
      i += 1;
    }
  }
  if (inQuotes || escaped) throw new Error("x509 svid SAN encoding invalid");
  entries.push(text.slice(start));

  return entries.map(entry => {
    const colon = entry.indexOf(":");
    if (colon <= 0) throw new Error("x509 svid SAN entry invalid");
    const type = entry.slice(0, colon).trim();
    const encoded = entry.slice(colon + 1).trim();
    if (!type || !encoded) throw new Error("x509 svid SAN entry invalid");

    let value = encoded;
    if (encoded.startsWith("\"")) {
      try {
        value = JSON.parse(encoded);
      } catch {
        throw new Error("x509 svid SAN encoding invalid");
      }
      if (typeof value !== "string") throw new Error("x509 svid SAN value invalid");
    }
    return Object.freeze({ type, value });
  });
}

function extractSingleSpiffeUriSan(cert) {
  const entries = parseSubjectAltNameEntries(cert.subjectAltName || "");
  const uris = entries
    .filter(entry => entry.type === "URI")
    .map(entry => entry.value.trim())
    .filter(Boolean);
  if (uris.length !== 1) {
    throw new Error("x509 svid must contain exactly one URI SAN");
  }
  return uris[0];
}

function certificateTimeMs(value, name) {
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) throw new Error(`x509 svid ${name} invalid`);
  return ms;
}

function certificateFingerprint(cert) {
  return cert.fingerprint256.replaceAll(":", "").toLowerCase();
}

function assertCertificateValidAt(cert, now, clockSkewMs, name) {
  const notBefore = certificateTimeMs(cert.validFrom, `${name} notBefore`);
  const notAfter = certificateTimeMs(cert.validTo, `${name} notAfter`);
  if (now + clockSkewMs < notBefore || now - clockSkewMs >= notAfter) {
    throw new Error(`x509 svid ${name} outside validity`);
  }
}

function verifiesIssuedCertificate(child, issuer) {
  try {
    return issuer.ca && child.checkIssued(issuer) && child.verify(issuer.publicKey);
  } catch {
    return false;
  }
}

function assertIntermediateCaPolicy(cert, subordinateCaCount) {
  const metadata = certificateAuthorityMetadata(cert);
  const basic = metadata.basicConstraints;
  if (!basic?.critical || !basic.ca) {
    throw new Error("x509 svid intermediate basic constraints invalid");
  }
  if (!metadata.keyUsage?.keyCertSign) {
    throw new Error("x509 svid intermediate key usage invalid");
  }
  if (basic.pathLenConstraint !== null && subordinateCaCount > basic.pathLenConstraint) {
    throw new Error("x509 svid intermediate path length exceeded");
  }
}

function findTrustedPath({ leaf, intermediates, trustAnchors, now, clockSkewMs }) {
  const seen = new Set([certificateFingerprint(leaf)]);

  function walk(child, depth) {
    for (const anchor of trustAnchors) {
      if (!verifiesIssuedCertificate(child, anchor)) continue;
      assertCertificateValidAt(anchor, now, clockSkewMs, "trust anchor");
      return { anchor, intermediateDepth: depth, issuers: [anchor] };
    }
    if (depth >= MAX_INTERMEDIATE_CERTS) return null;

    for (const intermediate of intermediates) {
      const fingerprint = certificateFingerprint(intermediate);
      if (seen.has(fingerprint) || !verifiesIssuedCertificate(child, intermediate)) continue;
      assertCertificateValidAt(intermediate, now, clockSkewMs, "intermediate");
      seen.add(fingerprint);
      const result = walk(intermediate, depth + 1);
      seen.delete(fingerprint);
      if (result) return { ...result, issuers: [intermediate, ...result.issuers] };
    }
    return null;
  }

  return walk(leaf, 0);
}

export class X509SvidVerifier {
  #trustAnchors;
  #clock;
  #clockSkewMs;
  #crlsDer;

  constructor({
    trustBundlePem,
    clock = () => Date.now(),
    clockSkewMs = DEFAULT_CLOCK_SKEW_MS,
    crlsDerBase64 = [],
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
    if (!Array.isArray(crlsDerBase64) || crlsDerBase64.length > 32) {
      throw new Error("x509 svid CRL set invalid");
    }
    this.#crlsDer = crlsDerBase64.map((value, index) => {
      const text = String(value || "");
      const der = Buffer.from(text, "base64");
      if (!der.length || der.toString("base64") !== text) {
        throw new Error(`x509 svid CRL ${index} invalid`);
      }
      return der;
    });
  }

  verify({ leafPem, intermediatesPem = [], expectedTrustDomain = null }) {
    const leaf = parseCertificate(leafPem, "x509 svid leaf");
    if (!Array.isArray(intermediatesPem) || intermediatesPem.length > MAX_INTERMEDIATE_CERTS) {
      throw new Error("x509 svid intermediate set invalid");
    }
    const intermediates = intermediatesPem.map((pem, index) =>
      parseCertificate(pem, `x509 svid intermediate ${index}`)
    );
    if (intermediates.some(cert => !cert.ca)) {
      throw new Error("x509 svid intermediate must be a CA");
    }
    const suppliedFingerprints = [leaf, ...intermediates].map(certificateFingerprint);
    if (new Set(suppliedFingerprints).size !== suppliedFingerprints.length) {
      throw new Error("x509 svid certificate chain contains duplicates");
    }
    const trustAnchorFingerprints = new Set(this.#trustAnchors.map(certificateFingerprint));
    if (intermediates.some(cert => trustAnchorFingerprints.has(certificateFingerprint(cert)))) {
      throw new Error("x509 svid intermediate duplicates trust anchor");
    }
    if (leaf.ca) throw new Error("x509 svid leaf must not be a CA");
    const now = this.#clock();
    const notBefore = certificateTimeMs(leaf.validFrom, "notBefore");
    const notAfter = certificateTimeMs(leaf.validTo, "notAfter");

    if (now + this.#clockSkewMs < notBefore) throw new Error("x509 svid not yet valid");
    if (now - this.#clockSkewMs >= notAfter) throw new Error("x509 svid expired");

    const identity = parseSpiffeId(extractSingleSpiffeUriSan(leaf));
    if (identity.path === "/") throw new Error("x509 svid leaf SPIFFE ID must have a non-root path");
    if (expectedTrustDomain !== null && identity.trustDomain !== expectedTrustDomain) {
      throw new Error("x509 svid trust domain mismatch");
    }

    const trustedPath = findTrustedPath({
      leaf,
      intermediates,
      trustAnchors: this.#trustAnchors,
      now,
      clockSkewMs: this.#clockSkewMs,
    });
    if (!trustedPath) throw new Error("x509 svid signature not trusted");

    const leafMetadata = certificateX509Metadata(leaf);
    if (!leafMetadata.basicConstraints || leafMetadata.basicConstraints.ca !== false) {
      throw new Error("x509 svid leaf basic constraints invalid");
    }
    if (!leafMetadata.keyUsage?.critical ||
        !leafMetadata.keyUsage.digitalSignature ||
        leafMetadata.keyUsage.keyCertSign ||
        leafMetadata.keyUsage.crlSign) {
      throw new Error("x509 svid leaf key usage invalid");
    }
    if (leafMetadata.extendedKeyUsage) {
      const usages = new Set(leafMetadata.extendedKeyUsage.usages);
      if (!usages.has("1.3.6.1.5.5.7.3.1") || !usages.has("1.3.6.1.5.5.7.3.2")) {
        throw new Error("x509 svid leaf extended key usage invalid");
      }
    }

    const pathIntermediates = trustedPath.issuers.slice(0, -1);
    for (let index = 0; index < pathIntermediates.length; index += 1) {
      assertIntermediateCaPolicy(pathIntermediates[index], index);
    }

    const pathIssuerPem = trustedPath.issuers.map(cert => cert.toString());
    const verifiedCrls = this.#crlsDer.map(crlDer =>
      verifyX509Crl({
        crlDer,
        trustBundlePem: pathIssuerPem,
        clock: this.#clock,
        clockSkewMs: this.#clockSkewMs,
        allowUnrelatedIssuer: true,
      })
    ).filter(Boolean);

    const chainChildren = [leaf, ...trustedPath.issuers.slice(0, -1)];
    for (let index = 0; index < chainChildren.length; index += 1) {
      const child = chainChildren[index];
      const issuer = trustedPath.issuers[index];
      const issuerFingerprint = certificateFingerprint(issuer);
      const issuerCrls = verifiedCrls.filter(crl => crl.signerFingerprint256 === issuerFingerprint);
      if (isSerialRevoked(child.serialNumber, issuerCrls)) {
        const kind = index === 0 ? "certificate" : "intermediate certificate";
        throw new Error(`x509 svid ${kind} revoked`);
      }
    }

    const signer = trustedPath.anchor;

    return new VerifiedSvidEvidence(EVIDENCE_SECRET, {
      spiffeId: identity.id,
      trustDomain: identity.trustDomain,
      path: identity.path,
      notBefore,
      notAfter,
      signerFingerprint256: certificateFingerprint(signer),
      leafFingerprint256: certificateFingerprint(leaf),
    });
  }
}

export const x509SvidLimits = Object.freeze({
  maxCertificatePemChars: MAX_CERT_PEM_CHARS,
  maxTrustBundleCerts: MAX_TRUST_BUNDLE_CERTS,
  maxIntermediateCerts: MAX_INTERMEDIATE_CERTS,
  defaultClockSkewMs: DEFAULT_CLOCK_SKEW_MS,
});
