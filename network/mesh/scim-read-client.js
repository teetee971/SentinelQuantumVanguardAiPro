const MAX_RESPONSE_BYTES = 512 * 1024;
const MAX_ALLOWED_HOSTS = 16;
const MAX_PAGE_SIZE = 200;
const MAX_FILTER_CHARS = 1024;
const MAX_RESOURCES = 500;
const MAX_GROUP_MEMBERS = 1000;

function httpsUrl(raw, name) {
  let url;
  try {
    url = new URL(String(raw || ""));
  } catch {
    throw new Error(`${name} invalid`);
  }
  if (url.protocol !== "https:") throw new Error(`${name} must use https`);
  if (url.username || url.password || url.search || url.hash) throw new Error(`${name} contains forbidden components`);
  if (url.href.length > 2048) throw new Error(`${name} too long`);
  return url;
}

async function readBoundedJson(response) {
  const declared = Number(response.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > MAX_RESPONSE_BYTES) throw new Error("scim response too large");
  if (!response.body) throw new Error("scim response body missing");
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_RESPONSE_BYTES) {
      await reader.cancel();
      throw new Error("scim response too large");
    }
    chunks.push(Buffer.from(value));
  }
  let parsed;
  try {
    parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new Error("scim response invalid json");
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("scim response invalid");
  return parsed;
}

function boundedString(value, max = 512) {
  if (typeof value !== "string") return null;
  const text = value.trim();
  return text && text.length <= max ? text : null;
}

function normalizeEmails(value) {
  if (!Array.isArray(value)) return [];
  return value
    .slice(0, 16)
    .map(item => ({
      value: boundedString(item?.value, 512),
      type: boundedString(item?.type, 64),
      primary: item?.primary === true,
    }))
    .filter(item => item.value);
}

export function normalizeScimUser(raw) {
  if (!raw || typeof raw !== "object") throw new Error("scim user invalid");
  const id = boundedString(raw.id, 256);
  const userName = boundedString(raw.userName, 512);
  if (!id || !userName) throw new Error("scim user identity invalid");

  const emails = normalizeEmails(raw.emails);
  const primary = emails.find(email => email.primary) || emails[0] || null;
  return Object.freeze({
    id,
    userName,
    displayName: boundedString(raw.displayName, 512),
    active: raw.active !== false,
    primaryEmail: primary?.value || null,
  });
}

export function normalizeScimGroup(raw) {
  if (!raw || typeof raw !== "object") throw new Error("scim group invalid");
  const id = boundedString(raw.id, 256);
  const displayName = boundedString(raw.displayName, 512);
  if (!id || !displayName) throw new Error("scim group identity invalid");

  const members = Array.isArray(raw.members)
    ? raw.members.slice(0, MAX_GROUP_MEMBERS)
      .map(member => boundedString(member?.value, 256))
      .filter(Boolean)
    : [];

  return Object.freeze({
    id,
    displayName,
    memberIds: Object.freeze([...new Set(members)]),
  });
}

export class ScimReadClient {
  #baseUrl;
  #allowedHosts;
  #tokenProvider;
  #fetch;

  constructor({ baseUrl, allowedHosts, tokenProvider, fetchImpl = globalThis.fetch }) {
    const base = httpsUrl(baseUrl, "scim base url");
    const hosts = new Set(
      [...(allowedHosts || [])]
        .map(v => String(v).trim().toLowerCase())
        .filter(Boolean)
    );
    if (!hosts.size || hosts.size > MAX_ALLOWED_HOSTS) throw new Error("scim allowed hosts invalid");
    if (!hosts.has(base.hostname.toLowerCase())) throw new Error("scim base host not allowed");
    if (typeof tokenProvider !== "function") throw new Error("scim token provider required");
    if (typeof fetchImpl !== "function") throw new Error("scim fetch implementation required");

    this.#baseUrl = new URL(base.href.endsWith("/") ? base.href : `${base.href}/`);
    this.#allowedHosts = hosts;
    this.#tokenProvider = tokenProvider;
    this.#fetch = fetchImpl;
  }

