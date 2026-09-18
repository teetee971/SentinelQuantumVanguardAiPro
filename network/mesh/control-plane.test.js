import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";
import { CA, CA_DNS_EXTRA } from "./x509-svid-test-fixtures.js";

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


test("integration registry is wired into the control plane", () => {
  const cp = new MeshControlPlane();
  const entry = cp.registerIntegration({
    id: "generic-oidc",
    category: "identity",
    protocol: "OIDC",
    status: "foundation",
  });
  assert.equal(entry.id, "generic-oidc");
  assert.deepEqual(cp.listIntegrations().map(x => x.id), ["generic-oidc"]);
  assert.throws(() => cp.registerIntegration({
    id: "bad",
    category: "identity",
    protocol: "UNSUPPORTED",
    status: "validated",
  }));
});


test("export and restore preserve nodes policies integrations and revocation", () => {
  const cp = new MeshControlPlane({ clock: () => 42 });
  cp.enrollNode({
    id: "device:restore",
    type: "device",
    publicKey: WG_KEY_A,
    groups: ["ops"],
    tags: ["managed"],
    deviceTrust: "trusted",
  });
  cp.registerIntegration({
    id: "generic-oidc",
    category: "identity",
    protocol: "OIDC",
    status: "foundation",
  });
  cp.replacePolicies([{
    id: "ops-allow",
    effect: "allow",
    groups: ["ops"],
    resourceTags: ["ops"],
  }]);
  cp.revokeNode("device:restore", "test");

  const snapshot = cp.exportState();
  const restored = new MeshControlPlane({ clock: () => 43 });
  assert.equal(restored.restoreState(snapshot), true);
  assert.equal(restored.getNode("device:restore").revoked, true);
  assert.deepEqual(restored.listIntegrations().map(x => x.id), ["generic-oidc"]);
  assert.equal(restored.getAudit().at(-1).type, "STATE_RESTORED");
});

test("restore rejects private key material and fingerprint tampering", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:safe", type: "device", publicKey: WG_KEY_A });
  const snapshot = cp.exportState();

  const privateKeySnapshot = structuredClone(snapshot);
  privateKeySnapshot.nodes[0].privateKey = "forbidden";
  assert.throws(() => new MeshControlPlane().restoreState(privateKeySnapshot), /private key/);

  const fingerprintSnapshot = structuredClone(snapshot);
  fingerprintSnapshot.nodes[0].publicKeyFingerprint = "0".repeat(64);
  assert.throws(() => new MeshControlPlane().restoreState(fingerprintSnapshot), /fingerprint mismatch/);
});


test("node credentials are high entropy, hashed at rest, restorable and revoked with node", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:auth", type: "device", publicKey: WG_KEY_A });
  const issued = cp.issueNodeCredential("device:auth");
  assert.equal(typeof issued.token, "string");
  assert.ok(issued.token.length >= 43);
  assert.equal(cp.authenticateNode("device:auth", issued.token), true);
  assert.equal(cp.authenticateNode("device:auth", issued.token + "x"), false);

  const snapshot = cp.exportState();
  assert.equal(JSON.stringify(snapshot).includes(issued.token), false);
  assert.match(snapshot.nodeCredentialHashes[0].tokenHash, /^[a-f0-9]{64}$/);

  const restored = new MeshControlPlane();
  restored.restoreState(snapshot);
  assert.equal(restored.authenticateNode("device:auth", issued.token), true);

  restored.revokeNode("device:auth", "lost");
  assert.equal(restored.authenticateNode("device:auth", issued.token), false);
});

test("rotating a node credential invalidates the previous token", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:rotate", type: "device", publicKey: WG_KEY_A });
  const first = cp.issueNodeCredential("device:rotate");
  const second = cp.issueNodeCredential("device:rotate");
  assert.equal(cp.authenticateNode("device:rotate", first.token), false);
  assert.equal(cp.authenticateNode("device:rotate", second.token), true);
});


test("mesh overlay addresses are host CIDRs, canonicalized and unique", () => {
  const cp = new MeshControlPlane();
  const first = cp.enrollNode({
    id: "device:addr-a",
    type: "device",
    publicKey: WG_KEY_A,
    meshAddresses: ["10.210.0.1/32", "2001:db8::1/128"],
  });
  assert.deepEqual(first.meshAddresses, ["10.210.0.1/32", "2001:db8::1/128"]);

  assert.throws(() => cp.enrollNode({
    id: "device:addr-b",
    type: "device",
    publicKey: WG_KEY_B,
    meshAddresses: ["10.210.0.1/32"],
  }), /already assigned/);

  assert.throws(() => cp.enrollNode({
    id: "device:bad-prefix",
    type: "device",
    publicKey: WG_KEY_C,
    meshAddresses: ["10.210.0.2/24"],
  }), /\/32 IPv4 or \/128 IPv6/);
});

