import test from "node:test";
import assert from "node:assert/strict";
import { MeshTransportCoordinator } from "./transport-coordinator.js";

test("prefers direct path when both endpoint advertisements are fresh", () => {
  let now = 1000;
  const c = new MeshTransportCoordinator({ clock: () => now });
  c.announceNodeEndpoints({ nodeId: "device:a", endpoints: ["198.51.100.10:51820"] });
  c.announceNodeEndpoints({ nodeId: "device:b", endpoints: ["203.0.113.20:51820"] });
  const path = c.selectPath({ sourceNodeId: "device:a", targetNodeId: "device:b" });
  assert.equal(path.mode, "direct");
  assert.deepEqual(path.targetEndpoints, ["203.0.113.20:51820"]);
});

test("uses available relay when direct endpoint data is missing or stale", () => {
  let now = 1000;
  const c = new MeshTransportCoordinator({ clock: () => now });
  c.registerRelay({ id: "relay-fr-1", region: "fr", endpoint: "192.0.2.10:3478" });
  c.announceNodeEndpoints({ nodeId: "device:a", endpoints: ["198.51.100.10:51820"], ttlMs: 10000 });
  c.announceNodeEndpoints({ nodeId: "device:b", endpoints: ["203.0.113.20:51820"], ttlMs: 10000 });
  now += 11000;
  const path = c.selectPath({ sourceNodeId: "device:a", targetNodeId: "device:b", preferredRegion: "fr" });
  assert.equal(path.mode, "relay");
  assert.equal(path.relay.id, "relay-fr-1");
});

test("returns unavailable when no direct path and no relay exists", () => {
  const c = new MeshTransportCoordinator();
  const path = c.selectPath({ sourceNodeId: "a", targetNodeId: "b" });
  assert.equal(path.mode, "unavailable");
});

test("offline and draining relays are never selected", () => {
  const c = new MeshTransportCoordinator();
  c.registerRelay({ id: "relay-off", region: "fr", endpoint: "192.0.2.11:3478", status: "offline" });
  c.registerRelay({ id: "relay-drain", region: "fr", endpoint: "192.0.2.12:3478", status: "draining" });
  assert.equal(c.selectPath({ sourceNodeId: "a", targetNodeId: "b" }).mode, "unavailable");
});

test("endpoint advertisements are bounded and validated", () => {
  const c = new MeshTransportCoordinator();
  assert.throws(() => c.announceNodeEndpoints({ nodeId: "device:a", endpoints: ["not-an-endpoint"] }));
  assert.throws(() => c.announceNodeEndpoints({ nodeId: "device:a", endpoints: Array(9).fill("198.51.100.1:51820") }));
});


test("NAT candidates combine node candidates with server-observed source address", () => {
  const c = new MeshTransportCoordinator({ clock: () => 1000 });
  const result = c.announceNatCandidates({
    nodeId: "device:nat",
    endpoints: ["10.0.0.2:51820"],
    observedAddress: "198.51.100.44",
    wireGuardPort: 51820,
    ttlMs: 120000,
  });
  assert.deepEqual(result.endpoints, ["10.0.0.2:51820", "198.51.100.44:51820"]);
});

test("IPv6 observed candidates are bracketed safely", () => {
  const c = new MeshTransportCoordinator({ clock: () => 1000 });
  const result = c.announceNatCandidates({
    nodeId: "device:v6",
    observedAddress: "2001:db8::4",
    wireGuardPort: 51820,
  });
  assert.deepEqual(result.endpoints, ["[2001:db8::4]:51820"]);
});
