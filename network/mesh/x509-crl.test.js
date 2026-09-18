import test from "node:test";
import assert from "node:assert/strict";
import { X509Certificate } from "node:crypto";
import { isSerialRevoked, parseX509CrlDer, verifyX509Crl } from "./x509-crl.js";
import {
  CRL_TEST_CA,
  CRL_TEST_LEAF,
  CRL_REVOKING_LEAF_DER_B64,
  CRL_EMPTY_DER_B64,
  CRL_NO_SIGN_CA,
  CRL_NO_SIGN_DER_B64,
} from "./x509-crl-test-fixtures.js";
import { UNSUPPORTED_CRL_TEST_CA, DELTA_CRL_DER_B64, INDIRECT_CRL_DER_B64 } from "./x509-crl-unsupported-test-fixtures.js";

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
