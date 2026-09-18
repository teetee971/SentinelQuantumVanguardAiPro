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

test("maps verified workload identity using the most specific allowed prefix", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [
      {
        trustDomain: "prod.example.test",
        pathPrefix: "/",
        subjectType: "workload",
        tags: ["cluster"],
      },
      {
        trustDomain: "prod.example.test",
        pathPrefix: "/workloads",
        subjectType: "agent",
        tags: ["ai"],
        groups: ["automation"],
      },
    ],
  });

  const evidence = verifiedEvidence();
  const mapped = policy.mapIdentity(evidence.spiffeId, { evidence });
  assert.equal(mapped.allowed, true);
  assert.equal(mapped.subject.type, "agent");
  assert.deepEqual(mapped.subject.tags, ["ai"]);
  assert.deepEqual(mapped.subject.groups, ["automation"]);
  assert.equal(mapped.subject.deviceTrust, "attested");
});

test("denies verified identities outside configured trust domains and unmapped paths", () => {
  const evidence = verifiedEvidence();

  const trustDenied = new SpiffeIdentityPolicy({
    trustDomains: ["staging.example.test"],
    mappings: [{
      trustDomain: "staging.example.test",
      pathPrefix: "/workloads",
      subjectType: "workload",
    }],
  });
  assert.deepEqual(
    trustDenied.mapIdentity(evidence.spiffeId, { evidence }),
    { allowed: false, reason: "SPIFFE_TRUST_DOMAIN_DENIED" }
  );

  const unmapped = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/other",
      subjectType: "workload",
    }],
  });
  assert.deepEqual(
    unmapped.mapIdentity(evidence.spiffeId, { evidence }),
    { allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" }
  );
});

test("does not match prefix lookalikes", () => {
  const evidence = verifiedEvidence();
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/workload",
      subjectType: "agent",
    }],
  });

  assert.deepEqual(
    policy.mapIdentity(evidence.spiffeId, { evidence }),
    { allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" }
  );
});

test("refuses forged or missing SVID evidence", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/workloads",
      subjectType: "workload",
    }],
  });

  for (const evidence of [null, { spiffeId: "spiffe://prod.example.test/workloads/api" }]) {
    assert.deepEqual(
      policy.mapIdentity("spiffe://prod.example.test/workloads/api", { evidence }),
      { allowed: false, reason: "SPIFFE_SVID_UNVERIFIED" }
    );
  }
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
