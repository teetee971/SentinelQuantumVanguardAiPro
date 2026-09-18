import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";

const WG_KEY_A = Buffer.alloc(32, 1).toString("base64");
const WG_KEY_B = Buffer.alloc(32, 2).toString("base64");
const WG_KEY_C = Buffer.alloc(32, 3).toString("base64");

test("enrollment accepts public keys only and rejects private key material", () => {
  const cp = new MeshControlPlane({ clock: () => 1000 });
  const node = cp.enrollNode({
    id: "device:alice",
    type: "device",
    publicKey: WG_KEY_A,
    tags: ["managed"],
    deviceTrust: "trusted",
  });
  assert.equal(node.publicKey, WG_KEY_A);
  assert.throws(() => cp.enrollNode({
    id: "device:bad",
    type: "device",
    publicKey: WG_KEY_B,
    privateKey: "must-never-enter-control-plane",
  }));
});

test("peer discovery is identity-policy driven and default-deny", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({
    id: "user:alice-device",
    type: "device",
    publicKey: WG_KEY_A,
    groups: ["engineering"],
    tags: ["managed"],
    deviceTrust: "trusted",
  });
  cp.enrollNode({
    id: "server:git",
    type: "workload",
    publicKey: WG_KEY_B,
    tags: ["source-control"],
    resources: [{ id: "svc:git", tags: ["source-control"], environment: "prod" }],
    deviceTrust: "attested",
  });

  assert.deepEqual(cp.discoverAuthorizedPeers("user:alice-device"), []);

  cp.replacePolicies([{
    id: "engineering-git",
    effect: "allow",
    actions: ["connect"],
    groups: ["engineering"],
    resourceTags: ["source-control"],
    deviceTrust: ["trusted"],
  }]);

  assert.deepEqual(cp.discoverAuthorizedPeers("user:alice-device").map(x => x.id), ["server:git"]);
});

test("revocation removes a node from discovery immediately", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:a", type: "device", publicKey: WG_KEY_A, tags: ["managed"], deviceTrust: "trusted" });
  cp.enrollNode({
    id: "device:b",
    type: "device",
    publicKey: WG_KEY_B,
    tags: ["peer"],
    resources: [{ id: "node:b", tags: ["peer"], environment: "home" }],
    deviceTrust: "trusted",
  });
  cp.replacePolicies([{ id: "allow-peer", effect: "allow", resourceTags: ["peer"] }]);
  assert.equal(cp.discoverAuthorizedPeers("device:a").length, 1);
  cp.revokeNode("device:b", "lost-device");
  assert.equal(cp.discoverAuthorizedPeers("device:a").length, 0);
});

test("explicit deny overrides broad allow", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "agent:build", type: "agent", publicKey: WG_KEY_A, tags: ["ci"], deviceTrust: "attested" });
  cp.enrollNode({
    id: "server:prod-db",
    type: "workload",
    publicKey: WG_KEY_C,
    tags: ["database"],
    resources: [{ id: "db:prod", tags: ["database"], environment: "prod" }],
    deviceTrust: "attested",
  });
  cp.replacePolicies([
    { id: "allow-ci", effect: "allow", subjectTypes: ["agent"], resourceTags: ["database"] },
    { id: "deny-prod-db", effect: "deny", subjectTypes: ["agent"], environments: ["prod"] },
  ]);
  assert.equal(cp.discoverAuthorizedPeers("agent:build").length, 0);
});

test("audit records enrollment, policy updates, discovery and revocation", () => {
  let now = 1;
  const cp = new MeshControlPlane({ clock: () => now++ });
  cp.enrollNode({ id: "device:a", type: "device", publicKey: WG_KEY_A });
  cp.replacePolicies([]);
  cp.discoverAuthorizedPeers("device:a");
  cp.revokeNode("device:a");
  const types = cp.getAudit().map(x => x.type);
  assert.deepEqual(types, ["NODE_ENROLLED","POLICY_SET_REPLACED","PEER_DISCOVERY","NODE_REVOKED"]);
});
