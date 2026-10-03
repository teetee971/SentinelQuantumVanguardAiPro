import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import { VpnLeaseSequenceAuthority } from "./lease-sequence-authority.js";
import { persistVpnLeaseState } from "./trusted-state-persist.js";

const TOKEN = "A".repeat(32);
const SECRET = "S".repeat(32);
const KEY = Buffer.alloc(32, 7).toString("base64");
const OTHER_DIGEST = "f".repeat(64);

function core() {
  return new VpnGatewayProvisioningCore({
    gateway: { id: "fr-par-01", endpointHost: "vpn.example.com", endpointPort: 51820, gatewayPublicKey: KEY, catalogSequence: 7, dnsServers: ["10.73.0.1"], clientIpv6Prefix: "2606:4700:abcd:1234::/64" },
    accessToken: TOKEN,
    clock: () => 2_000_000_000_000,
  });
}

class RecordingAuthority extends VpnLeaseSequenceAuthority {
  commits = [];
  record = null;
  transientFailures = 0;

  constructor({ transientFailures = 0 } = {}) {
    super();
    this.transientFailures = transientFailures;
  }

  async commitSnapshot(gatewayId, sequence, snapshotDigest) {
    this.commits.push({ gatewayId, sequence, snapshotDigest });
    if (this.transientFailures > 0) {
      this.transientFailures -= 1;
      throw new Error("temporary authority failure");
    }
    if (this.record && sequence < this.record.sequence) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED");
    }
    if (this.record && sequence === this.record.sequence && snapshotDigest !== this.record.snapshotDigest) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SNAPSHOT_CONFLICT");
    }
    this.record = { sequence, snapshotDigest };
    return this.record;
  }

  async readCommit() {
    if (!this.record) throw new Error("missing record");
    return this.record;
  }
}

test("persists one authenticated snapshot before committing its exact digest", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    const authority = new RecordingAuthority();
    const result = await persistVpnLeaseState({
      core: core(),
      stateStore: new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" }),
      sequenceAuthority: authority,
    });
    assert.equal(result.gatewayId, "fr-par-01");
    assert.equal(result.sequence, 1);
    assert.match(result.snapshotDigest, /^[a-f0-9]{64}$/);
    assert.equal(result.persisted, true);
    assert.deepEqual(authority.commits, [{
      gatewayId: "fr-par-01",
      sequence: 1,
      snapshotDigest: result.snapshotDigest,
    }]);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test("transient authority retry reuses the same local sequence and digest", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    const authority = new RecordingAuthority({ transientFailures: 1 });
    const store = new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" });
    let saveCount = 0;
    const originalSaveWithReceipt = store.saveWithReceipt.bind(store);
    store.saveWithReceipt = async (state) => {
      saveCount += 1;
      return originalSaveWithReceipt(state);
    };

    const result = await persistVpnLeaseState({ core: core(), stateStore: store, sequenceAuthority: authority });
    assert.equal(saveCount, 1);
    assert.equal(authority.commits.length, 2);
    assert.deepEqual(authority.commits[0], authority.commits[1]);
    assert.equal(result.sequence, 1);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test("rollback and same-sequence snapshot conflict terminate without a second local write", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    for (const existing of [
      { sequence: 8, snapshotDigest: OTHER_DIGEST, expected: /ROLLBACK_REJECTED/ },
      { sequence: 1, snapshotDigest: OTHER_DIGEST, expected: /SNAPSHOT_CONFLICT/ },
    ]) {
      const store = new VpnLeaseStateStore({
        path: join(dir, `leases-${existing.sequence}.json`),
        secret: SECRET,
        gatewayId: "fr-par-01",
      });
      let saveCount = 0;
      const originalSaveWithReceipt = store.saveWithReceipt.bind(store);
      store.saveWithReceipt = async (state) => {
        saveCount += 1;
        return originalSaveWithReceipt(state);
      };
      const authority = new RecordingAuthority();
      authority.record = { sequence: existing.sequence, snapshotDigest: existing.snapshotDigest };

      await assert.rejects(
        () => persistVpnLeaseState({ core: core(), stateStore: store, sequenceAuthority: authority }),
        existing.expected
      );
      assert.equal(saveCount, 1);
      assert.equal(authority.commits.length, 1);
    }
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test("fails closed when external snapshot authority is unimplemented", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    await assert.rejects(
      () => persistVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new VpnLeaseSequenceAuthority(),
      }),
      /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
    );
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test("rejects an authority that returns a different committed digest", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    class DivergentAuthority extends VpnLeaseSequenceAuthority {
      async commitSnapshot(_gatewayId, sequence) {
        return { sequence, snapshotDigest: OTHER_DIGEST };
      }
      async readCommit() {
        return { sequence: 1, snapshotDigest: OTHER_DIGEST };
      }
    }
    await assert.rejects(
      () => persistVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new DivergentAuthority(),
      }),
      /VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED/
    );
  } finally { await rm(dir, { recursive: true, force: true }); }
});
