import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
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
  const sequence = await stateStore.save(core.exportState());
  validateVpnLeaseSequenceBoundary({ gatewayId, sequence });

  try {
    await sequenceAuthority.commitSequence(gatewayId, sequence);
  } catch {
    throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_FAILED");
  }

  return Object.freeze({ gatewayId, sequence, persisted: true });
}
