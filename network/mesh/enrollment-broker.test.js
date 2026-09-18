import test from "node:test";
import assert from "node:assert/strict";
import { MeshEnrollmentBroker } from "./enrollment-broker.js";

const FP = "a".repeat(64);

test("invitation code is high entropy, one-time and fingerprint-bound", () => {
  const broker = new MeshEnrollmentBroker({ clock: () => 1000 });
  const issued = broker.createInvitation({
    nodeId: "device:android-01",
    publicKeyFingerprint: FP,
    ttlMs: 60000,
  });
  assert.ok(issued.code.length >= 43);

  const wrongFingerprint = broker.claim({
    nodeId: "device:android-01",
    code: issued.code,
    publicKeyFingerprint: "b".repeat(64),
  });
  assert.equal(wrongFingerprint.accepted, false);

  const claimed = broker.claim({
    nodeId: "device:android-01",
    code: issued.code,
    publicKeyFingerprint: FP,
  });
  assert.equal(claimed.accepted, true);

  const replay = broker.claim({
    nodeId: "device:android-01",
    code: issued.code,
    publicKeyFingerprint: FP,
  });
  assert.equal(replay.accepted, false);
});

test("invalid codes are bounded by an attempt limit", () => {
  const broker = new MeshEnrollmentBroker({ clock: () => 1000 });
  broker.createInvitation({
    nodeId: "device:android-02",
    publicKeyFingerprint: FP,
    ttlMs: 60000,
  });

  for (let i = 0; i < 5; i += 1) {
    const result = broker.claim({
      nodeId: "device:android-02",
      code: "x".repeat(43),
      publicKeyFingerprint: FP,
    });
    assert.equal(result.accepted, false);
  }
  const exceeded = broker.claim({
    nodeId: "device:android-02",
    code: "x".repeat(43),
    publicKeyFingerprint: FP,
  });
  assert.equal(exceeded.accepted, false);
  assert.equal(exceeded.reason, "INVITATION_ATTEMPTS_EXCEEDED");
});

test("expired invitations fail closed", () => {
  let now = 1000;
  const broker = new MeshEnrollmentBroker({ clock: () => now });
  const issued = broker.createInvitation({
    nodeId: "device:android-03",
    publicKeyFingerprint: FP,
    ttlMs: 30000,
  });
  now = 31001;
  const result = broker.claim({
    nodeId: "device:android-03",
    code: issued.code,
    publicKeyFingerprint: FP,
  });
  assert.equal(result.accepted, false);
  assert.equal(result.reason, "INVITATION_EXPIRED");
});

test("revocation removes a pending invitation", () => {
  const broker = new MeshEnrollmentBroker({ clock: () => 1000 });
  broker.createInvitation({
    nodeId: "device:android-04",
    publicKeyFingerprint: FP,
    ttlMs: 60000,
  });
  assert.equal(broker.revoke("device:android-04"), true);
  assert.equal(broker.pendingCount(), 0);
});