test("mesh addresses persist, can be updated, and are exposed to authorized peers", () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({
    id: "device:mesh-source",
    type: "device",
    publicKey: WG_KEY_A,
    groups: ["mesh"],
    meshAddresses: ["10.211.0.1/32"],
  });
  cp.enrollNode({
    id: "device:mesh-target",
    type: "device",
    publicKey: WG_KEY_B,
    meshAddresses: ["10.211.0.2/32", "2001:db8:1::2/128"],
    resources: [{ id: "svc:mesh-target", tags: ["mesh"], environment: "prod" }],
  });
  cp.replacePolicies([{
    id: "mesh-peers",
    effect: "allow",
    groups: ["mesh"],
    resourceTags: ["mesh"],
    actions: ["connect"],
  }]);

  const peers = cp.discoverAuthorizedPeers("device:mesh-source");
  assert.deepEqual(peers[0].meshAddresses, ["10.211.0.2/32", "2001:db8:1::2/128"]);

  cp.setNodeMeshAddresses("device:mesh-source", ["10.211.0.10/32"]);
  assert.deepEqual(cp.getNode("device:mesh-source").meshAddresses, ["10.211.0.10/32"]);

  const restored = new MeshControlPlane();
  restored.restoreState(cp.exportState());
  assert.deepEqual(restored.getNode("device:mesh-source").meshAddresses, ["10.211.0.10/32"]);
});


test("mesh overlay rejects unsafe special-use host addresses", () => {
  const cp = new MeshControlPlane();
  const unsafe = [
    "0.0.0.1/32",
    "127.0.0.1/32",
    "169.254.10.1/32",
    "224.0.0.1/32",
    "255.255.255.255/32",
    "::/128",
    "::1/128",
    "fe80::1/128",
    "ff02::1/128",
    "::ffff:192.0.2.1/128",
  ];

  unsafe.forEach((meshAddress, index) => {
    assert.throws(() => cp.enrollNode({
      id: `device:unsafe-${index}`,
      type: "device",
      publicKey: Buffer.alloc(32, 30 + index).toString("base64"),
      meshAddresses: [meshAddress],
    }), /unsafe mesh IP address/);
  });
});

test("mesh overlay allows private IPv4 and IPv6 ULA host addresses", () => {
  const cp = new MeshControlPlane();
  const node = cp.enrollNode({
    id: "device:private-overlay",
    type: "device",
    publicKey: WG_KEY_A,
    meshAddresses: ["10.220.0.1/32", "fd42:1234::1/128"],
  });
  assert.deepEqual(node.meshAddresses, ["10.220.0.1/32", "fd42:1234::1/128"]);
});


test("SPIFFE trust bundle persists through control plane snapshot and refuses rollback", () => {
  const cp = new MeshControlPlane({ clock: () => 1000 });
  const first = cp.installSpiffeTrustBundle({ sequence: 1, anchorsPem: [CA] });
  assert.equal(first.sequence, 1);
  assert.equal(cp.getSpiffeVerifierConfig().trustBundlePem.length, 1);

  const second = cp.installSpiffeTrustBundle({
    sequence: 2,
    anchorsPem: [CA, CA_DNS_EXTRA],
  });
  assert.equal(second.sequence, 2);

  const snapshot = cp.exportState();
  assert.equal(snapshot.spiffeTrustBundle.sequence, 2);

  const restored = new MeshControlPlane({ clock: () => 1001 });
  assert.equal(restored.restoreState(snapshot), true);
  assert.equal(restored.getSpiffeTrustBundle().sequence, 2);
  assert.equal(restored.getSpiffeVerifierConfig().trustBundlePem.length, 2);

  assert.throws(
    () => restored.installSpiffeTrustBundle({ sequence: 1, anchorsPem: [CA] }),
    /rollback or replay/
  );

  const auditTypes = cp.getAudit().map(event => event.type);
  assert.ok(auditTypes.includes("SPIFFE_TRUST_BUNDLE_INSTALLED"));
});
