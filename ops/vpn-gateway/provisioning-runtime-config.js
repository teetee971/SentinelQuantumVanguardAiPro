import { isIP } from "node:net";

const LOOPBACK_HOSTS = new Set(["127.0.0.1", "::1", "localhost"]);
const TOKEN = /^[A-Za-z0-9._~-]{32,2048}$/;
const INTERFACE = /^[A-Za-z0-9_.-]{1,15}$/;

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

function token(env, name, { optional = false } = {}) {
  const raw = String(env[name] || "").trim();
  if (!raw && optional) return null;
  if (!TOKEN.test(raw)) throw new Error(`VPN_RUNTIME_${name}_INVALID`);
  return raw;
}

function dnsList(raw) {
  const values = String(raw || "").split(",").map(value => value.trim()).filter(Boolean);
  if (values.length < 1 || values.length > 4 || values.some(value => isIP(value) === 0)) {
    throw new Error("VPN_RUNTIME_DNS_INVALID");
  }
  return values;
}

/**
 * Parse only the bounded configuration required by the existing provisioning core.
 * This module deliberately does not open sockets, execute WireGuard, or log secrets.
 */
export function loadVpnProvisioningRuntimeConfig(env = process.env) {
  const listenHost = String(env.SENTINEL_PROVISIONING_LISTEN_HOST || "127.0.0.1").trim();
  const listenPort = env.SENTINEL_PROVISIONING_LISTEN_PORT
    ? integer(env, "SENTINEL_PROVISIONING_LISTEN_PORT", { min: 1, max: 65535 })
    : 8787;

  if (!LOOPBACK_HOSTS.has(listenHost)) {
    throw new Error("VPN_RUNTIME_NON_LOOPBACK_BIND_FORBIDDEN");
  }

  const interfaceName = String(env.SENTINEL_WG_INTERFACE || "sentinel0").trim();
  if (!INTERFACE.test(interfaceName)) throw new Error("VPN_RUNTIME_INTERFACE_INVALID");

  return Object.freeze({
    listenHost,
    listenPort,
    interfaceName,
    accessToken: token(env, "SENTINEL_VPN_ACCESS_TOKEN"),
    adminToken: token(env, "SENTINEL_VPN_ADMIN_TOKEN", { optional: true }),
    gateway: Object.freeze({
      id: required(env, "SENTINEL_VPN_GATEWAY_ID"),
      endpointHost: required(env, "SENTINEL_VPN_ENDPOINT_HOST"),
      endpointPort: integer(env, "SENTINEL_VPN_ENDPOINT_PORT", { min: 1, max: 65535 }),
      gatewayPublicKey: required(env, "SENTINEL_VPN_GATEWAY_PUBLIC_KEY"),
      catalogSequence: integer(env, "SENTINEL_VPN_CATALOG_SEQUENCE", { min: 1, max: Number.MAX_SAFE_INTEGER }),
      dnsServers: Object.freeze(dnsList(required(env, "SENTINEL_VPN_DNS_SERVERS"))),
      clientIpv6Prefix: required(env, "SENTINEL_VPN_CLIENT_IPV6_PREFIX"),
    }),
    leaseMs: env.SENTINEL_VPN_LEASE_MS
      ? integer(env, "SENTINEL_VPN_LEASE_MS", { min: 60_000, max: 15 * 60 * 1000 })
      : 10 * 60 * 1000,
    maxClients: env.SENTINEL_VPN_MAX_CLIENTS
      ? integer(env, "SENTINEL_VPN_MAX_CLIENTS", { min: 1, max: 253 })
      : 253,
  });
}
