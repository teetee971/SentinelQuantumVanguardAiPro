import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
  vpnLeaseSnapshotDigest,
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseSequenceBoundary,
} from "./lease-sequence-authority.js";

export async function persistVpnLeaseState({
  core,
  stateStore,
  sequenceAuthority,
}) {
  if (!(core instanceof VpnGatewayProvisioningCore)) {
    throw new TypeError("VpnGatewayProvisioningCore required");
  }
  if (!(stateStore instanceof VpnLeaseStateStore)) {
    throw new TypeError("VpnLeaseStateStore required");
  }
  assertVpnLeaseSequenceAuthority(sequenceAuthority);

  const gatewayId = core.gatewayId;
  const state = core.exportState();
  const digest = vpnLeaseSnapshotDigest(state);
  const sequence = await stateStore.save(state);
  validateVpnLeaseSequenceBoundary({ gatewayId, sequence });

  try {
    await sequenceAuthority.commitSequence(gatewayId, sequence, digest);
    const committedSequence = await sequenceAuthority.readMinimumSequence(gatewayId);
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence: committedSequence });
    if (committedSequence !== sequence) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
    }
    await sequenceAuthority.assertCommittedSnapshot(gatewayId, sequence, digest);
  } catch (error) {
    if (error?.message === "VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED") throw error;
    throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_FAILED");
  }

  return Object.freeze({ gatewayId, sequence, persisted: true });
}
