import { createPublicKey, verify as verifySignature } from "node:crypto";

const MAX_TOKEN_CHARS = 16 * 1024;
const MAX_JWKS_BYTES = 256 * 1024;
const MAX_KEYS = 64;
const DEFAULT_CLOCK_SKEW_SECONDS = 60;
const SUPPORTED_ALGS = new Set(["RS256", "ES256"]);

function decodePart(part, name) {
  if (typeof part !== "string" || !/^[A-Za-z0-9_-]+$/.test(part) || part.length > MAX_TOKEN_CHARS) {
    throw new Error(`oidc ${name} invalid`);
  }
  const bytes = Buffer.from(part, "base64url");
  if (!bytes.length || bytes.toString("base64url") !== part) throw new Error(`oidc ${name} encoding invalid`);
  let parsed;
  try {
    parsed = JSON.parse(bytes.toString("utf8"));
  } catch {
    throw new Error(`oidc ${name} json invalid`);
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error(`oidc ${name} object invalid`);
  return parsed;
}

async function readBoundedJson(response) {
  const declared = Number(response.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > MAX_JWKS_BYTES) throw new Error("oidc jwks too large");
  if (!response.body) throw new Error("oidc jwks body missing");
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_JWKS_BYTES) {
      await reader.cancel();
      throw new Error("oidc jwks too large");
    }
    chunks.push(Buffer.from(value));
  }
  let parsed;
  try {
    parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new Error("oidc jwks invalid json");
  }
  return parsed;
}

function audienceAllows(aud, clientId, azp) {
  if (typeof aud === "string") return aud === clientId;
  if (!Array.isArray(aud) || !aud.length || aud.some(v => typeof v !== "string")) return false;
  if (!aud.includes(clientId)) return false;
  if (aud.length > 1 && azp !== clientId) return false;
  return true;
}

function numericDate(value, name, required = false) {
  if (value === undefined && !required) return null;
  if (!Number.isInteger(value) || value < 0 || value > Number.MAX_SAFE_INTEGER) {
    throw new Error(`oidc ${name} invalid`);
  }
  return value;
}

export class OidcIdTokenVerifier {
  #fetch;
  #clock;
  #clockSkewSeconds;
  #allowedAlgs;

  constructor({
    fetchImpl = globalThis.fetch,
    clock = () => Date.now(),
    clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS,
    allowedAlgs = ["RS256", "ES256"],
  } = {}) {
    if (typeof fetchImpl !== "function") throw new Error("oidc fetch implementation required");
    if (typeof clock !== "function") throw new Error("oidc clock required");
    if (!Number.isInteger(clockSkewSeconds) || clockSkewSeconds < 0 || clockSkewSeconds > 300) {
      throw new Error("oidc clock skew invalid");
    }
    const normalized = [...new Set(allowedAlgs.map(String))];
    if (!normalized.length || normalized.some(alg => !SUPPORTED_ALGS.has(alg))) {
      throw new Error("oidc allowed algorithms invalid");
    }
    this.#fetch = fetchImpl;
    this.#clock = clock;
    this.#clockSkewSeconds = clockSkewSeconds;
    this.#allowedAlgs = new Set(normalized);
  }

