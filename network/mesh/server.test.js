import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";
import { handleMeshRequest } from "./server.js";
import { MeshTransportCoordinator } from "./transport-coordinator.js";
import { MeshNatProbeRegistry } from "./nat-probe.js";
import { MeshPathNegotiator } from "./path-negotiator.js";
import { MeshRelayGrantBroker, MeshRelayRegistry } from "./relay.js";
import { MeshEnrollmentBroker } from "./enrollment-broker.js";

const TOKEN = "0123456789abcdef0123456789abcdef";
const KEY = Buffer.alloc(32, 7).toString("base64");

test("health is public but mutations require bearer authentication", async () => {
  const cp = new MeshControlPlane();
  const health = await handleMeshRequest({
    method: "GET", url: "/health/live", controlPlane: cp, adminToken: TOKEN
  });
  assert.equal(health.status, 200);

  const denied = await handleMeshRequest({
    method: "POST", url: "/v1/nodes", body: {}, controlPlane: cp, adminToken: TOKEN
  });
  assert.equal(denied.status, 401);
});

test("authenticated API supports enrollment policy discovery and revocation", async () => {
  const cp = new MeshControlPlane();
  const headers = { authorization: `Bearer ${TOKEN}` };

  for (const body of [
    { id: "device:a", type: "device", publicKey: KEY, groups: ["home"], deviceTrust: "trusted" },
    {
      id: "nas:home",
      type: "workload",
      publicKey: Buffer.alloc(32, 8).toString("base64"),
      resources: [{ id: "svc:nas", tags: ["nas"], environment: "home" }],
      deviceTrust: "attested"
    }
  ]) {
    const r = await handleMeshRequest({
      method: "POST", url: "/v1/nodes", headers, body, controlPlane: cp, adminToken: TOKEN
    });
    assert.equal(r.status, 201);
  }

  const policy = await handleMeshRequest({
    method: "POST",
    url: "/v1/policies",
    headers,
    body: { rules: [{ id: "home-nas", effect: "allow", groups: ["home"], resourceTags: ["nas"] }] },
    controlPlane: cp,
    adminToken: TOKEN,
  });
  assert.equal(policy.status, 200);

  const peers = await handleMeshRequest({
    method: "GET",
    url: "/v1/peers?node=device:a",
    headers,
    controlPlane: cp,
    adminToken: TOKEN,
  });
  assert.deepEqual(peers.body.peers.map(x => x.id), ["nas:home"]);

  const revoked = await handleMeshRequest({
    method: "POST",
    url: "/v1/revoke",
    headers,
    body: { nodeId: "nas:home", reason: "lost-device" },
    controlPlane: cp,
    adminToken: TOKEN,
  });
  assert.equal(revoked.status, 200);
});


test("mutations trigger persistence callback only after accepted changes", async () => {
  const cp = new MeshControlPlane();
  const headers = { authorization: `Bearer ${TOKEN}` };
  const snapshots = [];

  const created = await handleMeshRequest({
    method: "POST",
    url: "/v1/nodes",
    headers,
    body: { id: "device:persist", type: "device", publicKey: KEY },
    controlPlane: cp,
    adminToken: TOKEN,
    persist: async state => snapshots.push(state),
  });
  assert.equal(created.status, 201);
  assert.equal(snapshots.length, 1);
  assert.equal(snapshots[0].nodes.length, 1);

  const denied = await handleMeshRequest({
    method: "POST",
    url: "/v1/nodes",
    headers: {},
    body: { id: "device:nope", type: "device", publicKey: KEY },
    controlPlane: cp,
    adminToken: TOKEN,
    persist: async state => snapshots.push(state),
  });
  assert.equal(denied.status, 401);
  assert.equal(snapshots.length, 1);
});


