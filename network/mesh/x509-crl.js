import { createPublicKey, verify as verifySignature, X509Certificate } from "node:crypto";

const MAX_CRL_BYTES = 1024 * 1024;
const MAX_REVOKED = 100_000;
const OIDS = Object.freeze({
  "1.2.840.113549.1.1.11": "sha256",
  "1.2.840.113549.1.1.12": "sha384",
  "1.2.840.113549.1.1.13": "sha512",
  "1.2.840.10045.4.3.2": "sha256",
  "1.2.840.10045.4.3.3": "sha384",
  "1.2.840.10045.4.3.4": "sha512",
});

function readLength(buffer, offset) {
  if (offset >= buffer.length) throw new Error("DER length truncated");
  const first = buffer[offset++];
  if ((first & 0x80) === 0) return { length: first, offset };
  const count = first & 0x7f;
  if (count === 0 || count > 4 || offset + count > buffer.length) throw new Error("DER length invalid");
  let length = 0;
  for (let i = 0; i < count; i += 1) length = (length * 256) + buffer[offset + i];
  if (length < 128) throw new Error("DER length not canonical");
  return { length, offset: offset + count };
}

function readTlv(buffer, offset) {
  if (offset >= buffer.length) throw new Error("DER element truncated");
  const start = offset;
  const tag = buffer[offset++];
  const lengthInfo = readLength(buffer, offset);
  const contentStart = lengthInfo.offset;
  const end = contentStart + lengthInfo.length;
  if (end > buffer.length) throw new Error("DER element truncated");
  return { tag, start, contentStart, end, next: end, raw: buffer.subarray(start, end), content: buffer.subarray(contentStart, end) };
}

function decodeOid(bytes) {
  if (!bytes.length) throw new Error("OID empty");
  const first = bytes[0];
  const parts = [Math.min(2, Math.floor(first / 40)), first % 40];
  let value = 0;
  for (let i = 1; i < bytes.length; i += 1) {
    value = (value * 128) + (bytes[i] & 0x7f);
    if ((bytes[i] & 0x80) === 0) {
      parts.push(value);
      value = 0;
    }
  }
  if ((bytes.at(-1) & 0x80) !== 0) throw new Error("OID truncated");
  return parts.join(".");
}

function parseAlgorithm(sequence) {
  if (sequence.tag !== 0x30) throw new Error("CRL signature algorithm invalid");
  const oid = readTlv(sequence.content, 0);
  if (oid.tag !== 0x06) throw new Error("CRL signature OID missing");
  const oidText = decodeOid(oid.content);
  const hash = OIDS[oidText];
  if (!hash) throw new Error("CRL signature algorithm unsupported");
  return { oid: oidText, hash };
}

function parseIntegerHex(content) {
  if (!content.length) throw new Error("DER integer empty");
  let start = 0;
  while (start < content.length - 1 && content[start] === 0) start += 1;
  return Buffer.from(content.subarray(start)).toString("hex").toUpperCase() || "00";
}

function parseTime(element) {
  const text = element.content.toString("ascii");
  let iso;
  if (element.tag === 0x17) {
    const m = text.match(/^(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/);
    if (!m) throw new Error("CRL UTCTime invalid");
    const year = Number(m[1]) >= 50 ? 1900 + Number(m[1]) : 2000 + Number(m[1]);
    iso = `${year}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}Z`;
  } else if (element.tag === 0x18) {
    const m = text.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/);
    if (!m) throw new Error("CRL GeneralizedTime invalid");
    iso = `${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}Z`;
  } else {
    throw new Error("CRL time tag invalid");
  }
  const ms = Date.parse(iso);
  if (!Number.isFinite(ms)) throw new Error("CRL time invalid");
  return ms;
}

