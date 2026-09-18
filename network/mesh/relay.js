import dgram from "node:dgram";
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

const MAX_SESSIONS = 5000;
const MAX_SESSION_TTL_MS = 10 * 60 * 1000;
const MAX_PACKET_BYTES = 4096;
const MAX_PAYLOAD_BYTES = 2048;
const MAX_PACKETS_PER_SECOND = 200;
const MAX_BYTES_PER_SECOND = 512 * 1024;
const MAX_SESSION_PACKETS = 100000;
const MAX_SESSION_BYTES = 256 * 1024 * 1024;

function boundedId(value, name) {
  const s = String(value || "").trim();
  if (!/^[a-zA-Z0-9:_./-]{2,256}$/.test(s)) throw new Error(`invalid ${name}`);
  return s;
}

function boundedSessionKey(value, name) {
  const s = String(value || "").trim();
  if (!s || s.length > 512 || /[\u0000-\u001f\u007f]/.test(s)) throw new Error(`invalid ${name}`);
  return s;
}

function tokenHash(token) {
  return createHash("sha256").update(String(token), "utf8").digest();
}

function safeTokenMatch(expectedHash, token) {
  if (!expectedHash || typeof token !== "string" || token.length < 32 || token.length > 128) return false;
  const actual = tokenHash(token);
  return expectedHash.length === actual.length && timingSafeEqual(expectedHash, actual);
}

function renderPeer(rinfo) {
  return {
    address: rinfo.address.replace(/^::ffff:/, ""),
    port: rinfo.port,
    family: rinfo.family,
  };
}

export class MeshRelayRegistry {
  #sessions = new Map();
  #clock;

  constructor({ clock = () => Date.now() } = {}) {
    this.#clock = clock;
  }

  createSession({ sourceNodeId, targetNodeId, ttlMs = 120000 }) {
    if (this.#sessions.size >= MAX_SESSIONS) throw new Error("relay session capacity exceeded");
    const source = boundedId(sourceNodeId, "source node id");
    const target = boundedId(targetNodeId, "target node id");
    if (source === target) throw new Error("relay peers must differ");
    if (!Number.isInteger(ttlMs) || ttlMs < 10000 || ttlMs > MAX_SESSION_TTL_MS) {
      throw new Error("invalid relay session ttl");
    }

    const sessionId = randomBytes(18).toString("base64url");
    const sourceToken = randomBytes(32).toString("base64url");
    const targetToken = randomBytes(32).toString("base64url");
    const now = this.#clock();

    this.#sessions.set(sessionId, {
      id: sessionId,
      sourceNodeId: source,
      targetNodeId: target,
      sourceTokenHash: tokenHash(sourceToken),
      targetTokenHash: tokenHash(targetToken),
      sourcePeer: null,
      targetPeer: null,
      sourceSeq: -1,
      targetSeq: -1,
      packets: 0,
      bytes: 0,
      windowStartedAt: now,
      windowPackets: 0,
      windowBytes: 0,
      createdAt: now,
      expiresAt: now + ttlMs,
      closed: false,
    });

