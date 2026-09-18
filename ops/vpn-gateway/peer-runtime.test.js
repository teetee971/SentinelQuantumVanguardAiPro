import test from "node:test";
import assert from "node:assert/strict";
import { VpnGatewayPeerRuntime } from "./peer-runtime.js";

const KEY = Buffer.alloc(32, 31).toString("base64");

test("peer runtime invokes bounded add and remove commands", async () => {
  const calls = [];
  const runtime = new VpnGatewayPeerRuntime({
    scriptPath: "/opt/sentinel/manage-peer.sh",
    interfaceName: "sentinel0",
    runner: async (path, args, options) => {
      calls.push({ path, args, options });
      return { stdout: "", stderr: "" };
    },
  });

  const applied = await runtime.apply({
    devicePublicKey: KEY,
    clientAddresses: ["10.73.0.42/32", "fd73:1::2a/128"],
  });
  assert.equal(applied.accepted, true);
  assert.deepEqual(calls[0].args, [
    "add",
    KEY,
    "10.73.0.42/32",
    "fd73:1::2a/128",
  ]);
  assert.equal(calls[0].options.timeout, 10_000);
  assert.equal(calls[0].options.maxBuffer, 16 * 1024);
  assert.equal(calls[0].options.env.SENTINEL_WG_INTERFACE, "sentinel0");

  const removed = await runtime.remove(KEY);
  assert.equal(removed.accepted, true);
  assert.deepEqual(calls[1].args, ["remove", KEY]);
});

test("peer runtime rejects malformed keys and host routes before executing", async () => {
  let calls = 0;
  const runtime = new VpnGatewayPeerRuntime({
    runner: async () => {
      calls += 1;
      return { stdout: "", stderr: "" };
    },
  });

  assert.equal((await runtime.apply({
    devicePublicKey: "invalid",
    clientAddresses: ["10.73.0.2/32", "fd73:1::2/128"],
  })).reason, "VPN_PEER_RUNTIME_KEY_INVALID");

  assert.equal((await runtime.apply({
    devicePublicKey: KEY,
    clientAddresses: ["10.73.0.0/24", "fd73:1::2/128"],
  })).reason, "VPN_PEER_RUNTIME_ADDRESSES_INVALID");

  assert.equal(calls, 0);
});

test("peer runtime converts command failure to a bounded reason", async () => {
  const runtime = new VpnGatewayPeerRuntime({
    runner: async () => {
      throw new Error("sensitive command details");
    },
  });

  const result = await runtime.remove(KEY);
  assert.equal(result.accepted, false);
  assert.equal(result.reason, "VPN_PEER_RUNTIME_COMMAND_FAILED");
  assert.equal("error" in result, false);
});
