import test from "node:test";
import assert from "node:assert/strict";
import { parseSpiffeId, SpiffeIdentityPolicy } from "./spiffe-identity.js";

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

  const mapped = policy.mapIdentity("spiffe://prod.example.test/ns/ai/agent-01");
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
    policy.mapIdentity("spiffe://staging.example.test/workloads/api"),
    { allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" }
  );
  assert.throws(() =>
    policy.mapIdentity("spiffe://evil.example.test/workloads/api")
  );
});

test("does not match prefix lookalikes", () => {
  const policy = new SpiffeIdentityPolicy({
    trustDomains: ["prod.example.test"],
    mappings: [{
      trustDomain: "prod.example.test",
      pathPrefix: "/ai",
      subjectType: "agent",
    }],
  });

  assert.deepEqual(
    policy.mapIdentity("spiffe://prod.example.test/aix/agent"),
    { allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" }
  );
});