    return {
      sessionId,
      source: { nodeId: source, token: sourceToken },
      target: { nodeId: target, token: targetToken },
      expiresAt: now + ttlMs,
    };
  }

  authenticate(sessionId, token) {
    const session = this.#active(sessionId);
    if (!session) return null;
    if (safeTokenMatch(session.sourceTokenHash, token)) return { role: "source", nodeId: session.sourceNodeId };
    if (safeTokenMatch(session.targetTokenHash, token)) return { role: "target", nodeId: session.targetNodeId };
    return null;
  }

  registerPeer(sessionId, token, rinfo) {
    const session = this.#active(sessionId);
    if (!session) throw new Error("relay session unavailable");
    const auth = this.authenticate(sessionId, token);
    if (!auth) throw new Error("relay unauthorized");
    const peer = renderPeer(rinfo);
    if (auth.role === "source") session.sourcePeer = peer;
    else session.targetPeer = peer;
    return { sessionId, role: auth.role, peer: structuredClone(peer), expiresAt: session.expiresAt };
  }

  routePacket(sessionId, token, seq, payloadBytes) {
    const session = this.#active(sessionId);
    if (!session) throw new Error("relay session unavailable");
    const auth = this.authenticate(sessionId, token);
    if (!auth) throw new Error("relay unauthorized");
    if (!Number.isSafeInteger(seq) || seq < 0) throw new Error("invalid relay sequence");
    if (!Buffer.isBuffer(payloadBytes) || payloadBytes.length === 0 || payloadBytes.length > MAX_PAYLOAD_BYTES) {
      throw new Error("invalid relay payload");
    }

    const seqKey = auth.role === "source" ? "sourceSeq" : "targetSeq";
    if (seq <= session[seqKey]) throw new Error("relay replay detected");

    const now = this.#clock();
    if (now - session.windowStartedAt >= 1000) {
      session.windowStartedAt = now;
      session.windowPackets = 0;
      session.windowBytes = 0;
    }
    if (session.windowPackets + 1 > MAX_PACKETS_PER_SECOND) throw new Error("relay packet rate exceeded");
    if (session.windowBytes + payloadBytes.length > MAX_BYTES_PER_SECOND) throw new Error("relay byte rate exceeded");
    if (session.packets + 1 > MAX_SESSION_PACKETS) throw new Error("relay packet quota exceeded");
    if (session.bytes + payloadBytes.length > MAX_SESSION_BYTES) throw new Error("relay byte quota exceeded");

    const destination = auth.role === "source" ? session.targetPeer : session.sourcePeer;
    if (!destination) throw new Error("relay peer not registered");

    session[seqKey] = seq;
    session.windowPackets += 1;
    session.windowBytes += payloadBytes.length;
    session.packets += 1;
    session.bytes += payloadBytes.length;

    return {
      destination: structuredClone(destination),
      fromRole: auth.role,
      toRole: auth.role === "source" ? "target" : "source",
      payload: Buffer.from(payloadBytes),
      sessionPackets: session.packets,
      sessionBytes: session.bytes,
    };
  }

  close(sessionId) {
    const session = this.#sessions.get(String(sessionId || ""));
    if (!session) return false;
    session.closed = true;
    session.sourcePeer = null;
    session.targetPeer = null;
    return true;
  }

  describe(sessionId) {
    const s = this.#active(sessionId);
    if (!s) return null;
    return {
      id: s.id,
      sourceNodeId: s.sourceNodeId,
      targetNodeId: s.targetNodeId,
      sourceRegistered: Boolean(s.sourcePeer),
      targetRegistered: Boolean(s.targetPeer),
      packets: s.packets,
      bytes: s.bytes,
      createdAt: s.createdAt,
      expiresAt: s.expiresAt,
    };
  }

  pruneExpired() {
    const now = this.#clock();
    let removed = 0;
    for (const [id, s] of this.#sessions.entries()) {
      if (s.closed || s.expiresAt <= now) {
        this.#sessions.delete(id);
        removed += 1;
      }
    }
    return removed;
  }

  #active(sessionId) {
    const id = String(sessionId || "");
    const session = this.#sessions.get(id);
    if (!session || session.closed) return null;
    if (session.expiresAt <= this.#clock()) {
      this.#sessions.delete(id);
      return null;
    }
    return session;
  }
}


export class MeshRelayGrantBroker {
  #registry;
  #grants = new Map();

  constructor({ registry }) {
    if (!(registry instanceof MeshRelayRegistry)) throw new Error("relay registry required");
    this.#registry = registry;
  }

  ensureNegotiationGrant({ negotiationId, sourceNodeId, targetNodeId, relayEndpoint, ttlMs = 120000 }) {
    const key = boundedSessionKey(negotiationId, "negotiation id");
    const existing = this.#grants.get(key);
    if (existing) {
      return {
        negotiationId: key,
        relaySessionId: existing.relaySessionId,
        relayEndpoint: existing.relayEndpoint,
        sourceNodeId: existing.sourceNodeId,
        targetNodeId: existing.targetNodeId,
        expiresAt: existing.expiresAt,
      };
    }

    const issued = this.#registry.createSession({ sourceNodeId, targetNodeId, ttlMs });
    const grant = {
      negotiationId: key,
      relaySessionId: issued.sessionId,
      relayEndpoint: String(relayEndpoint || ""),
      sourceNodeId: issued.source.nodeId,
      targetNodeId: issued.target.nodeId,
      sourceToken: issued.source.token,
      targetToken: issued.target.token,
      sourceClaimed: false,
      targetClaimed: false,
      expiresAt: issued.expiresAt,
    };
    this.#grants.set(key, grant);
    return {
      negotiationId: key,
      relaySessionId: grant.relaySessionId,
      relayEndpoint: grant.relayEndpoint,
      sourceNodeId: grant.sourceNodeId,
      targetNodeId: grant.targetNodeId,
      expiresAt: grant.expiresAt,
    };
  }