function certificateSigningMetadata(cert) {
  const outer = readTlv(cert.raw, 0);
  if (outer.tag !== 0x30 || outer.next !== cert.raw.length) {
    throw new Error("CRL signer certificate invalid");
  }
  let cursor = 0;
  const tbs = readTlv(outer.content, cursor);
  if (tbs.tag !== 0x30) throw new Error("CRL signer certificate invalid");

  let p = 0;
  let el = readTlv(tbs.content, p);
  if (el.tag === 0xa0) p = el.next; // version
  for (let i = 0; i < 3; i += 1) { // serial, signature, issuer
    el = readTlv(tbs.content, p);
    p = el.next;
  }
  const validity = readTlv(tbs.content, p); p = validity.next;
  const subject = readTlv(tbs.content, p); p = subject.next;
  if (subject.tag !== 0x30) throw new Error("CRL signer certificate subject invalid");
  const spki = readTlv(tbs.content, p); p = spki.next;
  if (spki.tag !== 0x30) throw new Error("CRL signer certificate SPKI invalid");

  let keyUsage = null;
  while (p < tbs.content.length) {
    const extra = readTlv(tbs.content, p);
    p = extra.next;
    if (extra.tag !== 0xa3) continue;

    const extensions = readTlv(extra.content, 0);
    if (extensions.tag !== 0x30 || extensions.next !== extra.content.length) {
      throw new Error("CRL signer extensions invalid");
    }
    let ep = 0;
    while (ep < extensions.content.length) {
      const extension = readTlv(extensions.content, ep); ep = extension.next;
      if (extension.tag !== 0x30) throw new Error("CRL signer extension invalid");
      let xp = 0;
      const oid = readTlv(extension.content, xp); xp = oid.next;
      if (oid.tag !== 0x06) throw new Error("CRL signer extension OID invalid");
      const oidText = decodeOid(oid.content);
      let critical = false;
      let value = readTlv(extension.content, xp);
      if (value.tag === 0x01) {
        critical = value.content.length === 1 && value.content[0] !== 0;
        xp = value.next;
        value = readTlv(extension.content, xp);
      }
      if (value.tag !== 0x04) throw new Error("CRL signer extension value invalid");
      if (oidText !== "2.5.29.15") continue;

      const bitString = readTlv(value.content, 0);
      if (bitString.tag !== 0x03 || bitString.next !== value.content.length || bitString.content.length < 2) {
        throw new Error("CRL signer key usage invalid");
      }
      const unused = bitString.content[0];
      if (unused > 7) throw new Error("CRL signer key usage invalid");
      const firstByte = bitString.content[1];
      keyUsage = Object.freeze({
        critical,
        keyCertSign: (firstByte & 0x04) !== 0,
        crlSign: (firstByte & 0x02) !== 0,
      });
    }
  }

  return Object.freeze({
    subjectDer: Buffer.from(subject.raw),
    keyUsage,
  });
}

export function parseX509CrlDer(input) {
  const der = Buffer.isBuffer(input) ? Buffer.from(input) : Buffer.from(input || []);
  if (!der.length || der.length > MAX_CRL_BYTES) throw new Error("CRL DER invalid");

  const outer = readTlv(der, 0);
  if (outer.tag !== 0x30 || outer.next !== der.length) throw new Error("CRL outer sequence invalid");

  let cursor = 0;
  const tbs = readTlv(outer.content, cursor); cursor = tbs.next;
  if (tbs.tag !== 0x30) throw new Error("CRL TBSCertList invalid");
  const sigAlg = readTlv(outer.content, cursor); cursor = sigAlg.next;
  const signatureBits = readTlv(outer.content, cursor); cursor = signatureBits.next;
  if (cursor !== outer.content.length || signatureBits.tag !== 0x03 || signatureBits.content.length < 2 || signatureBits.content[0] !== 0) {
    throw new Error("CRL signature bits invalid");
  }
  const algorithm = parseAlgorithm(sigAlg);

  let p = 0;
  let next = readTlv(tbs.content, p);
  if (next.tag === 0x02) p = next.next; // v2 version
  const innerAlg = readTlv(tbs.content, p); p = innerAlg.next;
  const innerAlgorithm = parseAlgorithm(innerAlg);
  if (innerAlgorithm.oid !== algorithm.oid) throw new Error("CRL signature algorithm mismatch");

  const issuer = readTlv(tbs.content, p); p = issuer.next;
  if (issuer.tag !== 0x30) throw new Error("CRL issuer invalid");

  const thisUpdateEl = readTlv(tbs.content, p); p = thisUpdateEl.next;
  const thisUpdate = parseTime(thisUpdateEl);

  let nextUpdate = null;
  if (p < tbs.content.length) {
    next = readTlv(tbs.content, p);
    if (next.tag === 0x17 || next.tag === 0x18) {
      nextUpdate = parseTime(next);
      p = next.next;
    }
  }

  const revokedSerials = [];
  if (p < tbs.content.length) {
    next = readTlv(tbs.content, p);
    if (next.tag === 0x30) {
      let rp = 0;
      while (rp < next.content.length) {
        if (revokedSerials.length >= MAX_REVOKED) throw new Error("CRL revoked certificate limit exceeded");
        const entry = readTlv(next.content, rp); rp = entry.next;
        if (entry.tag !== 0x30) throw new Error("CRL revoked entry invalid");
        let ep = 0;
        const serial = readTlv(entry.content, ep); ep = serial.next;
        if (serial.tag !== 0x02) throw new Error("CRL revoked serial invalid");
        const revocationTime = readTlv(entry.content, ep);
        parseTime(revocationTime);
        revokedSerials.push(parseIntegerHex(serial.content));
      }
      p = next.next;
    }
  }

  return Object.freeze({
    der,
    tbsDer: Buffer.from(tbs.raw),
    issuerDer: Buffer.from(issuer.raw),
    signature: Buffer.from(signatureBits.content.subarray(1)),
    signatureAlgorithmOid: algorithm.oid,
    signatureHash: algorithm.hash,
    thisUpdate,
    nextUpdate,
    revokedSerials: Object.freeze([...new Set(revokedSerials)]),
  });
}