test("transport path is policy-gated and prefers direct connectivity", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  const headers = { authorization: `Bearer ${TOKEN}` };

  cp.enrollNode({
    id: "device:source",
    type: "device",
    publicKey: KEY,
    groups: ["home"],
    deviceTrust: "trusted",
  });
  cp.enrollNode({
    id: "device:target",
    type: "device",
    publicKey: Buffer.alloc(32, 9).toString("base64"),
    resources: [{ id: "svc:target", tags: ["home-resource"], environment: "home" }],
    deviceTrust: "trusted",
  });

  for (const body of [
    { nodeId: "device:source", endpoints: ["198.51.100.10:51820"], ttlMs: 120000 },
    { nodeId: "device:target", endpoints: ["203.0.113.20:51820"], ttlMs: 120000 },
  ]) {
    const r = await handleMeshRequest({
      method: "POST",
      url: "/v1/transport/endpoints",
      headers,
      body,
      controlPlane: cp,
      transport,
      adminToken: TOKEN,
    });
    assert.equal(r.status, 200);
  }

  const denied = await handleMeshRequest({
    method: "GET",
    url: "/v1/transport/path?source=device:source&target=device:target",
    headers,
    controlPlane: cp,
    transport,
    adminToken: TOKEN,
  });
  assert.equal(denied.status, 403);

  cp.replacePolicies([{
    id: "home-connect",
    effect: "allow",
    groups: ["home"],
    resourceTags: ["home-resource"],
    actions: ["connect"],
  }]);

  const allowed = await handleMeshRequest({
    method: "GET",
    url: "/v1/transport/path?source=device:source&target=device:target",
    headers,
    controlPlane: cp,
    transport,
    adminToken: TOKEN,
  });
  assert.equal(allowed.status, 200);
  assert.equal(allowed.body.mode, "direct");
  assert.deepEqual(allowed.body.targetEndpoints, ["203.0.113.20:51820"]);
});

test("revoked node cannot announce endpoints or receive a path", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator();
  const headers = { authorization: `Bearer ${TOKEN}` };
  cp.enrollNode({ id: "device:revoked", type: "device", publicKey: KEY });
  cp.revokeNode("device:revoked", "lost");

  const announce = await handleMeshRequest({
    method: "POST",
    url: "/v1/transport/endpoints",
    headers,
    body: { nodeId: "device:revoked", endpoints: ["198.51.100.10:51820"] },
    controlPlane: cp,
    transport,
    adminToken: TOKEN,
  });
  assert.equal(announce.status, 403);
});


test("node token can announce NAT candidates without admin token", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  cp.enrollNode({ id: "device:n1", type: "device", publicKey: KEY });
  const credential = cp.issueNodeCredential("device:n1");

  const response = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/transport/candidates",
    headers: {
      authorization: `Bearer ${credential.token}`,
      "x-sentinel-node-id": "device:n1",
    },
    body: {
      endpoints: ["10.0.0.2:51820"],
      wireGuardPort: 51820,
      ttlMs: 120000,
    },
    remoteAddress: "198.51.100.55",
    controlPlane: cp,
    transport,
    adminToken: TOKEN,
  });

  assert.equal(response.status, 200);
  assert.deepEqual(response.body.endpoints, [
    "10.0.0.2:51820",
    "198.51.100.55:51820",
  ]);
});

test("node token cannot impersonate another node", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator();
  cp.enrollNode({ id: "device:a", type: "device", publicKey: KEY });
  cp.enrollNode({
    id: "device:b",
    type: "device",
    publicKey: Buffer.alloc(32, 10).toString("base64"),
  });
  const credential = cp.issueNodeCredential("device:a");

  const response = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/transport/candidates",
    headers: {
      authorization: `Bearer ${credential.token}`,
      "x-sentinel-node-id": "device:b",
    },
    body: { endpoints: ["10.0.0.3:51820"], wireGuardPort: 51820 },
    remoteAddress: "198.51.100.56",
    controlPlane: cp,
    transport,
    adminToken: TOKEN,
  });

  assert.equal(response.status, 401);
});

test("admin can issue and revoke a node credential", async () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:admin-issued", type: "device", publicKey: KEY });
  const headers = { authorization: `Bearer ${TOKEN}` };

  const issued = await handleMeshRequest({
    method: "POST",
    url: "/v1/node-credentials",
    headers,
    body: { nodeId: "device:admin-issued" },
    controlPlane: cp,
    adminToken: TOKEN,
  });
  assert.equal(issued.status, 201);
  assert.equal(cp.authenticateNode("device:admin-issued", issued.body.token), true);

  const revoked = await handleMeshRequest({
    method: "POST",
    url: "/v1/node-credentials/revoke",
    headers,
    body: { nodeId: "device:admin-issued" },
    controlPlane: cp,
    adminToken: TOKEN,
  });
  assert.equal(revoked.status, 200);
  assert.equal(cp.authenticateNode("device:admin-issued", issued.body.token), false);
});


