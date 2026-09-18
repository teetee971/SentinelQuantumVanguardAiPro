import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";
import { SpiffeWorkloadBundleSync } from "./spiffe-workload-runtime.js";
import { SpiffeWorkloadGrpcError } from "./spiffe-workload-grpc.js";
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
  assert.deepEqual(result, { messages: 0, changedBundles: 0, retries: 0 });
});


class SequenceTransport {
  constructor(steps) {
    this.steps = [...steps];
    this.calls = 0;
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
    this.calls += 1;
    const step = this.steps.shift();
    if (step instanceof Error) throw step;
    for (const message of step || []) yield message;
  }
}

test("runtime retries bounded UNAVAILABLE failures with exponential backoff", async () => {
  const cp = new MeshControlPlane();
  const delays = [];
  const controller = new AbortController();

  const transport = new SequenceTransport([
    new SpiffeWorkloadGrpcError("unavailable", { grpcStatus: 14, retryable: true }),
    [{
      bundles: [{ trustDomain: "prod.example.test", anchorsPem: [CA] }],
    }],
  ]);

  const sync = new SpiffeWorkloadBundleSync({
    controlPlane: cp,
    persist: async () => {},
    transport,
    env: {},
    sleep: async ms => delays.push(ms),
  });

  await assert.rejects(
    () => sync.run({ maxRetries: 2, baseDelayMs: 25, maxDelayMs: 100 }),
    /stream ended unexpectedly/
  );
  assert.equal(transport.calls, 2);
  assert.deepEqual(delays, [25]);
  assert.equal(cp.getSpiffeTrustBundle("prod.example.test").sequence, 1);
});

test("runtime stops retrying after retry budget is exhausted", async () => {
  const cp = new MeshControlPlane();
  const delays = [];
  const transient = () => new SpiffeWorkloadGrpcError("unavailable", {
    grpcStatus: 14,
    retryable: true,
  });
  const transport = new SequenceTransport([transient(), transient(), transient()]);

  const sync = new SpiffeWorkloadBundleSync({
    controlPlane: cp,
    persist: async () => {},
    transport,
    env: {},
    sleep: async ms => delays.push(ms),
  });

  await assert.rejects(
    () => sync.run({ maxRetries: 2, baseDelayMs: 10, maxDelayMs: 15 }),
    /unavailable/
  );
  assert.equal(transport.calls, 3);
  assert.deepEqual(delays, [10, 15]);
});

test("runtime never retries PermissionDenied or other terminal gRPC failures", async () => {
  const cp = new MeshControlPlane();
  const delays = [];
  const transport = new SequenceTransport([
    new SpiffeWorkloadGrpcError("denied", {
      grpcStatus: 7,
      retryable: false,
    }),
  ]);

  const sync = new SpiffeWorkloadBundleSync({
    controlPlane: cp,
    persist: async () => {},
    transport,
    env: {},
    sleep: async ms => delays.push(ms),
  });

  await assert.rejects(
    () => sync.run({ maxRetries: 5 }),
    /denied/
  );
  assert.equal(transport.calls, 1);
  assert.deepEqual(delays, []);
});
