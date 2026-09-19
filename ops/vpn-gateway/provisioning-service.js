#!/usr/bin/env node
import { pathToFileURL } from "node:url";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnGatewayPeerRuntime } from "./peer-runtime.js";
import { createVpnProvisioningServer } from "./provisioning-server.js";

const LOOPBACK_HOSTS = new Set(["127.0.0.1", "::1", "localhost"]);

function required(env, name) {
  const value = String(env[name] || "").trim();
  if (!value) throw new Error(`VPN_RUNTIME_${name}_REQUIRED`);
  return value;
}

function integer(env, name, { min, max }) {
  const raw = required(env, name);
  if (!/^[0-9]+$/.test(raw)) throw new Error(`VPN_RUNTIME_${name}_INVALID`);
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < min || value > max) {
    throw new Error(`VPN_RUNTIME_${name}_INVALID`);
  }
  return value;
}

function dnsList(raw) {
  const values = String(raw || "")
    .split(",")
    .map(value => value.trim())
    .filter(Boolean);
  if (values.length < 1 || values.length > 4) throw new Error("VPN_RUNTIME_DNS_INVALID");
  return values;
}

export function loadVpnProvisioningRuntimeConfig(env = process.env) {
  const listenHost = String(env.SENTINEL_PROVISIONING_LISTEN_HOST || "127.0.0.1").trim();
  const listenPort = env.SENTINEL_PROVISIONING_LISTEN_PORT
    ? integer(env, "SENTINEL_PROVISIONING_LISTEN_PORT", { min: 1, max: 65535 })
    : 8787;

  if (!/^[A-Za-z0-9:._-]{1,255}$/.test(listenHost)) {
    throw new Error("VPN_RUNTIME_LISTEN_HOST_INVALID");
  }
  if (!LOOPBACK_HOSTS.has(listenHost) &&
      String(env.SENTINEL_PROVISIONING_ALLOW_REMOTE || "") !== "YES") {
    throw new Error("VPN_RUNTIME_REMOTE_BIND_NOT_ACKNOWLEDGED");
  }

  const gateway = {
    id: required(env, "SENTINEL_VPN_GATEWAY_ID"),
    endpointHost: required(env, "SENTINEL_VPN_ENDPOINT_HOST"),
    endpointPort: integer(env, "SENTINEL_VPN_ENDPOINT_PORT", { min: 1, max: 65535 }),
    gatewayPublicKey: required(env, "SENTINEL_VPN_GATEWAY_PUBLIC_KEY"),
    catalogSequence: integer(env, "SENTINEL_VPN_CATALOG_SEQUENCE", {
      min: 1,
      max: Number.MAX_SAFE_INTEGER,
    }),
    dnsServers: dnsList(required(env, "SENTINEL_VPN_DNS_SERVERS")),
    clientIpv6Prefix: required(env, "SENTINEL_VPN_CLIENT_IPV6_PREFIX"),
  };

  return Object.freeze({
    listenHost,
    listenPort,
    gateway,
    accessToken: required(env, "SENTINEL_VPN_ACCESS_TOKEN"),
    adminToken: env.SENTINEL_VPN_ADMIN_TOKEN
      ? required(env, "SENTINEL_VPN_ADMIN_TOKEN")
      : null,
    interfaceName: String(env.SENTINEL_WG_INTERFACE || "sentinel0").trim(),
    leaseMs: env.SENTINEL_VPN_LEASE_MS
      ? integer(env, "SENTINEL_VPN_LEASE_MS", { min: 60_000, max: 15 * 60 * 1000 })
      : 10 * 60 * 1000,
    maxClients: env.SENTINEL_VPN_MAX_CLIENTS
      ? integer(env, "SENTINEL_VPN_MAX_CLIENTS", { min: 1, max: 253 })
      : 253,
  });
}

export function createVpnProvisioningRuntime({
  env = process.env,
  runner,
  clock,
} = {}) {
  const config = loadVpnProvisioningRuntimeConfig(env);
  const core = new VpnGatewayProvisioningCore({
    gateway: config.gateway,
    accessToken: config.accessToken,
    leaseMs: config.leaseMs,
    maxClients: config.maxClients,
    ...(clock ? { clock } : {}),
  });
  const peerRuntime = new VpnGatewayPeerRuntime({
    interfaceName: config.interfaceName,
    ...(runner ? { runner } : {}),
  });
  const server = createVpnProvisioningServer({
    core,
    adminToken: config.adminToken,
    peerRuntime,
  });
  return Object.freeze({ config, core, peerRuntime, server });
}

export async function runVpnProvisioningService({
  env = process.env,
  stdout = process.stdout,
  stderr = process.stderr,
} = {}) {
  const runtime = createVpnProvisioningRuntime({ env });
  const { server, config } = runtime;

  const close = signal => {
    server.close(error => {
      if (error) {
        stderr.write(`Sentinel VPN provisioning shutdown failed after ${signal}.\n`);
        process.exitCode = 1;
      }
    });
  };

  process.once("SIGTERM", () => close("SIGTERM"));
  process.once("SIGINT", () => close("SIGINT"));

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(config.listenPort, config.listenHost, () => {
      server.off("error", reject);
      resolve();
    });
  });

  stdout.write(
    `Sentinel VPN provisioning service listening on ${config.listenHost}:${config.listenPort} for gateway ${config.gateway.id}.\n`
  );
  return runtime;
}

const invokedDirectly = process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href;

if (invokedDirectly) {
  runVpnProvisioningService().catch(error => {
    process.stderr.write(`Sentinel VPN provisioning service failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
