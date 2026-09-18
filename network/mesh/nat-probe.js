import dgram from "node:dgram";

const MAX_PACKET_BYTES = 1024;
const MAX_NODE_ID = 256;
const MAX_TOKEN = 128;
const MAX_TTL_MS = 5 * 60 * 1000;

function validNodeId(value) {
  const id = String(value || "").trim();
  if (!/^[a-zA-Z0-9:_./-]{2,256}$/.test(id)) throw new Error("invalid node id");
  return id;
}

function parseMessage(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0 || buffer.length > MAX_PACKET_BYTES) {
    throw new Error("invalid probe packet");
  }
  const parsed = JSON.parse(buffer.toString("utf8"));
  if (!parsed || typeof parsed !== "object") throw new Error("invalid probe payload");
  const nodeId = validNodeId(parsed.nodeId);
  const token = String(parsed.token || "");
  if (token.length < 32 || token.length > MAX_TOKEN) throw new Error("invalid probe token");
  const nonce = String(parsed.nonce || "");
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(nonce)) throw new Error("invalid probe nonce");
  return { nodeId, token, nonce };
}

export class MeshNatProbeRegistry {
  #entries = new Map();
  #clock;

  constructor({ clock = () => Date.now() } = {}) {
    this.#clock = clock;
  }

  record({ nodeId, address, port, family, ttlMs = 120000 }) {
    const id = validNodeId(nodeId);
    if (typeof address !== "string" || !address.trim()) throw new Error("invalid observed address");
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("invalid observed port");
    if (!["IPv4","IPv6"].includes(family)) throw new Error("invalid address family");
    if (!Number.isInteger(ttlMs) || ttlMs < 10000 || ttlMs > MAX_TTL_MS) throw new Error("invalid probe ttl");
    const value = {
      nodeId: id,
      address: address.replace(/^::ffff:/, ""),
      port,
      family,
      observedAt: this.#clock(),
      expiresAt: this.#clock() + ttlMs,
    };
    this.#entries.set(id, value);
    return structuredClone(value);
  }

  get(nodeId) {
    const id = validNodeId(nodeId);
    const value = this.#entries.get(id);
    if (!value) return null;
    if (value.expiresAt <= this.#clock()) {
      this.#entries.delete(id);
      return null;
    }
    return structuredClone(value);
  }

  pruneExpired() {
    const now = this.#clock();
    let removed = 0;
    for (const [id, value] of this.#entries.entries()) {
      if (value.expiresAt <= now) {
        this.#entries.delete(id);
        removed += 1;
      }
    }
    return removed;
  }
}

export function verifyNodeToken(controlPlane, nodeId, token) {
  return controlPlane.authenticateNode(nodeId, token) === true;
}

export function createNatProbeServer({
  controlPlane,
  registry = new MeshNatProbeRegistry(),
  host = "127.0.0.1",
  port = 3479,
}) {
  if (!controlPlane || typeof controlPlane.authenticateNode !== "function") {
    throw new Error("controlPlane with authenticateNode required");
  }
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error("invalid UDP port");
  const socket = dgram.createSocket(host.includes(":") ? "udp6" : "udp4");

  socket.on("message", (msg, rinfo) => {
    let response;
    try {
      const { nodeId, token, nonce } = parseMessage(msg);
      if (!verifyNodeToken(controlPlane, nodeId, token)) {
        response = { ok: false, error: "node_unauthorized", nonce };
      } else {
        const observed = registry.record({
          nodeId,
          address: rinfo.address,
          port: rinfo.port,
          family: rinfo.family,
        });
        response = {
          ok: true,
          nodeId,
          nonce,
          observed: {
            address: observed.address,
            port: observed.port,
            family: observed.family,
            expiresAt: observed.expiresAt,
          },
        };
      }
    } catch (error) {
      response = { ok: false, error: String(error?.message || "invalid_probe").slice(0, 128) };
    }
    const payload = Buffer.from(JSON.stringify(response), "utf8");
    if (payload.length <= MAX_PACKET_BYTES) {
      socket.send(payload, rinfo.port, rinfo.address);
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

export const meshNatProbeLimits = Object.freeze({
  maxPacketBytes: MAX_PACKET_BYTES,
  maxTtlMs: MAX_TTL_MS,
});
