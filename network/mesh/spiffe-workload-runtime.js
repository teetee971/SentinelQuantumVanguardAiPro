import { SpiffeWorkloadBundleIngestor } from "./spiffe-workload-api.js";
import { SpiffeWorkloadGrpcTransport } from "./spiffe-workload-grpc.js";

const DEFAULT_MAX_RETRIES = 5;
const DEFAULT_BASE_DELAY_MS = 500;
const DEFAULT_MAX_DELAY_MS = 10_000;
const DEFAULT_JITTER_RATIO = 0.2;
const TRANSIENT_NETWORK_CODES = new Set(["ECONNREFUSED", "ECONNRESET", "EPIPE", "ETIMEDOUT", "EHOSTUNREACH", "ENETUNREACH"]);

export class SpiffeWorkloadBundleSync {
  #transport;
  #ingestor;
  #sleep;
  #random;
  #controlPlane;
  #persist;

  constructor({
    controlPlane,
    persist,
    endpoint = null,
    env = process.env,
    transport = null,
    sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
    random = Math.random,
  }) {
    if (typeof persist !== "function") {
      throw new Error("SPIFFE Workload bundle sync requires durable persistence");
    }

    this.#controlPlane = controlPlane;
    this.#persist = persist;
    this.#transport = transport || new SpiffeWorkloadGrpcTransport({ endpoint, env });
    if (!this.#transport || typeof this.#transport.fetchX509Bundles !== "function") {
      throw new Error("SPIFFE Workload gRPC transport invalid");
    }
    if (typeof sleep !== "function") throw new Error("SPIFFE Workload sleep function invalid");
    if (typeof random !== "function") throw new Error("SPIFFE Workload random function invalid");
    this.#sleep = async ms => sleep(ms);
    this.#random = random;

    const endpointConfig = typeof this.#transport.endpointConfig === "function"
      ? this.#transport.endpointConfig()
      : null;

    this.#ingestor = new SpiffeWorkloadBundleIngestor({
      controlPlane,
      persist,
      endpoint: endpointConfig?.endpoint || endpoint,
      env,
      streamFactory: async ({ signal }) =>
        this.#transport.fetchX509Bundles({ signal }),
    });
  }

  endpointConfig() {
    return typeof this.#transport.endpointConfig === "function"
      ? this.#transport.endpointConfig()
      : null;
  }

  async run({
    signal = null,
    maxMessages,
    maxRetries = DEFAULT_MAX_RETRIES,
    baseDelayMs = DEFAULT_BASE_DELAY_MS,
    maxDelayMs = DEFAULT_MAX_DELAY_MS,
    jitterRatio = DEFAULT_JITTER_RATIO,
  } = {}) {
    if (!Number.isInteger(maxRetries) || maxRetries < 0 || maxRetries > 20) {
      throw new Error("SPIFFE Workload max retries invalid");
    }
    if (!Number.isInteger(baseDelayMs) || baseDelayMs < 1 || baseDelayMs > 60_000) {
      throw new Error("SPIFFE Workload base delay invalid");
    }
    if (!Number.isInteger(maxDelayMs) || maxDelayMs < baseDelayMs || maxDelayMs > 5 * 60_000) {
      throw new Error("SPIFFE Workload max delay invalid");
    }
    if (!Number.isFinite(jitterRatio) || jitterRatio < 0 || jitterRatio > 0.5) {
      throw new Error("SPIFFE Workload jitter ratio invalid");
    }

    let attempts = 0;
    for (;;) {
      if (signal?.aborted) {
        return { messages: 0, changedBundles: 0, retries: attempts };
      }

      try {
        const result = await this.#ingestor.consume({
          signal,
          ...(maxMessages === undefined ? {} : { maxMessages }),
        });

        if (signal?.aborted) {
          return { ...result, retries: attempts };
        }
        throw new Error("SPIFFE Workload bundle stream ended unexpectedly");
      } catch (error) {
        if (signal?.aborted) {
          return { messages: 0, changedBundles: 0, retries: attempts };
        }

        if (error?.grpcStatus === 7) {
          const redacted = this.#controlPlane.observeSpiffeTrustBundleSet([]);
          if (redacted.removedDomains.length > 0) {
            await this.#persist(this.#controlPlane.exportState());
          }
          throw error;
        }

        const retryable =
          error?.retryable === true ||
          TRANSIENT_NETWORK_CODES.has(error?.code);

        if (!retryable || attempts >= maxRetries) throw error;

        const base = Math.min(maxDelayMs, baseDelayMs * (2 ** attempts));
        const spread = Math.floor(base * jitterRatio);
        const jitter = spread === 0 ? 0 : Math.round((this.#random() * 2 - 1) * spread);
        const delayMs = Math.max(1, Math.min(maxDelayMs, base + jitter));
        attempts += 1;
        await this.#sleep(delayMs);
      }
    }
  }
}


export const spiffeWorkloadRuntimeLimits = Object.freeze({
  defaultMaxRetries: DEFAULT_MAX_RETRIES,
  defaultBaseDelayMs: DEFAULT_BASE_DELAY_MS,
  defaultMaxDelayMs: DEFAULT_MAX_DELAY_MS,
  defaultJitterRatio: DEFAULT_JITTER_RATIO,
});
