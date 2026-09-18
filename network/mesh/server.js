#!/usr/bin/env node
import http from "node:http";
import { fileURLToPath } from "node:url";
import { MeshControlPlane } from "./control-plane.js";
import { MeshStateStore } from "./state-store.js";
import { MeshTransportCoordinator } from "./transport-coordinator.js";
import { MeshNatProbeRegistry, createNatProbeServer } from "./nat-probe.js";
import { MeshPathNegotiator } from "./path-negotiator.js";
import { MeshRelayGrantBroker, MeshRelayRegistry, createMeshRelayServer } from "./relay.js";
import { MeshEnrollmentBroker } from "./enrollment-broker.js";

const MAX_BODY_BYTES = 256 * 1024;

function json(status, body) {
  return { status, headers: { "content-type": "application/json; charset=utf-8" }, body };
}

function bearer(headers) {
  const value = headers?.authorization || headers?.Authorization || "";
  return value.startsWith("Bearer ") ? value.slice(7) : "";
}

export async function handleMeshRequest({
  method,
  url,
  headers = {},
  body = null,
  controlPlane,
  adminToken,
  persist = null,
  transport = null,
  remoteAddress = null,
  natProbeRegistry = null,
  pathNegotiator = null,
  relayGrantBroker = null,
  relayEndpoint = null,
  enrollmentBroker = null,
}) {
  if (!(controlPlane instanceof MeshControlPlane)) throw new TypeError("controlPlane required");
  if (transport !== null && !(transport instanceof MeshTransportCoordinator)) throw new TypeError("transport invalid");
  if (pathNegotiator !== null && !(pathNegotiator instanceof MeshPathNegotiator)) throw new TypeError("pathNegotiator invalid");
  const parsed = new URL(url, "http://localhost");

  if (method === "GET" && parsed.pathname === "/health/live") {
    return json(200, { status: "ok", service: "sentinel-mesh-control-plane" });
  }

  if (method === "POST" && parsed.pathname === "/v1/enroll") {
    if (!(enrollmentBroker instanceof MeshEnrollmentBroker)) {
      return json(503, { error: "enrollment_not_configured" });
    }
    const node = controlPlane.getNode(body?.nodeId);
    if (!node || node.revoked) {
      return json(400, { error: "enrollment_rejected" });
    }
    const fingerprint = String(body?.publicKeyFingerprint || "").trim().toLowerCase();
    if (fingerprint !== node.publicKeyFingerprint) {
      return json(400, { error: "enrollment_rejected" });
    }
    const claim = enrollmentBroker.claim({
      nodeId: node.id,
      code: body?.code,
      publicKeyFingerprint: fingerprint,
    });
    if (!claim.accepted) {
      return json(400, { error: "enrollment_rejected", reason: claim.reason });
    }
    const credential = controlPlane.issueNodeCredential(node.id);
    if (persist) await persist(controlPlane.exportState());
    return json(201, credential);
  }

  if (method === "POST" && parsed.pathname === "/v1/node/transport/candidates") {
    if (!transport) return json(503, { error: "transport_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const announced = transport.announceNatCandidates({
      nodeId,
      endpoints: body?.endpoints || [],
      observedAddress: remoteAddress,
      wireGuardPort: body?.wireGuardPort,
      ttlMs: body?.ttlMs,
    });
    return json(200, announced);
  }

  if (method === "GET" && parsed.pathname === "/v1/node/peers") {
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    return json(200, { peers: controlPlane.discoverAuthorizedPeers(nodeId, "connect") });
  }

  if (method === "GET" && parsed.pathname === "/v1/node/nat-mapping") {
    if (!natProbeRegistry) return json(503, { error: "nat_probe_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const mapping = natProbeRegistry.get(nodeId);
    return mapping ? json(200, { mapping }) : json(404, { error: "nat_mapping_not_observed" });
  }

  if (method === "GET" && parsed.pathname === "/v1/node/transport/path") {
    if (!transport) return json(503, { error: "transport_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const targetNodeId = parsed.searchParams.get("target");
    const targetNode = controlPlane.getNode(targetNodeId);
    if (!targetNode || targetNode.revoked) return json(404, { error: "target_unknown_or_revoked" });
    const allowedPeers = controlPlane.discoverAuthorizedPeers(nodeId, "connect");
    if (!allowedPeers.some(peer => peer.id === targetNodeId)) {
      return json(403, { error: "policy_denied" });
    }
    const preferredRegion = parsed.searchParams.get("region");
    return json(200, transport.selectPath({
      sourceNodeId: nodeId,
      targetNodeId,
      preferredRegion,
    }));
  }

  if (method === "POST" && parsed.pathname === "/v1/node/negotiations") {
    if (!transport || !pathNegotiator) return json(503, { error: "negotiation_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const targetNodeId = String(body?.targetNodeId || "");
    const targetNode = controlPlane.getNode(targetNodeId);
    if (!targetNode || targetNode.revoked) return json(404, { error: "target_unknown_or_revoked" });
    const allowedPeers = controlPlane.discoverAuthorizedPeers(nodeId, "connect");
    if (!allowedPeers.some(peer => peer.id === targetNodeId)) {
      return json(403, { error: "policy_denied" });
    }
    const coordinated = transport.selectPath({
      sourceNodeId: nodeId,
      targetNodeId,
      preferredRegion: body?.preferredRegion || null,
    });
    const sourceMapping = natProbeRegistry?.get(nodeId);
    const targetMapping = natProbeRegistry?.get(targetNodeId);
    const sourceCandidates = [];
    const targetCandidates = [];
    if (sourceMapping) {
      sourceCandidates.push(sourceMapping.family === "IPv6"
        ? `[${sourceMapping.address}]:${sourceMapping.port}`
        : `${sourceMapping.address}:${sourceMapping.port}`);
    }
    if (targetMapping) {
      targetCandidates.push(targetMapping.family === "IPv6"
        ? `[${targetMapping.address}]:${targetMapping.port}`
        : `${targetMapping.address}:${targetMapping.port}`);
    }
    if (coordinated.mode === "direct") {
      targetCandidates.push(...coordinated.targetEndpoints);
    }
    const session = pathNegotiator.createSession({
      sourceNodeId: nodeId,
      targetNodeId,
      sourceCandidates,
      targetCandidates,
      relay: coordinated.relay || coordinated.fallbackRelay || null,
    });
    return json(201, session);
  }

  if (method === "POST" && parsed.pathname === "/v1/node/negotiations/direct-result") {
    if (!pathNegotiator) return json(503, { error: "negotiation_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const session = pathNegotiator.get(body?.sessionId);
    if (!session || session.sourceNodeId !== nodeId) return json(404, { error: "session_not_found" });
    const updated = pathNegotiator.recordDirectAttempt(body.sessionId, {
      endpoint: body?.endpoint,
      success: body?.success === true,
      latencyMs: body?.latencyMs ?? null,
      error: body?.error ?? null,
    });
    return json(200, updated);
  }

  if (method === "POST" && parsed.pathname === "/v1/node/negotiations/finalize") {
    if (!pathNegotiator) return json(503, { error: "negotiation_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const session = pathNegotiator.get(body?.sessionId);
    if (!session || session.sourceNodeId !== nodeId) return json(404, { error: "session_not_found" });
    const finalized = pathNegotiator.finalize(body.sessionId);
    if (finalized.state === "RELAY_REQUIRED") {
      if (!relayGrantBroker || !relayEndpoint) {
        return json(200, {
          ...finalized,
          relayDataPlane: "UNAVAILABLE",
        });
      }
      const grant = relayGrantBroker.ensureNegotiationGrant({
        negotiationId: finalized.id,
        sourceNodeId: finalized.sourceNodeId,
        targetNodeId: finalized.targetNodeId,
        relayEndpoint,
        ttlMs: Math.max(10000, Math.min(120000, finalized.expiresAt - Date.now())),
      });
      return json(200, {
        ...finalized,
        relayGrant: {
          negotiationId: grant.negotiationId,
          relaySessionId: grant.relaySessionId,
          relayEndpoint: grant.relayEndpoint,
          expiresAt: grant.expiresAt,
        },
      });
    }
    return json(200, finalized);
  }

  if (method === "POST" && parsed.pathname === "/v1/node/relay/claim") {
    if (!relayGrantBroker) return json(503, { error: "relay_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const grant = relayGrantBroker.claim({
      negotiationId: body?.negotiationId,
      nodeId,
    });
    return grant ? json(200, grant) : json(404, { error: "relay_grant_not_found" });
  }

  if (method === "POST" && parsed.pathname === "/v1/node/negotiations/keepalive") {
    if (!pathNegotiator) return json(503, { error: "negotiation_not_configured" });
    const nodeId = String(headers["x-sentinel-node-id"] || headers["X-Sentinel-Node-Id"] || "").trim();
    const token = bearer(headers);
    if (!controlPlane.authenticateNode(nodeId, token)) {
      return json(401, { error: "node_unauthorized" });
    }
    const session = pathNegotiator.get(body?.sessionId);
    if (!session || session.sourceNodeId !== nodeId) return json(404, { error: "session_not_found" });
    return json(200, pathNegotiator.keepAlive(body.sessionId));
  }

  const token = bearer(headers);
  if (!adminToken || token !== adminToken) {
    return json(401, { error: "unauthorized" });
  }

  try {
    if (method === "POST" && parsed.pathname === "/v1/nodes") {
      const enrolled = controlPlane.enrollNode(body);
      if (persist) await persist(controlPlane.exportState());
      return json(201, enrolled);
    }
    if (method === "POST" && parsed.pathname === "/v1/node-credentials") {
      const credential = controlPlane.issueNodeCredential(body?.nodeId);
      if (persist) await persist(controlPlane.exportState());
      return json(201, credential);
    }
    if (method === "POST" && parsed.pathname === "/v1/enrollment-invitations") {
      if (!(enrollmentBroker instanceof MeshEnrollmentBroker)) {
        return json(503, { error: "enrollment_not_configured" });
      }
      const node = controlPlane.getNode(body?.nodeId);
      if (!node || node.revoked) return json(404, { error: "node_unknown_or_revoked" });
      const invitation = enrollmentBroker.createInvitation({
        nodeId: node.id,
        publicKeyFingerprint: node.publicKeyFingerprint,
        ttlMs: body?.ttlMs,
      });
      return json(201, invitation);
    }
    if (method === "POST" && parsed.pathname === "/v1/enrollment-invitations/revoke") {
      if (!(enrollmentBroker instanceof MeshEnrollmentBroker)) {
        return json(503, { error: "enrollment_not_configured" });
      }
      const revoked = enrollmentBroker.revoke(body?.nodeId);
      return json(revoked ? 200 : 404, { revoked });
    }
    if (method === "POST" && parsed.pathname === "/v1/node-credentials/revoke") {
      const revoked = controlPlane.revokeNodeCredential(body?.nodeId);
      if (revoked && persist) await persist(controlPlane.exportState());
      return json(revoked ? 200 : 404, { revoked });
    }
    if (method === "POST" && parsed.pathname === "/v1/policies") {
      const count = controlPlane.replacePolicies(body?.rules);
      if (persist) await persist(controlPlane.exportState());
      return json(200, { count });
    }
    if (method === "POST" && parsed.pathname === "/v1/integrations") {
      const integration = controlPlane.registerIntegration(body);
      if (persist) await persist(controlPlane.exportState());
      return json(201, integration);
    }
    if (method === "POST" && parsed.pathname === "/v1/revoke") {
      const revoked = controlPlane.revokeNode(body?.nodeId, body?.reason);
      if (revoked && persist) await persist(controlPlane.exportState());
      return json(revoked ? 200 : 404, { revoked });
    }
    if (method === "GET" && parsed.pathname === "/v1/peers") {
      const nodeId = parsed.searchParams.get("node");
      const action = parsed.searchParams.get("action") || "connect";
      return json(200, { peers: controlPlane.discoverAuthorizedPeers(nodeId, action) });
    }
    if (method === "POST" && parsed.pathname === "/v1/transport/endpoints") {
      if (!transport) return json(503, { error: "transport_not_configured" });
      const node = controlPlane.getNode(body?.nodeId);
      if (!node || node.revoked) return json(403, { error: "node_unknown_or_revoked" });
      const announced = transport.announceNodeEndpoints({
        nodeId: node.id,
        endpoints: body?.endpoints,
        ttlMs: body?.ttlMs,
      });
      return json(200, announced);
    }
    if (method === "POST" && parsed.pathname === "/v1/transport/relays") {
      if (!transport) return json(503, { error: "transport_not_configured" });
      return json(201, transport.registerRelay(body));
    }
    if (method === "POST" && parsed.pathname === "/v1/transport/relay-status") {
      if (!transport) return json(503, { error: "transport_not_configured" });
      const updated = transport.setRelayStatus(body?.relayId, body?.status);
      return json(updated ? 200 : 404, { updated });
    }
    if (method === "GET" && parsed.pathname === "/v1/transport/path") {
      if (!transport) return json(503, { error: "transport_not_configured" });
      const sourceNodeId = parsed.searchParams.get("source");
      const targetNodeId = parsed.searchParams.get("target");
      const sourceNode = controlPlane.getNode(sourceNodeId);
      const targetNode = controlPlane.getNode(targetNodeId);
      if (!sourceNode || sourceNode.revoked || !targetNode || targetNode.revoked) {
        return json(403, { error: "node_unknown_or_revoked" });
      }
      const allowedPeers = controlPlane.discoverAuthorizedPeers(sourceNodeId, "connect");
      if (!allowedPeers.some(peer => peer.id === targetNodeId)) {
        return json(403, { error: "policy_denied" });
      }
      const preferredRegion = parsed.searchParams.get("region");
      return json(200, transport.selectPath({ sourceNodeId, targetNodeId, preferredRegion }));
    }
    if (method === "GET" && parsed.pathname === "/v1/transport/relays") {
      if (!transport) return json(503, { error: "transport_not_configured" });
      return json(200, { relays: transport.listRelays() });
    }
    if (method === "GET" && parsed.pathname === "/v1/integrations") {
      return json(200, { integrations: controlPlane.listIntegrations() });
    }
    if (method === "GET" && parsed.pathname === "/v1/audit") {
      const limit = Number.parseInt(parsed.searchParams.get("limit") || "100", 10);
      return json(200, { events: controlPlane.getAudit(Number.isFinite(limit) ? limit : 100) });
    }
    return json(404, { error: "not_found" });
  } catch (error) {
    return json(400, { error: String(error?.message || "invalid_request").slice(0, 512) });
  }
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error("request body too large");
    chunks.push(chunk);
  }
  if (!chunks.length) return null;
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

export function createMeshServer({ adminToken, controlPlane = new MeshControlPlane(), persist = null, transport = new MeshTransportCoordinator(), natProbeRegistry = null, pathNegotiator = new MeshPathNegotiator(), relayGrantBroker = null, relayEndpoint = null, enrollmentBroker = new MeshEnrollmentBroker() }) {
  if (!adminToken || adminToken.length < 24) {
    throw new Error("MESH_ADMIN_TOKEN must be at least 24 characters");
  }
  return http.createServer(async (req, res) => {
    try {
      const body = ["POST", "PUT", "PATCH"].includes(req.method) ? await readJson(req) : null;
      const result = await handleMeshRequest({
        method: req.method,
        url: req.url,
        headers: req.headers,
        body,
        controlPlane,
        adminToken,
        persist,
        transport,
        remoteAddress: req.socket?.remoteAddress || null,
        natProbeRegistry,
        pathNegotiator,
        relayGrantBroker,
        relayEndpoint,
        enrollmentBroker,
      });
      res.writeHead(result.status, result.headers);
      res.end(JSON.stringify(result.body));
    } catch (error) {
      res.writeHead(400, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify({ error: String(error?.message || "bad_request").slice(0, 512) }));
    }
  });
}

function isLoopbackHost(host) {
  return host === "127.0.0.1" || host === "::1" || host === "localhost";
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const host = process.env.MESH_BIND_HOST || "127.0.0.1";
  const port = Number.parseInt(process.env.MESH_PORT || "8788", 10);
  const adminToken = process.env.MESH_ADMIN_TOKEN || "";

  if (!isLoopbackHost(host) && process.env.MESH_ALLOW_REMOTE_BIND !== "true") {
    console.error("Refusing non-loopback bind without MESH_ALLOW_REMOTE_BIND=true");
    process.exit(1);
  }

  const controlPlane = new MeshControlPlane();
  const transport = new MeshTransportCoordinator();
  const natProbeRegistry = new MeshNatProbeRegistry();
  const pathNegotiator = new MeshPathNegotiator();
  const relayRegistry = new MeshRelayRegistry();
  const relayGrantBroker = new MeshRelayGrantBroker({ registry: relayRegistry });
  const enrollmentBroker = new MeshEnrollmentBroker();
  let relayEndpoint = null;
  let persist = null;
  const statePath = process.env.MESH_STATE_PATH || "";
  const stateSecret = process.env.MESH_STATE_SECRET || "";

  if (statePath) {
    if (!stateSecret) {
      console.error("MESH_STATE_SECRET is required when MESH_STATE_PATH is configured");
      process.exit(1);
    }
    const store = new MeshStateStore({ path: statePath, secret: stateSecret });
    try {
      const loaded = await store.load();
      controlPlane.restoreState(loaded.state);
      console.log(`Restored Sentinel Mesh state sequence ${loaded.sequence}`);
    } catch (error) {
      if (error?.code !== "ENOENT") {
        console.error(`Refusing to start with invalid persisted mesh state: ${error.message}`);
        process.exit(1);
      }
    }
    persist = state => store.save(state);
  }

  if (process.env.MESH_RELAY_ENABLED === "true") {
    const relayHost = process.env.MESH_RELAY_HOST || host;
    const relayPort = Number.parseInt(process.env.MESH_RELAY_PORT || "3480", 10);
    if (!isLoopbackHost(relayHost) && process.env.MESH_ALLOW_REMOTE_BIND !== "true") {
      console.error("Refusing non-loopback relay bind without MESH_ALLOW_REMOTE_BIND=true");
      process.exit(1);
    }
    const relayServer = createMeshRelayServer({
      registry: relayRegistry,
      host: relayHost,
      port: relayPort,
    });
    try {
      const bound = await relayServer.listen();
      relayEndpoint = process.env.MESH_RELAY_PUBLIC_ENDPOINT
        || `${bound.address.includes(":") ? `[${bound.address}]` : bound.address}:${bound.port}`;
      console.log(`Sentinel Mesh relay listening on ${bound.address}:${bound.port}/udp`);
    } catch (error) {
      console.error(`Failed to start Sentinel Mesh relay: ${error.message}`);
      process.exit(1);
    }
  }

  const server = createMeshServer({
    adminToken,
    controlPlane,
    persist,
    transport,
    natProbeRegistry,
    pathNegotiator,
    relayGrantBroker,
    relayEndpoint,
    enrollmentBroker,
  });
  server.listen(port, host, () => {
    console.log(`Sentinel Mesh control plane listening on http://${host}:${port}`);
  });

  if (process.env.MESH_NAT_PROBE_ENABLED === "true") {
    const probeHost = process.env.MESH_NAT_PROBE_HOST || host;
    const probePort = Number.parseInt(process.env.MESH_NAT_PROBE_PORT || "3479", 10);
    if (!isLoopbackHost(probeHost) && process.env.MESH_ALLOW_REMOTE_BIND !== "true") {
      console.error("Refusing non-loopback UDP probe bind without MESH_ALLOW_REMOTE_BIND=true");
      process.exit(1);
    }
    const natProbe = createNatProbeServer({
      controlPlane,
      registry: natProbeRegistry,
      host: probeHost,
      port: probePort,
    });
    try {
      const bound = await natProbe.listen();
      console.log(`Sentinel Mesh NAT probe listening on ${bound.address}:${bound.port}/udp`);
    } catch (error) {
      console.error(`Failed to start Sentinel Mesh NAT probe: ${error.message}`);
      process.exit(1);
    }
  }
}
