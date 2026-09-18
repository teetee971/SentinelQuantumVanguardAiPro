import { createHash, timingSafeEqual } from "node:crypto";

const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;
const HOST = /^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$/;
const TOKEN = /^[A-Za-z0-9._~-]{32,2048}$/;
const DNS_V4 = /^\d{1,3}(?:\.\d{1,3}){3}$/;
const DNS_V6 = /^[0-9A-Fa-f:]+$/;

function canonicalWireGuardKey(value) {
  if (typeof value !== "string" || value.length !== 44) return null;
  try {
    const decoded = Buffer.from(value, "base64");
    if (decoded.length !== 32 || decoded.toString("base64") !== value) return null;
    return value;
  } catch {
    return null;
  }
}

function canonicalTokenHash(value) {
  if (typeof value !== "string" || !TOKEN.test(value)) return null;
  return createHash("sha256").update(value, "utf8").digest();
}

function validateIpv4(value) {
  if (!DNS_V4.test(value)) return false;
  const parts = value.split(".").map(Number);
  return parts.length === 4 && parts.every(x => Number.isInteger(x) && x >= 0 && x <= 255);
}

function validateDns(value) {
  if (typeof value !== "string" || value.length < 2 || value.length > 45 || value.trim() !== value) {
    return false;
  }
  if (validateIpv4(value)) return true;
  return value.includes(":") && DNS_V6.test(value);
}

function normalizeGateway(config) {
  if (!config || typeof config !== "object") throw new TypeError("gateway config required");
  const id = String(config.id || "");
  const endpointHost = String(config.endpointHost || "");
  const endpointPort = Number(config.endpointPort);
  const gatewayPublicKey = canonicalWireGuardKey(config.gatewayPublicKey);
  const catalogSequence = Number(config.catalogSequence);
  const dnsServers = Array.isArray(config.dnsServers) ? [...config.dnsServers] : [];

  if (!GATEWAY_ID.test(id)) throw new Error("VPN_GATEWAY_ID_INVALID");
  if (!HOST.test(endpointHost) || endpointHost.endsWith(".invalid")) throw new Error("VPN_GATEWAY_HOST_INVALID");
  if (!Number.isInteger(endpointPort) || endpointPort < 1 || endpointPort > 65535) {
    throw new Error("VPN_GATEWAY_PORT_INVALID");
  }
  if (!gatewayPublicKey) throw new Error("VPN_GATEWAY_PUBLIC_KEY_INVALID");
  if (!Number.isSafeInteger(catalogSequence) || catalogSequence <= 0) {
    throw new Error("VPN_GATEWAY_CATALOG_SEQUENCE_INVALID");
  }
  if (dnsServers.length < 1 || dnsServers.length > 4 || dnsServers.some(x => !validateDns(x))) {
    throw new Error("VPN_GATEWAY_DNS_INVALID");
  }
  if (new Set(dnsServers).size !== dnsServers.length) throw new Error("VPN_GATEWAY_DNS_DUPLICATE");

  return Object.freeze({
    id,
    endpointHost,
    endpointPort,
    gatewayPublicKey,
    catalogSequence,
    dnsServers: Object.freeze(dnsServers),
  });
}

export class VpnGatewayProvisioningCore {
  #gateway;
  #tokenHash;
  #clock;
  #leaseMs;
  #maxClients;
  #leasesByKey = new Map();
  #nextIndex = 2;

