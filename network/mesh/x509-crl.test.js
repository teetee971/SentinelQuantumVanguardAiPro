import test from "node:test";
import assert from "node:assert/strict";
import { X509Certificate } from "node:crypto";
import { certificateX509Metadata, isSerialRevoked, parseX509CrlDer, verifyX509Crl } from "./x509-crl.js";
import {
  CRL_TEST_CA,
  CRL_TEST_LEAF,
  CRL_REVOKING_LEAF_DER_B64,
  CRL_EMPTY_DER_B64,
  CRL_NO_SIGN_CA,
  CRL_NO_SIGN_DER_B64,
} from "./x509-crl-test-fixtures.js";
import { UNSUPPORTED_CRL_TEST_CA, DELTA_CRL_DER_B64, INDIRECT_CRL_DER_B64 } from "./x509-crl-unsupported-test-fixtures.js";
import { RSA_PSS_CRL_TEST_CA, RSA_PSS_CRL_DER_B64 } from "./x509-crl-rsa-pss-test-fixtures.js";

const NOW = Date.parse("2026-09-18T16:00:00Z");

test("parses and verifies a signed CRL against the trusted CA", () => {
  const der = Buffer.from(CRL_REVOKING_LEAF_DER_B64, "base64");
  const parsed = parseX509CrlDer(der);
  assert.equal(parsed.revokedSerials.includes("1234ABCD"), true);
  assert.ok(parsed.thisUpdate <= NOW);
  assert.ok(parsed.nextUpdate > NOW);

  const verified = verifyX509Crl({
    crlDer: der,
    trustBundlePem: [CRL_TEST_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  });
  assert.equal(verified.revokedSerials.includes("1234ABCD"), true);
  assert.match(verified.signerFingerprint256, /^[a-f0-9]{64}$/);
});

test("empty signed CRL verifies and does not revoke the leaf", () => {
  const verified = verifyX509Crl({
    crlDer: Buffer.from(CRL_EMPTY_DER_B64, "base64"),
    trustBundlePem: [CRL_TEST_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  });
  assert.deepEqual(verified.revokedSerials, []);
  const leaf = new X509Certificate(CRL_TEST_LEAF);
  assert.equal(isSerialRevoked(leaf.serialNumber, [verified]), false);
});

test("revoking CRL matches the leaf serial number", () => {
  const verified = verifyX509Crl({
    crlDer: Buffer.from(CRL_REVOKING_LEAF_DER_B64, "base64"),
    trustBundlePem: [CRL_TEST_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  });
  const leaf = new X509Certificate(CRL_TEST_LEAF);
  assert.equal(leaf.serialNumber.toUpperCase(), "1234ABCD");
  assert.equal(isSerialRevoked(leaf.serialNumber, [verified]), true);
});

test("CRL signature fails against an unrelated trust bundle", () => {
  assert.throws(() => verifyX509Crl({
    crlDer: Buffer.from(CRL_REVOKING_LEAF_DER_B64, "base64"),
    trustBundlePem: [CRL_TEST_LEAF],
    clock: () => NOW,
    clockSkewMs: 0,
  }), /issuer not in trust bundle|signature not trusted|trust anchor invalid/);
});

test("CRL parser rejects malformed and oversized DER", () => {
  assert.throws(() => parseX509CrlDer(Buffer.from([0x30, 0x01, 0x00])), /invalid|truncated|TBSCertList/);
  assert.throws(() => parseX509CrlDer(Buffer.alloc(1024 * 1024 + 1)), /CRL DER invalid/);
});


test("rejects a mathematically valid CRL when signer key usage omits cRLSign", () => {
  assert.throws(() => verifyX509Crl({
    crlDer: Buffer.from(CRL_NO_SIGN_DER_B64, "base64"),
    trustBundlePem: [CRL_NO_SIGN_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  }), /signature not trusted/);
});


test("rejects CRLs outside their validity window", () => {
  const der = Buffer.from(CRL_REVOKING_LEAF_DER_B64, "base64");
  assert.throws(() => verifyX509Crl({
    crlDer: der,
    trustBundlePem: [CRL_TEST_CA],
    clock: () => Date.parse("2026-09-17T14:00:00Z"),
    clockSkewMs: 0,
  }), /not yet valid/);

  assert.throws(() => verifyX509Crl({
    crlDer: der,
    trustBundlePem: [CRL_TEST_CA],
    clock: () => Date.parse("2026-09-20T16:00:00Z"),
    clockSkewMs: 0,
  }), /expired/);
});

test("rejects CRL when signer certificate is outside its own validity", () => {
  assert.throws(() => verifyX509Crl({
    crlDer: Buffer.from(CRL_REVOKING_LEAF_DER_B64, "base64"),
    trustBundlePem: [CRL_TEST_CA],
    clock: () => Date.parse("2028-09-18T16:00:00Z"),
    clockSkewMs: 0,
  }), /CRL expired|signer certificate outside validity/);
});


test("rejects delta CRLs explicitly until delta semantics are supported", () => {
  assert.throws(() => parseX509CrlDer(Buffer.from(DELTA_CRL_DER_B64, "base64")), /delta CRL unsupported/);
  assert.throws(() => verifyX509Crl({
    crlDer: Buffer.from(DELTA_CRL_DER_B64, "base64"),
    trustBundlePem: [UNSUPPORTED_CRL_TEST_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  }), /delta CRL unsupported/);
});

test("rejects issuingDistributionPoint CRLs explicitly until indirect semantics are supported", () => {
  assert.throws(() => parseX509CrlDer(Buffer.from(INDIRECT_CRL_DER_B64, "base64")), /issuing distribution point CRL unsupported/);
  assert.throws(() => verifyX509Crl({
    crlDer: Buffer.from(INDIRECT_CRL_DER_B64, "base64"),
    trustBundlePem: [UNSUPPORTED_CRL_TEST_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  }), /issuing distribution point CRL unsupported/);
});


test("parses and verifies an RSA-PSS SHA-256 CRL with explicit bounded parameters", () => {
  const der = Buffer.from(RSA_PSS_CRL_DER_B64, "base64");
  const parsed = parseX509CrlDer(der);
  assert.equal(parsed.signatureAlgorithmOid, "1.2.840.113549.1.1.10");
  assert.equal(parsed.signatureHash, "sha256");
  assert.equal(parsed.signatureSaltLength, 32);

  const verified = verifyX509Crl({
    crlDer: der,
    trustBundlePem: [RSA_PSS_CRL_TEST_CA],
    clock: () => Date.parse("2026-09-18T19:10:00Z"),
    clockSkewMs: 0,
  });
  assert.deepEqual(verified.revokedSerials, []);
  assert.match(verified.signerFingerprint256, /^[a-f0-9]{64}$/);
});


test("rejects non-canonical DER INTEGER encoding in a revoked certificate serial", () => {
  const der = Buffer.from("MIIBgTBrAgEBMA0GCSqGSIb3DQEBCwUAMB8xHTAbBgNVBAMMFFNlbnRpbmVsIENSTCBUZXN0IENBFw0yNjA5MTgxNTAwMDBaFw0yNjA5MTkxNjAwMDBaMBgwFgIFABI0q80XDTI2MDkxODE1MzAwMFowDQYJKoZIhvcNAQELBQADggEBAEJA5kWPrkmHWZy7nzFlkY73MX2l3hluixIJAEIkGsl1GHlkUzMHylFxSRgyG0C9dtbdlMIWSYkSnJaOjRSe0Wek+ZTFRstjCP6hOPvTIpl9ECY1gDmKuf68hU9jZK4ylpqUNsaLIlCHLdZL3ASvtEoaYVixShvcHTeRSz1sHSPPpgnU+zktaQ+W4I54Ypw3AlD319uEUGj+EKXgfjM7jtPuMwvWrkB4U99XRH/mcbAPzZp87N0wuMkZj7L/aJ8YL1IzqYFFp0371BGk3pBOpaNBGJpLSdqE8uouywhgnuQTZiCygpwAvvHqmWOqdds+5zcBccmhXbCsdMO9hmdfV+s=", "base64");
  assert.throws(() => parseX509CrlDer(der), /DER integer not canonical/);
});

test("rejects non-canonical DER TRUE in X.509 certificate extensions even when Node parses the certificate", () => {
  const body = CRL_TEST_CA
    .replace("-----BEGIN CERTIFICATE-----", "")
    .replace("-----END CERTIFICATE-----", "")
    .replace(/\s+/g, "");
  const der = Buffer.from(body, "base64");
  const marker = Buffer.from([0x01, 0x01, 0xff]);
  const index = der.indexOf(marker);
  assert.notEqual(index, -1);
  der[index + 2] = 0x01;
  const encoded = der.toString("base64").match(/.{1,64}/g).join("\n");
  const pem = `-----BEGIN CERTIFICATE-----\n${encoded}\n-----END CERTIFICATE-----\n`;
  const cert = new X509Certificate(pem);
  assert.throws(() => certificateX509Metadata(cert), /boolean not canonical/);
});


test("rejects non-zero unused bits in DER Key Usage BIT STRING", () => {
  const body = CRL_TEST_CA
    .replace("-----BEGIN CERTIFICATE-----", "")
    .replace("-----END CERTIFICATE-----", "")
    .replace(/\s+/g, "");
  const der = Buffer.from(body, "base64");
  const marker = Buffer.from([0x03, 0x02, 0x01, 0x06]);
  const index = der.indexOf(marker);
  assert.notEqual(index, -1);
  der[index + 3] = 0x07;
  const encoded = der.toString("base64").match(/.{1,64}/g).join("\n");
  const pem = `-----BEGIN CERTIFICATE-----\n${encoded}\n-----END CERTIFICATE-----\n`;
  const cert = new X509Certificate(pem);
  assert.throws(() => certificateX509Metadata(cert), /BIT STRING not canonical/);
});


test("rejects non-canonical base-128 DER OID encoding", () => {
  const der = Buffer.from([
    0x30, 0x0d,
    0x30, 0x00,
    0x30, 0x05,
    0x06, 0x03, 0x2a, 0x80, 0x00,
    0x03, 0x02, 0x00, 0x00,
  ]);
  assert.throws(() => parseX509CrlDer(der), /OID not canonical/);
});

test("keeps canonical RSA-PSS OID decoding compatible with signed CRL fixtures", () => {
  const parsed = parseX509CrlDer(Buffer.from(RSA_PSS_CRL_DER_B64, "base64"));
  assert.equal(parsed.signatureAlgorithmOid, "1.2.840.113549.1.1.10");
  assert.equal(parsed.signatureHash, "sha256");
});


test("rejects long-form DER length with a redundant leading zero octet", () => {
  const der = Buffer.from([0x30, 0x82, 0x00, 0x80, ...Buffer.alloc(128)]);
  assert.throws(() => parseX509CrlDer(der), /DER length not canonical/);
});


test("rejects CRL version values other than v2", () => {
  const der = Buffer.from(CRL_REVOKING_LEAF_DER_B64, "base64");
  const marker = Buffer.from([0x02, 0x01, 0x01]);
  const index = der.indexOf(marker);
  assert.notEqual(index, -1);
  der[index + 2] = 0x00;
  assert.throws(() => parseX509CrlDer(der), /CRL version must be v2/);
});

test("reports v2 for CRLs that explicitly carry the RFC version field", () => {
  const parsed = parseX509CrlDer(Buffer.from(RSA_PSS_CRL_DER_B64, "base64"));
  assert.equal(parsed.version, 1);
});


function derLen(length) {
  if (length < 0x80) return Buffer.from([length]);
  if (length <= 0xff) return Buffer.from([0x81, length]);
  return Buffer.from([0x82, length >> 8, length & 0xff]);
}

function derTlv(tag, ...parts) {
  const content = Buffer.concat(parts);
  return Buffer.concat([Buffer.from([tag]), derLen(content.length), content]);
}

function syntheticCrlWithEntryExtension({ oidBytes, critical }) {
  const alg = derTlv(
    0x30,
    derTlv(0x06, Buffer.from("2a864886f70d01010b", "hex")),
    derTlv(0x05)
  );
  const extensionParts = [derTlv(0x06, Buffer.from(oidBytes))];
  if (critical) extensionParts.push(derTlv(0x01, Buffer.from([0xff])));
  extensionParts.push(derTlv(0x04));
  const entryExtension = derTlv(0x30, ...extensionParts);
  const entryExtensions = derTlv(0x30, entryExtension);
  const entry = derTlv(
    0x30,
    derTlv(0x02, Buffer.from([0x01])),
    derTlv(0x17, Buffer.from("260918150000Z", "ascii")),
    entryExtensions
  );
  const revoked = derTlv(0x30, entry);
  const tbs = derTlv(
    0x30,
    derTlv(0x02, Buffer.from([0x01])),
    alg,
    derTlv(0x30),
    derTlv(0x17, Buffer.from("260918150000Z", "ascii")),
    derTlv(0x17, Buffer.from("260919150000Z", "ascii")),
    revoked
  );
  return derTlv(0x30, tbs, alg, derTlv(0x03, Buffer.from([0x00, 0x00])));
}

test("rejects certificateIssuer CRL entry extensions until indirect CRL semantics are supported", () => {
  const der = syntheticCrlWithEntryExtension({
    oidBytes: [0x55, 0x1d, 0x1d],
    critical: true,
  });
  assert.throws(() => parseX509CrlDer(der), /certificateIssuer CRL entry extension unsupported/);
});

test("rejects unknown critical CRL entry extensions", () => {
  const der = syntheticCrlWithEntryExtension({
    oidBytes: [0x2a, 0x03, 0x04],
    critical: true,
  });
  assert.throws(() => parseX509CrlDer(der), /critical CRL entry extension unsupported/);
});

test("allows structurally valid unknown non-critical CRL entry extensions", () => {
  const der = syntheticCrlWithEntryExtension({
    oidBytes: [0x2a, 0x03, 0x04],
    critical: false,
  });
  const parsed = parseX509CrlDer(der);
  assert.deepEqual(parsed.revokedSerials, ["01"]);
  assert.equal(parsed.version, 1);
});


function syntheticCrlWithCrlExtension({ oidBytes, critical }) {
  const alg = derTlv(
    0x30,
    derTlv(0x06, Buffer.from("2a864886f70d01010b", "hex")),
    derTlv(0x05)
  );
  const extensionParts = [derTlv(0x06, Buffer.from(oidBytes))];
  if (critical) extensionParts.push(derTlv(0x01, Buffer.from([0xff])));
  extensionParts.push(derTlv(0x04));
  const extension = derTlv(0x30, ...extensionParts);
  const extensions = derTlv(0x30, extension);
  const explicitExtensions = derTlv(0xa0, extensions);
  const tbs = derTlv(
    0x30,
    derTlv(0x02, Buffer.from([0x01])),
    alg,
    derTlv(0x30),
    derTlv(0x17, Buffer.from("260918150000Z", "ascii")),
    derTlv(0x17, Buffer.from("260919150000Z", "ascii")),
    explicitExtensions
  );
  return derTlv(0x30, tbs, alg, derTlv(0x03, Buffer.from([0x00, 0x00])));
}

test("rejects unknown critical CRL-level extensions", () => {
  const der = syntheticCrlWithCrlExtension({
    oidBytes: [0x2a, 0x03, 0x05],
    critical: true,
  });
  assert.throws(() => parseX509CrlDer(der), /critical CRL extension unsupported/);
});

test("allows structurally valid unknown non-critical CRL-level extensions", () => {
  const der = syntheticCrlWithCrlExtension({
    oidBytes: [0x2a, 0x03, 0x05],
    critical: false,
  });
  const parsed = parseX509CrlDer(der);
  assert.deepEqual(parsed.extensionOids, ["1.2.3.5"]);
  assert.equal(parsed.version, 1);
});


function syntheticCrlWithDuplicateEntryExtension() {
  const alg = derTlv(
    0x30,
    derTlv(0x06, Buffer.from("2a864886f70d01010b", "hex")),
    derTlv(0x05)
  );
  const oid = Buffer.from([0x2a, 0x03, 0x06]);
  const ext = () => derTlv(0x30, derTlv(0x06, oid), derTlv(0x04));
  const entry = derTlv(
    0x30,
    derTlv(0x02, Buffer.from([0x01])),
    derTlv(0x17, Buffer.from("260918150000Z", "ascii")),
    derTlv(0x30, ext(), ext())
  );
  const tbs = derTlv(
    0x30,
    derTlv(0x02, Buffer.from([0x01])),
    alg,
    derTlv(0x30),
    derTlv(0x17, Buffer.from("260918150000Z", "ascii")),
    derTlv(0x17, Buffer.from("260919150000Z", "ascii")),
    derTlv(0x30, entry)
  );
  return derTlv(0x30, tbs, alg, derTlv(0x03, Buffer.from([0x00, 0x00])));
}

function syntheticCrlWithDuplicateCrlExtension() {
  const alg = derTlv(
    0x30,
    derTlv(0x06, Buffer.from("2a864886f70d01010b", "hex")),
    derTlv(0x05)
  );
  const oid = Buffer.from([0x2a, 0x03, 0x07]);
  const ext = () => derTlv(0x30, derTlv(0x06, oid), derTlv(0x04));
  const extensions = derTlv(0x30, ext(), ext());
  const tbs = derTlv(
    0x30,
    derTlv(0x02, Buffer.from([0x01])),
    alg,
    derTlv(0x30),
    derTlv(0x17, Buffer.from("260918150000Z", "ascii")),
    derTlv(0x17, Buffer.from("260919150000Z", "ascii")),
    derTlv(0xa0, extensions)
  );
  return derTlv(0x30, tbs, alg, derTlv(0x03, Buffer.from([0x00, 0x00])));
}

test("rejects duplicate OIDs in revoked-entry extensions", () => {
  assert.throws(() => parseX509CrlDer(syntheticCrlWithDuplicateEntryExtension()), /duplicate CRL revoked entry extension/);
});

test("rejects duplicate OIDs in CRL-level extensions", () => {
  assert.throws(() => parseX509CrlDer(syntheticCrlWithDuplicateCrlExtension()), /duplicate CRL extension/);
});


function syntheticCertificateMetadataRawWithExtensions(...extensions) {
  const tbs = derTlv(
    0x30,
    derTlv(0xa0, derTlv(0x02, Buffer.from([0x02]))),
    derTlv(0x02, Buffer.from([0x01])),
    derTlv(0x30),
    derTlv(0x30),
    derTlv(0x30),
    derTlv(0x30),
    derTlv(0x30),
    derTlv(0xa3, derTlv(0x30, ...extensions))
  );
  return derTlv(0x30, tbs);
}

function syntheticCertificateExtension(oidBytes, innerValue, critical = false) {
  const parts = [derTlv(0x06, Buffer.from(oidBytes))];
  if (critical) parts.push(derTlv(0x01, Buffer.from([0xff])));
  parts.push(derTlv(0x04, innerValue));
  return derTlv(0x30, ...parts);
}

test("rejects duplicate KeyUsage certificate extensions", () => {
  const keyUsageValue = derTlv(0x03, Buffer.from([0x00, 0x86]));
  const ext = syntheticCertificateExtension([0x55, 0x1d, 0x0f], keyUsageValue, true);
  const cert = { raw: syntheticCertificateMetadataRawWithExtensions(ext, ext) };
  assert.throws(() => certificateX509Metadata(cert), /duplicate certificate key usage extension/);
});

test("rejects duplicate BasicConstraints certificate extensions", () => {
  const basicConstraintsValue = derTlv(0x30, derTlv(0x01, Buffer.from([0xff])));
  const ext = syntheticCertificateExtension([0x55, 0x1d, 0x13], basicConstraintsValue, true);
  const cert = { raw: syntheticCertificateMetadataRawWithExtensions(ext, ext) };
  assert.throws(() => certificateX509Metadata(cert), /duplicate certificate basic constraints extension/);
});

test("rejects duplicate ExtendedKeyUsage certificate extensions", () => {
  const ekuValue = derTlv(0x30, derTlv(0x06, Buffer.from("2b06010505070301", "hex")));
  const ext = syntheticCertificateExtension([0x55, 0x1d, 0x25], ekuValue, false);
  const cert = { raw: syntheticCertificateMetadataRawWithExtensions(ext, ext) };
  assert.throws(() => certificateX509Metadata(cert), /duplicate certificate extended key usage extension/);
});
