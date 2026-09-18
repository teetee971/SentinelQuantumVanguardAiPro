import { constants as cryptoConstants, verify as verifySignature, X509Certificate } from "node:crypto";

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
const RSA_PSS_OID = "1.2.840.113549.1.1.10";
const MGF1_OID = "1.2.840.113549.1.1.8";
const HASH_OIDS = Object.freeze({
  "2.16.840.1.101.3.4.2.1": Object.freeze({ hash: "sha256", saltLength: 32 }),
  "2.16.840.1.101.3.4.2.2": Object.freeze({ hash: "sha384", saltLength: 48 }),
  "2.16.840.1.101.3.4.2.3": Object.freeze({ hash: "sha512", saltLength: 64 }),
});

function readLength(buffer, offset) {
  if (offset >= buffer.length) throw new Error("DER length truncated");
  const first = buffer[offset++];
  if ((first & 0x80) === 0) return { length: first, offset };
  const count = first & 0x7f;
  if (count === 0 || count > 4 || offset + count > buffer.length) throw new Error("DER length invalid");
  if (buffer[offset] === 0x00) throw new Error("DER length not canonical");
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
  const subidentifiers = [];
  let value = 0;
  let atStart = true;

  for (const byte of bytes) {
    if (atStart && byte === 0x80) throw new Error("OID not canonical");
    value = (value * 128) + (byte & 0x7f);
    if (!Number.isSafeInteger(value)) throw new Error("OID component too large");
    if ((byte & 0x80) === 0) {
      subidentifiers.push(value);
      value = 0;
      atStart = true;
    } else {
      atStart = false;
    }
  }
  if (!atStart) throw new Error("OID truncated");
  if (!subidentifiers.length) throw new Error("OID empty");

  const first = subidentifiers[0];
  let firstArc;
  let secondArc;
  if (first < 40) {
    firstArc = 0;
    secondArc = first;
  } else if (first < 80) {
    firstArc = 1;
    secondArc = first - 40;
  } else {
    firstArc = 2;
    secondArc = first - 80;
  }

  return [firstArc, secondArc, ...subidentifiers.slice(1)].join(".");
}

function parseHashAlgorithmIdentifier(sequence, name) {
  if (sequence.tag !== 0x30) throw new Error(`${name} invalid`);
  let p = 0;
  const oid = readTlv(sequence.content, p); p = oid.next;
  if (oid.tag !== 0x06) throw new Error(`${name} OID missing`);
  const oidText = decodeOid(oid.content);
  const profile = HASH_OIDS[oidText];
  if (!profile) throw new Error(`${name} unsupported`);
  if (p < sequence.content.length) {
    const params = readTlv(sequence.content, p); p = params.next;
    if (params.tag !== 0x05 || params.content.length !== 0) throw new Error(`${name} parameters invalid`);
  }
  if (p !== sequence.content.length) throw new Error(`${name} trailing data invalid`);
  return profile;
}

