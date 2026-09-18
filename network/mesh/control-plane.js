import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { isIP } from "node:net";
import { evaluateAccess, normalizeResource, normalizeSubject } from "./policy-engine.js";
import { validateIntegrationManifest } from "./integration-registry.js";
import { SpiffeTrustBundleManager } from "./spiffe-trust-bundle.js";

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

function canonicalIp(raw) {
  const value = String(raw || "").trim();
  const family = isIP(value);
  if (!family) throw new Error("invalid mesh IP address");
  const hostname = family === 6
    ? new URL(`http://[${value}]/`).hostname.slice(1, -1).toLowerCase()
    : new URL(`http://${value}/`).hostname;
  return { family, address: hostname };
}

function assertSafeMeshHost({ family, address }) {
  if (family === 4) {
    const octets = address.split(".").map(Number);
    const [a, b, c, d] = octets;
    if (
      a === 0 ||
      a === 127 ||
      (a === 169 && b === 254) ||
      a >= 224 ||
      (a === 255 && b === 255 && c === 255 && d === 255)
    ) {
      throw new Error("unsafe mesh IP address");
    }
    return;
  }

  const normalized = address.toLowerCase();
  const firstHextet = Number.parseInt(normalized.split(":")[0] || "0", 16);
  const isLinkLocal = firstHextet >= 0xfe80 && firstHextet <= 0xfebf;
  if (
    normalized === "::" ||
    normalized === "::1" ||
    normalized.startsWith("::ffff:") ||
    isLinkLocal ||
    normalized.startsWith("ff")
  ) {
    throw new Error("unsafe mesh IP address");
  }
}

function normalizeMeshAddresses(values) {
  if (values === undefined || values === null) return [];
  if (!Array.isArray(values) || values.length > 4) throw new Error("invalid mesh address set");
  const normalized = values.map(raw => {
    const value = String(raw || "").trim();
    const slash = value.lastIndexOf("/");
    if (slash <= 0) throw new Error("mesh address must be host CIDR");
    const parsed = canonicalIp(value.slice(0, slash));
    assertSafeMeshHost(parsed);
    const prefix = Number(value.slice(slash + 1));
    if ((parsed.family === 4 && prefix !== 32) || (parsed.family === 6 && prefix !== 128)) {
      throw new Error("mesh address must use /32 IPv4 or /128 IPv6");
    }
    return `${parsed.address}/${prefix}`;
  });
  return [...new Set(normalized)];
}

function assertMeshAddressesUnique(nodes, addresses, exceptNodeId = null) {
  const requested = new Set(addresses);
  for (const node of nodes.values()) {
    if (exceptNodeId && node.id === exceptNodeId) continue;
    for (const address of node.meshAddresses || []) {
      if (requested.has(address)) throw new Error("mesh address already assigned");
    }
  }
}

export class MeshControlPlane {
  #nodes = new Map();
  #policies = [];
  #integrations = new Map();
  #nodeCredentialHashes = new Map();
  #audit = [];
  #clock;
  #spiffeTrustBundle;

