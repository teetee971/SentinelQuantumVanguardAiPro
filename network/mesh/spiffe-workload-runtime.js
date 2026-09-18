import { SpiffeWorkloadBundleIngestor } from "./spiffe-workload-api.js";
import { SpiffeWorkloadGrpcTransport } from "./spiffe-workload-grpc.js";

export class SpiffeWorkloadBundleSync {
  #transport;
  #ingestor;

  constructor({
    controlPlane,
    persist,
    endpoint = null,
    env = process.env,
    transport = null,
  }) {
    if (typeof persist !== "function") {
      throw new Error("SPIFFE Workload bundle sync requires durable persistence");
    }

    this.#transport = transport || new SpiffeWorkloadGrpcTransport({ endpoint, env });
    if (!this.#transport || typeof this.#transport.fetchX509Bundles !== "function") {
      throw new Error("SPIFFE Workload gRPC transport invalid");
    }

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

  async run({ signal = null, maxMessages } = {}) {
    const result = await this.#ingestor.consume({
      signal,
      ...(maxMessages === undefined ? {} : { maxMessages }),
    });

    if (!signal?.aborted) {
      throw new Error("SPIFFE Workload bundle stream ended unexpectedly");
    }
    return result;
  }
}
