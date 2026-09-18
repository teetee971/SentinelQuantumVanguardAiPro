import test from "node:test";
import assert from "node:assert/strict";
import dgram from "node:dgram";
import { MeshRelayGrantBroker, MeshRelayRegistry, createMeshRelayServer } from "./relay.js";

function udpClient() {
  const socket = dgram.createSocket("udp4");
  const messages = [];
  socket.on("message", msg => messages.push(JSON.parse(msg.toString("utf8"))));
  return { socket, messages };
}

function bind(socket) {
  return new Promise((resolve, reject) => {
    socket.once("error", reject);
    socket.bind(0, "127.0.0.1", () => {
      socket.off("error", reject);
      resolve(socket.address());
    });
  });
}

function send(socket, port, payload) {
  return new Promise((resolve, reject) => {
    socket.send(Buffer.from(JSON.stringify(payload)), port, "127.0.0.1", error => error ? reject(error) : resolve());
  });
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

test("relay forwards only between the two registered session peers", async () => {
  const registry = new MeshRelayRegistry();
  const issued = registry.createSession({ sourceNodeId: "device:a", targetNodeId: "device:b" });
  const relay = createMeshRelayServer({ registry, host: "127.0.0.1", port: 0 });
  const relayAddress = await relay.listen();
  const source = udpClient();
  const target = udpClient();
  await bind(source.socket);
  await bind(target.socket);

  try {
    await send(source.socket, relayAddress.port, {
      type: "register", sessionId: issued.sessionId, token: issued.source.token,
    });
    await send(target.socket, relayAddress.port, {
      type: "register", sessionId: issued.sessionId, token: issued.target.token,
    });
    await sleep(25);

    const payload = Buffer.from("wireguard-ciphertext-placeholder").toString("base64");
    await send(source.socket, relayAddress.port, {
      type: "data", sessionId: issued.sessionId, token: issued.source.token, seq: 0, payload,
    });
    await sleep(25);

    const forwarded = target.messages.find(m => m.type === "data");
    assert.ok(forwarded);
    assert.equal(forwarded.sessionId, issued.sessionId);
    assert.equal(Buffer.from(forwarded.payload, "base64").toString("utf8"), "wireguard-ciphertext-placeholder");
  } finally {
    source.socket.close();
    target.socket.close();
    await relay.close();
  }
});

test("relay token cannot impersonate the other peer or arbitrary session", () => {
  const registry = new MeshRelayRegistry({ clock: () => 1000 });
  const one = registry.createSession({ sourceNodeId: "device:a", targetNodeId: "device:b" });
  const two = registry.createSession({ sourceNodeId: "device:c", targetNodeId: "device:d" });
  assert.equal(registry.authenticate(one.sessionId, one.source.token).role, "source");
  assert.equal(registry.authenticate(one.sessionId, one.target.token).role, "target");
  assert.equal(registry.authenticate(two.sessionId, one.source.token), null);
});

test("relay rejects replayed sequence numbers", () => {
  const registry = new MeshRelayRegistry({ clock: () => 1000 });
  const issued = registry.createSession({ sourceNodeId: "device:a", targetNodeId: "device:b" });
  registry.registerPeer(issued.sessionId, issued.source.token, { address: "127.0.0.1", port: 10001, family: "IPv4" });
  registry.registerPeer(issued.sessionId, issued.target.token, { address: "127.0.0.1", port: 10002, family: "IPv4" });
  registry.routePacket(issued.sessionId, issued.source.token, 0, Buffer.from("abc"));
  assert.throws(() => registry.routePacket(issued.sessionId, issued.source.token, 0, Buffer.from("abc")), /replay/);
});

test("relay refuses data before destination peer registers", () => {
  const registry = new MeshRelayRegistry({ clock: () => 1000 });
  const issued = registry.createSession({ sourceNodeId: "device:a", targetNodeId: "device:b" });
  registry.registerPeer(issued.sessionId, issued.source.token, { address: "127.0.0.1", port: 10001, family: "IPv4" });
  assert.throws(
    () => registry.routePacket(issued.sessionId, issued.source.token, 0, Buffer.from("abc")),
    /peer not registered/
  );
});

test("relay sessions expire fail-closed", () => {
  let now = 1000;
  const registry = new MeshRelayRegistry({ clock: () => now });
  const issued = registry.createSession({ sourceNodeId: "device:a", targetNodeId: "device:b", ttlMs: 10000 });
  assert.ok(registry.describe(issued.sessionId));
  now = 11001;
  assert.equal(registry.describe(issued.sessionId), null);
});


test("relay grant broker gives each node only its own one-time credential", () => {
  const registry = new MeshRelayRegistry({ clock: () => 1000 });
  const broker = new MeshRelayGrantBroker({ registry });
  const metadata = broker.ensureNegotiationGrant({
    negotiationId: "device:a->device:b:1000:1",
    sourceNodeId: "device:a",
    targetNodeId: "device:b",
    relayEndpoint: "192.0.2.50:3480",
    ttlMs: 120000,
  });

  assert.equal(metadata.sourceNodeId, "device:a");
  assert.equal(metadata.targetNodeId, "device:b");
  assert.equal("token" in metadata, false);

  const source = broker.claim({ negotiationId: metadata.negotiationId, nodeId: "device:a" });
  const target = broker.claim({ negotiationId: metadata.negotiationId, nodeId: "device:b" });
  assert.equal(source.role, "source");
  assert.equal(target.role, "target");
  assert.notEqual(source.token, target.token);
  assert.equal(registry.authenticate(metadata.relaySessionId, source.token).role, "source");
  assert.equal(registry.authenticate(metadata.relaySessionId, target.token).role, "target");

  assert.equal(broker.claim({ negotiationId: metadata.negotiationId, nodeId: "device:a" }), null);
  assert.equal(broker.claim({ negotiationId: metadata.negotiationId, nodeId: "device:other" }), null);
});
