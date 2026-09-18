import test from "node:test";
import assert from "node:assert/strict";
import { parseSpiffeId, SpiffeIdentityPolicy } from "./spiffe-identity.js";
import { X509SvidVerifier } from "./x509-svid-verifier.js";
import { NOW, CA, LEAF } from "./x509-svid-test-fixtures.js";


function verifiedEvidence() {
  return new X509SvidVerifier({
    trustBundlePem: [CA],
    clock: () => NOW,
    clockSkewMs: 0,
  }).verify({
    leafPem: LEAF,
    expectedTrustDomain: "prod.example.test",
  });
}

test("parses canonical SPIFFE IDs and rejects ambiguous forms", () => {
  assert.deepEqual(parseSpiffeId("spiffe://prod.example.test/ns/payments/sa/api"), {
    id: "spiffe://prod.example.test/ns/payments/sa/api",
    trustDomain: "prod.example.test",
    path: "/ns/payments/sa/api",
  });

  for (const invalid of [
    "https://prod.example.test/ns/payments",
    "spiffe://Prod.example.test/ns/payments",
    "spiffe://prod.example.test/ns//payments",
    "spiffe://prod.example.test/ns/%70ayments",
    "spiffe://prod.example.test/ns/../payments",
    "spiffe://user@prod.example.test/ns/payments",
  ]) {
    assert.throws(() => parseSpiffeId(invalid));
  }
});

test("maps workload identity using the most specific allowed prefix", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [
      {
        trustDomain: "prod.example.test",
        pathPrefix: "/ns",
        subjectType: "workload",
        tags: ["cluster"],
      },
      {
        trustDomain: "prod.example.test",
        pathPrefix: "/ns/ai",
        subjectType: "agent",
        tags: ["ai"],
        groups: ["automation"],
      },
    ],
  });

  const mapped = policy.mapIdentity("spiffe://prod.example.test/ns/ai/agent-01", { svidVerified: true });
  assert.equal(mapped.allowed, true);
  assert.equal(mapped.subject.type, "agent");
  assert.deepEqual(mapped.subject.tags, ["ai"]);
  assert.deepEqual(mapped.subject.groups, ["automation"]);
  assert.equal(mapped.subject.deviceTrust, "attested");
});

test("denies unknown trust domains and unmapped paths", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test", "staging.example.test"],
    mappings: [
      {
        trustDomain: "prod.example.test",
        pathPrefix: "/workloads",
        subjectType: "workload",
      },
    ],
  });

  assert.deepEqual(
    policy.mapIdentity("spiffe://staging.example.test/workloads/api", { svidVerified: true }),
    { allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" }
  );
  assert.deepEqual(
    policy.mapIdentity("spiffe://evil.example.test/workloads/api", { svidVerified: true }),
    { allowed: false, reason: "SPIFFE_TRUST_DOMAIN_DENIED" }
  );
});

test("does not match prefix lookalikes", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/workload",
      subjectType: "agent",
    }],
  });

  assert.deepEqual(
    policy.mapIdentity(verifiedEvidence().spiffeId, { evidence: verifiedEvidence() }),
    { allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" }
  );
});


test("refuses to mark a SPIFFE identity attested without verified SVID evidence", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/workloads",
      subjectType: "workload",
    }],
  });

  assert.deepEqual(
    policy.mapIdentity("spiffe://prod.example.test/workloads/api", { evidence: { spiffeId: "spiffe://prod.example.test/workloads/api" } }),
    { allowed: false, reason: "SPIFFE_SVID_UNVERIFIED" }
  );
});

test("rejects verified evidence bound to a different SPIFFE identity", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/workloads",
      subjectType: "workload",
    }],
  });
  const evidence = verifiedEvidence();
  assert.deepEqual(
    policy.mapIdentity("spiffe://prod.example.test/workloads/other", { evidence }),
    { allowed: false, reason: "SPIFFE_SVID_IDENTITY_MISMATCH" }
  );
});
