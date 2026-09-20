import test from "node:test";
import assert from "node:assert/strict";
import { VpnGatewayProvisioningCore, vpnGatewayProvisioningInternals } from "./provisioning-core.js";

const TOKEN = "A".repeat(32);
const GATEWAY_KEY = Buffer.alloc(32, 7).toString("base64");
const DEVICE_KEY_A = Buffer.alloc(32, 8).toString("base64");
const DEVICE_KEY_B = Buffer.alloc(32, 9).toString("base64");

function core(overrides = {}) {
  return new VpnGatewayProvisioningCore({
    gateway: {
      id: "fr-par-01",
      endpointHost: "vpn-fr-par-01.example.com",
      endpointPort: 51820,
      gatewayPublicKey: GATEWAY_KEY,
      catalogSequence: 7,
      dnsServers: ["10.73.0.1", "fd73:1::1"],
      clientIpv6Prefix: "2606:4700:abcd:1234::/64",
    },
    accessToken: TOKEN,
    clock: () => 2_000_000_000_000,
    ...overrides,
  });
}

test("provisions only after bearer-equivalent token and exact catalog/gateway binding", () => {
  const service = core();

  assert.equal(service.provision({
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 7,
    accessToken: "B".repeat(32),
  }).reason, "VPN_PROVISIONING_UNAUTHORIZED");

  assert.equal(service.provision({
    gatewayId: "de-fra-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 7,
    accessToken: TOKEN,
  }).reason, "VPN_PROVISIONING_GATEWAY_MISMATCH");

  assert.equal(service.provision({
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 6,
    accessToken: TOKEN,
  }).reason, "VPN_PROVISIONING_CATALOG_MISMATCH");
});

test("creates bounded dual-stack response without any client private key", () => {
  const service = core();
  const result = service.provision({
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 7,
    accessToken: TOKEN,
  });

  assert.equal(result.accepted, true);
  assert.equal(result.reason, "VPN_PROVISIONING_CREATED");
  assert.equal(result.response.gatewayId, "fr-par-01");
  assert.equal(result.response.devicePublicKey, DEVICE_KEY_A);
  assert.equal(result.response.gatewayPublicKey, GATEWAY_KEY);
  assert.equal(result.response.endpointHost, "vpn-fr-par-01.example.com");
  assert.equal(result.response.endpointPort, 51820);
  assert.deepEqual(result.response.clientAddresses, ["10.73.0.2/32", "2606:4700:abcd:1234::2/128"]);
  assert.deepEqual(result.response.dnsServers, ["10.73.0.1", "fd73:1::1"]);
  assert.equal(result.response.expiresAtMs, 2_000_000_600_000);
  assert.equal("privateKey" in result.response, false);
});

test("same active device key is idempotent", () => {
  const service = core();
  const request = {
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 7,
    accessToken: TOKEN,
  };

  const first = service.provision(request);
  const second = service.provision(request);
  assert.equal(first.reason, "VPN_PROVISIONING_CREATED");
  assert.equal(second.reason, "VPN_PROVISIONING_EXISTING_LEASE");
  assert.deepEqual(second.response, first.response);
});

test("revocation invalidates a lease and allows reprovisioning", () => {
  const service = core();
  const request = {
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 7,
    accessToken: TOKEN,
  };

  const first = service.provision(request);
  assert.equal(first.accepted, true);
  assert.equal(service.revoke(DEVICE_KEY_A), true);
  assert.equal(service.isRevoked(DEVICE_KEY_A), true);

  const second = service.provision(request);
  assert.equal(second.accepted, true);
  assert.equal(second.reason, "VPN_PROVISIONING_CREATED");
  assert.notDeepEqual(second.response.clientAddresses, first.response.clientAddresses);
});

test("capacity is enforced across active leases", () => {
  const service = core({ maxClients: 1 });

  assert.equal(service.provision({
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_A,
    catalogSequence: 7,
    accessToken: TOKEN,
  }).accepted, true);

  const denied = service.provision({
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY_B,
    catalogSequence: 7,
    accessToken: TOKEN,
  });
  assert.equal(denied.accepted, false);
  assert.equal(denied.reason, "VPN_PROVISIONING_CAPACITY_EXHAUSTED");
});

test("routed IPv6 client prefix rejects ULA and documentation ranges", () => {
  assert.equal(
    vpnGatewayProvisioningInternals.normalizeRoutedIpv6Prefix("2606:4700:abcd:1234::/64"),
    "2606:4700:abcd:1234::/64"
  );
  assert.equal(vpnGatewayProvisioningInternals.normalizeRoutedIpv6Prefix("fd73:1::/64"), null);
  assert.equal(vpnGatewayProvisioningInternals.normalizeRoutedIpv6Prefix("fe80::/64"), null);
  assert.equal(vpnGatewayProvisioningInternals.normalizeRoutedIpv6Prefix("2001:db8:1:2::/64"), null);
  assert.equal(
    vpnGatewayProvisioningInternals.ipv6HostFromPrefix("2606:4700:abcd:1234::/64", 42),
    "2606:4700:abcd:1234::2a/128"
  );
});

test("wireguard key and DNS validation reject malformed values", () => {
  assert.equal(vpnGatewayProvisioningInternals.canonicalWireGuardKey(GATEWAY_KEY), GATEWAY_KEY);
  assert.equal(vpnGatewayProvisioningInternals.canonicalWireGuardKey("A".repeat(44)), null);
  assert.equal(vpnGatewayProvisioningInternals.validateDns("10.73.0.1"), true);
  assert.equal(vpnGatewayProvisioningInternals.validateDns("fd73:1::1"), true);
  assert.equal(vpnGatewayProvisioningInternals.validateDns("999.1.1.1"), false);
});

test("exports and restores only bounded public lease state", () => {
  const source = core();
  const created = source.provision({
    gatewayId: "fr-par-01",
    devicePublicKey: DEVICE_KEY,
    catalogSequence: 9,
    accessToken: ACCESS_TOKEN,
  });
  assert.equal(created.accepted, true);
  const snapshot = source.exportState();
  assert.equal("privateKey" in snapshot, false);
  assert.equal(snapshot.leases.length, 1);

  const restored = core();
  restored.restoreState(snapshot);
  assert.deepEqual(restored.exportState(), snapshot);
});

test("restore rejects malformed, duplicate and secret-bearing lease state", () => {
  const target = core();
  assert.throws(() => target.restoreState({
    nextIndex: 2,
    leases: [{
      devicePublicKey: DEVICE_KEY,
      index: 2,
      expiresAtMs: 2_000_000_600_000,
      revoked: false,
      privateKey: "forbidden",
    }],
  }), /VPN_PROVISIONING_STATE_INVALID/);

  assert.throws(() => target.restoreState({
    nextIndex: 2,
    leases: [
      { devicePublicKey: DEVICE_KEY, index: 2, expiresAtMs: 2_000_000_600_000, revoked: false },
      { devicePublicKey: DEVICE_KEY, index: 3, expiresAtMs: 2_000_000_600_000, revoked: false },
    ],
  }), /VPN_PROVISIONING_STATE_INVALID/);
});