test("node can fetch a policy-authorized direct path with its own credential", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  cp.enrollNode({
    id: "device:path-source",
    type: "device",
    publicKey: KEY,
    groups: ["home"],
    deviceTrust: "trusted",
  });
  cp.enrollNode({
    id: "device:path-target",
    type: "device",
    publicKey: Buffer.alloc(32, 11).toString("base64"),
    resources: [{ id: "svc:path-target", tags: ["home-resource"], environment: "home" }],
    deviceTrust: "trusted",
  });
  cp.replacePolicies([{
    id: "home-path",
    effect: "allow",
    groups: ["home"],
    resourceTags: ["home-resource"],
    actions: ["connect"],
  }]);

  transport.announceNodeEndpoints({
    nodeId: "device:path-source",
    endpoints: ["198.51.100.70:51820"],
    ttlMs: 120000,
  });
  transport.announceNodeEndpoints({
    nodeId: "device:path-target",
    endpoints: ["203.0.113.70:51820"],
    ttlMs: 120000,
  });

  const credential = cp.issueNodeCredential("device:path-source");
  const response = await handleMeshRequest({
    method: "GET",
    url: "/v1/node/transport/path?target=device:path-target",
    headers: {
      authorization: `Bearer ${credential.token}`,
      "x-sentinel-node-id": "device:path-source",
    },
    controlPlane: cp,
    transport,
    adminToken: TOKEN,
  });

  assert.equal(response.status, 200);
  assert.equal(response.body.mode, "direct");
  assert.deepEqual(response.body.targetEndpoints, ["203.0.113.70:51820"]);
});


test("node can read only its authenticated UDP NAT mapping", async () => {
  const cp = new MeshControlPlane();
  const registry = new MeshNatProbeRegistry({ clock: () => 1000 });
  cp.enrollNode({ id: "device:nat-read", type: "device", publicKey: KEY });
  const credential = cp.issueNodeCredential("device:nat-read");

  registry.record({
    nodeId: "device:nat-read",
    address: "198.51.100.88",
    port: 42000,
    family: "IPv4",
    ttlMs: 120000,
  });

  const ok = await handleMeshRequest({
    method: "GET",
    url: "/v1/node/nat-mapping",
    headers: {
      authorization: `Bearer ${credential.token}`,
      "x-sentinel-node-id": "device:nat-read",
    },
    controlPlane: cp,
    adminToken: TOKEN,
    natProbeRegistry: registry,
  });
  assert.equal(ok.status, 200);
  assert.equal(ok.body.mapping.address, "198.51.100.88");
  assert.equal(ok.body.mapping.port, 42000);

  const denied = await handleMeshRequest({
    method: "GET",
    url: "/v1/node/nat-mapping",
    headers: {
      authorization: `Bearer ${credential.token}x`,
      "x-sentinel-node-id": "device:nat-read",
    },
    controlPlane: cp,
    adminToken: TOKEN,
    natProbeRegistry: registry,
  });
  assert.equal(denied.status, 401);
});


