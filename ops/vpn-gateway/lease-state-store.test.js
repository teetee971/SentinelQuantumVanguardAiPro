import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { VpnLeaseStateStore } from "./lease-state-store.js";

const SECRET = "S".repeat(32);

test("saves and loads a gateway-bound authenticated lease snapshot", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-store-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({
      path,
      secret: SECRET,
      gatewayId: "fr-par-01",
    });
    const state = {
      nextIndex: 3,
      leases: [{
        devicePublicKey: Buffer.alloc(32, 7).toString("base64"),
        index: 2,
        expiresAtMs: 2_000_000_600_000,
        revoked: false,
      }],
    };

    assert.equal(await store.save(state), 1);
    const loaded = await store.load();
    assert.equal(loaded.sequence, 1);
    assert.deepEqual(loaded.state, state);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("tampering is rejected by the HMAC boundary", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-store-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({
      path,
      secret: SECRET,
      gatewayId: "fr-par-01",
    });
    await store.save({ nextIndex: 2, leases: [] });

    const envelope = JSON.parse(await readFile(path, "utf8"));
    envelope.state.nextIndex = 99;
    await writeFile(path, JSON.stringify(envelope), "utf8");

    await assert.rejects(
      () => store.load(),
      /VPN_LEASE_STORE_INTEGRITY_FAILURE/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("snapshot from another gateway is rejected even with valid JSON", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-store-"));
  try {
    const path = join(dir, "leases.json");
    const source = new VpnLeaseStateStore({
      path,
      secret: SECRET,
      gatewayId: "fr-par-01",
    });
    await source.save({ nextIndex: 2, leases: [] });

    const other = new VpnLeaseStateStore({
      path,
      secret: SECRET,
      gatewayId: "de-fra-01",
    });
    await assert.rejects(
      () => other.load(),
      /VPN_LEASE_STORE_GATEWAY_MISMATCH/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("older snapshot is rejected after a newer sequence was observed", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-store-"));
  try {
    const path = join(dir, "leases.json");
    const store = new VpnLeaseStateStore({
      path,
      secret: SECRET,
      gatewayId: "fr-par-01",
    });

    await store.save({ nextIndex: 2, leases: [] });
    const first = await readFile(path);
    await store.save({ nextIndex: 3, leases: [] });
    await store.load();

    await writeFile(path, first);
    await assert.rejects(
      () => store.load(),
      /VPN_LEASE_STORE_REPLAY_DETECTED/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("weak secret and invalid gateway id fail at construction", () => {
  assert.throws(
    () => new VpnLeaseStateStore({
      path: "/tmp/test.json",
      secret: "short",
      gatewayId: "fr-par-01",
    }),
    /VPN_LEASE_STORE_SECRET_INVALID/
  );

  assert.throws(
    () => new VpnLeaseStateStore({
      path: "/tmp/test.json",
      secret: SECRET,
      gatewayId: "INVALID!",
    }),
    /VPN_LEASE_STORE_GATEWAY_INVALID/
  );
});
