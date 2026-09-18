import test from "node:test";
import assert from "node:assert/strict";
import {
  VpnGatewayAcceptanceGate,
  vpnGatewayAcceptanceRequiredChecks,
} from "./gateway-acceptance.js";

const NOW = 2_000_000_000_000;

function fullEvidence(overrides = {}) {
  const evidence = Object.fromEntries(
    vpnGatewayAcceptanceRequiredChecks.map(name => [
      name,
      {
        passed: true,
        checkedAtMs: NOW - 1_000,
        ...(name === "countryMatch" ? { observedCountryCode: "FR" } : {}),
      },
    ])
  );
  return { ...evidence, ...overrides };
}

test("accepts only a complete fresh evidence set for the advertised country", () => {
  const gate = new VpnGatewayAcceptanceGate();
  const result = gate.evaluate({
    gatewayId: "fr-par-01",
    expectedCountryCode: "FR",
    evidence: fullEvidence(),
    now: NOW,
  });
  assert.equal(result.accepted, true);
  assert.equal(result.state, "AVAILABLE");
  assert.equal(result.reason, "VPN_ACCEPTANCE_PASSED");
});

test("missing failed and stale evidence keep the gateway degraded", () => {
  const gate = new VpnGatewayAcceptanceGate();
  const evidence = fullEvidence({
    ipv6Egress: { passed: false, checkedAtMs: NOW - 1_000 },
    dnsPath: { passed: true, checkedAtMs: NOW - 600_000 },
  });
  delete evidence.keyRotation;

  const result = gate.evaluate({
    gatewayId: "fr-par-01",
    expectedCountryCode: "FR",
    evidence,
    now: NOW,
  });

  assert.equal(result.accepted, false);
  assert.equal(result.state, "DEGRADED");
  assert.deepEqual(result.failed, ["ipv6Egress"]);
  assert.deepEqual(result.stale, ["dnsPath"]);
  assert.deepEqual(result.missing, ["keyRotation"]);
});

test("country mismatch can never be promoted to AVAILABLE", () => {
  const gate = new VpnGatewayAcceptanceGate();
  const result = gate.evaluate({
    gatewayId: "fr-par-01",
    expectedCountryCode: "FR",
    evidence: fullEvidence({
      countryMatch: {
        passed: true,
        checkedAtMs: NOW - 1_000,
        observedCountryCode: "DE",
      },
    }),
    now: NOW,
  });
  assert.equal(result.accepted, false);
  assert.deepEqual(result.failed, ["countryMatch"]);
});

test("future-dated evidence fails freshness", () => {
  const gate = new VpnGatewayAcceptanceGate();
  const evidence = fullEvidence({
    udpReachable: { passed: true, checkedAtMs: NOW + 1 },
  });
  const result = gate.evaluate({
    gatewayId: "fr-par-01",
    expectedCountryCode: "FR",
    evidence,
    now: NOW,
  });
  assert.equal(result.accepted, false);
  assert.deepEqual(result.stale, ["udpReachable"]);
});

test("invalid gateway and country identifiers fail closed", () => {
  const gate = new VpnGatewayAcceptanceGate();
  assert.equal(gate.evaluate({
    gatewayId: "INVALID!",
    expectedCountryCode: "FR",
    evidence: fullEvidence(),
    now: NOW,
  }).reason, "VPN_ACCEPTANCE_GATEWAY_ID_INVALID");

  assert.equal(gate.evaluate({
    gatewayId: "fr-par-01",
    expectedCountryCode: "fr",
    evidence: fullEvidence(),
    now: NOW,
  }).reason, "VPN_ACCEPTANCE_COUNTRY_INVALID");
});