function parsePssParameters(element) {
  if (element.tag !== 0x30) throw new Error("CRL RSA-PSS parameters invalid");
  let p = 0;
  let hashProfile = null;
  let mgfHashProfile = null;
  let saltLength = null;
  let trailerField = 1;

  while (p < element.content.length) {
    const field = readTlv(element.content, p); p = field.next;
    if (field.tag === 0xa0) {
      if (hashProfile) throw new Error("CRL RSA-PSS hash duplicated");
      const alg = readTlv(field.content, 0);
      if (alg.next !== field.content.length) throw new Error("CRL RSA-PSS hash invalid");
      hashProfile = parseHashAlgorithmIdentifier(alg, "CRL RSA-PSS hash");
      continue;
    }
    if (field.tag === 0xa1) {
      if (mgfHashProfile) throw new Error("CRL RSA-PSS MGF duplicated");
      const mgf = readTlv(field.content, 0);
      if (mgf.tag !== 0x30 || mgf.next !== field.content.length) throw new Error("CRL RSA-PSS MGF invalid");
      let mp = 0;
      const mgfOid = readTlv(mgf.content, mp); mp = mgfOid.next;
      if (mgfOid.tag !== 0x06 || decodeOid(mgfOid.content) !== MGF1_OID) {
        throw new Error("CRL RSA-PSS MGF unsupported");
      }
      const mgfHash = readTlv(mgf.content, mp); mp = mgfHash.next;
      if (mp !== mgf.content.length) throw new Error("CRL RSA-PSS MGF trailing data invalid");
      mgfHashProfile = parseHashAlgorithmIdentifier(mgfHash, "CRL RSA-PSS MGF hash");
      continue;
    }
    if (field.tag === 0xa2) {
      if (saltLength !== null) throw new Error("CRL RSA-PSS salt length duplicated");
      const value = readTlv(field.content, 0);
      if (value.tag !== 0x02 || value.next !== field.content.length) throw new Error("CRL RSA-PSS salt length invalid");
      saltLength = parseNonNegativeInteger(value.content, "CRL RSA-PSS salt length");
      continue;
    }
    if (field.tag === 0xa3) {
      const value = readTlv(field.content, 0);
      if (value.tag !== 0x02 || value.next !== field.content.length) throw new Error("CRL RSA-PSS trailer field invalid");
      trailerField = parseNonNegativeInteger(value.content, "CRL RSA-PSS trailer field");
      continue;
    }
    throw new Error("CRL RSA-PSS parameter unsupported");
  }

  if (!hashProfile || !mgfHashProfile || saltLength === null) {
    throw new Error("CRL RSA-PSS explicit SHA-2 parameters required");
  }
  if (hashProfile.hash !== mgfHashProfile.hash) throw new Error("CRL RSA-PSS MGF hash mismatch");
  if (saltLength !== hashProfile.saltLength) throw new Error("CRL RSA-PSS salt length unsupported");
  if (trailerField !== 1) throw new Error("CRL RSA-PSS trailer field unsupported");

  return Object.freeze({
    hash: hashProfile.hash,
    padding: cryptoConstants.RSA_PKCS1_PSS_PADDING,
    saltLength,
    key: `pss:${hashProfile.hash}:${saltLength}:1`,
  });
}

function parseAlgorithm(sequence) {
  if (sequence.tag !== 0x30) throw new Error("CRL signature algorithm invalid");
  let p = 0;
  const oid = readTlv(sequence.content, p); p = oid.next;
  if (oid.tag !== 0x06) throw new Error("CRL signature OID missing");
  const oidText = decodeOid(oid.content);

  if (oidText === RSA_PSS_OID) {
    if (p >= sequence.content.length) throw new Error("CRL RSA-PSS parameters required");
    const params = readTlv(sequence.content, p); p = params.next;
    if (p !== sequence.content.length) throw new Error("CRL signature algorithm trailing data invalid");
    const pss = parsePssParameters(params);
    return Object.freeze({ oid: oidText, hash: pss.hash, padding: pss.padding, saltLength: pss.saltLength, key: pss.key });
  }

  const hash = OIDS[oidText];
  if (!hash) throw new Error("CRL signature algorithm unsupported");
  if (p < sequence.content.length) {
    const params = readTlv(sequence.content, p); p = params.next;
    if (params.tag !== 0x05 || params.content.length !== 0) throw new Error("CRL signature parameters invalid");
  }
  if (p !== sequence.content.length) throw new Error("CRL signature algorithm trailing data invalid");
  return Object.freeze({ oid: oidText, hash, padding: null, saltLength: null, key: `classic:${oidText}` });
}

function assertCanonicalNonNegativeInteger(content, name) {
  if (!content.length || (content[0] & 0x80) !== 0) throw new Error(`${name} invalid`);
  if (content.length > 1 && content[0] === 0x00 && (content[1] & 0x80) === 0) {
    throw new Error(`${name} not canonical`);
  }
}

function parseCanonicalBoolean(content, name) {
  if (content.length !== 1 || (content[0] !== 0x00 && content[0] !== 0xff)) {
    throw new Error(`${name} not canonical`);
  }
  return content[0] === 0xff;
}

function parseIntegerHex(content) {
  assertCanonicalNonNegativeInteger(content, "DER integer");
  const start = content.length > 1 && content[0] === 0x00 ? 1 : 0;
  return Buffer.from(content.subarray(start)).toString("hex").toUpperCase() || "00";
}

