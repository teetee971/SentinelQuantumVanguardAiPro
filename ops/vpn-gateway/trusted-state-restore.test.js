import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import { VpnLeaseSequenceAuthority } from "./lease-sequence-authority.js";
import { restoreVpnLeaseState } from "./trusted-state-restore.js";

const TOKEN = "A".repeat(32);
const SECRET = "S".repeat(32);
const GATEWAY_KEY = Buffer.alloc(32, 7).toString("base64");
const DEVICE_KEY = Buffer.alloc(32, 8).toString("base64");
const OTHER_DIGEST = "f".repeat(64);

function core() {
  return new VpnGatewayProvisioningCore({
    gateway: {
      id: "fr-par-01",
      endpointHost: "vpn-fr-par-01.example.com",
      endpointPort: 51820,
      gatewayPublicKey: GATEWAY_KEY,
      catalogSequence: 7,
      dnsServers: ["10.73.0.1"],
      clientIpv6Prefix: "2606:4700:abcd:1234::/64",
    },
    accessToken: TOKEN,
    clock: () => 2_000_000_000_000,
  });
}

class FixedAuthority extends VpnLeaseSequenceAuthority {
  constructor(record) {
    super();
    this.record = record;
  }
  async readCommit(gatewayId) {
    assert.equal(gatewayId, "fr-par-01");
    return this.record;
  }
}

test("trusted restore accepts only the exact authenticated snapshot commit", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-restore-"));
  try {
    const path = join(dir, "leases.json");
    const source = core();
    source.provision({ gatewayId: "fr-par-01", devicePublicKey: DEVICE_KEY, catalogSequence: 7, accessToken: TOKEN });
    const store = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    const receipt = await store.saveWithReceipt(source.exportState());

    const target = core();
    const result = await restoreVpnLeaseState({
      core: target,
      stateStore: new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" }),
      sequenceAuthority: new FixedAuthority(receipt),
    });
    assert.deepEqual(result, {
      gatewayId: "fr-par-01",
      sequence: 1,
      snapshotDigest: receipt.snapshotDigest,
      restored: true,
    });
    assert.deepEqual(target.exportState(), source.exportState());
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("trusted restore rejects a snapshot below the external monotonic sequence", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-restore-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    const receipt = await store.saveWithReceipt(core().exportState());

    await assert.rejects(
      () => restoreVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new FixedAuthority({ sequence: 2, snapshotDigest: receipt.snapshotDigest }),
      }),
      /VPN_LEASE_STORE_REPLAY_DETECTED/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("trusted restore rejects a locally signed snapshot ahead of the external commit record", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-restore-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    const first = await store.saveWithReceipt(core().exportState());
    await store.saveWithReceipt(core().exportState());

    await assert.rejects(
      () => restoreVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new FixedAuthority(first),
      }),
      /VPN_SEQUENCE_AUTHORITY_STATE_MISMATCH/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("trusted restore rejects a different snapshot digest at the same sequence", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-restore-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    const receipt = await store.saveWithReceipt(core().exportState());

    await assert.rejects(
      () => restoreVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new FixedAuthority({ sequence: receipt.sequence, snapshotDigest: OTHER_DIGEST }),
      }),
      /VPN_SEQUENCE_AUTHORITY_STATE_MISMATCH/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("trusted restore fails closed with the unimplemented authority", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-restore-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    await store.save(core().exportState());

    await assert.rejects(
      () => restoreVpnLeaseState({
        core: core(),
        stateStore: new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" }),
        sequenceAuthority: new VpnLeaseSequenceAuthority(),
      }),
      /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
