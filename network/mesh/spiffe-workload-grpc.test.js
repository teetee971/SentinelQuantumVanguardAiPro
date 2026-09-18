import test from "node:test";
import assert from "node:assert/strict";
import { Readable } from "node:stream";
import { X509Certificate } from "node:crypto";
import {
  SpiffeWorkloadGrpcTransport,
  createGrpcFrame,
  decodeGrpcFrames,
  decodeX509BundlesResponse,
  derBundleToPemCertificates,
} from "./spiffe-workload-grpc.js";
import { CA, CA_DNS_EXTRA } from "./x509-svid-test-fixtures.js";

function varint(value) {
  const out = [];
  let remaining = value;
  do {
    let byte = remaining & 0x7f;
    remaining = Math.floor(remaining / 128);
    if (remaining) byte |= 0x80;
    out.push(byte);
  } while (remaining);
  return Buffer.from(out);
}

function fieldBytes(field, value) {
  const bytes = Buffer.from(value);
  return Buffer.concat([
    varint((field << 3) | 2),
    varint(bytes.length),
    bytes,
  ]);
}

function bundleEntry(spiffeTrustDomain, derBundle) {
  const entry = Buffer.concat([
    fieldBytes(1, Buffer.from(spiffeTrustDomain, "utf8")),
    fieldBytes(2, derBundle),
  ]);
  return fieldBytes(2, entry);
}

function officialLikeResponse(entries) {
  return Buffer.concat(entries.map(([id, der]) => bundleEntry(id, der)));
}

test("decodes official X509BundlesResponse map entries and concatenated DER certs", () => {
  const der = Buffer.concat([
    new X509Certificate(CA).raw,
    new X509Certificate(CA_DNS_EXTRA).raw,
  ]);
  const payload = officialLikeResponse([
    ["spiffe://prod.example.test", der],
  ]);

  const bundles = decodeX509BundlesResponse(payload);
  assert.equal(bundles.length, 1);
  assert.equal(bundles[0].trustDomain, "prod.example.test");
  assert.equal(bundles[0].anchorsPem.length, 2);
  assert.equal(new X509Certificate(bundles[0].anchorsPem[0]).ca, true);
  assert.equal(new X509Certificate(bundles[0].anchorsPem[1]).ca, true);
});

test("DER bundle decoder rejects malformed and truncated certificates", () => {
  const valid = new X509Certificate(CA).raw;
  assert.equal(derBundleToPemCertificates(valid).length, 1);
  assert.throws(() => derBundleToPemCertificates(Buffer.from([0x31, 0x00])), /SEQUENCE/);
  assert.throws(() => derBundleToPemCertificates(valid.subarray(0, valid.length - 5)), /truncated|invalid/);
});

test("X509BundlesResponse rejects duplicate trust domains and non-domain SPIFFE IDs", () => {
  const der = new X509Certificate(CA).raw;
  assert.throws(
    () => decodeX509BundlesResponse(officialLikeResponse([
      ["spiffe://prod.example.test", der],
      ["spiffe://prod.example.test", der],
    ])),
    /duplicate trust domain/
  );
  assert.throws(
    () => decodeX509BundlesResponse(officialLikeResponse([
      ["spiffe://prod.example.test/workload", der],
    ])),
    /trust domain SPIFFE ID invalid/
  );
});

test("gRPC frame decoder handles fragmented streaming frames and rejects compression", async () => {
  const first = Buffer.from("first");
  const second = Buffer.from("second");
  const wire = Buffer.concat([createGrpcFrame(first), createGrpcFrame(second)]);

  async function* fragmented() {
    yield wire.subarray(0, 3);
    yield wire.subarray(3, 9);
    yield wire.subarray(9);
  }

  const decoded = [];
  for await (const message of decodeGrpcFrames(fragmented())) decoded.push(message.toString());
  assert.deepEqual(decoded, ["first", "second"]);

  const compressed = createGrpcFrame(Buffer.from("x"));
  compressed[0] = 1;
  await assert.rejects(async () => {
    for await (const _ of decodeGrpcFrames((async function* () { yield compressed; })())) {}
  }, /compressed/);
});

test("transport sends mandatory SPIFFE metadata and streams decoded bundle snapshots", async () => {
  const der = new X509Certificate(CA).raw;
  const payload = officialLikeResponse([
    ["spiffe://prod.example.test", der],
  ]);
  const frame = createGrpcFrame(payload);
  let requestHeaders = null;
  let sessionClosed = false;

  const connect = () => ({
    request(headers) {
      requestHeaders = headers;
      const stream = new Readable({ read() {} });
      stream.end = () => {
        queueMicrotask(() => {
          stream.emit("response", {
            ":status": 200,
            "content-type": "application/grpc",
          });
          stream.push(frame.subarray(0, 7));
          stream.push(frame.subarray(7));
          stream.emit("trailers", { "grpc-status": "0" });
          stream.push(null);
        });
      };
      stream.close = () => {};
      return stream;
    },
    close() {
      sessionClosed = true;
    },
  });

  const transport = new SpiffeWorkloadGrpcTransport({
    endpoint: "unix:///tmp/spire-agent.sock",
    connect,
  });

  const messages = [];
  for await (const message of transport.fetchX509Bundles()) messages.push(message);

  assert.equal(requestHeaders[":path"], "/SpiffeWorkloadAPI/FetchX509Bundles");
  assert.equal(requestHeaders["content-type"], "application/grpc");
  assert.equal(requestHeaders["workload.spiffe.io"], "true");
  assert.equal(messages.length, 1);
  assert.equal(messages[0].bundles[0].trustDomain, "prod.example.test");
  assert.equal(sessionClosed, true);
});

test("transport refuses non-zero grpc-status", async () => {
  const connect = () => ({
    request() {
      const stream = new Readable({ read() {} });
      stream.end = () => queueMicrotask(() => {
        stream.emit("response", {
          ":status": 200,
          "content-type": "application/grpc",
        });
        stream.emit("trailers", { "grpc-status": "7", "grpc-message": "denied" });
        stream.push(null);
      });
      stream.close = () => {};
      return stream;
    },
    close() {},
  });

  const transport = new SpiffeWorkloadGrpcTransport({
    endpoint: "unix:///tmp/spire-agent.sock",
    connect,
  });

  await assert.rejects(async () => {
    for await (const _ of transport.fetchX509Bundles()) {}
  }, /gRPC failure 7/);
});
