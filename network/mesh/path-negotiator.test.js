import test from "node:test";
import assert from "node:assert/strict";
import { MeshPathNegotiator } from "./path-negotiator.js";

test("establishes a direct path after a successful authorized candidate attempt", () => {
  let now = 1000;
  const n = new MeshPathNegotiator({ clock: () => now++ });
  const s = n.createSession({
    sourceNodeId: "device:a",
    targetNodeId: "device:b",
    sourceCandidates: ["198.51.100.1:51820"],
    targetCandidates: ["203.0.113.1:51820"],
  });
  const after = n.recordDirectAttempt(s.id, {
    endpoint: "203.0.113.1:51820",
    success: true,
    latencyMs: 23,
  });
  assert.equal(after.state, "DIRECT_ESTABLISHED");
  assert.equal(after.selectedPath.mode, "direct");
});

test("falls back to relay only after all direct candidates failed", () => {
  const n = new MeshPathNegotiator({ clock: () => 1000 });
  const s = n.createSession({
    sourceNodeId: "device:a",
    targetNodeId: "device:b",
    targetCandidates: ["203.0.113.1:51820","203.0.113.2:51820"],
    relay: { id: "relay-fr-1", endpoint: "192.0.2.10:3478", status: "available" },
  });
  n.recordDirectAttempt(s.id, { endpoint: "203.0.113.1:51820", success: false, error: "timeout" });
  let state = n.finalize(s.id);
  assert.equal(state.state, "NEGOTIATING");
  assert.deepEqual(state.remainingCandidates, ["203.0.113.2:51820"]);

  n.recordDirectAttempt(s.id, { endpoint: "203.0.113.2:51820", success: false, error: "timeout" });
  state = n.finalize(s.id);
  assert.equal(state.state, "RELAY_REQUIRED");
  assert.equal(state.selectedPath.mode, "relay");
});

test("returns unavailable after direct failure when no relay exists", () => {
  const n = new MeshPathNegotiator({ clock: () => 1000 });
  const s = n.createSession({
    sourceNodeId: "aa",
    targetNodeId: "bb",
    targetCandidates: ["203.0.113.1:51820"],
  });
  n.recordDirectAttempt(s.id, { endpoint: "203.0.113.1:51820", success: false });
  assert.equal(n.finalize(s.id).state, "UNAVAILABLE");
});

test("rejects attempts against endpoints not authorized in the session", () => {
  const n = new MeshPathNegotiator({ clock: () => 1000 });
  const s = n.createSession({
    sourceNodeId: "aa",
    targetNodeId: "bb",
    targetCandidates: ["203.0.113.1:51820"],
  });
  assert.throws(() => n.recordDirectAttempt(s.id, {
    endpoint: "203.0.113.99:51820",
    success: true,
  }), /not authorized/);
});

test("expired sessions are fail-closed", () => {
  let now = 1000;
  const n = new MeshPathNegotiator({ clock: () => now });
  const s = n.createSession({
    sourceNodeId: "aa",
    targetNodeId: "bb",
    targetCandidates: ["203.0.113.1:51820"],
    ttlMs: 10000,
  });
  now = 11001;
  assert.equal(n.get(s.id), null);
});
