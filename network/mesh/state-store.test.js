import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { MeshStateStore } from "./state-store.js";

const SECRET = "0123456789abcdef0123456789abcdef";

test("state store saves atomically and restores authenticated state", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-mesh-state-"));
  try {
    const path = join(dir, "state.json");
    const store = new MeshStateStore({ path, secret: SECRET });
    assert.equal(await store.save({ schemaVersion: 1, nodes: [], policies: [], integrations: [], audit: [] }), 1);
    const loaded = await store.load();
    assert.equal(loaded.sequence, 1);
    assert.equal(loaded.state.schemaVersion, 1);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("tampering is rejected", async () => {
  const dir = await mkdtemp(join(tmpdir(), "sentinel-mesh-state-"));
  try {
    const path = join(dir, "state.json");
    const store = new MeshStateStore({ path, secret: SECRET });
    await store.save({ schemaVersion: 1, nodes: [], policies: [], integrations: [], audit: [] });
    const envelope = JSON.parse(await readFile(path, "utf8"));
    envelope.state.nodes.push({ id: "forged" });
    await writeFile(path, JSON.stringify(envelope), "utf8");
    await assert.rejects(() => store.load(), /integrity failure/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("weak persistence secrets are rejected", () => {
  assert.throws(() => new MeshStateStore({ path: "/tmp/state", secret: "short" }));
});
