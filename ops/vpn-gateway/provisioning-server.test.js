import test from "node:test";
import assert from "node:assert/strict";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnGatewayPeerRuntime } from "./peer-runtime.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import { VpnLeaseSequenceAuthority } from "./lease-sequence-authority.js";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  handleVpnProvisioningRequest,
  createVpnProvisioningServer,
  vpnProvisioningServerInternals,
} from "./provisioning-server.js";

const ACCESS_TOKEN = "A".repeat(32);
const ADMIN_TOKEN = "Z".repeat(32);
const GATEWAY_KEY = Buffer.alloc(32, 21).toString("base64");
const DEVICE_KEY = Buffer.alloc(32, 22).toString("base64");

function service() {
  return new VpnGatewayProvisioningCore({
    gateway: {
      id: "fr-par-01",
      endpointHost: "vpn-fr-par-01.example.com",
      endpointPort: 51820,
      gatewayPublicKey: GATEWAY_KEY,
      catalogSequence: 9,
      dnsServers: ["10.73.0.1", "fd73:1::1"],
      clientIpv6Prefix: "2606:4700:abcd:1234::/64",
    },
    accessToken: ACCESS_TOKEN,
    clock: () => 2_000_000_000_000,
  });
}

function runtime(calls, { fail = false } = {}) {
  return new VpnGatewayPeerRuntime({
    scriptPath: "/opt/sentinel/manage-peer.sh",
    runner: async (_path, args) => {
      calls.push(args);
      if (fail) throw new Error("simulated failure");
      return { stdout: "", stderr: "" };
    },
  });
}

class MemorySequenceAuthority extends VpnLeaseSequenceAuthority {
  constructor({ failCommits = false } = {}) {
    super();
    this.sequence = null;
    this.failCommits = failCommits;
  }

  async commitSequence(gatewayId, sequence) {
    assert.equal(gatewayId, "fr-par-01");
    if (this.failCommits) throw new Error("simulated authority failure");
    if (this.sequence !== null && sequence < this.sequence) {
      throw new Error("rollback rejected");
    }
    this.sequence = sequence;
    return sequence;
  }

  async readMinimumSequence(gatewayId) {
    assert.equal(gatewayId, "fr-par-01");
    if (this.sequence === null) throw new Error("sequence missing");
    return this.sequence;
  }
}

test("health endpoint is public and contains no secrets", async () => {
  const response = await handleVpnProvisioningRequest({
    method: "GET",
    url: "/health/live",
    core: service(),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(response.body, {
    status: "ok",
    service: "sentinel-vpn-provisioning",
    gatewayId: "fr-par-01",
  });
  assert.equal(JSON.stringify(response.body).includes(ACCESS_TOKEN), false);
});

test("provision endpoint rejects missing bearer authentication", async () => {
  const response = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/provision",
    headers: {},
    body: {
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
    },
    core: service(),
  });
  assert.equal(response.status, 401);
  assert.equal(response.body.error, "VPN_PROVISIONING_UNAUTHORIZED");
});

test("provision endpoint applies the WireGuard peer before returning success", async () => {
  const calls = [];
  const response = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/provision",
    headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
    body: {
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
    },
    core: service(),
    peerRuntime: runtime(calls),
  });
  assert.equal(response.status, 201);
  assert.equal(response.body.devicePublicKey, DEVICE_KEY);
  assert.equal(response.body.gatewayPublicKey, GATEWAY_KEY);
  assert.deepEqual(response.body.clientAddresses, [
    "10.73.0.2/32",
    "2606:4700:abcd:1234::2/128",
  ]);
  assert.deepEqual(calls, [[
    "add",
    DEVICE_KEY,
    "10.73.0.2/32",
    "2606:4700:abcd:1234::2/128",
  ]]);
  assert.equal("privateKey" in response.body, false);
});

test("provision endpoint fails closed when the peer runtime is unavailable", async () => {
  const core = service();
  const response = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/provision",
    headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
    body: {
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
    },
    core,
  });
  assert.equal(response.status, 503);
  assert.equal(response.body.error, "VPN_PEER_RUNTIME_NOT_CONFIGURED");
  assert.equal(core.isRevoked(DEVICE_KEY), true);
});

test("provision endpoint rolls back the lease when peer apply fails", async () => {
  const core = service();
  const calls = [];
  const response = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/provision",
    headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
    body: {
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
    },
    core,
    peerRuntime: runtime(calls, { fail: true }),
  });
  assert.equal(response.status, 503);
  assert.equal(response.body.error, "VPN_PEER_RUNTIME_COMMAND_FAILED");
  assert.equal(core.isRevoked(DEVICE_KEY), true);
});

test("provision endpoint rejects unexpected request fields", async () => {
  const response = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/provision",
    headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
    body: {
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
      privateKey: "must-never-be-accepted",
    },
    core: service(),
  });
  assert.equal(response.status, 400);
  assert.equal(response.body.error, "invalid_request_schema");
});

