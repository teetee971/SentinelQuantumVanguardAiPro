import test from "node:test";
import assert from "node:assert/strict";
import { X509Certificate } from "node:crypto";
import { X509SvidVerifier, VerifiedSvidEvidence, isVerifiedSvidEvidence, parseSubjectAltNameEntries } from "./x509-svid-verifier.js";
import { certificateAuthorityMetadata } from "./x509-crl.js";

import { NOW, CA, LEAF, MULTI, EXPIRED, BAD_LEAF, CA_DNS_EXTRA, LEAF_DNS_EXTRA } from "./x509-svid-test-fixtures.js";
import { CRL_TEST_CA, CRL_TEST_LEAF, CRL_REVOKING_LEAF_DER_B64, CRL_EMPTY_DER_B64, CRL_NO_SIGN_DER_B64 } from "./x509-crl-test-fixtures.js";
import { CHAIN_ROOT_CA, CHAIN_INTERMEDIATE_CA, CHAIN_LEAF } from "./x509-svid-chain-test-fixtures.js";
import { INTERMEDIATE_CRL_ROOT_CA, INTERMEDIATE_CRL_CA, INTERMEDIATE_CRL_LEAF, INTERMEDIATE_CRL_REVOKING_LEAF_DER_B64 } from "./x509-svid-intermediate-crl-test-fixtures.js";
import { LEAF_POLICY_TEST_CA, LEAF_BAD_KEY_USAGE, LEAF_BAD_EKU } from "./x509-svid-leaf-policy-test-fixtures.js";

function verifier() {
  return new X509SvidVerifier({
    trustBundlePem: [CRL_TEST_CA],
    clock: () => NOW,
    clockSkewMs: 0,
  });
}

