import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
  vpnLeaseSnapshotDigest,
  VpnLeaseSequenceAuthority,
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseSequenceBoundary,
} from "./lease-sequence-authority.js";

export async function restoreVpnLeaseState({
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
  const minimumSequence = await sequenceAuthority.readMinimumSequence(gatewayId);
  validateVpnLeaseSequenceBoundary({ gatewayId, sequence: minimumSequence });

  const snapshot = await stateStore.load({ minimumSequence });
  validateVpnLeaseSequenceBoundary({ gatewayId, sequence: snapshot.sequence });
  if (snapshot.sequence !== minimumSequence ||
      await sequenceAuthority.readMinimumSequence(gatewayId) !== snapshot.sequence) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_RESTORE_UNCONFIRMED");
  }
  await sequenceAuthority.assertCommittedSnapshot(gatewayId, snapshot.sequence, vpnLeaseSnapshotDigest(snapshot.state));
  core.restoreState(snapshot.state);

  return Object.freeze({
    gatewayId,
    sequence: snapshot.sequence,
    restored: true,
  });
}

export function isVpnLeaseSequenceAuthority(value) {
  return value instanceof VpnLeaseSequenceAuthority;
}
