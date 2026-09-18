import test from "node:test";
import assert from "node:assert/strict";
import { FOUNDATION_INTEGRATIONS, validateIntegrationManifest } from "./integration-registry.js";

test("foundation catalog never claims planned adapters are validated", () => {
  assert.ok(FOUNDATION_INTEGRATIONS.length >= 20);
  assert.ok(FOUNDATION_INTEGRATIONS.every(x => ["foundation","planned","validated"].includes(x.status)));
  assert.equal(FOUNDATION_INTEGRATIONS.filter(x => x.status === "validated").length, 0);
});

test("integration manifests are bounded to supported standard protocols", () => {
  assert.equal(validateIntegrationManifest({
    id: "example-oidc",
    category: "identity",
    protocol: "OIDC",
    status: "planned"
  }), true);
  assert.throws(() => validateIntegrationManifest({
    id: "bad",
    category: "identity",
    protocol: "CUSTOM_MAGIC",
    status: "validated"
  }));
});