function parseCrlEntryExtensions(sequence) {
  if (sequence.tag !== 0x30) throw new Error("CRL revoked entry extensions invalid");
  let p = 0;
  while (p < sequence.content.length) {
    const extension = readTlv(sequence.content, p); p = extension.next;
    if (extension.tag !== 0x30) throw new Error("CRL revoked entry extension invalid");

    let ep = 0;
    const oid = readTlv(extension.content, ep); ep = oid.next;
    if (oid.tag !== 0x06) throw new Error("CRL revoked entry extension OID invalid");
    const oidText = decodeOid(oid.content);

    let critical = false;
    let value = readTlv(extension.content, ep);
    if (value.tag === 0x01) {
      critical = parseCanonicalBoolean(value.content, "CRL revoked entry extension critical boolean");
      ep = value.next;
      value = readTlv(extension.content, ep);
    }
    if (value.tag !== 0x04) throw new Error("CRL revoked entry extension value invalid");
    ep = value.next;
    if (ep !== extension.content.length) throw new Error("CRL revoked entry extension trailing data invalid");

    if (oidText === "2.5.29.29") {
      throw new Error("certificateIssuer CRL entry extension unsupported");
    }
    if (critical) {
      throw new Error("critical CRL entry extension unsupported");
    }
  }
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

function parseNonNegativeInteger(content, name) {
  assertCanonicalNonNegativeInteger(content, name);
  let value = 0;
  for (const byte of content) {
    value = (value * 256) + byte;
    if (!Number.isSafeInteger(value)) throw new Error(`${name} too large`);
  }
  return value;
}

export function certificateX509Metadata(cert) {
  const outer = readTlv(cert.raw, 0);
  if (outer.tag !== 0x30 || outer.next !== cert.raw.length) {
    throw new Error("CA certificate invalid");
  }
  let cursor = 0;
  const tbs = readTlv(outer.content, cursor);
  if (tbs.tag !== 0x30) throw new Error("CA certificate invalid");

  let p = 0;
  let el = readTlv(tbs.content, p);
  if (el.tag === 0xa0) p = el.next;
  for (let i = 0; i < 3; i += 1) {
    el = readTlv(tbs.content, p);
    p = el.next;
  }
  const validity = readTlv(tbs.content, p); p = validity.next;
  const subject = readTlv(tbs.content, p); p = subject.next;
  if (subject.tag !== 0x30) throw new Error("CA certificate subject invalid");
  const spki = readTlv(tbs.content, p); p = spki.next;
  if (spki.tag !== 0x30) throw new Error("CA certificate SPKI invalid");

  let keyUsage = null;
  let basicConstraints = null;
  let extendedKeyUsage = null;
  while (p < tbs.content.length) {
    const extra = readTlv(tbs.content, p);
    p = extra.next;
    if (extra.tag !== 0xa3) continue;

    const extensions = readTlv(extra.content, 0);
    if (extensions.tag !== 0x30 || extensions.next !== extra.content.length) {
      throw new Error("CA certificate extensions invalid");
    }
    let ep = 0;
    while (ep < extensions.content.length) {
      const extension = readTlv(extensions.content, ep); ep = extension.next;
      if (extension.tag !== 0x30) throw new Error("CA certificate extension invalid");
      let xp = 0;
      const oid = readTlv(extension.content, xp); xp = oid.next;
      if (oid.tag !== 0x06) throw new Error("CA certificate extension OID invalid");
      const oidText = decodeOid(oid.content);
      let critical = false;
      let value = readTlv(extension.content, xp);
      if (value.tag === 0x01) {
        critical = parseCanonicalBoolean(value.content, "certificate extension critical boolean");
        xp = value.next;
        value = readTlv(extension.content, xp);
      }
      if (value.tag !== 0x04) throw new Error("CA certificate extension value invalid");

      if (oidText === "2.5.29.15") {
        const bitString = readTlv(value.content, 0);
        if (bitString.tag !== 0x03 || bitString.next !== value.content.length || bitString.content.length < 2) {
          throw new Error("CA certificate key usage invalid");
        }
        const unused = bitString.content[0];
        if (unused > 7) throw new Error("CA certificate key usage invalid");
        const lastByte = bitString.content.at(-1);
        if (unused > 0 && (lastByte & ((1 << unused) - 1)) !== 0) {
          throw new Error("CA certificate key usage BIT STRING not canonical");
        }
        const firstByte = bitString.content[1];
        const secondByte = bitString.content.length > 2 ? bitString.content[2] : 0;
        keyUsage = Object.freeze({
          critical,
          digitalSignature: (firstByte & 0x80) !== 0,
          keyEncipherment: (firstByte & 0x20) !== 0,
          keyAgreement: (firstByte & 0x08) !== 0,
          keyCertSign: (firstByte & 0x04) !== 0,
          crlSign: (firstByte & 0x02) !== 0,
          encipherOnly: (firstByte & 0x01) !== 0,
          decipherOnly: (secondByte & 0x80) !== 0,
        });
      }

      if (oidText === "2.5.29.19") {
        const sequence = readTlv(value.content, 0);
        if (sequence.tag !== 0x30 || sequence.next !== value.content.length) {
          throw new Error("CA certificate basic constraints invalid");
        }
        let bp = 0;
        let ca = false;
        let pathLenConstraint = null;
        if (bp < sequence.content.length) {
          let item = readTlv(sequence.content, bp);
          if (item.tag === 0x01) {
            ca = parseCanonicalBoolean(item.content, "CA certificate basic constraints boolean");
            bp = item.next;
          }
        }
        if (bp < sequence.content.length) {
          const item = readTlv(sequence.content, bp);
          if (item.tag !== 0x02) throw new Error("CA certificate path length invalid");
          pathLenConstraint = parseNonNegativeInteger(item.content, "CA certificate path length");
          bp = item.next;
        }
        if (bp !== sequence.content.length || (pathLenConstraint !== null && !ca)) {
          throw new Error("CA certificate basic constraints invalid");
        }
        basicConstraints = Object.freeze({ critical, ca, pathLenConstraint });
      }


      if (oidText === "2.5.29.37") {
        const sequence = readTlv(value.content, 0);
        if (sequence.tag !== 0x30 || sequence.next !== value.content.length) {
          throw new Error("certificate extended key usage invalid");
        }
        const usages = [];
        let up = 0;
        while (up < sequence.content.length) {
          const usage = readTlv(sequence.content, up);
          up = usage.next;
          if (usage.tag !== 0x06) throw new Error("certificate extended key usage invalid");
          usages.push(decodeOid(usage.content));
        }
        extendedKeyUsage = Object.freeze({ critical, usages: Object.freeze(usages) });
      }
    }
  }

  return Object.freeze({
    subjectDer: Buffer.from(subject.raw),
    keyUsage,
    basicConstraints,
    extendedKeyUsage,
  });
}

export const certificateAuthorityMetadata = certificateX509Metadata;

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
  let version = null;
  if (next.tag === 0x02) {
    version = parseNonNegativeInteger(next.content, "CRL version");
    if (version !== 1) throw new Error("CRL version must be v2");
    p = next.next;
  }
  const innerAlg = readTlv(tbs.content, p); p = innerAlg.next;
  const innerAlgorithm = parseAlgorithm(innerAlg);
  if (innerAlgorithm.key !== algorithm.key) throw new Error("CRL signature algorithm mismatch");

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
  let hasEntryExtensions = false;
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
        const revocationTime = readTlv(entry.content, ep); ep = revocationTime.next;
        parseTime(revocationTime);
        if (ep < entry.content.length) {
          const entryExtensions = readTlv(entry.content, ep);
          if (entryExtensions.tag !== 0x30 || entryExtensions.next !== entry.content.length) {
            throw new Error("CRL revoked entry extensions invalid");
          }
          parseCrlEntryExtensions(entryExtensions);
          hasEntryExtensions = true;
        }
        revokedSerials.push(parseIntegerHex(serial.content));
      }
      p = next.next;
    }
  }

  if (hasEntryExtensions && version !== 1) throw new Error("CRL entry extensions require v2");

  const extensionOids = [];
  if (p < tbs.content.length) {
    if (version !== 1) throw new Error("CRL extensions require v2");
    const explicitExtensions = readTlv(tbs.content, p);
    if (explicitExtensions.tag !== 0xa0 || explicitExtensions.next !== tbs.content.length) {
      throw new Error("CRL trailing data unsupported");
    }
    const extensions = readTlv(explicitExtensions.content, 0);
    if (extensions.tag !== 0x30 || extensions.next !== explicitExtensions.content.length) {
      throw new Error("CRL extensions invalid");
    }
    let xp = 0;
    while (xp < extensions.content.length) {
      const extension = readTlv(extensions.content, xp); xp = extension.next;
      if (extension.tag !== 0x30) throw new Error("CRL extension invalid");
      let ep = 0;
      const oid = readTlv(extension.content, ep); ep = oid.next;
      if (oid.tag !== 0x06) throw new Error("CRL extension OID invalid");
      const oidText = decodeOid(oid.content);
      let value = readTlv(extension.content, ep);
      if (value.tag === 0x01) {
        parseCanonicalBoolean(value.content, "CRL extension critical boolean");
        ep = value.next;
        value = readTlv(extension.content, ep);
      }
      if (value.tag !== 0x04) throw new Error("CRL extension value invalid");
      ep = value.next;
      if (ep !== extension.content.length) throw new Error("CRL extension trailing data invalid");
      extensionOids.push(oidText);
      if (oidText === "2.5.29.27") throw new Error("delta CRL unsupported");
      if (oidText === "2.5.29.28") throw new Error("issuing distribution point CRL unsupported");
    }
    p = explicitExtensions.next;
  }
  if (p !== tbs.content.length) throw new Error("CRL trailing data unsupported");

  return Object.freeze({
    der,
    tbsDer: Buffer.from(tbs.raw),
    issuerDer: Buffer.from(issuer.raw),
    signature: Buffer.from(signatureBits.content.subarray(1)),
    signatureAlgorithmOid: algorithm.oid,
    signatureHash: algorithm.hash,
    signaturePadding: algorithm.padding,
    signatureSaltLength: algorithm.saltLength,
    version,
    thisUpdate,
    nextUpdate,
    revokedSerials: Object.freeze([...new Set(revokedSerials)]),
    extensionOids: Object.freeze(extensionOids),
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
  if (parsed.nextUpdate === null) throw new Error("CRL nextUpdate required");
  if (parsed.nextUpdate <= parsed.thisUpdate) throw new Error("CRL validity window invalid");
  if (now + clockSkewMs < parsed.thisUpdate) throw new Error("CRL not yet valid");
  if (now - clockSkewMs >= parsed.nextUpdate) throw new Error("CRL expired");

  const issuerCandidates = [];
  for (const pem of trustBundlePem) {
    let cert;
    try { cert = new X509Certificate(pem); } catch { throw new Error("CRL trust anchor invalid"); }
    if (!cert.ca) continue;
    const metadata = certificateAuthorityMetadata(cert);
    if (!metadata.subjectDer.equals(parsed.issuerDer)) continue;
    issuerCandidates.push({ cert, metadata });
  }

  if (!issuerCandidates.length) {
    if (allowUnrelatedIssuer) return null;
    throw new Error("CRL issuer not in trust bundle");
  }

  let signer = null;
  let authorizedUsageSeen = false;
  let timeValidSignerSeen = false;
  for (const { cert, metadata } of issuerCandidates) {
    if (!metadata.keyUsage?.critical || !metadata.keyUsage.keyCertSign || !metadata.keyUsage.crlSign) {
      continue;
    }
    authorizedUsageSeen = true;

    const validFrom = Date.parse(cert.validFrom);
    const validTo = Date.parse(cert.validTo);
    if (!Number.isFinite(validFrom) || !Number.isFinite(validTo)) {
      throw new Error("CRL signer certificate validity invalid");
    }
    if (now + clockSkewMs < validFrom || now - clockSkewMs >= validTo) {
      continue;
    }
    timeValidSignerSeen = true;

    const verificationKey = parsed.signaturePadding === null
      ? cert.publicKey
      : { key: cert.publicKey, padding: parsed.signaturePadding, saltLength: parsed.signatureSaltLength };
    const ok = verifySignature(parsed.signatureHash, parsed.tbsDer, verificationKey, parsed.signature);
    if (ok) {
      signer = cert;
      break;
    }
  }
  if (!signer) {
    if (authorizedUsageSeen && !timeValidSignerSeen) {
      throw new Error("CRL signer certificate outside validity");
    }
    throw new Error("CRL signature not trusted");
  }

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
