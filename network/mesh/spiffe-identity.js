const MAX_TRUST_DOMAINS = 32;
const MAX_MAPPINGS = 256;
const MAX_PATH_CHARS = 1024;

function validTrustDomain(value) {
  if (typeof value !== "string" || value.length < 1 || value.length > 255) return false;
  if (value !== value.toLowerCase()) return false;
  const labels = value.split(".");
  if (!labels.length) return false;
  return labels.every(label =>
    /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label)
  );
}

function parsePath(pathname) {
  if (typeof pathname !== "string" || pathname.length < 1 || pathname.length > MAX_PATH_CHARS) {
    throw new Error("spiffe path invalid");
  }
  if (!pathname.startsWith("/")) throw new Error("spiffe path invalid");
  if (pathname.includes("%") || pathname.includes("//")) throw new Error("spiffe path not canonical");
  const segments = pathname.split("/").slice(1);
  if (segments.some(segment =>
    segment === "." ||
    segment === ".." ||
    (segment && !/^[A-Za-z0-9._-]{1,128}$/.test(segment))
  )) {
    throw new Error("spiffe path invalid");
  }
  return pathname === "/" ? "/" : `/${segments.filter(Boolean).join("/")}`;
}

export function parseSpiffeId(raw) {
  let url;
  try {
    url = new URL(String(raw || ""));
  } catch {
    throw new Error("spiffe id invalid");
  }
  if (url.protocol !== "spiffe:") throw new Error("spiffe scheme invalid");
  if (url.username || url.password || url.port || url.search || url.hash) {
    throw new Error("spiffe id contains forbidden components");
  }
  const trustDomain = url.hostname;
  if (!validTrustDomain(trustDomain)) throw new Error("spiffe trust domain invalid");
  const path = parsePath(url.pathname);
  const id = `spiffe://${trustDomain}${path}`;
  if (id !== String(raw)) throw new Error("spiffe id not canonical");
  return Object.freeze({ id, trustDomain, path });
}

function prefixMatches(path, prefix) {
  if (path === prefix) return true;
  if (prefix === "/") return true;
  return path.startsWith(prefix.endsWith("/") ? prefix : `${prefix}/`);
}

export class SpiffeIdentityPolicy {
  #trustDomains;
  #mappings;

  constructor({ trustDomains, mappings }) {
    const domains = [...new Set(
      [...(trustDomains || [])].map(v => String(v).trim())
    )];
    if (!domains.length || domains.length > MAX_TRUST_DOMAINS || domains.some(v => !validTrustDomain(v))) {
      throw new Error("spiffe trust domains invalid");
    }

    if (!Array.isArray(mappings) || !mappings.length || mappings.length > MAX_MAPPINGS) {
      throw new Error("spiffe mappings invalid");
    }

    const normalizedMappings = mappings.map((mapping, index) => {
      if (!mapping || typeof mapping !== "object") throw new Error(`spiffe mapping ${index} invalid`);
      const trustDomain = String(mapping.trustDomain || "").trim();
      if (!domains.includes(trustDomain)) throw new Error(`spiffe mapping ${index} trust domain invalid`);
      const pathPrefix = parsePath(String(mapping.pathPrefix || "/"));
      const subjectType = String(mapping.subjectType || "");
      if (!["workload", "agent"].includes(subjectType)) {
        throw new Error(`spiffe mapping ${index} subject type invalid`);
      }
      const tags = Array.isArray(mapping.tags)
        ? [...new Set(mapping.tags.map(v => String(v).trim()).filter(Boolean))].slice(0, 32)
        : [];
      const groups = Array.isArray(mapping.groups)
        ? [...new Set(mapping.groups.map(v => String(v).trim()).filter(Boolean))].slice(0, 32)
        : [];
      if ([...tags, ...groups].some(v => v.length > 128)) {
        throw new Error(`spiffe mapping ${index} attributes invalid`);
      }
      return Object.freeze({
        trustDomain,
        pathPrefix,
        subjectType,
        tags: Object.freeze(tags),
        groups: Object.freeze(groups),
      });
    });

    this.#trustDomains = new Set(domains);
    this.#mappings = Object.freeze(normalizedMappings);
  }

  mapIdentity(rawSpiffeId, { svidVerified = false } = {}) {
    if (svidVerified !== true) {
      return Object.freeze({ allowed: false, reason: "SPIFFE_SVID_UNVERIFIED" });
    }
    const identity = parseSpiffeId(rawSpiffeId);
    if (!this.#trustDomains.has(identity.trustDomain)) {
      return Object.freeze({ allowed: false, reason: "SPIFFE_TRUST_DOMAIN_DENIED" });
    }

    const matches = this.#mappings
      .filter(mapping =>
        mapping.trustDomain === identity.trustDomain &&
        prefixMatches(identity.path, mapping.pathPrefix)
      )
      .sort((a, b) => b.pathPrefix.length - a.pathPrefix.length);

    const mapping = matches[0];
    if (!mapping) {
      return Object.freeze({ allowed: false, reason: "SPIFFE_MAPPING_NOT_FOUND" });
    }

    return Object.freeze({
      allowed: true,
      reason: "SPIFFE_MAPPING_MATCHED",
      subject: Object.freeze({
        type: mapping.subjectType,
        id: identity.id,
        groups: Object.freeze([...mapping.groups]),
        tags: Object.freeze([...mapping.tags]),
        deviceTrust: "attested",
      }),
    });
  }
}

export const spiffeIdentityLimits = Object.freeze({
  maxTrustDomains: MAX_TRUST_DOMAINS,
  maxMappings: MAX_MAPPINGS,
  maxPathChars: MAX_PATH_CHARS,
});
