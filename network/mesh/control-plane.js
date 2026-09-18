import { createHash } from "node:crypto";
import { evaluateAccess, normalizeResource, normalizeSubject } from "./policy-engine.js";
import { validateIntegrationManifest } from "./integration-registry.js";

const MAX_NODES = 10_000;
const MAX_POLICIES = 1_000;
const MAX_AUDIT = 20_000;

function requireId(value, name) {
  const id = String(value || "").trim();
  if (!/^[a-zA-Z0-9:_./-]{2,256}$/.test(id)) throw new Error(`invalid ${name}`);
  return id;
}

function normalizeWireGuardPublicKey(value) {
  const key = String(value || "").trim();
  const decoded = Buffer.from(key, "base64");
  if (decoded.length !== 32 || decoded.toString("base64") !== key) {
    throw new Error("invalid WireGuard public key");
  }
  return key;
}

function fingerprint(key) {
  return createHash("sha256").update(key, "utf8").digest("hex");
}

function immutableClone(value) {
  return JSON.parse(JSON.stringify(value));
}

export class MeshControlPlane {
  #nodes = new Map();
  #policies = [];
  #integrations = new Map();
  #audit = [];
  #clock;

  constructor({ clock = () => Date.now() } = {}) {
    this.#clock = clock;
  }

  enrollNode(input) {
    if (this.#nodes.size >= MAX_NODES) throw new Error("node capacity exceeded");
    if (!input || typeof input !== "object") throw new TypeError("node input required");
    if ("privateKey" in input || "private_key" in input) {
      throw new Error("private keys are forbidden in control plane enrollment");
    }

    const id = requireId(input.id, "node id");
    if (this.#nodes.has(id)) throw new Error("node already enrolled");

    const subject = normalizeSubject({
      type: input.type,
      id,
      groups: input.groups || [],
      tags: input.tags || [],
      deviceTrust: input.deviceTrust || "unknown",
    });
    const publicKey = normalizeWireGuardPublicKey(input.publicKey);

    const node = {
      id,
      subject,
      publicKey,
      publicKeyFingerprint: fingerprint(publicKey),
      endpointHints: Array.isArray(input.endpointHints)
        ? input.endpointHints.map(v => String(v).trim()).filter(Boolean).slice(0, 8)
        : [],
      resources: Array.isArray(input.resources)
        ? input.resources.slice(0, 32).map(normalizeResource)
        : [],
      revoked: false,
      enrolledAt: this.#clock(),
      revokedAt: null,
    };

    this.#nodes.set(id, node);
    this.#record("NODE_ENROLLED", id, { keyFingerprint: node.publicKeyFingerprint });
    return immutableClone(node);
  }

  revokeNode(nodeId, reason = "ADMIN_REVOKE") {
    const id = requireId(nodeId, "node id");
    const node = this.#nodes.get(id);
    if (!node) return false;
    if (node.revoked) return true;
    node.revoked = true;
    node.revokedAt = this.#clock();
    this.#record("NODE_REVOKED", id, { reason: String(reason).slice(0, 256) });
    return true;
  }

  registerIntegration(manifest) {
    validateIntegrationManifest(manifest);
    const id = String(manifest.id).trim();
    const normalized = immutableClone({
      id,
      category: String(manifest.category || "generic").slice(0, 128),
      protocol: manifest.protocol,
      status: manifest.status,
    });
    this.#integrations.set(id, normalized);
    this.#record("INTEGRATION_REGISTERED", id, {
      protocol: normalized.protocol,
      status: normalized.status,
    });
    return immutableClone(normalized);
  }

  listIntegrations() {
    return immutableClone([...this.#integrations.values()]);
  }

  replacePolicies(rules) {
    if (!Array.isArray(rules)) throw new TypeError("rules must be an array");
    if (rules.length > MAX_POLICIES) throw new Error("policy capacity exceeded");

    const validated = rules.map((rule, i) => {
      if (!rule || typeof rule !== "object") throw new TypeError(`rule ${i} invalid`);
      const id = requireId(rule.id || `rule-${i}`, "rule id");
      if (!["allow", "deny"].includes(rule.effect)) throw new Error(`rule ${id} invalid effect`);
      return immutableClone({ ...rule, id });
    });

    this.#policies = validated;
    this.#record("POLICY_SET_REPLACED", "control-plane", { count: validated.length });
    return validated.length;
  }

  discoverAuthorizedPeers(requestingNodeId, action = "connect") {
    const id = requireId(requestingNodeId, "node id");
    const requester = this.#nodes.get(id);
    if (!requester || requester.revoked) {
      this.#record("PEER_DISCOVERY_DENIED", id, { reason: "REQUESTER_UNKNOWN_OR_REVOKED" });
      return [];
    }

    const peers = [];
    for (const target of this.#nodes.values()) {
      if (target.id === requester.id || target.revoked) continue;

      const targetResources = target.resources.length
        ? target.resources
        : [{ id: `node:${target.id}`, tags: target.subject.tags, environment: "unknown" }];

      let allowed = false;
      for (const resource of targetResources) {
        const decision = evaluateAccess({
          subject: requester.subject,
          resource,
          action,
          rules: this.#policies,
        });
        if (decision.allowed) {
          allowed = true;
          break;
        }
      }

      if (allowed) {
        peers.push({
          id: target.id,
          publicKey: target.publicKey,
          publicKeyFingerprint: target.publicKeyFingerprint,
          endpointHints: [...target.endpointHints],
        });
      }
    }

    this.#record("PEER_DISCOVERY", id, { action, peerCount: peers.length });
    return immutableClone(peers);
  }

  getNode(nodeId) {
    const node = this.#nodes.get(requireId(nodeId, "node id"));
    return node ? immutableClone(node) : null;
  }

  getAudit(limit = 100) {
    const n = Number.isInteger(limit) ? Math.min(Math.max(limit, 1), 1000) : 100;
    return immutableClone(this.#audit.slice(-n));
  }

  #record(type, subjectId, details) {
    const event = {
      sequence: this.#audit.length ? this.#audit[this.#audit.length - 1].sequence + 1 : 1,
      timestamp: this.#clock(),
      type,
      subjectId,
      details: immutableClone(details),
    };
    this.#audit.push(event);
    if (this.#audit.length > MAX_AUDIT) this.#audit.shift();
  }
}

export const meshControlPlaneLimits = Object.freeze({
  maxNodes: MAX_NODES,
  maxPolicies: MAX_POLICIES,
  maxAuditEvents: MAX_AUDIT,
});