test("node negotiates a direct path then records success", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  const registry = new MeshNatProbeRegistry({ clock: () => 1000 });
  const negotiator = new MeshPathNegotiator({ clock: () => 1000 });
  const headersFor = token => ({
    authorization: `Bearer ${token}`,
    "x-sentinel-node-id": "device:source-neg",
  });

  cp.enrollNode({
    id: "device:source-neg",
    type: "device",
    publicKey: KEY,
    groups: ["mesh"],
    deviceTrust: "trusted",
  });
  cp.enrollNode({
    id: "device:target-neg",
    type: "device",
    publicKey: Buffer.alloc(32, 14).toString("base64"),
    resources: [{ id: "svc:target-neg", tags: ["mesh-target"], environment: "prod" }],
    deviceTrust: "trusted",
  });
  cp.replacePolicies([{
    id: "mesh-connect",
    effect: "allow",
    groups: ["mesh"],
    resourceTags: ["mesh-target"],
    actions: ["connect"],
  }]);
  const credential = cp.issueNodeCredential("device:source-neg");

  transport.announceNodeEndpoints({
    nodeId: "device:source-neg",
    endpoints: ["198.51.100.90:51820"],
    ttlMs: 120000,
  });
  transport.announceNodeEndpoints({
    nodeId: "device:target-neg",
    endpoints: ["203.0.113.90:51820"],
    ttlMs: 120000,
  });

  const created = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations",
    headers: headersFor(credential.token),
    body: { targetNodeId: "device:target-neg" },
    controlPlane: cp,
    transport,
    natProbeRegistry: registry,
    pathNegotiator: negotiator,
    adminToken: TOKEN,
  });
  assert.equal(created.status, 201);
  assert.ok(created.body.targetCandidates.includes("203.0.113.90:51820"));

  const result = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations/direct-result",
    headers: headersFor(credential.token),
    body: {
      sessionId: created.body.id,
      endpoint: "203.0.113.90:51820",
      success: true,
      latencyMs: 18,
    },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    adminToken: TOKEN,
  });
  assert.equal(result.status, 200);
  assert.equal(result.body.state, "DIRECT_ESTABLISHED");
});

test("negotiation session cannot be controlled by another node", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  const negotiator = new MeshPathNegotiator({ clock: () => 1000 });

  cp.enrollNode({
    id: "device:owner",
    type: "device",
    publicKey: KEY,
    groups: ["mesh"],
    deviceTrust: "trusted",
  });
  cp.enrollNode({
    id: "device:target",
    type: "device",
    publicKey: Buffer.alloc(32, 15).toString("base64"),
    resources: [{ id: "svc:t", tags: ["mesh-target"], environment: "prod" }],
  });
  cp.enrollNode({
    id: "device:attacker",
    type: "device",
    publicKey: Buffer.alloc(32, 16).toString("base64"),
  });
  cp.replacePolicies([{
    id: "allow-owner",
    effect: "allow",
    groups: ["mesh"],
    resourceTags: ["mesh-target"],
    actions: ["connect"],
  }]);
  const owner = cp.issueNodeCredential("device:owner");
  const attacker = cp.issueNodeCredential("device:attacker");

  transport.announceNodeEndpoints({
    nodeId: "device:owner",
    endpoints: ["198.51.100.91:51820"],
    ttlMs: 120000,
  });
  transport.announceNodeEndpoints({
    nodeId: "device:target",
    endpoints: ["203.0.113.91:51820"],
    ttlMs: 120000,
  });

  const created = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations",
    headers: {
      authorization: `Bearer ${owner.token}`,
      "x-sentinel-node-id": "device:owner",
    },
    body: { targetNodeId: "device:target" },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    adminToken: TOKEN,
  });
  assert.equal(created.status, 201);

  const hijack = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations/finalize",
    headers: {
      authorization: `Bearer ${attacker.token}`,
      "x-sentinel-node-id": "device:attacker",
    },
    body: { sessionId: created.body.id },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    adminToken: TOKEN,
  });
  assert.equal(hijack.status, 404);
});

test("relay fallback is returned only after all direct candidates fail", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  const negotiator = new MeshPathNegotiator({ clock: () => 1000 });

  cp.enrollNode({
    id: "device:relay-source",
    type: "device",
    publicKey: KEY,
    groups: ["mesh"],
  });
  cp.enrollNode({
    id: "device:relay-target",
    type: "device",
    publicKey: Buffer.alloc(32, 17).toString("base64"),
    resources: [{ id: "svc:r", tags: ["mesh-target"], environment: "prod" }],
  });
  cp.replacePolicies([{
    id: "allow-relay",
    effect: "allow",
    groups: ["mesh"],
    resourceTags: ["mesh-target"],
    actions: ["connect"],
  }]);
  const credential = cp.issueNodeCredential("device:relay-source");

  transport.announceNodeEndpoints({
    nodeId: "device:relay-source",
    endpoints: ["198.51.100.92:51820"],
    ttlMs: 120000,
  });
  transport.announceNodeEndpoints({
    nodeId: "device:relay-target",
    endpoints: ["203.0.113.92:51820"],
    ttlMs: 120000,
  });
  transport.registerRelay({
    id: "relay-fr-test",
    region: "fr",
    endpoint: "192.0.2.92:3478",
    status: "available",
  });

  const headers = {
    authorization: `Bearer ${credential.token}`,
    "x-sentinel-node-id": "device:relay-source",
  };

  const created = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations",
    headers,
    body: { targetNodeId: "device:relay-target", preferredRegion: "fr" },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    adminToken: TOKEN,
  });
  assert.equal(created.status, 201);

  for (const endpoint of created.body.targetCandidates) {
    const failed = await handleMeshRequest({
      method: "POST",
      url: "/v1/node/negotiations/direct-result",
      headers,
      body: { sessionId: created.body.id, endpoint, success: false, error: "timeout" },
      controlPlane: cp,
      transport,
      pathNegotiator: negotiator,
      adminToken: TOKEN,
    });
    assert.equal(failed.status, 200);
  }

  const finalized = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations/finalize",
    headers,
    body: { sessionId: created.body.id },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    adminToken: TOKEN,
  });
  assert.equal(finalized.status, 200);
  assert.equal(finalized.body.state, "RELAY_REQUIRED");
});


