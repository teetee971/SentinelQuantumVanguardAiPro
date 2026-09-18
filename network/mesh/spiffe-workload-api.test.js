import test from "node:test";
import assert from "node:assert/strict";
import { MeshControlPlane } from "./control-plane.js";
import { CA, CA_DNS_EXTRA } from "./x509-svid-test-fixtures.js";
import { parseSpiffeEndpoint, SpiffeWorkloadBundleIngestor } from "./spiffe-workload-api.js";

test("parses strict Unix SPIFFE endpoint and injects mandatory anti-SSRF metadata", () => {
  const parsed = parseSpiffeEndpoint({ endpoint: "unix:///tmp/spire-agent.sock" });
  assert.equal(parsed.scheme, "unix");
  assert.equal(parsed.address, "/tmp/spire-agent.sock");
  assert.deepEqual(parsed.metadata, { "workload.spiffe.io": "true" });

  assert.throws(
    () => parseSpiffeEndpoint({ endpoint: "unix://host/tmp/agent.sock" }),
    /authority forbidden/
  );
  assert.throws(
    () => parseSpiffeEndpoint({ endpoint: "unix:///tmp/agent.sock?x=1" }),
    /forbidden components/
  );
  assert.throws(
    () => parseSpiffeEndpoint({ endpoint: "unix:///tmp/%2e%2e/agent.sock" }),
    /percent-encoding forbidden/
  );
  assert.throws(
    () => parseSpiffeEndpoint({ endpoint: "unix:///tmp/agent%00.sock" }),
    /percent-encoding forbidden/
  );
});

test("TCP endpoint is limited to literal loopback until strong external authentication exists", () => {
  const parsed = parseSpiffeEndpoint({
    endpoint: "tcp://127.0.0.1:8000",
  });
  assert.equal(parsed.scheme, "tcp");
  assert.equal(parsed.address, "127.0.0.1");
  assert.equal(parsed.port, 8000);
  assert.equal(parsed.networkAuthentication, "loopback");

  const ipv6 = parseSpiffeEndpoint({
    endpoint: "tcp://[::1]:8001",
  });
  assert.equal(ipv6.address, "::1");
  assert.equal(ipv6.port, 8001);
  assert.equal(ipv6.networkAuthentication, "loopback");

  assert.throws(
    () => parseSpiffeEndpoint({
      endpoint: "tcp://localhost:8000",
    }),
    /host must be an IP/
  );
  assert.throws(
    () => parseSpiffeEndpoint({
      endpoint: "tcp://192.0.2.10:8000",
    }),
    /externally verified strong network authentication/
  );
  assert.throws(
    () => parseSpiffeEndpoint({
      endpoint: "tcp://127.0.0.1:8000/path",
    }),
    /path forbidden/
  );
});

test("falls back to SPIFFE_ENDPOINT_SOCKET environment value", () => {
  const parsed = parseSpiffeEndpoint({
    env: { SPIFFE_ENDPOINT_SOCKET: "unix:///run/spire/sockets/agent.sock" },
  });
  assert.equal(parsed.address, "/run/spire/sockets/agent.sock");
});

test("ingests normalized bundle stream updates with local monotonic sequencing", async () => {
  const cp = new MeshControlPlane({ clock: () => 1000 });
  const persisted = [];
  let streamArgs = null;

  const ingestor = new SpiffeWorkloadBundleIngestor({
    controlPlane: cp,
    endpoint: "unix:///tmp/spire-agent.sock",
    persist: async state => persisted.push(state),
    streamFactory: async args => {
      streamArgs = args;
      return (async function* () {
        yield {
          bundles: [{
            trustDomain: "prod.example.test",
            anchorsPem: [CA],
          }],
        };
        yield {
          bundles: [{
            trustDomain: "prod.example.test",
            anchorsPem: [CA],
          }],
        };
        yield {
          bundles: [{
            trustDomain: "prod.example.test",
            anchorsPem: [CA, CA_DNS_EXTRA],
          }],
        };
      })();
    },
  });

  const result = await ingestor.consume();
  assert.deepEqual(result, { messages: 3, changedBundles: 2 });
  assert.deepEqual(streamArgs.metadata, { "workload.spiffe.io": "true" });
  assert.equal(streamArgs.endpoint.scheme, "unix");
  assert.equal(cp.getSpiffeTrustBundle("prod.example.test").sequence, 2);
  assert.equal(persisted.length, 2);
});

test("ingestor rejects malformed or unbounded streams", async () => {
  const cp = new MeshControlPlane();

  const malformed = new SpiffeWorkloadBundleIngestor({
    controlPlane: cp,
    endpoint: "unix:///tmp/spire-agent.sock",
    streamFactory: async () => (async function* () {
      yield { bundles: [] };
    })(),
  });
  await assert.rejects(() => malformed.consume(), /stream count invalid/);

  const tooMany = new SpiffeWorkloadBundleIngestor({
    controlPlane: cp,
    endpoint: "unix:///tmp/spire-agent.sock",
    streamFactory: async () => (async function* () {
      yield { bundles: [{ trustDomain: "prod.example.test", anchorsPem: [CA] }] };
      yield { bundles: [{ trustDomain: "prod.example.test", anchorsPem: [CA] }] };
    })(),
  });
  await assert.rejects(() => tooMany.consume({ maxMessages: 1 }), /message limit exceeded/);
});


test("complete Workload API snapshots redact missing trust domains", async () => {
  const cp = new MeshControlPlane({ clock: () => 2000 });
  const persisted = [];

  const ingestor = new SpiffeWorkloadBundleIngestor({
    controlPlane: cp,
    endpoint: "unix:///tmp/spire-agent.sock",
    persist: async state => persisted.push(state),
    streamFactory: async () => (async function* () {
      yield {
        bundles: [
          { trustDomain: "prod.example.test", anchorsPem: [CA] },
          { trustDomain: "staging.example.test", anchorsPem: [CA_DNS_EXTRA] },
        ],
      };
      yield {
        bundles: [
          { trustDomain: "prod.example.test", anchorsPem: [CA] },
        ],
      };
    })(),
  });

  const result = await ingestor.consume();
  assert.deepEqual(result, { messages: 2, changedBundles: 3 });
  assert.equal(cp.getSpiffeTrustBundle("prod.example.test").sequence, 1);
  assert.equal(cp.getSpiffeTrustBundle("staging.example.test"), null);
  assert.equal(persisted.length, 2);
  assert.ok(cp.getAudit().some(event => event.type === "SPIFFE_TRUST_BUNDLE_REDACTED"));
});
