import test from "node:test";
import assert from "node:assert/strict";
import { evaluateAccess } from "./policy-engine.js";

const base = {
  subject: {
    type: "user",
    id: "user:thierry",
    groups: ["engineering"],
    tags: ["managed"],
    deviceTrust: "trusted"
  },
  resource: {
    id: "service:git",
    tags: ["prod","source-control"],
    environment: "prod"
  },
  action: "connect"
};

test("default deny when nothing matches", () => {
  assert.equal(evaluateAccess({ ...base, rules: [] }).allowed, false);
});

test("allows by identity attributes instead of IP address", () => {
  const result = evaluateAccess({
    ...base,
    rules: [{
      id: "engineering-prod",
      effect: "allow",
      actions: ["connect"],
      subjectTypes: ["user"],
      groups: ["engineering"],
      resourceTags: ["prod"],
      deviceTrust: ["trusted"]
    }]
  });
  assert.deepEqual(result, {
    allowed: true,
    reason: "EXPLICIT_ALLOW",
    ruleId: "engineering-prod"
  });
});

test("deny overrides allow", () => {
  const result = evaluateAccess({
    ...base,
    rules: [
      {
        id: "engineering-prod",
        effect: "allow",
        groups: ["engineering"],
        resourceTags: ["prod"]
      },
      {
        id: "quarantine-untrusted",
        effect: "deny",
        deviceTrust: ["trusted"],
        resourceTags: ["prod"]
      }
    ]
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, "EXPLICIT_DENY");
});

test("supports workload and AI-agent subjects", () => {
  for (const type of ["workload","agent"]) {
    const result = evaluateAccess({
      subject: { type, id: `${type}:payments`, tags: ["prod"], deviceTrust: "attested" },
      resource: { id: "db:payments", tags: ["payments"], environment: "prod" },
      action: "connect",
      rules: [{
        id: "payments-east-west",
        effect: "allow",
        subjectTypes: [type],
        subjectTags: ["prod"],
        resourceTags: ["payments"],
        deviceTrust: ["attested"]
      }]
    });
    assert.equal(result.allowed, true);
  }
});
