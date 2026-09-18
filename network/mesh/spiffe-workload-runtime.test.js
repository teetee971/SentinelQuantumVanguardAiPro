import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";
import { SpiffeWorkloadBundleSync } from "./spiffe-workload-runtime.js";
import { CA } from "./x509-svid-test-fixtures.js";

class FakeTransport {
  constructor(messages) {
    this.messages = messages;
  }

  endpointConfig() {
    return {
      scheme: "unix",
      address: "/tmp/spire-agent.sock",
      endpoint: "unix:///tmp/spire-agent.sock",
      metadata: { "workload.spiffe.io": "true" },
    };
  }

  async *fetchX509Bundles() {
    for (const message of this.messages) yield message;
  }
}

test("runtime sync requires durable persistence", () => {
  assert.throws(() => new SpiffeWorkloadBundleSync({
    controlPlane: new MeshControlPlane(),
    persist: null,
    transport: new FakeTransport([]),
    env: {},
  }), /requires durable persistence/);
});

test("runtime sync persists bundle updates then fails closed if stream ends", async () => {
  const cp = new MeshControlPlane();
  const snapshots = [];
  const sync = new SpiffeWorkloadBundleSync({
    controlPlane: cp,
    persist: async state => snapshots.push(state),
    transport: new FakeTransport([{
      bundles: [{
        trustDomain: "prod.example.test",
        anchorsPem: [CA],
      }],
    }]),
    env: {},
  });

  await assert.rejects(() => sync.run(), /stream ended unexpectedly/);
  assert.equal(cp.getSpiffeTrustBundle("prod.example.test").sequence, 1);
  assert.equal(snapshots.length, 1);
  assert.equal(snapshots[0].spiffeTrustBundle.bundles[0].sequence, 1);
});

test("runtime sync exits cleanly when explicitly aborted", async () => {
  const cp = new MeshControlPlane();
  const controller = new AbortController();
  controller.abort();

  const sync = new SpiffeWorkloadBundleSync({
    controlPlane: cp,
    persist: async () => {},
    transport: new FakeTransport([]),
    env: {},
  });

  const result = await sync.run({ signal: controller.signal });
  assert.deepEqual(result, { messages: 0, changedBundles: 0 });
});
