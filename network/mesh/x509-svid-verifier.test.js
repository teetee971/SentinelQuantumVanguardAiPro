import test from "node:test";
import assert from "node:assert/strict";
import { X509SvidVerifier, VerifiedSvidEvidence, isVerifiedSvidEvidence } from "./x509-svid-verifier.js";

import { NOW, CA, LEAF, MULTI, EXPIRED, BAD_LEAF } from "./x509-svid-test-fixtures.js";

function verifier() {
  return new X509SvidVerifier({
    trustBundlePem: [CA],
    clock: () => NOW,
    clockSkewMs: 0,
  });
}

test("verifies a trusted X.509 SVID with exactly one canonical SPIFFE URI SAN", () => {
  const result = verifier().verify({
    leafPem: LEAF,
    expectedTrustDomain: "prod.example.test",
  });
  assert.equal(isVerifiedSvidEvidence(result), true);
  assert.equal(result.spiffeId, "spiffe://prod.example.test/workloads/api");
  assert.equal(result.trustDomain, "prod.example.test");
  assert.match(result.leafFingerprint256, /^[a-f0-9]{64}$/);
});

test("rejects trust-domain mismatch", () => {
  assert.throws(() => verifier().verify({
    leafPem: LEAF,
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
