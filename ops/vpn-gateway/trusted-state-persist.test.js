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

function core() {
  return new VpnGatewayProvisioningCore({
    gateway: { id: "fr-par-01", endpointHost: "vpn.example.com", endpointPort: 51820, gatewayPublicKey: KEY, catalogSequence: 7, dnsServers: ["10.73.0.1"], clientIpv6Prefix: "2606:4700:abcd:1234::/64" },
    accessToken: TOKEN,
    clock: () => 2_000_000_000_000,
  });
}

class RecordingAuthority extends VpnLeaseSequenceAuthority {
  commits = [];
  sequence = 0;
  async commitSequence(gatewayId, sequence) {
    this.commits.push({ gatewayId, sequence });
    this.sequence = sequence;
  }
  async readMinimumSequence() { return this.sequence; }
}

test("persists authenticated state before committing the external sequence", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    const authority = new RecordingAuthority();
    const result = await persistVpnLeaseState({
      core: core(),
      stateStore: new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" }),
      sequenceAuthority: authority,
    });
    assert.deepEqual(result, { gatewayId: "fr-par-01", sequence: 1, persisted: true });
    assert.deepEqual(authority.commits, [{ gatewayId: "fr-par-01", sequence: 1 }]);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test("fails closed when external monotonic commit is unavailable", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    await assert.rejects(
      () => persistVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new VpnLeaseSequenceAuthority(),
      }),
      /VPN_SEQUENCE_AUTHORITY_COMMIT_FAILED/
    );
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test("rejects an authority that acknowledges commit without advancing its trusted floor", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-persist-"));
  try {
    class NonAdvancingAuthority extends VpnLeaseSequenceAuthority {
      async commitSequence() {}
      async readMinimumSequence() { return 1; }
    }
    const store = new VpnLeaseStateStore({ path: join(dir, "leases.json"), secret: SECRET, gatewayId: "fr-par-01" });
    await store.save(core().exportState());
    await assert.rejects(
      () => persistVpnLeaseState({ core: core(), stateStore: store, sequenceAuthority: new NonAdvancingAuthority() }),
      /VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED/
    );
  } finally { await rm(dir, { recursive: true, force: true }); }
});
