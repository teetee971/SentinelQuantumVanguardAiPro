import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, verify } from "node:crypto";
import { VpnGatewayAcceptanceGate, vpnGatewayAcceptanceRequiredChecks } from "./gateway-acceptance.js";
import {
  buildVpnGatewayCatalogPayload,
  signVpnGatewayCatalog,
  vpnGatewayCatalogPublisherConstants,
} from "./catalog-publication.js";

const NOW = 2_000_000_000_000;
const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });

function evidence(country = "FR") {
  return Object.fromEntries(
    vpnGatewayAcceptanceRequiredChecks.map(name => [
      name,
      {
        passed: true,
        checkedAtMs: NOW - 1_000,
        ...(name === "countryMatch" ? { observedCountryCode: country } : {}),
      },
    ])
  );
}

function accepted(id = "fr-par-01", country = "FR") {
  return new VpnGatewayAcceptanceGate().evaluate({
    gatewayId: id,
    expectedCountryCode: country,
    evidence: evidence(country),
    now: NOW,
  });
}

function available(id = "fr-par-01", countryCode = "FR") {
  return {
    id,
    countryCode,
    status: "AVAILABLE",
    endpointHostname: "vpn-fr.example.com",
    endpointPort: 51820,
    ipv4: true,
    ipv6: true,
    loadPercent: 20,
    latencyMs: 15,
    healthCheckedAtMs: NOW - 1_000,
    dnsServerAddresses: ["fd73:1::1", "10.73.0.1"],
    acceptance: accepted(id, countryCode),
  };
}

test("publisher emits exact Android-compatible signed envelope", () => {
  const signed = signVpnGatewayCatalog({
    privateKey,
    sequence: 9,
    issuedAtMs: NOW - 1_000,
    expiresAtMs: NOW + 60_000,
    issuerId: "sentinel-vpn",
    keyId: "catalog-key-1",
    gateways: [available()],
  });

  assert.equal(
    verify("sha256", signed.payload, publicKey, signed.signature),
    true
  );
  const text = signed.payload.toString("utf8");
  assert.match(text, /^sentinel-vpn-gateway-catalog-v1\n/);
  assert.match(text, /catalog_id=public-vpn/);
  assert.match(text, /gateway=fr-par-01\|FR\|AVAILABLE\|vpn-fr\.example\.com\|51820\|1\|1\|20\|15\|1999999999000\|10\.73\.0\.1,fd73:1::1/);

  const lines = signed.envelope.split("\n");
  assert.equal(lines.length, 3);
  assert.equal(lines[0], "key_id=catalog-key-1");
  assert.ok(lines[1].startsWith("payload_hex="));
  assert.ok(lines[2].startsWith("signature_hex="));
});

test("publisher sorts gateway ids canonically", () => {
  const payload = buildVpnGatewayCatalogPayload({
    sequence: 1,
    issuedAtMs: NOW,
    expiresAtMs: NOW + 60_000,
    issuerId: "sentinel-vpn",
    keyId: "catalog-key-1",
    gateways: [
      { id: "us-nyc-01", countryCode: "US", status: "PLANNED", ipv4: true, ipv6: true },
      { id: "fr-par-01", countryCode: "FR", status: "PLANNED", ipv4: true, ipv6: true },
    ],
  }).toString("utf8");

  const gateways = payload.split("\n").filter(line => line.startsWith("gateway="));
  assert.equal(gateways[0].startsWith("gateway=fr-par-01|"), true);
  assert.equal(gateways[1].startsWith("gateway=us-nyc-01|"), true);
});

test("AVAILABLE is rejected without a matching positive acceptance result", () => {
  for (const acceptance of [
    undefined,
    { accepted: false, state: "DEGRADED", gatewayId: "fr-par-01", reason: "VPN_ACCEPTANCE_INCOMPLETE" },
    { accepted: true, state: "AVAILABLE", gatewayId: "de-fra-01", reason: "VPN_ACCEPTANCE_PASSED" },
  ]) {
    assert.throws(
      () => buildVpnGatewayCatalogPayload({
        sequence: 1,
        issuedAtMs: NOW,
        expiresAtMs: NOW + 60_000,
        issuerId: "sentinel-vpn",
        keyId: "catalog-key-1",
        gateways: [{ ...available(), acceptance }],
      }),
      /VPN_CATALOG_AVAILABLE_WITHOUT_ACCEPTANCE/
    );
  }
});

test("DEGRADED and PLANNED records do not manufacture production metadata", () => {
  const payload = buildVpnGatewayCatalogPayload({
    sequence: 2,
    issuedAtMs: NOW,
    expiresAtMs: NOW + 60_000,
    issuerId: "sentinel-vpn",
    keyId: "catalog-key-1",
    gateways: [
      { id: "be-bru-01", countryCode: "BE", status: "DEGRADED", ipv4: true, ipv6: true },
      { id: "fr-par-01", countryCode: "FR", status: "PLANNED", ipv4: true, ipv6: true },
    ],
  }).toString("utf8");

  assert.match(payload, /gateway=be-bru-01\|BE\|DEGRADED\|-\|-\|1\|1\|-\|-\|-\|-/);
  assert.match(payload, /gateway=fr-par-01\|FR\|PLANNED\|-\|-\|1\|1\|-\|-\|-\|-/);
});

test("duplicate gateways, invalid lifetimes and non-P256 signing keys fail closed", () => {
  assert.throws(
    () => buildVpnGatewayCatalogPayload({
      sequence: 1,
      issuedAtMs: NOW,
      expiresAtMs: NOW + 60_000,
      issuerId: "sentinel-vpn",
      keyId: "catalog-key-1",
      gateways: [
        { id: "fr-par-01", countryCode: "FR", status: "PLANNED" },
        { id: "fr-par-01", countryCode: "FR", status: "PLANNED" },
      ],
    }),
    /VPN_CATALOG_GATEWAY_DUPLICATE/
  );

  assert.throws(
    () => buildVpnGatewayCatalogPayload({
      sequence: 1,
      issuedAtMs: NOW,
      expiresAtMs: NOW + vpnGatewayCatalogPublisherConstants.maxLifetimeMs + 1,
      issuerId: "sentinel-vpn",
      keyId: "catalog-key-1",
    }),
    /VPN_CATALOG_LIFETIME_INVALID/
  );

  const rsa = generateKeyPairSync("rsa", { modulusLength: 2048 });
  assert.throws(
    () => signVpnGatewayCatalog({
      privateKey: rsa.privateKey,
      sequence: 1,
      issuedAtMs: NOW,
      expiresAtMs: NOW + 60_000,
      issuerId: "sentinel-vpn",
      keyId: "catalog-key-1",
    }),
    /VPN_CATALOG_SIGNING_KEY_INVALID/
  );
});
