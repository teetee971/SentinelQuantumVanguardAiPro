import { createHash, randomBytes } from "node:crypto";

const MAX_METADATA_BYTES = 128 * 1024;
const MAX_ALLOWED_HOSTS = 16;
const MAX_SCOPES = 32;

function normalizeHttpsUrl(raw, name) {
  let url;
  try {
    url = new URL(String(raw || ""));
  } catch {
    throw new Error(`${name} invalid`);
  }
  if (url.protocol !== "https:") throw new Error(`${name} must use https`);
  if (url.username || url.password || url.hash || url.search) throw new Error(`${name} contains forbidden components`);
  if (url.href.length > 2048) throw new Error(`${name} too long`);
  return url;
}

function base64Url(bytes) {
  return Buffer.from(bytes).toString("base64url");
}

async function readBoundedJson(response) {
  const declared = Number(response.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > MAX_METADATA_BYTES) {
    throw new Error("oidc metadata too large");
  }
  if (!response.body) throw new Error("oidc metadata body missing");

  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_METADATA_BYTES) {
      await reader.cancel();
      throw new Error("oidc metadata too large");
    }
    chunks.push(Buffer.from(value));
  }

  let parsed;
  try {
    parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new Error("oidc metadata invalid json");
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("oidc metadata invalid");
  }
  return parsed;
}

function validateEndpoint(raw, allowedHosts, name) {
  const url = normalizeHttpsUrl(raw, name);
  if (!allowedHosts.has(url.hostname.toLowerCase())) {
    throw new Error(`${name} host not allowed`);
  }
  return url.href;
}

export class OidcProvider {
  #issuer;
  #allowedHosts;
  #fetch;

  constructor({ issuer, allowedHosts, fetchImpl = globalThis.fetch }) {
    this.#issuer = normalizeHttpsUrl(issuer, "oidc issuer");
    const hosts = new Set(
      [...(allowedHosts || [])]
        .map(v => String(v).trim().toLowerCase())
        .filter(Boolean)
    );
    if (!hosts.size || hosts.size > MAX_ALLOWED_HOSTS) throw new Error("oidc allowed hosts invalid");
    if (!hosts.has(this.#issuer.hostname.toLowerCase())) throw new Error("oidc issuer host not allowed");
    if (typeof fetchImpl !== "function") throw new Error("oidc fetch implementation required");
    this.#allowedHosts = hosts;
    this.#fetch = fetchImpl;
  }

  issuer() {
    return this.#issuer.href;
  }

  discoveryUrl() {
    const base = this.#issuer.href.endsWith("/") ? this.#issuer.href : `${this.#issuer.href}/`;
    return new URL(".well-known/openid-configuration", base).href;
  }

  async discover() {
    const response = await this.#fetch(this.discoveryUrl(), {
      method: "GET",
      redirect: "manual",
      headers: {
        accept: "application/json",
        "cache-control": "no-cache",
      },
    });
    if (!response || response.status !== 200) throw new Error("oidc discovery http failure");
    if (response.type === "opaqueredirect" || (response.status >= 300 && response.status < 400)) {
      throw new Error("oidc discovery redirect forbidden");
    }

    const metadata = await readBoundedJson(response);
    const metadataIssuer = normalizeHttpsUrl(metadata.issuer, "oidc metadata issuer").href;
    if (metadataIssuer !== this.#issuer.href) throw new Error("oidc issuer mismatch");

    const authorizationEndpoint = validateEndpoint(
      metadata.authorization_endpoint,
      this.#allowedHosts,
      "oidc authorization endpoint"
    );
    const tokenEndpoint = validateEndpoint(
      metadata.token_endpoint,
      this.#allowedHosts,
      "oidc token endpoint"
    );
    const jwksUri = validateEndpoint(
      metadata.jwks_uri,
      this.#allowedHosts,
      "oidc jwks uri"
    );

    const responseTypes = Array.isArray(metadata.response_types_supported)
      ? metadata.response_types_supported.map(String)
      : [];
    if (!responseTypes.includes("code")) throw new Error("oidc authorization code unsupported");

    const challengeMethods = Array.isArray(metadata.code_challenge_methods_supported)
      ? metadata.code_challenge_methods_supported.map(String)
      : [];
    if (!challengeMethods.includes("S256")) throw new Error("oidc pkce s256 unsupported");

    return Object.freeze({
      issuer: metadataIssuer,
      authorizationEndpoint,
      tokenEndpoint,
      jwksUri,
      responseTypes: Object.freeze([...responseTypes]),
      codeChallengeMethods: Object.freeze([...challengeMethods]),
    });
  }

  createAuthorizationRequest({
    metadata,
    clientId,
    redirectUri,
    scopes = ["openid", "profile", "email"],
  }) {
    if (!metadata || metadata.issuer !== this.#issuer.href) throw new Error("oidc metadata not bound to provider");
    const client = String(clientId || "").trim();
    if (!client || client.length > 512) throw new Error("oidc client id invalid");
    const redirect = normalizeHttpsUrl(redirectUri, "oidc redirect uri");
    if (!this.#allowedHosts.has(redirect.hostname.toLowerCase())) {
      throw new Error("oidc redirect host not allowed");
    }

    if (!Array.isArray(scopes) || !scopes.length || scopes.length > MAX_SCOPES) {
      throw new Error("oidc scopes invalid");
    }
    const normalizedScopes = [...new Set(scopes.map(v => String(v).trim()).filter(Boolean))];
    if (!normalizedScopes.includes("openid")) throw new Error("oidc openid scope required");
    if (normalizedScopes.some(v => !/^[A-Za-z0-9._:-]{1,128}$/.test(v))) {
      throw new Error("oidc scope invalid");
    }

    const state = base64Url(randomBytes(32));
    const nonce = base64Url(randomBytes(32));
    const verifier = base64Url(randomBytes(48));
    const challenge = base64Url(createHash("sha256").update(verifier, "ascii").digest());

    const url = new URL(metadata.authorizationEndpoint);
    url.searchParams.set("response_type", "code");
    url.searchParams.set("client_id", client);
    url.searchParams.set("redirect_uri", redirect.href);
    url.searchParams.set("scope", normalizedScopes.join(" "));
    url.searchParams.set("state", state);
    url.searchParams.set("nonce", nonce);
    url.searchParams.set("code_challenge", challenge);
    url.searchParams.set("code_challenge_method", "S256");

    return Object.freeze({
      authorizationUrl: url.href,
      state,
      nonce,
      codeVerifier: verifier,
      redirectUri: redirect.href,
    });
  }
}

export const oidcProviderLimits = Object.freeze({
  maxMetadataBytes: MAX_METADATA_BYTES,
  maxAllowedHosts: MAX_ALLOWED_HOSTS,
  maxScopes: MAX_SCOPES,
});
