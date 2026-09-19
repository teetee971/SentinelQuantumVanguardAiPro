import { createPrivateKey, sign } from "node:crypto";

const DOMAIN = "sentinel-vpn-gateway-catalog-v1";
const CATALOG_ID = "public-vpn";
const SIGNATURE_ALGORITHM = "sha256";
const ID = /^[A-Za-z0-9._:-]{1,64}$/;
const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;
const COUNTRY = /^[A-Z]{2}$/;
const HOST = /^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$/;
const NUMERIC_IP = /^[0-9A-Fa-f:.]+$/;
const MAX_GATEWAYS = 128;
const MAX_PAYLOAD_BYTES = 96 * 1024;
const MAX_ENVELOPE_CHARS = 262_144;
const MAX_LIFETIME_MS = 24 * 60 * 60 * 1000;

function p256PrivateKey(value) {
  const key = value && typeof value === "object" && value.type === "private" && typeof value.export === "function"
    ? value
    : createPrivateKey(value);
  const details = key.asymmetricKeyDetails || {};
  if (key.asymmetricKeyType !== "ec" || !["prime256v1", "secp256r1"].includes(details.namedCurve)) {
    throw new Error("VPN_CATALOG_SIGNING_KEY_INVALID");
  }
  return key;
}

function fieldOrDash(value) {
  return value === null || value === undefined ? "-" : String(value);
}

function validateDns(values) {
  if (!Array.isArray(values) || values.length > 4) throw new Error("VPN_CATALOG_DNS_INVALID");
  if (new Set(values).size !== values.length) throw new Error("VPN_CATALOG_DNS_DUPLICATE");
  for (const value of values) {
    if (typeof value !== "string" || value.length < 2 || value.length > 45 ||
        value.trim() !== value || !NUMERIC_IP.test(value) ||
        (!value.includes(":") && !/^\d{1,3}(?:\.\d{1,3}){3}$/.test(value))) {
      throw new Error("VPN_CATALOG_DNS_INVALID");
    }
  }
  return [...values].sort();
}

function normalizeGateway(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new Error("VPN_CATALOG_GATEWAY_INVALID");
  }

  const id = String(input.id || "");
  const countryCode = String(input.countryCode || "");
  const status = String(input.status || "");
  if (!GATEWAY_ID.test(id)) throw new Error("VPN_CATALOG_GATEWAY_ID_INVALID");
  if (!COUNTRY.test(countryCode)) throw new Error("VPN_CATALOG_COUNTRY_INVALID");
  if (!["PLANNED","PROVISIONING","DEGRADED","AVAILABLE","DRAINING","OFFLINE","REVOKED"].includes(status)) {
    throw new Error("VPN_CATALOG_STATUS_INVALID");
  }

  const endpointHostname = input.endpointHostname ?? null;
  const endpointPort = input.endpointPort ?? null;
  const ipv4 = input.ipv4 === true;
  const ipv6 = input.ipv6 === true;
  const loadPercent = input.loadPercent ?? null;
  const latencyMs = input.latencyMs ?? null;
  const healthCheckedAtMs = input.healthCheckedAtMs ?? null;
  const dnsServerAddresses = validateDns(input.dnsServerAddresses || []);

  if (endpointHostname !== null &&
      (typeof endpointHostname !== "string" || !HOST.test(endpointHostname) || endpointHostname.endsWith(".invalid"))) {
    throw new Error("VPN_CATALOG_ENDPOINT_INVALID");
  }
  if (endpointPort !== null && (!Number.isInteger(endpointPort) || endpointPort < 1 || endpointPort > 65535)) {
    throw new Error("VPN_CATALOG_PORT_INVALID");
  }
  if (loadPercent !== null && (!Number.isInteger(loadPercent) || loadPercent < 0 || loadPercent > 100)) {
    throw new Error("VPN_CATALOG_LOAD_INVALID");
  }
  if (latencyMs !== null && (!Number.isInteger(latencyMs) || latencyMs < 0 || latencyMs > 60_000)) {
    throw new Error("VPN_CATALOG_LATENCY_INVALID");
  }
  if (healthCheckedAtMs !== null && (!Number.isSafeInteger(healthCheckedAtMs) || healthCheckedAtMs < 0)) {
    throw new Error("VPN_CATALOG_HEALTH_TIME_INVALID");
  }

  if (status === "AVAILABLE") {
    const acceptance = input.acceptance;
    if (!acceptance || acceptance.accepted !== true || acceptance.state !== "AVAILABLE" ||
        acceptance.gatewayId !== id || acceptance.reason !== "VPN_ACCEPTANCE_PASSED") {
      throw new Error("VPN_CATALOG_AVAILABLE_WITHOUT_ACCEPTANCE");
    }
    if (!endpointHostname || endpointPort === null || !ipv4 || !ipv6 ||
        loadPercent === null || latencyMs === null || healthCheckedAtMs === null ||
        dnsServerAddresses.length === 0) {
      throw new Error("VPN_CATALOG_AVAILABLE_FIELDS_INVALID");
    }
  }

  return {
    id,
    countryCode,
    status,
    endpointHostname,
    endpointPort,
    ipv4,
    ipv6,
    loadPercent,
    latencyMs,
    healthCheckedAtMs,
    dnsServerAddresses,
  };
}