  async verify({ token, metadata, clientId, nonce }) {
    if (typeof token !== "string" || token.length < 16 || token.length > MAX_TOKEN_CHARS) {
      throw new Error("oidc id token size invalid");
    }
    const parts = token.split(".");
    if (parts.length !== 3) throw new Error("oidc id token format invalid");
    const [encodedHeader, encodedPayload, encodedSignature] = parts;
    const header = decodePart(encodedHeader, "header");
    const claims = decodePart(encodedPayload, "payload");

    const alg = String(header.alg || "");
    const kid = String(header.kid || "");
    if (!this.#allowedAlgs.has(alg)) throw new Error("oidc id token alg forbidden");
    if (!kid || kid.length > 256) throw new Error("oidc id token kid invalid");
    if (header.typ !== undefined && header.typ !== "JWT") throw new Error("oidc id token typ invalid");

    const issuer = String(metadata?.issuer || "");
    const jwksUri = String(metadata?.jwksUri || "");
    if (!issuer.startsWith("https://") || !jwksUri.startsWith("https://")) {
      throw new Error("oidc metadata invalid");
    }
    const client = String(clientId || "").trim();
    if (!client || client.length > 512) throw new Error("oidc client id invalid");
    const expectedNonce = String(nonce || "");
    if (!expectedNonce || expectedNonce.length > 512) throw new Error("oidc nonce invalid");

    const jwks = await this.#fetchJwks(jwksUri);
    const key = jwks.keys.find(jwk =>
      jwk && typeof jwk === "object" &&
      jwk.kid === kid &&
      (jwk.use === undefined || jwk.use === "sig") &&
      (jwk.alg === undefined || jwk.alg === alg)
    );
    if (!key) throw new Error("oidc signing key not found");

    let publicKey;
    try {
      publicKey = createPublicKey({ key, format: "jwk" });
    } catch {
      throw new Error("oidc signing key invalid");
    }

    const signature = Buffer.from(encodedSignature, "base64url");
    if (!signature.length || signature.toString("base64url") !== encodedSignature) {
      throw new Error("oidc signature encoding invalid");
    }
    const signingInput = Buffer.from(`${encodedHeader}.${encodedPayload}`, "ascii");

    const verified = alg === "RS256"
      ? verifySignature("RSA-SHA256", signingInput, publicKey, signature)
      : verifySignature("sha256", signingInput, { key: publicKey, dsaEncoding: "ieee-p1363" }, signature);
    if (!verified) throw new Error("oidc id token signature invalid");

    if (claims.iss !== issuer) throw new Error("oidc id token issuer mismatch");
    if (!audienceAllows(claims.aud, client, claims.azp)) throw new Error("oidc id token audience mismatch");
    if (claims.nonce !== expectedNonce) throw new Error("oidc id token nonce mismatch");
    if (typeof claims.sub !== "string" || !claims.sub || claims.sub.length > 512) {
      throw new Error("oidc id token subject invalid");
    }

    const nowSeconds = Math.floor(this.#clock() / 1000);
    const exp = numericDate(claims.exp, "exp", true);
    const nbf = numericDate(claims.nbf, "nbf");
    const iat = numericDate(claims.iat, "iat", true);
    if (exp <= nowSeconds - this.#clockSkewSeconds) throw new Error("oidc id token expired");
    if (nbf !== null && nbf > nowSeconds + this.#clockSkewSeconds) throw new Error("oidc id token not yet valid");
    if (iat > nowSeconds + this.#clockSkewSeconds) throw new Error("oidc id token issued in future");

    return Object.freeze({
      subject: claims.sub,
      issuer: claims.iss,
      audience: Array.isArray(claims.aud) ? Object.freeze([...claims.aud]) : claims.aud,
      issuedAt: iat,
      expiresAt: exp,
      email: typeof claims.email === "string" && claims.email.length <= 512 ? claims.email : null,
      emailVerified: claims.email_verified === true,
      authTime: Number.isInteger(claims.auth_time) ? claims.auth_time : null,
      acr: typeof claims.acr === "string" && claims.acr.length <= 256 ? claims.acr : null,
      amr: Array.isArray(claims.amr)
        ? Object.freeze(claims.amr.filter(v => typeof v === "string" && v.length <= 128).slice(0, 16))
        : Object.freeze([]),
    });
  }

  async #fetchJwks(jwksUri) {
    let url;
    try {
      url = new URL(jwksUri);
    } catch {
      throw new Error("oidc jwks uri invalid");
    }
    if (url.protocol !== "https:" || url.username || url.password || url.hash) {
      throw new Error("oidc jwks uri invalid");
    }
    const response = await this.#fetch(url.href, {
      method: "GET",
      redirect: "manual",
      headers: { accept: "application/json", "cache-control": "no-cache" },
    });
    if (!response || response.status !== 200) throw new Error("oidc jwks http failure");
    const jwks = await readBoundedJson(response);
    if (!jwks || !Array.isArray(jwks.keys) || !jwks.keys.length || jwks.keys.length > MAX_KEYS) {
      throw new Error("oidc jwks invalid");
    }
    return jwks;
  }
}

export const oidcIdTokenLimits = Object.freeze({
  maxTokenChars: MAX_TOKEN_CHARS,
  maxJwksBytes: MAX_JWKS_BYTES,
  maxKeys: MAX_KEYS,
  defaultClockSkewSeconds: DEFAULT_CLOCK_SKEW_SECONDS,
});
