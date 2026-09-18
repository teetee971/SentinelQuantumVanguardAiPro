import { isIP } from "node:net";
import { isAbsolute } from "node:path";

const MAX_BUNDLES_PER_MESSAGE = 32;
const MAX_MESSAGES_PER_RUN = 10_000;
const WORKLOAD_METADATA = Object.freeze({ "workload.spiffe.io": "true" });

export function parseSpiffeEndpoint({
  endpoint = null,
  env = process.env,
  tcpAuthenticatedNetwork = false,
} = {}) {
  const raw = endpoint || env?.SPIFFE_ENDPOINT_SOCKET || "";
  if (typeof raw !== "string" || !raw || raw.length > 4096) {
    throw new Error("SPIFFE endpoint missing or invalid");
  }

  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("SPIFFE endpoint invalid");
  }

  if (url.username || url.password || url.search || url.hash) {
    throw new Error("SPIFFE endpoint contains forbidden components");
  }

  if (url.protocol === "unix:") {
    if (url.host) throw new Error("SPIFFE unix endpoint authority forbidden");
    const path = decodeURIComponent(url.pathname);
    if (!isAbsolute(path) || !path || path.length > 4096) {
      throw new Error("SPIFFE unix endpoint path invalid");
    }
    return Object.freeze({
      scheme: "unix",
      address: path,
      endpoint: raw,
      metadata: WORKLOAD_METADATA,
    });
  }

  if (url.protocol === "tcp:") {
    if (tcpAuthenticatedNetwork !== true) {
      throw new Error("SPIFFE TCP endpoint requires authenticated network assertion");
    }
    if (!isIP(url.hostname)) throw new Error("SPIFFE TCP endpoint host must be an IP address");
    if (!url.port) throw new Error("SPIFFE TCP endpoint port required");
    const port = Number(url.port);
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      throw new Error("SPIFFE TCP endpoint port invalid");
    }
    if (url.pathname !== "/" && url.pathname !== "") {
      throw new Error("SPIFFE TCP endpoint path forbidden");
    }
    return Object.freeze({
      scheme: "tcp",
      address: url.hostname,
      port,
      endpoint: raw,
      metadata: WORKLOAD_METADATA,
    });
  }

  throw new Error("SPIFFE endpoint scheme must be unix or tcp");
}

function normalizeBundleUpdate(update) {
  if (!update || typeof update !== "object" || !Array.isArray(update.bundles)) {
    throw new Error("SPIFFE bundle stream message invalid");
  }
  if (!update.bundles.length || update.bundles.length > MAX_BUNDLES_PER_MESSAGE) {
    throw new Error("SPIFFE bundle stream count invalid");
  }
  return update.bundles.map((bundle, index) => {
    if (!bundle || typeof bundle !== "object") {
      throw new Error(`SPIFFE bundle stream item ${index} invalid`);
    }
    if (!Array.isArray(bundle.anchorsPem) || !bundle.anchorsPem.length) {
      throw new Error(`SPIFFE bundle stream item ${index} anchors invalid`);
    }
    return {
      trustDomain: bundle.trustDomain,
      anchorsPem: bundle.anchorsPem,
    };
  });
}

export class SpiffeWorkloadBundleIngestor {
  #controlPlane;
  #persist;
  #streamFactory;
  #endpoint;

  constructor({
    controlPlane,
    persist = null,
    streamFactory,
    endpoint = null,
    env = process.env,
    tcpAuthenticatedNetwork = false,
  }) {
    if (!controlPlane || typeof controlPlane.observeSpiffeTrustBundle !== "function" || typeof controlPlane.exportState !== "function") {
      throw new Error("SPIFFE control plane adapter invalid");
    }
    if (persist !== null && typeof persist !== "function") throw new Error("SPIFFE persist callback invalid");
    if (typeof streamFactory !== "function") throw new Error("SPIFFE stream factory required");

    this.#controlPlane = controlPlane;
    this.#persist = persist;
    this.#streamFactory = streamFactory;
    this.#endpoint = parseSpiffeEndpoint({ endpoint, env, tcpAuthenticatedNetwork });
  }

  endpointConfig() {
    return structuredClone(this.#endpoint);
  }

  async consume({ signal = null, maxMessages = MAX_MESSAGES_PER_RUN } = {}) {
    if (!Number.isInteger(maxMessages) || maxMessages < 1 || maxMessages > MAX_MESSAGES_PER_RUN) {
      throw new Error("SPIFFE max messages invalid");
    }
    const stream = await this.#streamFactory({
      endpoint: this.endpointConfig(),
      metadata: WORKLOAD_METADATA,
      signal,
    });
    if (!stream || typeof stream[Symbol.asyncIterator] !== "function") {
      throw new Error("SPIFFE stream factory did not return an async iterable");
    }

    let messages = 0;
    let changedBundles = 0;
    for await (const message of stream) {
      if (signal?.aborted) break;
      messages += 1;
      if (messages > maxMessages) throw new Error("SPIFFE stream message limit exceeded");

      let changedInMessage = false;
      for (const bundle of normalizeBundleUpdate(message)) {
        const result = this.#controlPlane.observeSpiffeTrustBundle(bundle);
        if (result.changed) {
          changedBundles += 1;
          changedInMessage = true;
        }
      }
      if (changedInMessage && this.#persist) {
        await this.#persist(this.#controlPlane.exportState());
      }
    }

    return Object.freeze({ messages, changedBundles });
  }
}

export const spiffeWorkloadApiLimits = Object.freeze({
  maxBundlesPerMessage: MAX_BUNDLES_PER_MESSAGE,
  maxMessagesPerRun: MAX_MESSAGES_PER_RUN,
});
