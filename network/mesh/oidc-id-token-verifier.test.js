import test from "node:test";
import assert from "node:assert/strict";
import {
  generateKeyPairSync,
  sign,
} from "node:crypto";
import { OidcIdTokenVerifier } from "./oidc-id-token-verifier.js";

function b64json(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function makeRs256Token({ privateKey, kid, claims }) {
  const header = b64json({ alg: "RS256", kid, typ: "JWT" });
  const payload = b64json(claims);
  const input = `${header}.${payload}`;
  const signature = sign("RSA-SHA256", Buffer.from(input, "ascii"), privateKey).toString("base64url");
  return `${input}.${signature}`;
}

const issuer = "https://id.example.test/tenant";
const clientId = "sentinel-mesh";
const nonce = "nonce-value-123";
const nowMs = 1_700_000_000_000;

function fixture() {
  const { publicKey, privateKey } = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicExponent: 0x10001,
  });
  const kid = "key-1";
  const jwk = publicKey.export({ format: "jwk" });
  jwk.kid = kid;
  jwk.alg = "RS256";
  jwk.use = "sig";

  const claims = {
    iss: issuer,
    sub: "user-123",
    aud: clientId,
    exp: Math.floor(nowMs / 1000) + 600,
    iat: Math.floor(nowMs / 1000) - 10,
    nonce,
    email: "user@example.test",
    email_verified: true,
  };
  const token = makeRs256Token({ privateKey, kid, claims });
  return { jwk, token, claims, privateKey, kid };
}

function verifierFor(jwk) {
  return new OidcIdTokenVerifier({
    clock: () => nowMs,
    allowedHosts: ["keys.example.test"],
    fetchImpl: async () => new Response(JSON.stringify({ keys: [jwk] }), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
  });
}

test("verifies RS256 signature and required OIDC claims", async () => {
  const { jwk, token } = fixture();
  const identity = await verifierFor(jwk).verify({
    token,
    metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
    clientId,
    nonce,
  });
  assert.equal(identity.subject, "user-123");
  assert.equal(identity.email, "user@example.test");
  assert.equal(identity.emailVerified, true);
});

test("rejects audience nonce issuer and expiry mismatches", async () => {
  const { jwk, privateKey, kid, claims } = fixture();
  const verifier = verifierFor(jwk);

  for (const changed of [
    { ...claims, aud: "other-client" },
    { ...claims, nonce: "wrong-nonce" },
    { ...claims, iss: "https://id.example.test/other" },
    { ...claims, exp: Math.floor(nowMs / 1000) - 120 },
  ]) {
    const token = makeRs256Token({ privateKey, kid, claims: changed });
    await assert.rejects(() => verifier.verify({
      token,
      metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
      clientId,
      nonce,
    }));
  }
});

test("requires azp when multiple audiences are present", async () => {
  const { jwk, privateKey, kid, claims } = fixture();
  const verifier = verifierFor(jwk);
  const bad = makeRs256Token({
    privateKey,
    kid,
    claims: { ...claims, aud: [clientId, "other"] },
  });
  await assert.rejects(() => verifier.verify({
    token: bad,
    metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
    clientId,
    nonce,
  }), /audience mismatch/);

  const good = makeRs256Token({
    privateKey,
    kid,
    claims: { ...claims, aud: [clientId, "other"], azp: clientId },
  });
  const identity = await verifier.verify({
    token: good,
    metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
    clientId,
    nonce,
  });
  assert.equal(identity.subject, claims.sub);
});

test("rejects forged signatures and unknown keys", async () => {
  const { token } = fixture();
  const other = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const otherJwk = other.publicKey.export({ format: "jwk" });
  otherJwk.kid = "other";
  otherJwk.alg = "RS256";
  otherJwk.use = "sig";

  await assert.rejects(() => verifierFor(otherJwk).verify({
    token,
    metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
    clientId,
    nonce,
  }), /signing key not found/);
});

test("rejects algorithm substitution before signature processing", async () => {
  const { jwk, claims } = fixture();
  const header = b64json({ alg: "none", kid: "key-1", typ: "JWT" });
  const payload = b64json(claims);
  const token = `${header}.${payload}.AA`;

  await assert.rejects(() => verifierFor(jwk).verify({
    token,
    metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
    clientId,
    nonce,
  }), /alg forbidden/);
});


test("verifies ES256 with IEEE-P1363 JWS signatures", async () => {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const jwk = publicKey.export({ format: "jwk" });
  jwk.kid = "ec-key-1";
  jwk.alg = "ES256";
  jwk.use = "sig";

  const header = b64json({ alg: "ES256", kid: "ec-key-1", typ: "JWT" });
  const payload = b64json({
    iss: issuer,
    sub: "user-es256",
    aud: clientId,
    exp: Math.floor(nowMs / 1000) + 600,
    iat: Math.floor(nowMs / 1000) - 10,
    nonce,
  });
  const input = `${header}.${payload}`;
  const signature = sign(
    "sha256",
    Buffer.from(input, "ascii"),
    { key: privateKey, dsaEncoding: "ieee-p1363" }
  ).toString("base64url");
  const token = `${input}.${signature}`;

  const verifier = new OidcIdTokenVerifier({
    clock: () => nowMs,
    allowedHosts: ["keys.example.test"],
    allowedAlgs: ["ES256"],
    fetchImpl: async () => new Response(JSON.stringify({ keys: [jwk] }), { status: 200 }),
  });

  const identity = await verifier.verify({
    token,
    metadata: { issuer, jwksUri: "https://keys.example.test/jwks" },
    clientId,
    nonce,
  });
  assert.equal(identity.subject, "user-es256");
});

test("refuses JWKS hosts outside the verifier allowlist", async () => {
  const { jwk, token } = fixture();
  const verifier = new OidcIdTokenVerifier({
    clock: () => nowMs,
    allowedHosts: ["keys.example.test"],
    fetchImpl: async () => new Response(JSON.stringify({ keys: [jwk] }), { status: 200 }),
  });
  await assert.rejects(() => verifier.verify({
    token,
    metadata: { issuer, jwksUri: "https://evil.example.test/jwks" },
    clientId,
    nonce,
  }), /jwks host not allowed/);
});