test("relay-required negotiation exposes metadata and each node claims only its own relay token", async () => {
  const cp = new MeshControlPlane();
  const transport = new MeshTransportCoordinator({ clock: () => 1000 });
  const negotiator = new MeshPathNegotiator({ clock: () => 1000 });
  const relayRegistry = new MeshRelayRegistry({ clock: () => 1000 });
  const relayGrantBroker = new MeshRelayGrantBroker({ registry: relayRegistry });

  cp.enrollNode({
    id: "device:relay-src",
    type: "device",
    publicKey: KEY,
    groups: ["mesh"],
  });
  cp.enrollNode({
    id: "device:relay-dst",
    type: "device",
    publicKey: Buffer.alloc(32, 18).toString("base64"),
    resources: [{ id: "svc:relay-dst", tags: ["mesh-target"], environment: "prod" }],
  });
  cp.replacePolicies([{
    id: "allow-relay-data",
    effect: "allow",
    groups: ["mesh"],
    resourceTags: ["mesh-target"],
    actions: ["connect"],
  }]);

  const sourceCred = cp.issueNodeCredential("device:relay-src");
  const targetCred = cp.issueNodeCredential("device:relay-dst");

  transport.announceNodeEndpoints({
    nodeId: "device:relay-src",
    endpoints: ["198.51.100.93:51820"],
    ttlMs: 120000,
  });
  transport.announceNodeEndpoints({
    nodeId: "device:relay-dst",
    endpoints: ["203.0.113.93:51820"],
    ttlMs: 120000,
  });
  transport.registerRelay({
    id: "relay-fr-live",
    region: "fr",
    endpoint: "192.0.2.93:3480",
    status: "available",
  });

  const sourceHeaders = {
    authorization: `Bearer ${sourceCred.token}`,
    "x-sentinel-node-id": "device:relay-src",
  };

  const created = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations",
    headers: sourceHeaders,
    body: { targetNodeId: "device:relay-dst", preferredRegion: "fr" },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    relayGrantBroker,
    relayEndpoint: "192.0.2.93:3480",
    adminToken: TOKEN,
  });
  assert.equal(created.status, 201);

  for (const endpoint of created.body.targetCandidates) {
    const failed = await handleMeshRequest({
      method: "POST",
      url: "/v1/node/negotiations/direct-result",
      headers: sourceHeaders,
      body: { sessionId: created.body.id, endpoint, success: false, error: "timeout" },
      controlPlane: cp,
      transport,
      pathNegotiator: negotiator,
      relayGrantBroker,
      relayEndpoint: "192.0.2.93:3480",
      adminToken: TOKEN,
    });
    assert.equal(failed.status, 200);
  }

  const finalized = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/negotiations/finalize",
    headers: sourceHeaders,
    body: { sessionId: created.body.id },
    controlPlane: cp,
    transport,
    pathNegotiator: negotiator,
    relayGrantBroker,
    relayEndpoint: "192.0.2.93:3480",
    adminToken: TOKEN,
  });
  assert.equal(finalized.status, 200);
  assert.equal(finalized.body.state, "RELAY_REQUIRED");
  assert.equal(finalized.body.relayGrant.relayEndpoint, "192.0.2.93:3480");
  assert.equal("token" in finalized.body.relayGrant, false);

  const sourceClaim = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/relay/claim",
    headers: sourceHeaders,
    body: { negotiationId: created.body.id },
    controlPlane: cp,
    relayGrantBroker,
    adminToken: TOKEN,
  });
  assert.equal(sourceClaim.status, 200);
  assert.equal(sourceClaim.body.role, "source");

  const targetClaim = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/relay/claim",
    headers: {
      authorization: `Bearer ${targetCred.token}`,
      "x-sentinel-node-id": "device:relay-dst",
    },
    body: { negotiationId: created.body.id },
    controlPlane: cp,
    relayGrantBroker,
    adminToken: TOKEN,
  });
  assert.equal(targetClaim.status, 200);
  assert.equal(targetClaim.body.role, "target");
  assert.notEqual(sourceClaim.body.token, targetClaim.body.token);

  const secondSourceClaim = await handleMeshRequest({
    method: "POST",
    url: "/v1/node/relay/claim",
    headers: sourceHeaders,
    body: { negotiationId: created.body.id },
    controlPlane: cp,
    relayGrantBroker,
    adminToken: TOKEN,
  });
  assert.equal(secondSourceClaim.status, 404);
});


