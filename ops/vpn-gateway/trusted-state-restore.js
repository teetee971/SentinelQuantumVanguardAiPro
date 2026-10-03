import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
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
  const authoritativeSequence = await sequenceAuthority.readMinimumSequence(gatewayId);
  validateVpnLeaseSequenceBoundary({ gatewayId, sequence: authoritativeSequence });

  const snapshot = await stateStore.load({ minimumSequence: authoritativeSequence });
  validateVpnLeaseSequenceBoundary({ gatewayId, sequence: snapshot.sequence });

  // The external monotonic authority is the commit record, not merely a loose lower bound.
  // Accepting a locally signed snapshot ahead of it would allow a snapshot written before a
  // failed authority commit to become trusted after restart. Exact equality closes that window.
  if (snapshot.sequence !== authoritativeSequence) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STATE_MISMATCH");
  }

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
