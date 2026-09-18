import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";
import { handleMeshRequest } from "./server.js";

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
