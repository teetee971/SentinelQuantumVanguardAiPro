const MAX_ENDPOINTS_PER_NODE = 8;
const MAX_RELAYS = 256;
const MAX_TTL_MS = 10 * 60 * 1000;

function boundedString(value, name, max = 256) {
  const s = String(value || "").trim();
  if (!s || s.length > max) throw new Error(`invalid ${name}`);
  return s;
}

function parseEndpoint(raw) {
  const value = boundedString(raw, "endpoint", 512);
  const match = value.match(/^([a-z0-9.-]+|\[[0-9a-fA-F:]+\]):([1-9][0-9]{0,4})$/);
  if (!match) throw new Error("invalid endpoint");
  const port = Number(match[2]);
  if (port > 65535) throw new Error("invalid endpoint port");
  return value;
}

export class MeshTransportCoordinator {
  #nodes = new Map();
  #relays = new Map();
  #clock;

  constructor({ clock = () => Date.now() } = {}) {
    this.#clock = clock;
  }

  announceNatCandidates({ nodeId, endpoints = [], observedAddress = null, wireGuardPort = null, ttlMs = 120000 }) {
    const combined = [...endpoints];
    if (observedAddress && wireGuardPort) {
      const host = String(observedAddress).trim().replace(/^::ffff:/, "");
      const rendered = host.includes(":") ? `[${host}]:${wireGuardPort}` : `${host}:${wireGuardPort}`;
      combined.push(rendered);
    }
    return this.announceNodeEndpoints({ nodeId, endpoints: combined, ttlMs });
  }

  announceNodeEndpoints({ nodeId, endpoints, ttlMs = 120000 }) {
    const id = boundedString(nodeId, "node id");
    if (!Array.isArray(endpoints) || endpoints.length > MAX_ENDPOINTS_PER_NODE) {
      throw new Error("invalid endpoint set");
    }
    if (!Number.isInteger(ttlMs) || ttlMs < 10000 || ttlMs > MAX_TTL_MS) {
      throw new Error("invalid endpoint ttl");
    }
    const normalized = [...new Set(endpoints.map(parseEndpoint))];
    this.#nodes.set(id, {
      endpoints: normalized,
      expiresAt: this.#clock() + ttlMs,
    });
    return { nodeId: id, endpoints: [...normalized], expiresAt: this.#nodes.get(id).expiresAt };
  }

  registerRelay({ id, region, endpoint, status = "available", capacity = 1000 }) {
    if (this.#relays.size >= MAX_RELAYS && !this.#relays.has(id)) throw new Error("relay capacity exceeded");
    const relayId = boundedString(id, "relay id", 128);
    const relay = {
      id: relayId,
      region: boundedString(region, "relay region", 64),
      endpoint: parseEndpoint(endpoint),
      status: ["available","degraded","draining","offline"].includes(status) ? status : "offline",
      capacity: Number.isInteger(capacity) && capacity > 0 && capacity <= 100000 ? capacity : 1000,
      updatedAt: this.#clock(),
    };
    this.#relays.set(relayId, relay);
    return structuredClone(relay);
  }

  setRelayStatus(relayId, status) {
    const id = boundedString(relayId, "relay id", 128);
    const relay = this.#relays.get(id);
    if (!relay) return false;
    if (!["available","degraded","draining","offline"].includes(status)) throw new Error("invalid relay status");
    relay.status = status;
    relay.updatedAt = this.#clock();
    return true;
  }

  selectPath({ sourceNodeId, targetNodeId, preferredRegion = null }) {
    const now = this.#clock();
    const source = this.#nodes.get(boundedString(sourceNodeId, "source node id"));
    const target = this.#nodes.get(boundedString(targetNodeId, "target node id"));

    const sourceFresh = source && source.expiresAt > now;
    const targetFresh = target && target.expiresAt > now;

    const relays = [...this.#relays.values()]
      .filter(r => r.status === "available")
      .sort((a,b) => {
        const ap = preferredRegion && a.region === preferredRegion ? 0 : 1;
        const bp = preferredRegion && b.region === preferredRegion ? 0 : 1;
        return ap - bp || a.id.localeCompare(b.id);
      });

    if (sourceFresh && targetFresh && source.endpoints.length && target.endpoints.length) {
      return {
        mode: "direct",
        targetEndpoints: [...target.endpoints],
        relay: null,
        fallbackRelay: relays.length ? structuredClone(relays[0]) : null,
        reason: "FRESH_DIRECT_ENDPOINTS_AVAILABLE",
      };
    }

    if (relays.length) {
      const relay = relays[0];
      return {
        mode: "relay",
        targetEndpoints: [],
        relay: structuredClone(relay),
        reason: targetFresh ? "DIRECT_PATH_UNAVAILABLE" : "TARGET_ENDPOINTS_STALE_OR_MISSING",
      };
    }

    return {
      mode: "unavailable",
      targetEndpoints: [],
      relay: null,
      reason: "NO_VALID_DIRECT_PATH_OR_RELAY",
    };
  }

  pruneExpired() {
    const now = this.#clock();
    let removed = 0;
    for (const [id, value] of this.#nodes.entries()) {
      if (value.expiresAt <= now) {
        this.#nodes.delete(id);
        removed += 1;
      }
    }
    return removed;
  }

  listRelays() {
    return [...this.#relays.values()].map(structuredClone);
  }
}

export const meshTransportLimits = Object.freeze({
  maxEndpointsPerNode: MAX_ENDPOINTS_PER_NODE,
  maxRelays: MAX_RELAYS,
  maxTtlMs: MAX_TTL_MS,
});
