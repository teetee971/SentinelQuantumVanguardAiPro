import test from "node:test";
import assert from "node:assert/strict";
import { SpiffeTrustBundleManager } from "./spiffe-trust-bundle.js";
import { CA, CA_DNS_EXTRA } from "./x509-svid-test-fixtures.js";

test("installs strictly monotonic trust bundles and rejects replay or rollback", () => {
  const manager = new SpiffeTrustBundleManager();
  const first = manager.install({ sequence: 1, anchorsPem: [CA] });
  assert.equal(first.sequence, 1);

  assert.throws(
    () => manager.install({ sequence: 1, anchorsPem: [CA] }),
    /rollback or replay/
  );
  assert.throws(
    () => manager.install({ sequence: 0, anchorsPem: [CA] }),
    /sequence invalid/
  );

  const second = manager.install({ sequence: 2, anchorsPem: [CA, CA_DNS_EXTRA] });
  assert.equal(second.sequence, 2);
  assert.equal(second.fingerprints256.length, 2);
});

test("rotation supports CA overlap while preserving canonical digest", () => {
  const manager = new SpiffeTrustBundleManager();
  const current = manager.install({
    sequence: 7,
    anchorsPem: [CA, CA_DNS_EXTRA],
  });
  assert.match(current.digest, /^[a-f0-9]{64}$/);

  const config = manager.verifierConfig();
  assert.equal(config.sequence, 7);
  assert.equal(config.trustBundlePem.length, 2);
  assert.equal(config.digest, current.digest);
});

test("snapshot restore validates digest and sequence", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.install({ sequence: 3, anchorsPem: [CA] });
  const snapshot = structuredClone(manager.exportState());

  const restored = new SpiffeTrustBundleManager();
  const state = restored.restoreState(snapshot);
  assert.equal(state.sequence, 3);
  assert.equal(restored.current().digest, snapshot.current.digest);

  const tamperedDigest = structuredClone(snapshot);
  tamperedDigest.current.digest = "0".repeat(64);
  assert.throws(
    () => new SpiffeTrustBundleManager().restoreState(tamperedDigest),
    /digest mismatch/
  );

  const tamperedSequence = structuredClone(snapshot);
  tamperedSequence.sequence = 4;
  assert.throws(
    () => new SpiffeTrustBundleManager().restoreState(tamperedSequence),
    /sequence mismatch/
  );
});

test("restore refuses rollback over a newer in-memory bundle", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.install({ sequence: 5, anchorsPem: [CA_DNS_EXTRA] });

  const old = new SpiffeTrustBundleManager();
  old.install({ sequence: 4, anchorsPem: [CA] });
  const oldSnapshot = old.exportState();

  assert.throws(
    () => manager.restoreState(oldSnapshot),
    /rollback detected/
  );
});

test("rejects duplicate or non-CA anchors", () => {
  const manager = new SpiffeTrustBundleManager();
  assert.throws(
    () => manager.install({ sequence: 1, anchorsPem: [CA, CA] }),
    /duplicate trust anchor/
  );
});
