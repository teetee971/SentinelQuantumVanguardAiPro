import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
  VpnLeaseSequenceAuthority,
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseCommitBoundary,
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
  const authorityCommit = await sequenceAuthority.readCommit(gatewayId);
  const authoritative = validateVpnLeaseCommitBoundary({
    gatewayId,
    sequence: authorityCommit?.sequence,
    snapshotDigest: authorityCommit?.snapshotDigest,
  });

  const snapshot = await stateStore.loadWithReceipt({ minimumSequence: authoritative.sequence });
  const local = validateVpnLeaseCommitBoundary({
    gatewayId,
    sequence: snapshot.sequence,
    snapshotDigest: snapshot.snapshotDigest,
  });

  if (
    local.sequence !== authoritative.sequence ||
    local.snapshotDigest !== authoritative.snapshotDigest
  ) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STATE_MISMATCH");
  }

  core.restoreState(snapshot.state);

  return Object.freeze({
    gatewayId,
    sequence: snapshot.sequence,
    snapshotDigest: snapshot.snapshotDigest,
    restored: true,
  });
}

export function isVpnLeaseSequenceAuthority(value) {
  return value instanceof VpnLeaseSequenceAuthority;
}
