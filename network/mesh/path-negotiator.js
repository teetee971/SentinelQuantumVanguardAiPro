const MAX_SESSIONS = 10000;
const MAX_CANDIDATES = 16;
const DEFAULT_SESSION_TTL_MS = 120000;
const MAX_SESSION_TTL_MS = 10 * 60 * 1000;

function id(value, name) {
  const s = String(value || "").trim();
  if (!/^[a-zA-Z0-9:_./-]{2,256}$/.test(s)) throw new Error(`invalid ${name}`);
  return s;
}

function endpoint(value) {
  const s = String(value || "").trim();
  if (!/^([a-z0-9.-]+|\[[0-9a-fA-F:]+\]):([1-9][0-9]{0,4})$/.test(s)) {
    throw new Error("invalid candidate endpoint");
  }
  const port = Number(s.slice(s.lastIndexOf(":") + 1));
  if (port > 65535) throw new Error("invalid candidate endpoint");
  return s;
}

export class MeshPathNegotiator {
  #sessions = new Map();
  #clock;

  constructor({ clock = () => Date.now() } = {}) {
    this.#clock = clock;
  }

  createSession({
    sourceNodeId,
    targetNodeId,
    sourceCandidates = [],
    targetCandidates = [],
    relay = null,
    ttlMs = DEFAULT_SESSION_TTL_MS,
  }) {
    if (this.#sessions.size >= MAX_SESSIONS) throw new Error("session capacity exceeded");
    const source = id(sourceNodeId, "source node id");
    const target = id(targetNodeId, "target node id");
    if (source === target) throw new Error("source and target must differ");
    if (!Array.isArray(sourceCandidates) || !Array.isArray(targetCandidates)) {
      throw new Error("candidate arrays required");
    }
    if (sourceCandidates.length > MAX_CANDIDATES || targetCandidates.length > MAX_CANDIDATES) {
      throw new Error("too many candidates");
    }
    if (!Number.isInteger(ttlMs) || ttlMs < 10000 || ttlMs > MAX_SESSION_TTL_MS) {
      throw new Error("invalid session ttl");
    }

    const now = this.#clock();
    const sessionId = `${source}->${target}:${now}:${this.#sessions.size + 1}`;
    const session = {
      id: sessionId,
      sourceNodeId: source,
      targetNodeId: target,
      sourceCandidates: [...new Set(sourceCandidates.map(endpoint))],
      targetCandidates: [...new Set(targetCandidates.map(endpoint))],
      relay: relay ? structuredClone(relay) : null,
      state: "NEGOTIATING",
      directAttempts: [],
      selectedPath: null,
      createdAt: now,
      expiresAt: now + ttlMs,
      lastUpdatedAt: now,
    };
    this.#sessions.set(sessionId, session);
    return structuredClone(session);
  }

  recordDirectAttempt(sessionId, { endpoint, success, latencyMs = null, error = null }) {
    const session = this.#getActive(sessionId);
    if (session.state !== "NEGOTIATING") throw new Error("session not negotiating");
    if (session.directAttempts.length >= MAX_CANDIDATES * 2) throw new Error("too many direct attempts");
    const candidate = endpoint ? endpoint : null;
    if (!candidate || !session.targetCandidates.includes(candidate)) {
      throw new Error("candidate not authorized for session");
    }
    if (latencyMs !== null && (!Number.isFinite(latencyMs) || latencyMs < 0 || latencyMs > 60000)) {
      throw new Error("invalid latency");
    }
    const attempt = {
      endpoint: candidate,
      success: success === true,
      latencyMs: success === true ? latencyMs : null,
      error: success === true ? null : String(error || "direct_failed").slice(0, 256),
      at: this.#clock(),
    };
    session.directAttempts.push(attempt);
    session.lastUpdatedAt = attempt.at;

    if (attempt.success) {
      session.state = "DIRECT_ESTABLISHED";
      session.selectedPath = {
        mode: "direct",
        endpoint: candidate,
        relay: null,
        latencyMs: attempt.latencyMs,
      };
    }
    return structuredClone(session);
  }

  finalize(sessionId) {
    const session = this.#getActive(sessionId);
    if (session.state === "DIRECT_ESTABLISHED") return structuredClone(session);

    const attempted = new Set(session.directAttempts.map(a => a.endpoint));
    const remaining = session.targetCandidates.filter(c => !attempted.has(c));
    if (remaining.length) {
      return {
        ...structuredClone(session),
        state: "NEGOTIATING",
        remainingCandidates: remaining,
      };
    }

    if (session.relay && session.relay.status === "available") {
      session.state = "RELAY_REQUIRED";
      session.selectedPath = {
        mode: "relay",
        endpoint: session.relay.endpoint,
        relay: structuredClone(session.relay),
        latencyMs: null,
      };
    } else {
      session.state = "UNAVAILABLE";
      session.selectedPath = null;
    }
    session.lastUpdatedAt = this.#clock();
    return structuredClone(session);
  }

  keepAlive(sessionId) {
    const session = this.#getActive(sessionId);
    session.lastUpdatedAt = this.#clock();
    return {
      id: session.id,
      state: session.state,
      lastUpdatedAt: session.lastUpdatedAt,
      expiresAt: session.expiresAt,
    };
  }

  get(sessionId) {
    try {
      return structuredClone(this.#getActive(sessionId));
    } catch {
      return null;
    }
  }

  pruneExpired() {
    const now = this.#clock();
    let removed = 0;
    for (const [sessionId, session] of this.#sessions.entries()) {
      if (session.expiresAt <= now) {
        this.#sessions.delete(sessionId);
        removed += 1;
      }
    }
    return removed;
  }

  #getActive(sessionId) {
    const key = String(sessionId || "");
    const session = this.#sessions.get(key);
    if (!session) throw new Error("session not found");
    if (session.expiresAt <= this.#clock()) {
      this.#sessions.delete(key);
      throw new Error("session expired");
    }
    return session;
  }
}

export const meshPathNegotiationLimits = Object.freeze({
  maxSessions: MAX_SESSIONS,
  maxCandidates: MAX_CANDIDATES,
  maxSessionTtlMs: MAX_SESSION_TTL_MS,
});
