#!/usr/bin/env node
import http from "node:http";
import { createHash, timingSafeEqual } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnGatewayPeerRuntime } from "./peer-runtime.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
  VpnLeaseSequenceAuthority,
  assertVpnLeaseSequenceAuthority,
} from "./lease-sequence-authority.js";
import { persistVpnLeaseState } from "./trusted-state-persist.js";

export { RedisVpnLeaseSequenceAuthority } from "./redis-lease-sequence-authority.js";

const MAX_BODY_BYTES = 8 * 1024;
const ADMIN_TOKEN = /^[A-Za-z0-9._~-]{32,2048}$/;

function json(status, body) {
  return {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
    body,
  };
}

function bearer(headers) {
  const value = headers?.authorization || headers?.Authorization || "";
  return typeof value === "string" && value.startsWith("Bearer ") ? value.slice(7) : "";
}

function tokenDigest(value) {
  if (typeof value !== "string" || !ADMIN_TOKEN.test(value)) return null;
  return createHash("sha256").update(value, "utf8").digest();
}

function constantTimeTokenMatch(candidate, expectedDigest) {
  const digest = tokenDigest(candidate);
  return digest !== null &&
    digest.length === expectedDigest.length &&
    timingSafeEqual(digest, expectedDigest);
}

function validateLeasePersistenceConfiguration(stateStore, sequenceAuthority) {
  if (stateStore !== null && !(stateStore instanceof VpnLeaseStateStore)) {
    throw new TypeError("VpnLeaseStateStore invalid");
  }
  if (sequenceAuthority !== null && !(sequenceAuthority instanceof VpnLeaseSequenceAuthority)) {
    throw new TypeError("VpnLeaseSequenceAuthority invalid");
  }
  if ((stateStore === null) !== (sequenceAuthority === null)) {
    throw new TypeError("VPN_LEASE_PERSISTENCE_REQUIRES_STORE_AND_SEQUENCE_AUTHORITY");
  }
  if (sequenceAuthority !== null) assertVpnLeaseSequenceAuthority(sequenceAuthority);
}

async function persistCurrentLeaseState({ core, stateStore, sequenceAuthority }) {
  if (stateStore === null) return null;
  return persistVpnLeaseState({ core, stateStore, sequenceAuthority });
}

function revocationInvalidationDigest(gatewayId, sequence, snapshotDigest) {
  return createHash("sha256")
    .update("sentinel-vpn-revocation-invalidation:v1\0", "utf8")
    .update(gatewayId, "utf8")
    .update("\0", "utf8")
    .update(String(sequence), "utf8")
    .update("\0", "utf8")
    .update(snapshotDigest, "utf8")
    .digest("hex");
}

/**
 * A destructive runtime revocation must never leave an older active snapshot trusted after restart.
 *
 * - If local snapshot and authority already differ, exact-digest restore fails closed naturally.
 * - If they match and the local state already equals the revoked in-memory state, persistence
 *   actually reached a safe durable state despite the reported failure.
 * - Otherwise invalidate only that exact committed digest, at the same sequence. The next valid
 *   local save can then advance to N+1 without colliding with an artificial sequence tombstone.
 */
async function invalidateStaleRestoreAfterRevocationFailure({
  core,
  stateStore,
  sequenceAuthority,
}) {
  if (stateStore === null || sequenceAuthority === null) return false;

  let local;
  let authorityCommit;
  try {
    local = await stateStore.loadWithReceipt();
    authorityCommit = await sequenceAuthority.readCommit(core.gatewayId);
  } catch {
    return false;
  }

  if (
    local.sequence !== authorityCommit.sequence ||
    local.snapshotDigest !== authorityCommit.snapshotDigest
  ) {
    return true;
  }

  if (isDeepStrictEqual(local.state, core.exportState())) {
    return true;
  }

  const invalidationDigest = revocationInvalidationDigest(
    core.gatewayId,
    local.sequence,
    local.snapshotDigest
  );

  try {
    await sequenceAuthority.invalidateSnapshot(
      core.gatewayId,
      local.sequence,
      local.snapshotDigest,
      invalidationDigest
    );
    const confirmed = await sequenceAuthority.readCommit(core.gatewayId);
    return confirmed.sequence === local.sequence &&
      confirmed.snapshotDigest === invalidationDigest;
  } catch {
    try {
      const latest = await sequenceAuthority.readCommit(core.gatewayId);
      return latest.sequence !== local.sequence ||
        latest.snapshotDigest !== local.snapshotDigest;
    } catch {
      return false;
    }
  }
}

