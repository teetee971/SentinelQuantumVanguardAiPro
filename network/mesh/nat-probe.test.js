import test from "node:test";
import assert from "node:assert/strict";
import dgram from "node:dgram";
import { MeshControlPlane } from "./control-plane.js";
import { MeshNatProbeRegistry, createNatProbeServer } from "./nat-probe.js";

const KEY = Buffer.alloc(32, 12).toString("base64");

function sendUdp({ host, port, payload }) {
  return new Promise((resolve, reject) => {
    const socket = dgram.createSocket(host.includes(":") ? "udp6" : "udp4");
    const timer = setTimeout(() => {
      socket.close();
      reject(new Error("udp timeout"));
    }, 2000);
    socket.on("message", msg => {
      clearTimeout(timer);
      socket.close();
      resolve(JSON.parse(msg.toString("utf8")));
    });
    const data = Buffer.from(JSON.stringify(payload), "utf8");
    socket.send(data, port, host);
  });
}

test("UDP probe records the real source mapping for an authenticated node", async () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:udp", type: "device", publicKey: KEY });
  const credential = cp.issueNodeCredential("device:udp");

  const probe = createNatProbeServer({
    controlPlane: cp,
    host: "127.0.0.1",
    port: 0,
  });
  const address = await probe.listen();
  try {
    const response = await sendUdp({
      host: "127.0.0.1",
      port: address.port,
      payload: {
        nodeId: "device:udp",
        token: credential.token,
        nonce: "nonce_12345678",
      },
    });
    assert.equal(response.ok, true);
    assert.equal(response.nodeId, "device:udp");
    assert.equal(response.observed.address, "127.0.0.1");
    assert.ok(response.observed.port > 0);
    const stored = probe.registry.get("device:udp");
    assert.equal(stored.port, response.observed.port);
  } finally {
    await probe.close();
  }
});

test("UDP probe rejects token impersonation", async () => {
  const cp = new MeshControlPlane();
  cp.enrollNode({ id: "device:a", type: "device", publicKey: KEY });
  cp.enrollNode({
    id: "device:b",
    type: "device",
    publicKey: Buffer.alloc(32, 13).toString("base64"),
  });
  const credential = cp.issueNodeCredential("device:a");

  const probe = createNatProbeServer({ controlPlane: cp, host: "127.0.0.1", port: 0 });
  const address = await probe.listen();
  try {
    const response = await sendUdp({
      host: "127.0.0.1",
      port: address.port,
      payload: {
        nodeId: "device:b",
        token: credential.token,
        nonce: "nonce_abcdef12",
      },
    });
    assert.equal(response.ok, false);
    assert.equal(response.error, "node_unauthorized");
  } finally {
    await probe.close();
  }
});

test("probe registry expires stale mappings", () => {
  let now = 1000;
  const registry = new MeshNatProbeRegistry({ clock: () => now });
  registry.record({
    nodeId: "device:ttl",
    address: "198.51.100.1",
    port: 51820,
    family: "IPv4",
    ttlMs: 10000,
  });
  assert.ok(registry.get("device:ttl"));
  now = 11001;
  assert.equal(registry.get("device:ttl"), null);
});
