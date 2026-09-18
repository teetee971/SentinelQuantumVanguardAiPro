const REQUIRED_CHECKS = Object.freeze([
  "udpReachable",
  "provisioning",
  "revocation",
  "ipv4Egress",
  "ipv6Egress",
  "dnsPath",
  "countryMatch",
  "reconnect",
  "mtu",
  "monitoring",
  "rateLimiting",
  "keyRotation",
]);

export class VpnGatewayAcceptanceGate {
  #maxEvidenceAgeMs;

  constructor({ maxEvidenceAgeMs = 5 * 60 * 1000 } = {}) {
    if (!Number.isSafeInteger(maxEvidenceAgeMs) || maxEvidenceAgeMs < 30_000 || maxEvidenceAgeMs > 60 * 60 * 1000) {
      throw new Error("VPN_ACCEPTANCE_EVIDENCE_AGE_INVALID");
    }
    this.#maxEvidenceAgeMs = maxEvidenceAgeMs;
  }

  evaluate({ gatewayId, expectedCountryCode, evidence, now = Date.now() }) {
    if (!/^[a-z0-9][a-z0-9-]{1,62}$/.test(String(gatewayId || ""))) {
      return this.#degraded("VPN_ACCEPTANCE_GATEWAY_ID_INVALID");
    }
    if (!/^[A-Z]{2}$/.test(String(expectedCountryCode || ""))) {
      return this.#degraded("VPN_ACCEPTANCE_COUNTRY_INVALID");
    }
    if (!Number.isSafeInteger(now) || now < 0 || !evidence || typeof evidence !== "object" || Array.isArray(evidence)) {
      return this.#degraded("VPN_ACCEPTANCE_INPUT_INVALID");
    }

    const missing = [];
    const failed = [];
    const stale = [];

    for (const name of REQUIRED_CHECKS) {
      const check = evidence[name];
      if (!check || typeof check !== "object" || Array.isArray(check)) {
        missing.push(name);
        continue;
      }
      if (check.passed !== true) failed.push(name);
      if (!Number.isSafeInteger(check.checkedAtMs) || check.checkedAtMs < 0 ||
          check.checkedAtMs > now || now - check.checkedAtMs > this.#maxEvidenceAgeMs) {
        stale.push(name);
      }
    }

    const country = evidence.countryMatch;
    if (country && country.passed === true && country.observedCountryCode !== expectedCountryCode) {
      failed.push("countryMatch");
    }

    const uniqueFailed = [...new Set(failed)].sort();
    const uniqueStale = [...new Set(stale)].sort();
    if (missing.length || uniqueFailed.length || uniqueStale.length) {
      return Object.freeze({
        accepted: false,
        state: "DEGRADED",
        reason: "VPN_ACCEPTANCE_INCOMPLETE",
        gatewayId,
        missing: Object.freeze(missing.sort()),
        failed: Object.freeze(uniqueFailed),
        stale: Object.freeze(uniqueStale),
      });
    }

    return Object.freeze({
      accepted: true,
      state: "AVAILABLE",
      reason: "VPN_ACCEPTANCE_PASSED",
      gatewayId,
      checkedAtMs: now,
    });
  }

  #degraded(reason) {
    return Object.freeze({
      accepted: false,
      state: "DEGRADED",
      reason,
      missing: Object.freeze([]),
      failed: Object.freeze([]),
      stale: Object.freeze([]),
    });
  }
}

export const vpnGatewayAcceptanceRequiredChecks = REQUIRED_CHECKS;