  constructor({
    gateway,
    accessToken,
    clock = () => Date.now(),
    leaseMs = 10 * 60 * 1000,
    maxClients = 253,
  }) {
    this.#gateway = normalizeGateway(gateway);
    this.#tokenHash = canonicalTokenHash(accessToken);
    if (!this.#tokenHash) throw new Error("VPN_PROVISIONING_ACCESS_TOKEN_INVALID");
    if (typeof clock !== "function") throw new TypeError("clock must be a function");
    if (!Number.isSafeInteger(leaseMs) || leaseMs < 60_000 || leaseMs > 15 * 60 * 1000) {
      throw new Error("VPN_PROVISIONING_LEASE_INVALID");
    }
    if (!Number.isInteger(maxClients) || maxClients < 1 || maxClients > 253) {
      throw new Error("VPN_PROVISIONING_CAPACITY_INVALID");
    }
    this.#clock = clock;
    this.#leaseMs = leaseMs;
    this.#maxClients = maxClients;
  }

  get gatewayId() {
    return this.#gateway.id;
  }

  authenticate(accessToken) {
    const candidate = canonicalTokenHash(accessToken);
    return candidate !== null &&
      candidate.length === this.#tokenHash.length &&
      timingSafeEqual(candidate, this.#tokenHash);
  }

  provision({ gatewayId, devicePublicKey, catalogSequence, accessToken }) {
    if (!this.authenticate(accessToken)) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_UNAUTHORIZED" });
    }
    if (gatewayId !== this.#gateway.id) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_GATEWAY_MISMATCH" });
    }
    if (!Number.isSafeInteger(catalogSequence) || catalogSequence !== this.#gateway.catalogSequence) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_CATALOG_MISMATCH" });
    }
    const publicKey = canonicalWireGuardKey(devicePublicKey);
    if (!publicKey) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_DEVICE_KEY_INVALID" });
    }

    const now = Number(this.#clock());
    if (!Number.isSafeInteger(now) || now < 0) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_CLOCK_INVALID" });
    }

    const existing = this.#leasesByKey.get(publicKey);
    if (existing && !existing.revoked && existing.expiresAtMs > now) {
      return Object.freeze({
        accepted: true,
        reason: "VPN_PROVISIONING_EXISTING_LEASE",
        response: this.#response(publicKey, existing),
      });
    }

    if (this.#activeLeaseCount(now) >= this.#maxClients) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_CAPACITY_EXHAUSTED" });
    }

    const index = this.#allocateIndex(now);
    if (index === null) {
      return Object.freeze({ accepted: false, reason: "VPN_PROVISIONING_ADDRESS_EXHAUSTED" });
    }
    const lease = {
      index,
      expiresAtMs: now + this.#leaseMs,
      revoked: false,
    };
    this.#leasesByKey.set(publicKey, lease);
    return Object.freeze({
      accepted: true,
      reason: "VPN_PROVISIONING_CREATED",
      response: this.#response(publicKey, lease),
    });
  }

  revoke(devicePublicKey) {
    const publicKey = canonicalWireGuardKey(devicePublicKey);
    if (!publicKey) return false;
    const lease = this.#leasesByKey.get(publicKey);
    if (!lease || lease.revoked) return false;
    lease.revoked = true;
    return true;
  }

  isRevoked(devicePublicKey) {
    const publicKey = canonicalWireGuardKey(devicePublicKey);
    return publicKey ? this.#leasesByKey.get(publicKey)?.revoked === true : false;
  }

  #activeLeaseCount(now) {
    let active = 0;
    for (const lease of this.#leasesByKey.values()) {
      if (!lease.revoked && lease.expiresAtMs > now) active += 1;
    }
    return active;
  }

  #allocateIndex(now) {
    const used = new Set();
    for (const lease of this.#leasesByKey.values()) {
      if (!lease.revoked && lease.expiresAtMs > now) used.add(lease.index);
    }
    for (let attempt = 0; attempt < 253; attempt += 1) {
      const candidate = this.#nextIndex;
      this.#nextIndex = this.#nextIndex >= 254 ? 2 : this.#nextIndex + 1;
      if (!used.has(candidate)) return candidate;
    }
    return null;
  }

  #response(devicePublicKey, lease) {
    const index = lease.index;
    return Object.freeze({
      gatewayId: this.#gateway.id,
      devicePublicKey,
      gatewayPublicKey: this.#gateway.gatewayPublicKey,
      endpointHost: this.#gateway.endpointHost,
      endpointPort: this.#gateway.endpointPort,
      clientAddresses: Object.freeze([
        `10.73.0.${index}/32`,
        `fd73:1::${index.toString(16)}/128`,
      ]),
      dnsServers: this.#gateway.dnsServers,
      expiresAtMs: lease.expiresAtMs,
    });
  }
}

export const vpnGatewayProvisioningInternals = Object.freeze({
  canonicalWireGuardKey,
  validateDns,
});