function gatewayLine(gateway) {
  return [
    gateway.id,
    gateway.countryCode,
    gateway.status,
    fieldOrDash(gateway.endpointHostname),
    fieldOrDash(gateway.endpointPort),
    gateway.ipv4 ? "1" : "0",
    gateway.ipv6 ? "1" : "0",
    fieldOrDash(gateway.loadPercent),
    fieldOrDash(gateway.latencyMs),
    fieldOrDash(gateway.healthCheckedAtMs),
    gateway.dnsServerAddresses.length ? gateway.dnsServerAddresses.join(",") : "-",
  ].join("|");
}

export function buildVpnGatewayCatalogPayload({
  sequence,
  issuedAtMs,
  expiresAtMs,
  issuerId,
  keyId,
  gateways = [],
} = {}) {
  if (!Number.isSafeInteger(sequence) || sequence <= 0) throw new Error("VPN_CATALOG_SEQUENCE_INVALID");
  if (!Number.isSafeInteger(issuedAtMs) || issuedAtMs < 0 ||
      !Number.isSafeInteger(expiresAtMs) || expiresAtMs < 0) {
    throw new Error("VPN_CATALOG_TIME_INVALID");
  }
  if (expiresAtMs <= issuedAtMs || expiresAtMs - issuedAtMs > MAX_LIFETIME_MS) {
    throw new Error("VPN_CATALOG_LIFETIME_INVALID");
  }
  if (!ID.test(String(issuerId || "")) || !ID.test(String(keyId || ""))) {
    throw new Error("VPN_CATALOG_ID_INVALID");
  }
  if (!Array.isArray(gateways) || gateways.length > MAX_GATEWAYS) {
    throw new Error("VPN_CATALOG_GATEWAYS_INVALID");
  }

  const normalized = gateways.map(normalizeGateway).sort((a,b)=>a.id.localeCompare(b.id));
  if (new Set(normalized.map(x=>x.id)).size !== normalized.length) {
    throw new Error("VPN_CATALOG_GATEWAY_DUPLICATE");
  }

  const lines = [
    DOMAIN,
    `catalog_id=${CATALOG_ID}`,
    `sequence=${sequence}`,
    `issued_at_ms=${issuedAtMs}`,
    `expires_at_ms=${expiresAtMs}`,
    `issuer_id=${issuerId}`,
    `key_id=${keyId}`,
    ...normalized.map(g=>`gateway=${gatewayLine(g)}`),
  ];
  const payload = Buffer.from(lines.join("\n"), "utf8");
  if (payload.length < 1 || payload.length > MAX_PAYLOAD_BYTES) {
    throw new Error("VPN_CATALOG_PAYLOAD_TOO_LARGE");
  }
  return payload;
}

export function signVpnGatewayCatalog({ privateKey, ...fields } = {}) {
  const key = p256PrivateKey(privateKey);
  const payload = buildVpnGatewayCatalogPayload(fields);
  const signature = sign(SIGNATURE_ALGORITHM, payload, key);
  if (signature.length < 64 || signature.length > 80) {
    throw new Error("VPN_CATALOG_SIGNATURE_SIZE_INVALID");
  }
  const envelope = [
    `key_id=${fields.keyId}`,
    `payload_hex=${payload.toString("hex")}`,
    `signature_hex=${signature.toString("hex")}`,
  ].join("\n");
  if (envelope.length > MAX_ENVELOPE_CHARS) throw new Error("VPN_CATALOG_ENVELOPE_TOO_LARGE");
  return Object.freeze({ envelope, payload, signature });
}

export const vpnGatewayCatalogPublisherConstants = Object.freeze({
  domain: DOMAIN,
  catalogId: CATALOG_ID,
  maxGateways: MAX_GATEWAYS,
  maxLifetimeMs: MAX_LIFETIME_MS,
});
