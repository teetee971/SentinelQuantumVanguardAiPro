#!/usr/bin/env node
import http from "node:http";
import { fileURLToPath } from "node:url";
import { MeshControlPlane } from "./control-plane.js";

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
}) {
  if (!(controlPlane instanceof MeshControlPlane)) throw new TypeError("controlPlane required");
  const parsed = new URL(url, "http://localhost");

  if (method === "GET" && parsed.pathname === "/health/live") {
    return json(200, { status: "ok", service: "sentinel-mesh-control-plane" });
  }

  const token = bearer(headers);
  if (!adminToken || token !== adminToken) {
    return json(401, { error: "unauthorized" });
  }

  try {
    if (method === "POST" && parsed.pathname === "/v1/nodes") {
      return json(201, controlPlane.enrollNode(body));
    }
    if (method === "POST" && parsed.pathname === "/v1/policies") {
      const count = controlPlane.replacePolicies(body?.rules);
      return json(200, { count });
    }
    if (method === "POST" && parsed.pathname === "/v1/integrations") {
      return json(201, controlPlane.registerIntegration(body));
    }
    if (method === "POST" && parsed.pathname === "/v1/revoke") {
      const revoked = controlPlane.revokeNode(body?.nodeId, body?.reason);
      return json(revoked ? 200 : 404, { revoked });
    }
    if (method === "GET" && parsed.pathname === "/v1/peers") {
      const nodeId = parsed.searchParams.get("node");
      const action = parsed.searchParams.get("action") || "connect";
      return json(200, { peers: controlPlane.discoverAuthorizedPeers(nodeId, action) });
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

export function createMeshServer({ adminToken, controlPlane = new MeshControlPlane() }) {
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

  const server = createMeshServer({ adminToken });
  server.listen(port, host, () => {
    console.log(`Sentinel Mesh control plane listening on http://${host}:${port}`);
  });
}