  constructor({ clock = () => Date.now(), spiffeTrustBundle = new SpiffeTrustBundleManager() } = {}) {
    if (!(spiffeTrustBundle instanceof SpiffeTrustBundleManager)) {
      throw new TypeError("spiffeTrustBundle invalid");
    }
    this.#clock = clock;
    this.#spiffeTrustBundle = spiffeTrustBundle;
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
    const meshAddresses = normalizeMeshAddresses(input.meshAddresses);
    assertMeshAddressesUnique(this.#nodes, meshAddresses);

    const node = {
      id,
      subject,
      publicKey,
      publicKeyFingerprint: fingerprint(publicKey),
      meshAddresses,
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
    this.#nodeCredentialHashes.delete(id);
    this.#record("NODE_REVOKED", id, { reason: String(reason).slice(0, 256) });
    return true;
  }

  issueNodeCredential(nodeId) {
    const id = requireId(nodeId, "node id");
    const node = this.#nodes.get(id);
    if (!node || node.revoked) throw new Error("node unknown or revoked");
    const token = randomBytes(32).toString("base64url");
    const tokenHash = createHash("sha256").update(token, "utf8").digest("hex");
    this.#nodeCredentialHashes.set(id, tokenHash);
    this.#record("NODE_CREDENTIAL_ISSUED", id, { tokenHashFingerprint: tokenHash.slice(0, 16) });
    return { nodeId: id, token };
  }

  authenticateNode(nodeId, token) {
    const id = requireId(nodeId, "node id");
    const node = this.#nodes.get(id);
    if (!node || node.revoked || typeof token !== "string" || token.length < 32 || token.length > 128) return false;
    const expectedHex = this.#nodeCredentialHashes.get(id);
    if (!expectedHex) return false;
    const actual = createHash("sha256").update(token, "utf8").digest();
    const expected = Buffer.from(expectedHex, "hex");
    return expected.length === actual.length && timingSafeEqual(expected, actual);
  }

  revokeNodeCredential(nodeId) {
    const id = requireId(nodeId, "node id");
    const removed = this.#nodeCredentialHashes.delete(id);
    if (removed) this.#record("NODE_CREDENTIAL_REVOKED", id, {});
    return removed;
  }

  setNodeMeshAddresses(nodeId, meshAddresses) {
    const id = requireId(nodeId, "node id");
    const node = this.#nodes.get(id);
    if (!node || node.revoked) throw new Error("node unknown or revoked");
    const normalized = normalizeMeshAddresses(meshAddresses);
    assertMeshAddressesUnique(this.#nodes, normalized, id);
    node.meshAddresses = normalized;
    this.#record("NODE_MESH_ADDRESSES_SET", id, { meshAddresses: normalized });
    return immutableClone(node);
  }

  installSpiffeTrustBundle(bundle) {
    const installed = this.#spiffeTrustBundle.install(bundle);
    this.#record("SPIFFE_TRUST_BUNDLE_INSTALLED", installed.trustDomain, {
      trustDomain: installed.trustDomain,
      sequence: installed.sequence,
      digest: installed.digest,
      anchorCount: installed.anchorsPem.length,
    });
    return immutableClone(installed);
  }

  observeSpiffeTrustBundle(bundle) {
    const result = this.#spiffeTrustBundle.observe(bundle);
    if (result.changed) {
      this.#record("SPIFFE_TRUST_BUNDLE_OBSERVED", result.bundle.trustDomain, {
        trustDomain: result.bundle.trustDomain,
        sequence: result.bundle.sequence,
        digest: result.bundle.digest,
        anchorCount: result.bundle.anchorsPem.length,
      });
    }
    return immutableClone(result);
  }

  observeSpiffeTrustBundleSet(bundles) {
    const result = this.#spiffeTrustBundle.observeSet(bundles);
    for (const trustDomain of result.changedDomains) {
      const current = this.#spiffeTrustBundle.current(trustDomain);
      this.#record("SPIFFE_TRUST_BUNDLE_OBSERVED", trustDomain, {
        trustDomain,
        sequence: current.sequence,
        digest: current.digest,
        anchorCount: current.anchorsPem.length,
      });
    }
    for (const trustDomain of result.removedDomains) {
      this.#record("SPIFFE_TRUST_BUNDLE_REDACTED", trustDomain, {
        trustDomain,
      });
    }
    return immutableClone(result);
  }

  getSpiffeTrustBundle(trustDomain) {
    return immutableClone(this.#spiffeTrustBundle.current(trustDomain));
  }

  listSpiffeTrustBundles() {
    return immutableClone(this.#spiffeTrustBundle.listMetadata());
  }

  getSpiffeVerifierConfig(trustDomain) {
    return immutableClone(this.#spiffeTrustBundle.verifierConfig(trustDomain));
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
          meshAddresses: [...(target.meshAddresses || [])],
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

  exportState() {
    return immutableClone({
      schemaVersion: 1,
      nodes: [...this.#nodes.values()],
      policies: this.#policies,
      integrations: [...this.#integrations.values()],
      nodeCredentialHashes: [...this.#nodeCredentialHashes.entries()].map(([nodeId, tokenHash]) => ({ nodeId, tokenHash })),
      spiffeTrustBundle: this.#spiffeTrustBundle.exportState(),
      audit: this.#audit,
    });
  }

  restoreState(state) {
    if (!state || typeof state !== "object" || state.schemaVersion !== 1) {
      throw new Error("unsupported mesh state schema");
    }
    if (!Array.isArray(state.nodes) || state.nodes.length > MAX_NODES) {
      throw new Error("invalid node snapshot");
    }
    if (!Array.isArray(state.policies) || state.policies.length > MAX_POLICIES) {
      throw new Error("invalid policy snapshot");
    }
    if (!Array.isArray(state.integrations) || state.integrations.length > 1000) {
      throw new Error("invalid integration snapshot");
    }
    if (state.nodeCredentialHashes !== undefined && (!Array.isArray(state.nodeCredentialHashes) || state.nodeCredentialHashes.length > MAX_NODES)) {
      throw new Error("invalid node credential snapshot");
    }
    if (state.spiffeTrustBundle !== undefined && (!state.spiffeTrustBundle || typeof state.spiffeTrustBundle !== "object")) {
      throw new Error("invalid SPIFFE trust bundle snapshot");
    }
    if (!Array.isArray(state.audit) || state.audit.length > MAX_AUDIT) {
      throw new Error("invalid audit snapshot");
    }

    const nextNodes = new Map();
    for (const raw of state.nodes) {
      if (!raw || typeof raw !== "object") throw new Error("invalid node entry");
      if ("privateKey" in raw || "private_key" in raw) throw new Error("private key found in snapshot");
      const id = requireId(raw.id, "node id");
      const publicKey = normalizeWireGuardPublicKey(raw.publicKey);
      const subject = normalizeSubject(raw.subject);
      const meshAddresses = normalizeMeshAddresses(raw.meshAddresses || []);
      assertMeshAddressesUnique(nextNodes, meshAddresses);
      const resources = Array.isArray(raw.resources) ? raw.resources.slice(0, 32).map(normalizeResource) : [];
      const restored = {
        id,
        subject,
        publicKey,
        publicKeyFingerprint: fingerprint(publicKey),
        meshAddresses,
        endpointHints: Array.isArray(raw.endpointHints)
          ? raw.endpointHints.map(v => String(v).trim()).filter(Boolean).slice(0, 8)
          : [],
        resources,
        revoked: raw.revoked === true,
        enrolledAt: Number.isFinite(raw.enrolledAt) ? raw.enrolledAt : 0,
        revokedAt: Number.isFinite(raw.revokedAt) ? raw.revokedAt : null,
      };
      if (raw.publicKeyFingerprint && raw.publicKeyFingerprint !== restored.publicKeyFingerprint) {
        throw new Error("public key fingerprint mismatch");
      }
      if (nextNodes.has(id)) throw new Error("duplicate node in snapshot");
      nextNodes.set(id, restored);
    }

    const nextPolicies = state.policies.map((rule, i) => {
      if (!rule || typeof rule !== "object") throw new Error(`invalid policy ${i}`);
      const id = requireId(rule.id || `rule-${i}`, "rule id");
      if (!["allow", "deny"].includes(rule.effect)) throw new Error(`rule ${id} invalid effect`);
      return immutableClone({ ...rule, id });
    });

    const nextIntegrations = new Map();
    for (const manifest of state.integrations) {
      validateIntegrationManifest(manifest);
      nextIntegrations.set(manifest.id, immutableClone(manifest));
    }

    const nextNodeCredentialHashes = new Map();
    for (const raw of state.nodeCredentialHashes || []) {
      const nodeId = requireId(raw?.nodeId, "node credential id");
      if (!nextNodes.has(nodeId) || nextNodes.get(nodeId).revoked) throw new Error("credential bound to unknown or revoked node");
      const tokenHash = String(raw?.tokenHash || "");
      if (!/^[a-f0-9]{64}$/.test(tokenHash)) throw new Error("invalid node credential hash");
      if (nextNodeCredentialHashes.has(nodeId)) throw new Error("duplicate node credential");
      nextNodeCredentialHashes.set(nodeId, tokenHash);
    }

    const nextAudit = state.audit.map((event, i) => {
      if (!event || typeof event !== "object") throw new Error(`invalid audit event ${i}`);
      if (!Number.isInteger(event.sequence) || event.sequence < 1) throw new Error("invalid audit sequence");
      return immutableClone(event);
    });
    for (let i = 1; i < nextAudit.length; i += 1) {
      if (nextAudit[i].sequence !== nextAudit[i - 1].sequence + 1) {
        throw new Error("non-contiguous audit sequence");
      }
    }

    if (state.spiffeTrustBundle !== undefined) {
      this.#spiffeTrustBundle.restoreState(state.spiffeTrustBundle);
    }

    this.#nodes = nextNodes;
    this.#policies = nextPolicies;
    this.#integrations = nextIntegrations;
    this.#nodeCredentialHashes = nextNodeCredentialHashes;
    this.#audit = nextAudit;
    this.#record("STATE_RESTORED", "control-plane", {
      nodes: nextNodes.size,
      policies: nextPolicies.length,
      integrations: nextIntegrations.size,
      nodeCredentials: nextNodeCredentialHashes.size,
      spiffeTrustDomains: this.#spiffeTrustBundle.listMetadata().length,
    });
    return true;
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
