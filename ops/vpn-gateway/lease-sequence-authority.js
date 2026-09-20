const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;

export class VpnLeaseSequenceAuthority {
  async readMinimumSequence(_gatewayId) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async commitSequence(_gatewayId, _sequence) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }
}

export function assertVpnLeaseSequenceAuthority(authority) {
  if (!(authority instanceof VpnLeaseSequenceAuthority)) {
    throw new TypeError("VpnLeaseSequenceAuthority required");
  }
  return authority;
}

export function validateVpnLeaseSequenceBoundary({ gatewayId, sequence }) {
  if (!GATEWAY_ID.test(String(gatewayId || ""))) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID");
  }
  if (!Number.isSafeInteger(sequence) || sequence < 1) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID");
  }
  return Object.freeze({ gatewayId, sequence });
}
