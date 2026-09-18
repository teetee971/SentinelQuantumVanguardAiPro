import test from "node:test";
import assert from "node:assert/strict";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import {
  handleVpnProvisioningRequest,
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
    },
    accessToken: ACCESS_TOKEN,
    clock: () => 2_000_000_000_000,
  });
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

test("provision endpoint returns bounded public provisioning data", async () => {
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
  });
  assert.equal(response.status, 201);
  assert.equal(response.body.devicePublicKey, DEVICE_KEY);
  assert.equal(response.body.gatewayPublicKey, GATEWAY_KEY);
  assert.deepEqual(response.body.clientAddresses, [
    "10.73.0.2/32",
    "fd73:1::2/128",
  ]);
  assert.equal("privateKey" in response.body, false);
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

test("admin revocation requires separate admin token", async () => {
  const core = service();
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
  });
  assert.equal(denied.status, 401);

  const revoked = await handleVpnProvisioningRequest({
    method: "POST",
    url: "/v1/admin/revoke",
    headers: { authorization: `Bearer ${ADMIN_TOKEN}` },
    body: { devicePublicKey: DEVICE_KEY },
    core,
    adminTokenDigest,
  });
  assert.equal(revoked.status, 200);
  assert.equal(revoked.body.revoked, true);
  assert.equal(core.isRevoked(DEVICE_KEY), true);
});

test("admin token comparison is canonical and constant-time compatible", () => {
  const digest = vpnProvisioningServerInternals.tokenDigest(ADMIN_TOKEN);
  assert.equal(vpnProvisioningServerInternals.constantTimeTokenMatch(ADMIN_TOKEN, digest), true);
  assert.equal(vpnProvisioningServerInternals.constantTimeTokenMatch("Y".repeat(32), digest), false);
  assert.equal(vpnProvisioningServerInternals.constantTimeTokenMatch("short", digest), false);
});