export function verifyX509Crl({
  crlDer,
  trustBundlePem,
  clock = () => Date.now(),
  clockSkewMs = 60_000,
  allowUnrelatedIssuer = false,
}) {
  if (!Array.isArray(trustBundlePem) || !trustBundlePem.length || trustBundlePem.length > 32) {
    throw new Error("CRL trust bundle invalid");
  }
  if (typeof clock !== "function") throw new Error("CRL clock invalid");
  if (!Number.isInteger(clockSkewMs) || clockSkewMs < 0 || clockSkewMs > 5 * 60_000) {
    throw new Error("CRL clock skew invalid");
  }

  const parsed = parseX509CrlDer(crlDer);
  const now = clock();
  if (now + clockSkewMs < parsed.thisUpdate) throw new Error("CRL not yet valid");
  if (parsed.nextUpdate !== null && now - clockSkewMs >= parsed.nextUpdate) throw new Error("CRL expired");

  const issuerCandidates = [];
  for (const pem of trustBundlePem) {
    let cert;
    try { cert = new X509Certificate(pem); } catch { throw new Error("CRL trust anchor invalid"); }
    if (!cert.ca) continue;
    const metadata = certificateSigningMetadata(cert);
    if (!metadata.subjectDer.equals(parsed.issuerDer)) continue;
    issuerCandidates.push({ cert, metadata });
  }

  if (!issuerCandidates.length) {
    if (allowUnrelatedIssuer) return null;
    throw new Error("CRL issuer not in trust bundle");
  }

  let signer = null;
  for (const { cert, metadata } of issuerCandidates) {
    if (!metadata.keyUsage?.critical || !metadata.keyUsage.keyCertSign || !metadata.keyUsage.crlSign) {
      continue;
    }
    const ok = verifySignature(parsed.signatureHash, parsed.tbsDer, cert.publicKey, parsed.signature);
    if (ok) {
      signer = cert;
      break;
    }
  }
  if (!signer) throw new Error("CRL signature not trusted");

  return Object.freeze({
    revokedSerials: parsed.revokedSerials,
    thisUpdate: parsed.thisUpdate,
    nextUpdate: parsed.nextUpdate,
    signerFingerprint256: signer.fingerprint256.replaceAll(":", "").toLowerCase(),
  });
}

export function isSerialRevoked(serialNumber, verifiedCrls) {
  const serial = String(serialNumber || "").replace(/^0+/, "").toUpperCase() || "0";
  return (verifiedCrls || []).some(crl =>
    Array.isArray(crl?.revokedSerials) && crl.revokedSerials.some(value =>
      (String(value).replace(/^0+/, "").toUpperCase() || "0") === serial
    )
  );
}

export const x509CrlLimits = Object.freeze({
  maxCrlBytes: MAX_CRL_BYTES,
  maxRevokedCertificates: MAX_REVOKED,
});
