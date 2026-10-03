import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnGatewayPeerRuntime } from "./peer-runtime.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import { VpnLeaseSequenceAuthority } from "./lease-sequence-authority.js";
import {
  handleVpnProvisioningRequest,
  vpnProvisioningServerInternals,
} from "./provisioning-server.js";

const ACCESS_TOKEN = "A".repeat(32);
const ADMIN_TOKEN = "Z".repeat(32);
const SECRET = "S".repeat(32);
const GATEWAY_KEY = Buffer.alloc(32, 31).toString("base64");
const DEVICE_KEY = Buffer.alloc(32, 32).toString("base64");

function core() {
  return new VpnGatewayProvisioningCore({
    gateway: {
      id: "fr-par-01",
      endpointHost: "vpn-fr-par-01.example.com",
      endpointPort: 51820,
      gatewayPublicKey: GATEWAY_KEY,
      catalogSequence: 9,
      dnsServers: ["10.73.0.1"],
      clientIpv6Prefix: "2606:4700:abcd:1234::/64",
    },
    accessToken: ACCESS_TOKEN,
    clock: () => 2_000_000_000_000,
  });
}

class MemorySequenceAuthority extends VpnLeaseSequenceAuthority {
  constructor(sequence) {
    super();
    this.sequence = sequence;
  }

  async readMinimumSequence(gatewayId) {
    assert.equal(gatewayId, "fr-par-01");
    return this.sequence;
  }

  async commitSequence(gatewayId, sequence) {
    assert.equal(gatewayId, "fr-par-01");
    if (sequence < this.sequence) throw new Error("rollback rejected");
    this.sequence = sequence;
    return sequence;
  }
}

test("failed durable revoke advances authority so the old active snapshot cannot restore", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-vpn-revoke-failclosed-"));
  try {
    const path = join(dir, "leases.json");
    const service = core();
    const created = service.provision({
      gatewayId: "fr-par-01",
      devicePublicKey: DEVICE_KEY,
      catalogSequence: 9,
      accessToken: ACCESS_TOKEN,
    });
    assert.equal(created.accepted, true);

    const store = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    assert.equal(await store.save(service.exportState()), 1);
    const sequenceAuthority = new MemorySequenceAuthority(1);

    // Simulate a storage failure after the runtime peer has been destructively removed.
    store.save = async () => {
      throw new Error("simulated durable revoke failure");
    };

    const runtimeCalls = [];
    const peerRuntime = new VpnGatewayPeerRuntime({
      scriptPath: "/opt/sentinel/manage-peer.sh",
      runner: async (_path, args) => {
        runtimeCalls.push(args);
        return { stdout: "", stderr: "" };
      },
    });

    const response = await handleVpnProvisioningRequest({
      method: "POST",
      url: "/v1/admin/revoke",
      headers: { authorization: `Bearer ${ADMIN_TOKEN}` },
      body: { devicePublicKey: DEVICE_KEY },
      core: service,
      adminTokenDigest: vpnProvisioningServerInternals.tokenDigest(ADMIN_TOKEN),
      peerRuntime,
      stateStore: store,
      sequenceAuthority,
    });

    assert.equal(response.status, 503);
    assert.equal(response.body.error, "VPN_LEASE_STATE_PERSIST_FAILED");
    assert.equal(service.isRevoked(DEVICE_KEY), true);
    assert.deepEqual(runtimeCalls.at(-1), ["remove", DEVICE_KEY]);
    assert.equal(sequenceAuthority.sequence, 2);

    const restartStore = new VpnLeaseStateStore({ path, secret: SECRET, gatewayId: "fr-par-01" });
    await assert.rejects(
      restartStore.load({ minimumSequence: sequenceAuthority.sequence }),
      /VPN_LEASE_STORE_REPLAY_DETECTED/
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