export async function handleVpnProvisioningRequest({
  method,
  url,
  headers = {},
  body = null,
  core,
  adminTokenDigest = null,
  peerRuntime = null,
  stateStore = null,
  sequenceAuthority = null,
}) {
  if (!(core instanceof VpnGatewayProvisioningCore)) {
    throw new TypeError("VpnGatewayProvisioningCore required");
  }
  if (peerRuntime !== null && !(peerRuntime instanceof VpnGatewayPeerRuntime)) {
    throw new TypeError("VpnGatewayPeerRuntime invalid");
  }
  validateLeasePersistenceConfiguration(stateStore, sequenceAuthority);
  const parsed = new URL(url, "http://localhost");

  if (method === "GET" && parsed.pathname === "/health/live") {
    return json(200, {
      status: "ok",
      service: "sentinel-vpn-provisioning",
      gatewayId: core.gatewayId,
    });
  }

  if (method === "POST" && parsed.pathname === "/v1/provision") {
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return json(400, { error: "invalid_request" });
    }
    const allowed = new Set(["gatewayId", "devicePublicKey", "catalogSequence"]);
    if (Object.keys(body).some(key => !allowed.has(key)) || Object.keys(body).length !== allowed.size) {
      return json(400, { error: "invalid_request_schema" });
    }

    const result = core.provision({
      gatewayId: body.gatewayId,
      devicePublicKey: body.devicePublicKey,
      catalogSequence: body.catalogSequence,
      accessToken: bearer(headers),
    });

    if (!result.accepted) {
      const unauthorized = result.reason === "VPN_PROVISIONING_UNAUTHORIZED";
      const capacity = result.reason === "VPN_PROVISIONING_CAPACITY_EXHAUSTED" ||
        result.reason === "VPN_PROVISIONING_ADDRESS_EXHAUSTED";
      return json(
        unauthorized ? 401 : capacity ? 503 : 400,
        { error: result.reason }
      );
    }
    if (!peerRuntime) {
      core.revoke(body.devicePublicKey);
      return json(503, { error: "VPN_PEER_RUNTIME_NOT_CONFIGURED" });
    }
    const applied = await peerRuntime.apply({
      devicePublicKey: result.response.devicePublicKey,
      clientAddresses: result.response.clientAddresses,
    });
    if (!applied.accepted) {
      core.revoke(body.devicePublicKey);
      return json(503, { error: applied.reason });
    }

    if (stateStore) {
      try {
        await persistCurrentLeaseState({ core, stateStore, sequenceAuthority });
      } catch {
        const removed = await peerRuntime.remove(body.devicePublicKey);
        if (removed.accepted) {
          core.revoke(body.devicePublicKey);
          try {
            await persistCurrentLeaseState({ core, stateStore, sequenceAuthority });
          } catch {
            // Request already fails. Exact sequence+digest restore rejects uncommitted snapshots.
          }
        }
        return json(503, {
          error: removed.accepted
            ? "VPN_LEASE_STATE_PERSIST_FAILED"
            : "VPN_PEER_ROLLBACK_FAILED",
        });
      }
    }
    return json(result.reason === "VPN_PROVISIONING_CREATED" ? 201 : 200, result.response);
  }

  if (method === "POST" && parsed.pathname === "/v1/admin/revoke") {
    if (!adminTokenDigest) return json(503, { error: "admin_not_configured" });
    if (!constantTimeTokenMatch(bearer(headers), adminTokenDigest)) {
      return json(401, { error: "admin_unauthorized" });
    }
    if (!body || typeof body !== "object" || Array.isArray(body) ||
        Object.keys(body).length !== 1 || typeof body.devicePublicKey !== "string") {
      return json(400, { error: "invalid_request" });
    }
    if (!peerRuntime) return json(503, { error: "VPN_PEER_RUNTIME_NOT_CONFIGURED" });
    const removed = await peerRuntime.remove(body.devicePublicKey);
    if (!removed.accepted) {
      return json(503, { error: removed.reason });
    }
    const revoked = core.revoke(body.devicePublicKey);
    if (revoked && stateStore) {
      try {
        await persistCurrentLeaseState({ core, stateStore, sequenceAuthority });
      } catch {
        await invalidateStaleRestoreAfterRevocationFailure({
          core,
          stateStore,
          sequenceAuthority,
        });
        return json(503, { error: "VPN_LEASE_STATE_PERSIST_FAILED" });
      }
    }
    return revoked
      ? json(200, { revoked: true })
      : json(404, { error: "lease_not_found_or_already_revoked" });
  }

  return json(404, { error: "not_found" });
}

async function readJson(req) {
  const contentType = String(req.headers["content-type"] || "").toLowerCase();
  if (!contentType.startsWith("application/json")) {
    const error = new Error("content_type_invalid");
    error.statusCode = 415;
    throw error;
  }

  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > MAX_BODY_BYTES) {
      const error = new Error("body_too_large");
      error.statusCode = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  if (total === 0) return null;
  const text = Buffer.concat(chunks).toString("utf8");
  try {
    return JSON.parse(text);
  } catch {
    const error = new Error("json_invalid");
    error.statusCode = 400;
    throw error;
  }
}

export function createVpnProvisioningServer({
  core,
  adminToken = null,
  peerRuntime,
  stateStore = null,
  sequenceAuthority = null,
}) {
  if (!(core instanceof VpnGatewayProvisioningCore)) {
    throw new TypeError("VpnGatewayProvisioningCore required");
  }
  if (!(peerRuntime instanceof VpnGatewayPeerRuntime)) {
    throw new TypeError("VpnGatewayPeerRuntime required");
  }
  validateLeasePersistenceConfiguration(stateStore, sequenceAuthority);
  const adminTokenDigest = adminToken === null ? null : tokenDigest(adminToken);
  if (adminToken !== null && !adminTokenDigest) {
    throw new Error("VPN_PROVISIONING_ADMIN_TOKEN_INVALID");
  }

  return http.createServer(async (req, res) => {
    try {
      const needsBody = req.method === "POST";
      const body = needsBody ? await readJson(req) : null;
      const result = await handleVpnProvisioningRequest({
        method: req.method,
        url: req.url || "/",
        headers: req.headers,
        body,
        core,
        adminTokenDigest,
        peerRuntime,
        stateStore,
        sequenceAuthority,
      });
      res.writeHead(result.status, result.headers);
      res.end(JSON.stringify(result.body));
    } catch (error) {
      const status = Number.isInteger(error?.statusCode) ? error.statusCode : 500;
      res.writeHead(status, {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
      });
      res.end(JSON.stringify({
        error: status >= 500 ? "internal_error" : error.message,
      }));
    }
  });
}

export const vpnProvisioningServerInternals = Object.freeze({
  tokenDigest,
  constantTimeTokenMatch,
  validateLeasePersistenceConfiguration,
  persistCurrentLeaseState,
  revocationInvalidationDigest,
  invalidateStaleRestoreAfterRevocationFailure,
});
