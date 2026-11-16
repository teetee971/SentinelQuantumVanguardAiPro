import test from "node:test";
import assert from "node:assert/strict";
import { ScimReadClient, normalizeScimGroup, normalizeScimUser } from "./scim-read-client.js";

function responseJson(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/scim+json" } });
}

test("normalizes bounded SCIM users and groups", () => {
  assert.deepEqual(normalizeScimUser({
    id: "u-1",
    userName: "alice",
    displayName: "Alice",
    active: true,
    emails: [
      { value: "secondary@example.test", primary: false },
      { value: "alice@example.test", primary: true },
    ],
  }), {
    id: "u-1",
    userName: "alice",
    displayName: "Alice",
    active: true,
    primaryEmail: "alice@example.test",
  });

  assert.deepEqual(normalizeScimGroup({
    id: "g-1",
    displayName: "Engineering",
    members: [{ value: "u-1" }, { value: "u-2" }, { value: "u-1" }],
  }), {
    id: "g-1",
    displayName: "Engineering",
    memberIds: ["u-1", "u-2"],
  });
});

test("reads users with explicit bounded pagination and bearer auth", async () => {
  let request = null;
  const client = new ScimReadClient({
    baseUrl: "https://scim.example.test/scim/v2/",
    allowedHosts: ["scim.example.test"],
    tokenProvider: async () => "token-value-1234567890",
    fetchImpl: async (url, options) => {
      request = { url: String(url), options };
      return responseJson({
        totalResults: 1,
        startIndex: 1,
        itemsPerPage: 1,
        Resources: [{ id: "u-1", userName: "alice", active: true }],
      });
    },
  });

  const result = await client.listUsers({ count: 10, filter: 'active eq true' });
  assert.equal(result.users[0].userName, "alice");
  const url = new URL(request.url);
  assert.equal(url.pathname, "/scim/v2/Users");
  assert.equal(url.searchParams.get("count"), "10");
  assert.equal(url.searchParams.get("filter"), "active eq true");
  assert.equal(request.options.headers.authorization, "Bearer token-value-1234567890");
  assert.equal(request.options.redirect, "manual");
});

test("reads service-provider capabilities without enabling write operations", async () => {
  const client = new ScimReadClient({
    baseUrl: "https://scim.example.test/scim/v2/",
    allowedHosts: ["scim.example.test"],
    tokenProvider: async () => "token-value-1234567890",
    fetchImpl: async () => responseJson({
      patch: { supported: true },
      bulk: { supported: false },
      filter: { supported: true, maxResults: 100 },
      sort: { supported: true },
      etag: { supported: true },
      authenticationSchemes: [{ type: "oauthbearertoken" }],
    }),
  });

  const config = await client.serviceProviderConfig();
  assert.equal(config.patchSupported, true);
  assert.equal(config.bulkSupported, false);
  assert.deepEqual(config.authenticationSchemes, ["oauthbearertoken"]);
  assert.equal(typeof client.createUser, "undefined");
});

test("rejects oversized pages filters and insecure base URLs", async () => {
  assert.throws(() => new ScimReadClient({
    baseUrl: "http://scim.example.test/scim/v2/",
    allowedHosts: ["scim.example.test"],
    tokenProvider: async () => "token-value-1234567890",
  }), /must use https/);

  const client = new ScimReadClient({
    baseUrl: "https://scim.example.test/scim/v2/",
    allowedHosts: ["scim.example.test"],
    tokenProvider: async () => "token-value-1234567890",
    fetchImpl: async () => responseJson({
      totalResults: 0,
      startIndex: 1,
      itemsPerPage: 0,
      Resources: [],
    }),
  });

  await assert.rejects(() => client.listUsers({ count: 201 }), /count invalid/);
  await assert.rejects(() => client.listGroups({ filter: "x".repeat(1025) }), /filter invalid/);
});

test("rejects responses that exceed the requested page size", async () => {
  const client = new ScimReadClient({
    baseUrl: "https://scim.example.test/scim/v2/",
    allowedHosts: ["scim.example.test"],
    tokenProvider: async () => "token-value-1234567890",
    fetchImpl: async () => responseJson({
      totalResults: 2,
      startIndex: 1,
      itemsPerPage: 2,
      Resources: [
        { id: "u-1", userName: "alice" },
        { id: "u-2", userName: "bob" },
      ],
    }),
  });

  await assert.rejects(() => client.listUsers({ count: 1 }), /resource count invalid/);
});