  async serviceProviderConfig() {
    const body = await this.#get("ServiceProviderConfig");
    return Object.freeze({
      patchSupported: body.patch?.supported === true,
      bulkSupported: body.bulk?.supported === true,
      filterSupported: body.filter?.supported === true,
      filterMaxResults: Number.isInteger(body.filter?.maxResults) ? body.filter.maxResults : null,
      changePasswordSupported: body.changePassword?.supported === true,
      sortSupported: body.sort?.supported === true,
      etagSupported: body.etag?.supported === true,
      authenticationSchemes: Object.freeze(
        Array.isArray(body.authenticationSchemes)
          ? body.authenticationSchemes.slice(0, 16)
            .map(s => boundedString(s?.type, 128))
            .filter(Boolean)
          : []
      ),
    });
  }

  async listUsers({ startIndex = 1, count = 100, filter = null } = {}) {
    const body = await this.#list("Users", { startIndex, count, filter });
    return Object.freeze({
      totalResults: body.totalResults,
      startIndex: body.startIndex,
      itemsPerPage: body.itemsPerPage,
      users: Object.freeze(body.resources.map(normalizeScimUser)),
    });
  }

  async listGroups({ startIndex = 1, count = 100, filter = null } = {}) {
    const body = await this.#list("Groups", { startIndex, count, filter });
    return Object.freeze({
      totalResults: body.totalResults,
      startIndex: body.startIndex,
      itemsPerPage: body.itemsPerPage,
      groups: Object.freeze(body.resources.map(normalizeScimGroup)),
    });
  }

  async #list(resource, { startIndex, count, filter }) {
    if (!Number.isInteger(startIndex) || startIndex < 1 || startIndex > 10_000_000) {
      throw new Error("scim startIndex invalid");
    }
    if (!Number.isInteger(count) || count < 1 || count > MAX_PAGE_SIZE) {
      throw new Error("scim count invalid");
    }
    if (filter !== null) {
      if (typeof filter !== "string" || !filter.trim() || filter.length > MAX_FILTER_CHARS || /[\u0000-\u001f\u007f]/.test(filter)) {
        throw new Error("scim filter invalid");
      }
    }

    const url = this.#url(resource);
    url.searchParams.set("startIndex", String(startIndex));
    url.searchParams.set("count", String(count));
    if (filter !== null) url.searchParams.set("filter", filter);

    const raw = await this.#request(url);
    const resources = Array.isArray(raw.Resources) ? raw.Resources : [];
    if (resources.length > MAX_RESOURCES || resources.length > count) throw new Error("scim resource count invalid");

    const totalResults = Number(raw.totalResults);
    const returnedStart = Number(raw.startIndex);
    const itemsPerPage = Number(raw.itemsPerPage);
    if (![totalResults, returnedStart, itemsPerPage].every(Number.isInteger)) {
      throw new Error("scim pagination invalid");
    }
    if (totalResults < 0 || returnedStart < 1 || itemsPerPage < 0 || itemsPerPage > MAX_PAGE_SIZE) {
      throw new Error("scim pagination invalid");
    }
    return { totalResults, startIndex: returnedStart, itemsPerPage, resources };
  }

  async #get(relativePath) {
    return this.#request(this.#url(relativePath));
  }

  async #request(url) {
    const token = await this.#tokenProvider();
    if (typeof token !== "string" || token.length < 16 || token.length > 4096 || /[\u0000-\u001f\u007f]/.test(token)) {
      throw new Error("scim bearer token invalid");
    }

    const response = await this.#fetch(url.href, {
      method: "GET",
      redirect: "manual",
      headers: {
        accept: "application/scim+json, application/json",
        authorization: `Bearer ${token}`,
        "cache-control": "no-store",
      },
    });
    if (!response || response.status !== 200) throw new Error("scim http failure");
    if (response.type === "opaqueredirect" || (response.status >= 300 && response.status < 400)) {
      throw new Error("scim redirect forbidden");
    }
    return readBoundedJson(response);
  }

  #url(relativePath) {
    if (!/^[A-Za-z0-9/_-]{1,256}$/.test(relativePath)) throw new Error("scim path invalid");
    const url = new URL(relativePath, this.#baseUrl);
    if (url.protocol !== "https:" || !this.#allowedHosts.has(url.hostname.toLowerCase())) {
      throw new Error("scim target not allowed");
    }
    return url;
  }
}

export const scimReadLimits = Object.freeze({
  maxResponseBytes: MAX_RESPONSE_BYTES,
  maxPageSize: MAX_PAGE_SIZE,
  maxFilterChars: MAX_FILTER_CHARS,
  maxResources: MAX_RESOURCES,
  maxGroupMembers: MAX_GROUP_MEMBERS,
});
