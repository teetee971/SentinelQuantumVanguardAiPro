import test from "node:test";
import assert from "node:assert/strict";
import { loadVpnProvisioningRuntimeConfig } from "./provisioning-runtime-config.js";

const BASE = Object.freeze({
  SENTINEL_VPN_GATEWAY_ID: "fr-par-01",
  SENTINEL_VPN_ENDPOINT_HOST: "vpn-fr-par-01.example.com",
  SENTINEL_VPN_ENDPOINT_PORT: "51820",
  SENTINEL_VPN_GATEWAY_PUBLIC_KEY: Buffer.alloc(32, 7).toString("base64"),
  SENTINEL_VPN_CATALOG_SEQUENCE: "12",
  SENTINEL_VPN_DNS_SERVERS: "10.73.0.1,2001:4860:4860::8888",
  SENTINEL_VPN_CLIENT_IPV6_PREFIX: "2001:db8:1234:5678::/64",
  SENTINEL_VPN_ACCESS_TOKEN: "A".repeat(32),
  SENTINEL_VPN_ADMIN_TOKEN: "Z".repeat(32),
});

test("defaults remain loopback-only and bounded", () => {
  const config = loadVpnProvisioningRuntimeConfig({ ...BASE });
  assert.equal(config.listenHost, "127.0.0.1");
  assert.equal(config.listenPort, 8787);
  assert.equal(config.interfaceName, "sentinel0");
  assert.equal(config.leaseMs, 600_000);
  assert.equal(config.maxClients, 253);
});

test("non-loopback bind always fails closed", () => {
  for (const host of ["0.0.0.0", "::", "192.0.2.10"]) {
    assert.throws(
      () => loadVpnProvisioningRuntimeConfig({ ...BASE, SENTINEL_PROVISIONING_LISTEN_HOST: host }),
      /VPN_RUNTIME_NON_LOOPBACK_BIND_FORBIDDEN/
    );
  }
});

test("secrets and numeric bounds fail closed", () => {
  const missing = { ...BASE };
  delete missing.SENTINEL_VPN_ACCESS_TOKEN;
  assert.throws(() => loadVpnProvisioningRuntimeConfig(missing), /SENTINEL_VPN_ACCESS_TOKEN_INVALID/);
  assert.throws(
    () => loadVpnProvisioningRuntimeConfig({ ...BASE, SENTINEL_VPN_MAX_CLIENTS: "254" }),
    /SENTINEL_VPN_MAX_CLIENTS_INVALID/
  );
});

test("DNS values must be IP literals and admin token is optional", () => {
  const config = loadVpnProvisioningRuntimeConfig({ ...BASE, SENTINEL_VPN_ADMIN_TOKEN: "" });
  assert.equal(config.adminToken, null);
  assert.throws(
    () => loadVpnProvisioningRuntimeConfig({ ...BASE, SENTINEL_VPN_DNS_SERVERS: "resolver.example" }),
    /VPN_RUNTIME_DNS_INVALID/
  );
});
