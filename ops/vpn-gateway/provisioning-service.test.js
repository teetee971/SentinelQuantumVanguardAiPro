import test from "node:test";
import assert from "node:assert/strict";
import { loadVpnProvisioningRuntimeConfig } from "./provisioning-service.js";

const BASE = Object.freeze({
  SENTINEL_VPN_GATEWAY_ID: "fr-par-01",
  SENTINEL_VPN_ENDPOINT_HOST: "vpn-fr-par-01.example.com",
  SENTINEL_VPN_ENDPOINT_PORT: "51820",
  SENTINEL_VPN_GATEWAY_PUBLIC_KEY: Buffer.alloc(32, 7).toString("base64"),
  SENTINEL_VPN_CATALOG_SEQUENCE: "12",
  SENTINEL_VPN_DNS_SERVERS: "10.73.0.1,2001:4860:4860::8888",
  SENTINEL_VPN_CLIENT_IPV6_PREFIX: "2a01:db8:1234:5678::/64",
  SENTINEL_VPN_ACCESS_TOKEN: "A".repeat(32),
  SENTINEL_VPN_ADMIN_TOKEN: "Z".repeat(32),
});

test("runtime config defaults to loopback and bounded defaults", () => {
  const config = loadVpnProvisioningRuntimeConfig({ ...BASE });
  assert.equal(config.listenHost, "127.0.0.1");
  assert.equal(config.listenPort, 8787);
  assert.equal(config.interfaceName, "sentinel0");
  assert.equal(config.leaseMs, 600_000);
  assert.equal(config.maxClients, 253);
  assert.equal(config.gateway.id, "fr-par-01");
});

test("remote bind fails closed unless explicitly acknowledged", () => {
  assert.throws(
    () => loadVpnProvisioningRuntimeConfig({
      ...BASE,
      SENTINEL_PROVISIONING_LISTEN_HOST: "0.0.0.0",
    }),
    /VPN_RUNTIME_REMOTE_BIND_NOT_ACKNOWLEDGED/
  );

  const config = loadVpnProvisioningRuntimeConfig({
    ...BASE,
    SENTINEL_PROVISIONING_LISTEN_HOST: "0.0.0.0",
    SENTINEL_PROVISIONING_ALLOW_REMOTE: "YES",
  });
  assert.equal(config.listenHost, "0.0.0.0");
});

test("missing secrets and malformed numeric fields fail closed", () => {
  const missingToken = { ...BASE };
  delete missingToken.SENTINEL_VPN_ACCESS_TOKEN;
  assert.throws(
    () => loadVpnProvisioningRuntimeConfig(missingToken),
    /VPN_RUNTIME_SENTINEL_VPN_ACCESS_TOKEN_REQUIRED/
  );

  assert.throws(
    () => loadVpnProvisioningRuntimeConfig({
      ...BASE,
      SENTINEL_VPN_ENDPOINT_PORT: "70000",
    }),
    /VPN_RUNTIME_SENTINEL_VPN_ENDPOINT_PORT_INVALID/
  );

  assert.throws(
    () => loadVpnProvisioningRuntimeConfig({
      ...BASE,
      SENTINEL_VPN_MAX_CLIENTS: "254",
    }),
    /VPN_RUNTIME_SENTINEL_VPN_MAX_CLIENTS_INVALID/
  );
});

test("DNS list is bounded and admin token remains optional", () => {
  const config = loadVpnProvisioningRuntimeConfig({
    ...BASE,
    SENTINEL_VPN_DNS_SERVERS: "10.73.0.1",
    SENTINEL_VPN_ADMIN_TOKEN: "",
  });
  assert.deepEqual(config.gateway.dnsServers, ["10.73.0.1"]);
  assert.equal(config.adminToken, null);

  assert.throws(
    () => loadVpnProvisioningRuntimeConfig({
      ...BASE,
      SENTINEL_VPN_DNS_SERVERS: "1,2,3,4,5",
    }),
    /VPN_RUNTIME_DNS_INVALID/
  );
});
