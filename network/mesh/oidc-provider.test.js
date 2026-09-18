import test from "node:test";
import assert from "node:assert/strict";
import { OidcProvider } from "./oidc-provider.js";

function responseJson(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const metadata = {
  issuer: "https://id.example.test/tenant",
  authorization_endpoint: "https://id.example.test/authorize",
  token_endpoint: "https://id.example.test/token",
  jwks_uri: "https://keys.example.test/jwks",
  response_types_supported: ["code"],
  code_challenge_methods_supported: ["S256"],
};

test("discovers an exact issuer with allowlisted HTTPS endpoints", async () => {
  const provider = new OidcProvider({
    issuer: metadata.issuer,
    allowedHosts: ["id.example.test", "keys.example.test"],
    fetchImpl: async (url, options) => {
      assert.equal(url, "https://id.example.test/tenant/.well-known/openid-configuration");
      assert.equal(options.redirect, "manual");
      return responseJson(metadata);
    },
  });

  const discovered = await provider.discover();
  assert.equal(discovered.issuer, metadata.issuer);
  assert.equal(discovered.jwksUri, metadata.jwks_uri);
});

test("rejects issuer mismatch and off-allowlist endpoints", async () => {
  const mismatch = new OidcProvider({
    issuer: metadata.issuer,
    allowedHosts: ["id.example.test", "keys.example.test"],
    fetchImpl: async () => responseJson({ ...metadata, issuer: "https://id.example.test/other" }),
  });
  await assert.rejects(() => mismatch.discover(), /issuer mismatch/);

  const offHost = new OidcProvider({
    issuer: metadata.issuer,
    allowedHosts: ["id.example.test"],
    fetchImpl: async () => responseJson(metadata),
  });
  await assert.rejects(() => offHost.discover(), /jwks uri host not allowed/);
});

test("requires authorization code and PKCE S256 support", async () => {
  const provider = new OidcProvider({
    issuer: metadata.issuer,
    allowedHosts: ["id.example.test", "keys.example.test"],
    fetchImpl: async () => responseJson({
      ...metadata,
      code_challenge_methods_supported: ["plain"],
    }),
  });
  await assert.rejects(() => provider.discover(), /pkce s256 unsupported/);
});

test("builds bounded authorization code request with PKCE state and nonce", async () => {
  const provider = new OidcProvider({
    issuer: metadata.issuer,
    allowedHosts: ["id.example.test", "keys.example.test", "mesh.example.test"],
    fetchImpl: async () => responseJson(metadata),
  });
  const discovered = await provider.discover();
  const request = provider.createAuthorizationRequest({
    metadata: discovered,
    clientId: "sentinel-mesh",
    redirectUri: "https://mesh.example.test/oidc/callback",
  });

  const url = new URL(request.authorizationUrl);
  assert.equal(url.searchParams.get("response_type"), "code");
  assert.equal(url.searchParams.get("code_challenge_method"), "S256");
  assert.ok(request.state.length >= 32);
  assert.ok(request.nonce.length >= 32);
  assert.ok(request.codeVerifier.length >= 43);
  assert.notEqual(request.codeVerifier, url.searchParams.get("code_challenge"));
});

test("refuses insecure issuer and redirect URLs", async () => {
  assert.throws(() => new OidcProvider({
    issuer: "http://id.example.test",
    allowedHosts: ["id.example.test"],
  }), /must use https/);

  const provider = new OidcProvider({
    issuer: metadata.issuer,
    allowedHosts: ["id.example.test", "keys.example.test"],
    fetchImpl: async () => responseJson(metadata),
  });
  const discovered = await provider.discover();
  assert.throws(() => provider.createAuthorizationRequest({
    metadata: discovered,
    clientId: "sentinel",
    redirectUri: "https://evil.example.test/callback",
  }), /redirect host not allowed/);
});