test("admin revocation removes the runtime peer before marking the lease revoked", async () => {
  const core = service();
  const calls = [];
  const peerRuntime = runtime(calls);
  const provisioned = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/provision",
    headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
    body: {
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
    },
    core,
    peerRuntime,
  });
  assert.equal(provisioned.status, 201);

  const adminTokenDigest = vpnProvisioningServerInternals.tokenDigest(ADMIN_TOKEN);

  const denied = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/admin/revoke",
    headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
    body: { devicePublicKey: DEVICE_KEY },
    core,
    adminTokenDigest,
    peerRuntime,
  });
  assert.equal(denied.status, 401);

  const revoked = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/admin/revoke",
    headers: { authorization: `Bearer ${ADMIN_TOKEN}` },
    body: { devicePublicKey: DEVICE_KEY },
    core,
    adminTokenDigest,
    peerRuntime,
  });
  assert.equal(revoked.status, 200);
  assert.equal(revoked.body.revoked, true);
  assert.equal(core.isRevoked(DEVICE_KEY), true);
  assert.deepEqual(calls.at(-1), ["remove", DEVICE_KEY]);
});

test("admin token comparison is canonical and constant-time compatible", () => {
  const digest = vpnProvisioningServerInternals.tokenDigest(ADMIN_TOKEN);
  assert.equal(vpnProvisioningServerInternals.constantTimeTokenMatch(ADMIN_TOKEN, digest), true);
  assert.equal(vpnProvisioningServerInternals.constantTimeTokenMatch("Y".repeat(32), digest), false);
  assert.equal(vpnProvisioningServerInternals.constantTimeTokenMatch("short", digest), false);
});

test("provision persists lease state through the external monotonic authority", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-server-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({ path, secret: "S".repeat(32), gatewayId: "fr-par-01" });
    const sequenceAuthority = new MemorySequenceAuthority();
    const calls = [];
    const core = service();
    const ok = await handleVpnProvisioningRequest({
      method: "POST", url: "/v1/provision",
      headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
      body: { gatewayId: "fr-par-01", devicePublicKey: DEVICE_KEY, catalogSequence: 9 },
      core,
      peerRuntime: runtime(calls),
      stateStore: store,
      sequenceAuthority,
    });
    assert.equal(ok.status, 201);
    assert.equal(sequenceAuthority.sequence, 1);
    const loaded = await store.load({ minimumSequence: 1 });
    assert.equal(loaded.sequence, 1);
    assert.equal(loaded.state.leases.length, 1);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("provision removes the peer and revokes the lease if trusted persistence cannot commit", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-server-"));
  try {
    const store = new VpnLeaseStateStore({
      path: join(dir, "leases.json"),
      secret: "S".repeat(32),
      gatewayId: "fr-par-01",
    });
    const sequenceAuthority = new MemorySequenceAuthority({ failCommits: true });
    const core = service();
    const calls = [];
    const failed = await handleVpnProvisioningRequest({
      method: "POST", url: "/v1/provision",
      headers: { authorization: `Bearer ${ACCESS_TOKEN}` },
      body: { gatewayId: "fr-par-01", devicePublicKey: DEVICE_KEY, catalogSequence: 9 },
      core,
      peerRuntime: runtime(calls),
      stateStore: store,
      sequenceAuthority,
    });
    assert.equal(failed.status, 503);
    assert.equal(failed.body.error, "VPN_LEASE_STATE_PERSIST_FAILED");
    assert.equal(core.isRevoked(DEVICE_KEY), true);
    assert.deepEqual(calls.at(-1), ["remove", DEVICE_KEY]);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("lease persistence configuration requires store and authority together", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-server-"));
  try {
    const store = new VpnLeaseStateStore({
      path: join(dir, "leases.json"),
      secret: "S".repeat(32),
      gatewayId: "fr-par-01",
    });
    assert.throws(
      () => vpnProvisioningServerInternals.validateLeasePersistenceConfiguration(store, null),
      /VPN_LEASE_PERSISTENCE_REQUIRES_STORE_AND_SEQUENCE_AUTHORITY/
    );
    assert.throws(
      () => vpnProvisioningServerInternals.validateLeasePersistenceConfiguration(null, new MemorySequenceAuthority()),
      /VPN_LEASE_PERSISTENCE_REQUIRES_STORE_AND_SEQUENCE_AUTHORITY/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("server factory accepts only validated lease persistence components", () => {
  const calls = [];
  const peerRuntime = runtime(calls);
  assert.throws(
    () => createVpnProvisioningServer({
      core: service(),
      peerRuntime,
      stateStore: {},
    }),
    /VpnLeaseStateStore invalid/
  );
  assert.throws(
    () => createVpnProvisioningServer({
      core: service(),
      peerRuntime,
      sequenceAuthority: {},
    }),
    /VpnLeaseSequenceAuthority invalid/
  );
});
