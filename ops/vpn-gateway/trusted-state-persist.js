import { VpnGatewayProvisioningCore } from "./provisioning-core.js";
import { VpnLeaseStateStore } from "./lease-state-store.js";
import {
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseCommitBoundary,
} from "./lease-sequence-authority.js";

const MAX_AUTHORITY_ATTEMPTS = 2;
const TERMINAL_AUTHORITY_ERRORS = new Set([
  "VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED",
  "VPN_SEQUENCE_AUTHORITY_SNAPSHOT_CONFLICT",
  "VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR",
  "VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID",
  "VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED",
  "VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED",
]);

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
  const receipt = await stateStore.saveWithReceipt(core.exportState());
  const boundary = validateVpnLeaseCommitBoundary({
    gatewayId,
    sequence: receipt.sequence,
    snapshotDigest: receipt.snapshotDigest,
  });

  let lastError = null;
  for (let attempt = 1; attempt <= MAX_AUTHORITY_ATTEMPTS; attempt += 1) {
    try {
      await sequenceAuthority.commitSnapshot(
        boundary.gatewayId,
        boundary.sequence,
        boundary.snapshotDigest
      );
      const committed = await sequenceAuthority.readCommit(boundary.gatewayId);
      const verified = validateVpnLeaseCommitBoundary({
        gatewayId: boundary.gatewayId,
        sequence: committed?.sequence,
        snapshotDigest: committed?.snapshotDigest,
      });
      if (
        verified.sequence !== boundary.sequence ||
        verified.snapshotDigest !== boundary.snapshotDigest
      ) {
        throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
      }
      return Object.freeze({
        gatewayId: boundary.gatewayId,
        sequence: boundary.sequence,
        snapshotDigest: boundary.snapshotDigest,
        persisted: true,
      });
    } catch (error) {
      lastError = error;
      if (TERMINAL_AUTHORITY_ERRORS.has(error?.message)) throw error;
      if (attempt === MAX_AUTHORITY_ATTEMPTS) break;
    }
  }

  const failure = new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_FAILED");
  if (lastError) failure.cause = lastError;
  throw failure;
}
