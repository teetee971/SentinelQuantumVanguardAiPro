import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import { mkdir, open, readFile, rename, rm } from "node:fs/promises";
import { dirname } from "node:path";

const SCHEMA_VERSION = 1;
const MAX_FILE_BYTES = 16 * 1024 * 1024;

function canonicalPayload(sequence, state) {
  return JSON.stringify({ schemaVersion: SCHEMA_VERSION, sequence, state });
}

function sign(sequence, state, secret) {
  return createHmac("sha256", secret).update(canonicalPayload(sequence, state)).digest("hex");
}

function assertSecret(secret) {
  if (typeof secret !== "string" || secret.length < 32) {
    throw new Error("mesh persistence secret must be at least 32 characters");
  }
}

export class MeshStateStore {
  #path;
  #secret;
  #lastSequence = 0;
  #pendingSave = Promise.resolve();

  constructor({ path, secret }) {
    if (typeof path !== "string" || !path.trim()) throw new Error("state path required");
    assertSecret(secret);
    this.#path = path;
    this.#secret = secret;
  }

  async save(state) {
    // Capture the request before queueing; callers may mutate their own state later.
    const snapshot = JSON.parse(JSON.stringify(state));
    const result = this.#pendingSave.then(() => this.#save(snapshot));
    this.#pendingSave = result.catch(() => {});
    return result;
  }

  async #save(state) {
    const sequence = this.#lastSequence + 1;
    const envelope = {
      schemaVersion: SCHEMA_VERSION,
      sequence,
      state,
      mac: sign(sequence, state, this.#secret),
    };
    const encoded = JSON.stringify(envelope);
    if (Buffer.byteLength(encoded, "utf8") > MAX_FILE_BYTES) {
      throw new Error("mesh state snapshot too large");
    }

    await mkdir(dirname(this.#path), { recursive: true, mode: 0o700 });
    const temp = `${this.#path}.tmp-${process.pid}-${randomUUID()}`;
    try {
      const handle = await open(temp, "wx", 0o600);
      try {
        await handle.writeFile(encoded, "utf8");
        await handle.sync();
      } finally {
        await handle.close();
      }
      await rename(temp, this.#path);
    } finally {
      await rm(temp, { force: true });
    }
    this.#lastSequence = sequence;
    return sequence;
  }

  async load() {
    await this.#pendingSave;
    const raw = await readFile(this.#path);
    if (raw.length > MAX_FILE_BYTES) throw new Error("mesh state file too large");
    const envelope = JSON.parse(raw.toString("utf8"));

    if (envelope?.schemaVersion !== SCHEMA_VERSION) throw new Error("unsupported mesh state envelope");
    if (!Number.isInteger(envelope.sequence) || envelope.sequence < 1) throw new Error("invalid mesh state sequence");
    if (!envelope.state || typeof envelope.state !== "object") throw new Error("invalid mesh state payload");
    if (typeof envelope.mac !== "string" || !/^[a-f0-9]{64}$/.test(envelope.mac)) {
      throw new Error("invalid mesh state MAC");
    }

    const expected = sign(envelope.sequence, envelope.state, this.#secret);
    const providedBytes = Buffer.from(envelope.mac, "hex");
    const expectedBytes = Buffer.from(expected, "hex");
    if (!timingSafeEqual(providedBytes, expectedBytes)) throw new Error("mesh state integrity failure");

    if (envelope.sequence < this.#lastSequence) {
      throw new Error("mesh state replay detected");
    }
    this.#lastSequence = envelope.sequence;
    return { sequence: envelope.sequence, state: envelope.state };
  }
}

export const meshStateStoreLimits = Object.freeze({ maxFileBytes: MAX_FILE_BYTES });