test("verifies a trusted X.509 SVID with exactly one canonical SPIFFE URI SAN", () => {
  const result = verifier().verify({
    leafPem: CRL_TEST_LEAF,
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
  assert.equal(result.spiffeId, "spiffe://prod.example.test/workloads/revoked");
  assert.equal(result.trustDomain, "prod.example.test");
  assert.match(result.leafFingerprint256, /^[a-f0-9]{64}$/);
});

test("rejects trust-domain mismatch", () => {
  assert.throws(() => verifier().verify({
    leafPem: CRL_TEST_LEAF,
    expectedTrustDomain: "staging.example.test",
  }), /trust domain mismatch/);
});

test("rejects expired SVIDs", () => {
  assert.throws(() => verifier().verify({
    leafPem: EXPIRED,
    expectedTrustDomain: "prod.example.test",
  }), /expired/);
});

test("rejects multiple URI SAN identities", () => {
  assert.throws(() => verifier().verify({
    leafPem: MULTI,
    expectedTrustDomain: "prod.example.test",
  }), /exactly one URI SAN/);
});

test("rejects a leaf signed by an untrusted authority", () => {
  assert.throws(() => verifier().verify({
    leafPem: BAD_LEAF,
    expectedTrustDomain: "prod.example.test",
  }), /signature not trusted/);
});

test("rejects malformed bundle and leaf material", () => {
  assert.throws(() => new X509SvidVerifier({ trustBundlePem: [] }), /trust bundle invalid/);
  assert.throws(() => verifier().verify({ leafPem: "not a cert" }), /leaf invalid/);
});


test("opaque SVID evidence cannot be constructed directly", () => {
  assert.throws(() => new VerifiedSvidEvidence(Symbol("fake"), {
    spiffeId: "spiffe://prod.example.test/workloads/api",
    trustDomain: "prod.example.test",
    path: "/workloads/api",
    notBefore: NOW - 1000,
    notAfter: NOW + 1000,
    signerFingerprint256: "a".repeat(64),
    leafFingerprint256: "b".repeat(64),
  }), /cannot be constructed directly/);
  assert.equal(isVerifiedSvidEvidence({
    spiffeId: "spiffe://prod.example.test/workloads/api",
  }), false);
});


test("allows non-URI SAN types when exactly one SPIFFE URI SAN is present", () => {
  const result = new X509SvidVerifier({
    trustBundlePem: [CA_DNS_EXTRA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: LEAF_DNS_EXTRA,
    expectedTrustDomain: "prod.example.test",
  });

  assert.equal(isVerifiedSvidEvidence(result), true);
  assert.equal(result.spiffeId, "spiffe://prod.example.test/workloads/dns-extra");
});


test("SAN parser respects JSON-quoted values containing comma separators", () => {
  const entries = parseSubjectAltNameEntries(
    'DNS:"example.com, injected", URI:spiffe://prod.example.test/workloads/api'
  );
  assert.deepEqual(entries, [
    { type: "DNS", value: "example.com, injected" },
    { type: "URI", value: "spiffe://prod.example.test/workloads/api" },
  ]);
});

test("SAN parser rejects malformed quoted encodings", () => {
  assert.throws(
    () => parseSubjectAltNameEntries('DNS:"unterminated, URI:spiffe://prod.example.test/workloads/api'),
    /SAN encoding invalid/
  );
});


test("accepts a valid SVID when verified CRLs do not revoke its serial", () => {
  const result = new X509SvidVerifier({
    trustBundlePem: [CRL_TEST_CA],
    crlsDerBase64: [CRL_EMPTY_DER_B64],
    clock: () => NOW,
    clockSkewMs: 0,
  }).verify({
    leafPem: CRL_TEST_LEAF,
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
});

test("rejects a validly signed SVID when a verified CRL revokes its serial", () => {
  assert.throws(() => new X509SvidVerifier({
    trustBundlePem: [CRL_TEST_CA],
    crlsDerBase64: [CRL_REVOKING_LEAF_DER_B64],
    clock: () => NOW,
    clockSkewMs: 0,
  }).verify({
    leafPem: CRL_TEST_LEAF,
    expectedTrustDomain: "prod.example.test",
  }), /certificate revoked/);
});


test("ignores global CRLs issued by an unrelated trust domain", () => {
  const result = new X509SvidVerifier({
    trustBundlePem: [CRL_TEST_CA],
    crlsDerBase64: [CRL_NO_SIGN_DER_B64, CRL_EMPTY_DER_B64],
    clock: () => NOW,
    clockSkewMs: 0,
  }).verify({
    leafPem: CRL_TEST_LEAF,
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
});


test("verifies a SPIFFE SVID through one bounded intermediate CA", () => {
  const result = new X509SvidVerifier({
    trustBundlePem: [CHAIN_ROOT_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: CHAIN_LEAF,
    intermediatesPem: [CHAIN_INTERMEDIATE_CA],
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
  assert.equal(result.spiffeId, "spiffe://prod.example.test/workloads/intermediate");
});

test("does not silently treat a missing intermediate as a trusted direct signer", () => {
  assert.throws(() => new X509SvidVerifier({
    trustBundlePem: [CHAIN_ROOT_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: CHAIN_LEAF,
    expectedTrustDomain: "prod.example.test",
  }), /signature not trusted/);
});

test("rejects non-CA and oversized intermediate sets", () => {
  const v = new X509SvidVerifier({
    trustBundlePem: [CHAIN_ROOT_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  });
  assert.throws(() => v.verify({
    leafPem: CHAIN_LEAF,
    intermediatesPem: [LEAF],
    expectedTrustDomain: "prod.example.test",
  }), /intermediate must be a CA/);
  assert.throws(() => v.verify({
    leafPem: CHAIN_LEAF,
    intermediatesPem: Array(9).fill(CHAIN_INTERMEDIATE_CA),
    expectedTrustDomain: "prod.example.test",
  }), /intermediate set invalid/);
});

test("ignores unrelated CRLs without blocking a valid intermediate chain", () => {
  const result = new X509SvidVerifier({
    trustBundlePem: [CHAIN_ROOT_CA, CRL_TEST_CA],
    crlsDerBase64: [CRL_EMPTY_DER_B64],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: CHAIN_LEAF,
    intermediatesPem: [CHAIN_INTERMEDIATE_CA],
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
});

test("rejects a leaf revoked by the CRL issued by its trusted intermediate CA", () => {
  assert.throws(() => new X509SvidVerifier({
    trustBundlePem: [INTERMEDIATE_CRL_ROOT_CA],
    crlsDerBase64: [INTERMEDIATE_CRL_REVOKING_LEAF_DER_B64],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: INTERMEDIATE_CRL_LEAF,
    intermediatesPem: [INTERMEDIATE_CRL_CA],
    expectedTrustDomain: "prod.example.test",
  }), /certificate revoked/);
});


test("parses critical CA policy metadata from the SPIFFE intermediate", () => {
  const metadata = certificateAuthorityMetadata(new X509Certificate(CHAIN_INTERMEDIATE_CA));
  assert.equal(metadata.basicConstraints?.critical, true);
  assert.equal(metadata.basicConstraints?.ca, true);
  assert.equal(metadata.basicConstraints?.pathLenConstraint, 0);
  assert.equal(metadata.keyUsage?.keyCertSign, true);
});

test("keeps issuer-aware CRL validation compatible with enforced intermediate CA policy", () => {
  const result = new X509SvidVerifier({
    trustBundlePem: [INTERMEDIATE_CRL_ROOT_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: INTERMEDIATE_CRL_LEAF,
    intermediatesPem: [INTERMEDIATE_CRL_CA],
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
});


test("rejects a leaf SVID that lacks digitalSignature or sets CA signing usage", () => {
  assert.throws(() => new X509SvidVerifier({
    trustBundlePem: [LEAF_POLICY_TEST_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: LEAF_BAD_KEY_USAGE,
    expectedTrustDomain: "prod.example.test",
  }), /leaf key usage invalid/);
});

test("rejects a leaf SVID whose EKU omits clientAuth", () => {
  assert.throws(() => new X509SvidVerifier({
    trustBundlePem: [LEAF_POLICY_TEST_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: LEAF_BAD_EKU,
    expectedTrustDomain: "prod.example.test",
  }), /leaf extended key usage invalid/);
});


test("rejects an intermediate certificate that duplicates a configured trust anchor", () => {
  assert.throws(() => new X509SvidVerifier({
    trustBundlePem: [CHAIN_ROOT_CA],
    clock: () => Date.parse("2026-09-19T00:00:00Z"),
    clockSkewMs: 0,
  }).verify({
    leafPem: CHAIN_LEAF,
    intermediatesPem: [CHAIN_INTERMEDIATE_CA, CHAIN_ROOT_CA],
    expectedTrustDomain: "prod.example.test",
  }), /intermediate duplicates trust anchor/);
});