  claim({ negotiationId, nodeId }) {
    const key = boundedSessionKey(negotiationId, "negotiation id");
    const claimant = boundedId(nodeId, "node id");
    const grant = this.#grants.get(key);
    if (!grant) return null;

    if (claimant === grant.sourceNodeId) {
      if (grant.sourceClaimed) return null;
      grant.sourceClaimed = true;
      const token = grant.sourceToken;
      grant.sourceToken = null;
      return {
        negotiationId: key,
        relaySessionId: grant.relaySessionId,
        relayEndpoint: grant.relayEndpoint,
        role: "source",
        token,
        expiresAt: grant.expiresAt,
      };
    }

    if (claimant === grant.targetNodeId) {
      if (grant.targetClaimed) return null;
      grant.targetClaimed = true;
      const token = grant.targetToken;
      grant.targetToken = null;
      return {
        negotiationId: key,
        relaySessionId: grant.relaySessionId,
        relayEndpoint: grant.relayEndpoint,
        role: "target",
        token,
        expiresAt: grant.expiresAt,
      };
    }

    return null;
  }

  pruneExpired(now = Date.now()) {
    let removed = 0;
    for (const [id, grant] of this.#grants.entries()) {
      if (grant.expiresAt <= now) {
        this.#grants.delete(id);
        removed += 1;
      }
    }
    return removed;
  }
}

function parseEnvelope(msg) {
  if (!Buffer.isBuffer(msg) || msg.length < 2 || msg.length > MAX_PACKET_BYTES) throw new Error("invalid relay packet");
  const packet = JSON.parse(msg.toString("utf8"));
  if (!packet || typeof packet !== "object") throw new Error("invalid relay envelope");
  const sessionId = String(packet.sessionId || "");
  const token = String(packet.token || "");
  const type = String(packet.type || "");
  if (!sessionId || sessionId.length > 64) throw new Error("invalid relay session id");
  if (token.length < 32 || token.length > 128) throw new Error("invalid relay token");
  if (!["register","data"].includes(type)) throw new Error("invalid relay packet type");
  return { packet, sessionId, token, type };
}

export function createMeshRelayServer({
  registry = new MeshRelayRegistry(),
  host = "127.0.0.1",
  port = 3480,
}) {
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error("invalid relay UDP port");
  const socket = dgram.createSocket(host.includes(":") ? "udp6" : "udp4");

  socket.on("message", (msg, rinfo) => {
    let reply = null;
    try {
      const { packet, sessionId, token, type } = parseEnvelope(msg);
      if (type === "register") {
        const registered = registry.registerPeer(sessionId, token, rinfo);
        reply = Buffer.from(JSON.stringify({
          ok: true,
          type: "registered",
          sessionId,
          role: registered.role,
          expiresAt: registered.expiresAt,
        }), "utf8");
      } else {
        if (!Number.isSafeInteger(packet.seq) || packet.seq < 0) throw new Error("invalid relay sequence");
        if (typeof packet.payload !== "string" || packet.payload.length > 4096) throw new Error("invalid relay payload");
        const payload = Buffer.from(packet.payload, "base64");
        if (payload.toString("base64") !== packet.payload) throw new Error("invalid relay payload");
        const routed = registry.routePacket(sessionId, token, packet.seq, payload);
        const forwarded = Buffer.from(JSON.stringify({
          type: "data",
          sessionId,
          seq: packet.seq,
          payload: payload.toString("base64"),
        }), "utf8");
        if (forwarded.length > MAX_PACKET_BYTES) throw new Error("relay forwarded packet too large");
        socket.send(forwarded, routed.destination.port, routed.destination.address);
      }
    } catch (error) {
      reply = Buffer.from(JSON.stringify({
        ok: false,
        error: String(error?.message || "relay_error").slice(0, 128),
      }), "utf8");
    }

    if (reply && reply.length <= MAX_PACKET_BYTES) {
      socket.send(reply, rinfo.port, rinfo.address);
    }
  });

  return {
    registry,
    socket,
    listen() {
      return new Promise((resolve, reject) => {
        const onError = error => {
          socket.off("listening", onListening);
          reject(error);
        };
        const onListening = () => {
          socket.off("error", onError);
          resolve(socket.address());
        };
        socket.once("error", onError);
        socket.once("listening", onListening);
        socket.bind(port, host);
      });
    },
    close() {
      return new Promise(resolve => socket.close(() => resolve()));
    },
  };
}

export const meshRelayLimits = Object.freeze({
  maxSessions: MAX_SESSIONS,
  maxPacketBytes: MAX_PACKET_BYTES,
  maxPayloadBytes: MAX_PAYLOAD_BYTES,
  maxPacketsPerSecond: MAX_PACKETS_PER_SECOND,
  maxBytesPerSecond: MAX_BYTES_PER_SECOND,
  maxSessionPackets: MAX_SESSION_PACKETS,
  maxSessionBytes: MAX_SESSION_BYTES,
  maxSessionTtlMs: MAX_SESSION_TTL_MS,
});
