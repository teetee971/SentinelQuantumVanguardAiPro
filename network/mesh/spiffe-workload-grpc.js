import http2 from "node:http2";
import net from "node:net";
import { X509Certificate } from "node:crypto";
import { parseSpiffeEndpoint } from "./spiffe-workload-api.js";

const MAX_GRPC_MESSAGE_BYTES = 2 * 1024 * 1024;
const MAX_BUNDLES = 32;
const MAX_CERTS_PER_BUNDLE = 32;
const MAX_BUNDLE_BYTES = 1024 * 1024;
const GRPC_PATH = "/SpiffeWorkloadAPI/FetchX509Bundles";

export class SpiffeWorkloadGrpcError extends Error {
  constructor(message, { grpcStatus = null, retryable = false, code = null } = {}) {
    super(message);
    this.name = "SpiffeWorkloadGrpcError";
    this.grpcStatus = grpcStatus;
    this.retryable = retryable === true;
    if (code) this.code = code;
  }
}

function grpcFailure(status, message = null) {
  const retryable = status === 14; // UNAVAILABLE only; fail closed for all other statuses.
  return new SpiffeWorkloadGrpcError(
    `Workload API gRPC failure ${status}${message ? `: ${message}` : ""}`,
    { grpcStatus: status, retryable }
  );
}

function decodeVarint(buffer, offset) {
  let value = 0;
  let shift = 0;
  let cursor = offset;
  for (let i = 0; i < 10; i += 1) {
    if (cursor >= buffer.length) throw new Error("protobuf varint truncated");
    const byte = buffer[cursor++];
    value += (byte & 0x7f) * (2 ** shift);
    if ((byte & 0x80) === 0) {
      if (!Number.isSafeInteger(value)) throw new Error("protobuf varint overflow");
      return { value, offset: cursor };
    }
    shift += 7;
  }
  throw new Error("protobuf varint too long");
}

function readLengthDelimited(buffer, offset, maxBytes = MAX_GRPC_MESSAGE_BYTES) {
  const lengthResult = decodeVarint(buffer, offset);
  const length = lengthResult.value;
  if (length < 0 || length > maxBytes) throw new Error("protobuf field too large");
  const end = lengthResult.offset + length;
  if (end > buffer.length) throw new Error("protobuf field truncated");
  return {
    value: buffer.subarray(lengthResult.offset, end),
    offset: end,
  };
}

function skipField(buffer, offset, wireType) {
  if (wireType === 0) return decodeVarint(buffer, offset).offset;
  if (wireType === 1) {
    if (offset + 8 > buffer.length) throw new Error("protobuf fixed64 truncated");
    return offset + 8;
  }
  if (wireType === 2) return readLengthDelimited(buffer, offset).offset;
  if (wireType === 5) {
    if (offset + 4 > buffer.length) throw new Error("protobuf fixed32 truncated");
    return offset + 4;
  }
  throw new Error("protobuf wire type unsupported");
}

function decodeBundleEntry(entry) {
  let offset = 0;
  let key = null;
  let value = null;

  while (offset < entry.length) {
    const tagResult = decodeVarint(entry, offset);
    offset = tagResult.offset;
    const field = tagResult.value >>> 3;
    const wireType = tagResult.value & 7;

    if (field === 1) {
      if (wireType !== 2) throw new Error("bundle map key wire type invalid");
      const read = readLengthDelimited(entry, offset, 2048);
      key = read.value.toString("utf8");
      offset = read.offset;
      continue;
    }
    if (field === 2) {
      if (wireType !== 2) throw new Error("bundle map value wire type invalid");
      const read = readLengthDelimited(entry, offset, MAX_BUNDLE_BYTES);
      value = Buffer.from(read.value);
      offset = read.offset;
      continue;
    }
    offset = skipField(entry, offset, wireType);
  }

  if (!key || !value || value.length === 0) throw new Error("bundle map entry incomplete");
  return { key, value };
}