test("admin issues a one-time enrollment invitation and node claims without admin token", async () => {
  const cp = new MeshControlPlane();
  const broker = new MeshEnrollmentBroker({ clock: () => 1000 });
  const publicKey = Buffer.alloc(32, 19).toString("base64");
  const node = cp.enrollNode({
    id: "device:enroll-android",
    type: "device",
    publicKey,
    deviceTrust: "unknown",
  });

  const invitation = await handleMeshRequest({
    method: "POST",
    url: "/v1/enrollment-invitations",
    headers: { authorization: `Bearer ${TOKEN}` },
    body: { nodeId: node.id, ttlMs: 60000 },
    controlPlane: cp,
    enrollmentBroker: broker,
    adminToken: TOKEN,
  });
  assert.equal(invitation.status, 201);
  assert.ok(invitation.body.code.length >= 43);
  assert.equal(invitation.body.publicKeyFingerprint, node.publicKeyFingerprint);

  const claimed = await handleMeshRequest({
    method: "POST",
    url: "/v1/enroll",
    headers: {},
    body: {
      nodeId: node.id,
      code: invitation.body.code,
      publicKeyFingerprint: node.publicKeyFingerprint,
    },
    controlPlane: cp,
    enrollmentBroker: broker,
    adminToken: TOKEN,
  });
  assert.equal(claimed.status, 201);
  assert.equal(claimed.body.nodeId, node.id);
  assert.equal(cp.authenticateNode(node.id, claimed.body.token), true);

  const replay = await handleMeshRequest({
    method: "POST",
    url: "/v1/enroll",
    headers: {},
    body: {
      nodeId: node.id,
      code: invitation.body.code,
      publicKeyFingerprint: node.publicKeyFingerprint,
    },
    controlPlane: cp,
    enrollmentBroker: broker,
    adminToken: TOKEN,
  });
  assert.equal(replay.status, 400);
});

test("enrollment rejects fingerprint mismatch before issuing a node credential", async () => {
  const cp = new MeshControlPlane();
  const broker = new MeshEnrollmentBroker({ clock: () => 1000 });
  const node = cp.enrollNode({
    id: "device:enroll-mismatch",
    type: "device",
    publicKey: Buffer.alloc(32, 20).toString("base64"),
  });
  const invitation = broker.createInvitation({
    nodeId: node.id,
    publicKeyFingerprint: node.publicKeyFingerprint,
    ttlMs: 60000,
  });

  const rejected = await handleMeshRequest({
    method: "POST",
    url: "/v1/enroll",
    body: {
      nodeId: node.id,
      code: invitation.code,
      publicKeyFingerprint: "f".repeat(64),
    },
    controlPlane: cp,
    enrollmentBroker: broker,
    adminToken: TOKEN,
  });
  assert.equal(rejected.status, 400);
  assert.equal(cp.authenticateNode(node.id, "x".repeat(43)), false);
});