function trustDomainFromSpiffeId(raw) {
  let url;
  try {
    url = new URL(String(raw || ""));
  } catch {
    throw new Error("bundle trust domain SPIFFE ID invalid");
  }
  if (
    url.protocol !== "spiffe:" ||
    url.username ||
    url.password ||
    url.port ||
    url.search ||
    url.hash ||
    (url.pathname !== "" && url.pathname !== "/")
  ) {
    throw new Error("bundle trust domain SPIFFE ID invalid");
  }
  const host = url.hostname;
  if (
    !host ||
    host !== host.toLowerCase() ||
    !host.split(".").every(label =>
      /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label)
    )
  ) {
    throw new Error("bundle trust domain invalid");
  }
  return host;
}

function derObjectLength(buffer, offset) {
  if (buffer[offset] !== 0x30) throw new Error("DER certificate must start with SEQUENCE");
  if (offset + 2 > buffer.length) throw new Error("DER certificate truncated");
  const firstLength = buffer[offset + 1];
  if ((firstLength & 0x80) === 0) {
    return { headerBytes: 2, contentBytes: firstLength };
  }
  const count = firstLength & 0x7f;
  if (count === 0 || count > 4 || offset + 2 + count > buffer.length) {
    throw new Error("DER certificate length invalid");
  }
  let contentBytes = 0;
  for (let i = 0; i < count; i += 1) {
    contentBytes = (contentBytes * 256) + buffer[offset + 2 + i];
  }
  if (contentBytes < 128) throw new Error("DER certificate length not canonical");
  return { headerBytes: 2 + count, contentBytes };
}

export function derBundleToPemCertificates(bundle) {
  if (!Buffer.isBuffer(bundle) || bundle.length === 0 || bundle.length > MAX_BUNDLE_BYTES) {
    throw new Error("DER bundle invalid");
  }

  const certs = [];
  let offset = 0;
  while (offset < bundle.length) {
    if (certs.length >= MAX_CERTS_PER_BUNDLE) throw new Error("DER bundle certificate limit exceeded");
    const { headerBytes, contentBytes } = derObjectLength(bundle, offset);
    const end = offset + headerBytes + contentBytes;
    if (end > bundle.length) throw new Error("DER certificate truncated");
    const der = bundle.subarray(offset, end);
    let cert;
    try {
      cert = new X509Certificate(der);
    } catch {
      throw new Error("DER certificate invalid");
    }
    certs.push(cert.toString());
    offset = end;
  }

  if (!certs.length) throw new Error("DER bundle empty");
  return certs;
}

export function decodeX509BundlesResponse(payload) {
  if (!Buffer.isBuffer(payload) || payload.length === 0 || payload.length > MAX_GRPC_MESSAGE_BYTES) {
    throw new Error("X509BundlesResponse invalid");
  }

  const bundles = [];
  const seen = new Set();
  let offset = 0;

  while (offset < payload.length) {
    const tagResult = decodeVarint(payload, offset);
    offset = tagResult.offset;
    const field = tagResult.value >>> 3;
    const wireType = tagResult.value & 7;

    if (field === 2) {
      if (wireType !== 2) throw new Error("X509BundlesResponse bundle wire type invalid");
      const read = readLengthDelimited(payload, offset, MAX_BUNDLE_BYTES + 4096);
      offset = read.offset;
      const entry = decodeBundleEntry(read.value);
      const trustDomain = trustDomainFromSpiffeId(entry.key);
      if (seen.has(trustDomain)) throw new Error("duplicate trust domain in X509BundlesResponse");
      seen.add(trustDomain);
      if (bundles.length >= MAX_BUNDLES) throw new Error("X509BundlesResponse bundle limit exceeded");
      bundles.push({
        trustDomain,
        anchorsPem: derBundleToPemCertificates(entry.value),
      });
      continue;
    }

    offset = skipField(payload, offset, wireType);
  }

  if (!bundles.length) throw new Error("X509BundlesResponse bundles missing");
  return bundles;
}

export function encodeEmptyGrpcRequest() {
  return Buffer.from([0, 0, 0, 0, 0]);
}

export function createGrpcFrame(payload) {
  if (!Buffer.isBuffer(payload) || payload.length > MAX_GRPC_MESSAGE_BYTES) {
    throw new Error("gRPC payload invalid");
  }
  const frame = Buffer.allocUnsafe(5 + payload.length);
  frame[0] = 0;
  frame.writeUInt32BE(payload.length, 1);
  payload.copy(frame, 5);
  return frame;
}

export async function* decodeGrpcFrames(chunks) {
  let pending = Buffer.alloc(0);

  for await (const raw of chunks) {
    const chunk = Buffer.isBuffer(raw) ? raw : Buffer.from(raw);
    if (pending.length + chunk.length > MAX_GRPC_MESSAGE_BYTES + 5) {
      throw new Error("gRPC stream buffer limit exceeded");
    }
    pending = pending.length ? Buffer.concat([pending, chunk]) : chunk;

    while (pending.length >= 5) {
      const compressed = pending[0];
      const length = pending.readUInt32BE(1);
      if (compressed !== 0) throw new Error("compressed gRPC messages unsupported");
      if (length > MAX_GRPC_MESSAGE_BYTES) throw new Error("gRPC message too large");
      if (pending.length < 5 + length) break;

      const payload = Buffer.from(pending.subarray(5, 5 + length));
      pending = pending.subarray(5 + length);
      yield payload;
    }
  }

  if (pending.length !== 0) throw new Error("truncated gRPC frame");
}

function http2SessionForEndpoint(endpoint) {
  if (endpoint.scheme === "unix") {
    return http2.connect("http://localhost", {
      createConnection: () => net.connect(endpoint.address),
    });
  }
  if (endpoint.scheme === "tcp") {
    return http2.connect(`http://${endpoint.address}:${endpoint.port}`);
  }
  throw new Error("SPIFFE endpoint transport unsupported");
}

export class SpiffeWorkloadGrpcTransport {
  #endpoint;
  #connect;

  constructor({
    endpoint = null,
    env = process.env,
    connect = http2SessionForEndpoint,
  } = {}) {
    if (typeof connect !== "function") throw new Error("gRPC connector invalid");
    this.#endpoint = parseSpiffeEndpoint({ endpoint, env });
    this.#connect = connect;
  }

  endpointConfig() {
    return structuredClone(this.#endpoint);
  }

  async *fetchX509Bundles({ signal = null } = {}) {
    const session = this.#connect(this.#endpoint);
    if (!session || typeof session.request !== "function") {
      throw new Error("gRPC session invalid");
    }

    let request;
    try {
      request = session.request({
        ":method": "POST",
        ":path": GRPC_PATH,
        "content-type": "application/grpc",
        "te": "trailers",
        "workload.spiffe.io": "true",
      });

      if (signal) {
        const onAbort = () => request.close(http2.constants.NGHTTP2_CANCEL);
        if (signal.aborted) onAbort();
        else signal.addEventListener("abort", onAbort, { once: true });
      }

      let status = null;
      let grpcStatus = null;
      let grpcMessage = null;

      request.on("response", headers => {
        status = Number(headers[":status"]);
        const contentType = String(headers["content-type"] || "");
        if (headers["grpc-status"] !== undefined) {
          grpcStatus = Number(headers["grpc-status"]);
          grpcMessage = headers["grpc-message"] === undefined ? null : String(headers["grpc-message"]);
        }
        if (status !== 200) request.destroy(new Error(`Workload API HTTP status ${status}`));
        if (!contentType.startsWith("application/grpc")) {
          request.destroy(new Error("Workload API content type invalid"));
        }
      });
      request.on("trailers", headers => {
        grpcStatus = headers["grpc-status"] === undefined ? null : Number(headers["grpc-status"]);
        grpcMessage = headers["grpc-message"] === undefined ? null : String(headers["grpc-message"]);
      });

      request.end(encodeEmptyGrpcRequest());

      for await (const payload of decodeGrpcFrames(request)) {
        yield { bundles: decodeX509BundlesResponse(payload) };
      }

      if (status !== 200) throw new Error("Workload API HTTP failure");
      if (grpcStatus === null) throw new Error("Workload API grpc-status missing");
      if (grpcStatus !== 0) {
        throw grpcFailure(grpcStatus, grpcMessage);
      }
    } finally {
      try { request?.close(); } catch {}
      try { session.close(); } catch {}
    }
  }
}

export const spiffeWorkloadGrpcLimits = Object.freeze({
  maxGrpcMessageBytes: MAX_GRPC_MESSAGE_BYTES,
  maxBundles: MAX_BUNDLES,
  maxCertsPerBundle: MAX_CERTS_PER_BUNDLE,
  maxBundleBytes: MAX_BUNDLE_BYTES,
});
