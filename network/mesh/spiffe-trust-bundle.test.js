import test from "node:test";
import assert from "node:assert/strict";
import { SpiffeTrustBundleManager } from "./spiffe-trust-bundle.js";
import { CA, CA_DNS_EXTRA } from "./x509-svid-test-fixtures.js";
import { CRL_REVOKING_LEAF_DER_B64 } from "./x509-crl-test-fixtures.js";

test("installs strictly monotonic trust bundles and rejects sequence replay or rollback", () => {
  const manager = new SpiffeTrustBundleManager();
  const first = manager.install({
    trustDomain: "prod.example.test",
    sequence: 1,
    anchorsPem: [CA],
  });
  assert.equal(first.sequence, 1);

  assert.throws(
    () => manager.install({
      trustDomain: "prod.example.test",
      sequence: 1,
      anchorsPem: [CA_DNS_EXTRA],
    }),
    /rollback or replay/
  );

  const second = manager.install({
    trustDomain: "prod.example.test",
    sequence: 2,
    anchorsPem: [CA, CA_DNS_EXTRA],
  });
  assert.equal(second.sequence, 2);
  assert.equal(second.fingerprints256.length, 2);

  assert.throws(
    () => manager.install({
      trustDomain: "prod.example.test",
      sequence: 3,
      anchorsPem: [CA, CA_DNS_EXTRA],
    }),
    /content replay/
  );
});

test("rotation supports CA overlap and per-domain metadata", () => {
  const manager = new SpiffeTrustBundleManager();
  const current = manager.install({
    trustDomain: "prod.example.test",
    sequence: 7,
    anchorsPem: [CA, CA_DNS_EXTRA],
  });
  assert.match(current.digest, /^[a-f0-9]{64}$/);
  assert.match(current.contentDigest, /^[a-f0-9]{64}$/);

  const config = manager.verifierConfig("prod.example.test");
  assert.equal(config.expectedTrustDomain, "prod.example.test");
  assert.equal(config.sequence, 7);
  assert.equal(config.trustBundlePem.length, 2);

  const metadata = manager.listMetadata();
  assert.equal("anchorsPem" in metadata[0], false);
});

test("snapshot restore validates digest and last sequence state", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.install({
    trustDomain: "prod.example.test",
    sequence: 3,
    anchorsPem: [CA],
  });
  const snapshot = structuredClone(manager.exportState());

  const restored = new SpiffeTrustBundleManager();
  const state = restored.restoreState(snapshot);
  assert.equal(state.bundles[0].sequence, 3);
  assert.equal(state.lastSequences[0].sequence, 3);
  assert.equal(
    restored.current("prod.example.test").digest,
    snapshot.bundles[0].digest
  );

  const tamperedDigest = structuredClone(snapshot);
  tamperedDigest.bundles[0].digest = "0".repeat(64);
  assert.throws(
    () => new SpiffeTrustBundleManager().restoreState(tamperedDigest),
    /digest mismatch/
  );

  const tamperedSequence = structuredClone(snapshot);
  tamperedSequence.lastSequences[0].sequence = 4;
  assert.throws(
    () => new SpiffeTrustBundleManager().restoreState(tamperedSequence),
    /sequence state mismatch/
  );
});

test("restore refuses rollback even when a trust domain is currently absent", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.observeSet([{
    trustDomain: "prod.example.test",
    anchorsPem: [CA],
  }]);
  manager.observeSet([{
    trustDomain: "staging.example.test",
    anchorsPem: [CA_DNS_EXTRA],
  }]);

  const old = new SpiffeTrustBundleManager();
  old.install({
    trustDomain: "prod.example.test",
    sequence: 1,
    anchorsPem: [CA],
  });

  assert.throws(
    () => manager.restoreState(old.exportState()),
    /rollback or replay detected/
  );
});

test("trust domains rotate independently", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.install({
    trustDomain: "prod.example.test",
    sequence: 4,
    anchorsPem: [CA],
  });
  manager.install({
    trustDomain: "staging.example.test",
    sequence: 1,
    anchorsPem: [CA_DNS_EXTRA],
  });

  manager.install({
    trustDomain: "staging.example.test",
    sequence: 2,
    anchorsPem: [CA_DNS_EXTRA, CA],
  });

  assert.equal(manager.current("prod.example.test").sequence, 4);
  assert.equal(manager.current("staging.example.test").sequence, 2);
});

test("observed full bundle sets remove redacted domains immediately", () => {
  const manager = new SpiffeTrustBundleManager();

  const first = manager.observeSet([
    { trustDomain: "prod.example.test", anchorsPem: [CA] },
    { trustDomain: "staging.example.test", anchorsPem: [CA_DNS_EXTRA] },
  ]);
  assert.deepEqual(first.changedDomains, ["prod.example.test", "staging.example.test"]);
  assert.deepEqual(first.removedDomains, []);

  const second = manager.observeSet([
    { trustDomain: "prod.example.test", anchorsPem: [CA] },
  ]);
  assert.deepEqual(second.changedDomains, []);
  assert.deepEqual(second.removedDomains, ["staging.example.test"]);
  assert.equal(manager.current("staging.example.test"), null);
  assert.equal(manager.current("prod.example.test").sequence, 1);
});

test("reappearing observed trust domain continues its local monotonic sequence", () => {
  const manager = new SpiffeTrustBundleManager();

  manager.observeSet([
    { trustDomain: "prod.example.test", anchorsPem: [CA] },
  ]);
  manager.observeSet([
    { trustDomain: "staging.example.test", anchorsPem: [CA_DNS_EXTRA] },
  ]);
  assert.equal(manager.current("prod.example.test"), null);

  const reappeared = manager.observeSet([
    { trustDomain: "prod.example.test", anchorsPem: [CA] },
  ]);
  assert.deepEqual(reappeared.changedDomains, ["prod.example.test"]);
  assert.deepEqual(reappeared.removedDomains, ["staging.example.test"]);
  assert.equal(manager.current("prod.example.test").sequence, 2);

  const snapshot = manager.exportState();
  const prodSequence = snapshot.lastSequences
    .find(item => item.trustDomain === "prod.example.test").sequence;
  assert.equal(prodSequence, 2);
});

test("observe single-domain update does not increment sequence when content is unchanged", () => {
  const manager = new SpiffeTrustBundleManager();

  const first = manager.observe({
    trustDomain: "prod.example.test",
    anchorsPem: [CA],
  });
  assert.equal(first.changed, true);
  assert.equal(first.bundle.sequence, 1);

  const same = manager.observe({
    trustDomain: "prod.example.test",
    anchorsPem: [CA],
  });
  assert.equal(same.changed, false);
  assert.equal(same.bundle.sequence, 1);

  const rotated = manager.observe({
    trustDomain: "prod.example.test",
    anchorsPem: [CA, CA_DNS_EXTRA],
  });
  assert.equal(rotated.changed, true);
  assert.equal(rotated.bundle.sequence, 2);
});

test("rejects duplicate anchors and duplicate domains in observed sets", () => {
  const manager = new SpiffeTrustBundleManager();
  assert.throws(
    () => manager.install({
      trustDomain: "prod.example.test",
      sequence: 1,
      anchorsPem: [CA, CA],
    }),
    /duplicate trust anchor/
  );

  assert.throws(
    () => manager.observeSet([
      { trustDomain: "prod.example.test", anchorsPem: [CA] },
      { trustDomain: "prod.example.test", anchorsPem: [CA_DNS_EXTRA] },
    ]),
    /duplicate trust domain/
  );
});


test("empty observed bundle snapshot redacts all active domains while preserving monotonic counters", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.observeSet([
    { trustDomain: "prod.example.test", anchorsPem: [CA] },
    { trustDomain: "staging.example.test", anchorsPem: [CA_DNS_EXTRA] },
  ]);

  const result = manager.observeSet([]);
  assert.deepEqual(result.changedDomains, []);
  assert.deepEqual(result.removedDomains, ["prod.example.test", "staging.example.test"]);
  assert.equal(manager.current("prod.example.test"), null);
  assert.equal(manager.current("staging.example.test"), null);

  const snapshot = manager.exportState();
  assert.deepEqual(snapshot.bundles, []);
  assert.equal(snapshot.lastSequences.length, 2);
  assert.equal(snapshot.lastSequences.find(x => x.trustDomain === "prod.example.test").sequence, 1);
});


test("CRL snapshots persist, restore, rotate and retain monotonic sequence", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.observeSet(
    [{ trustDomain: "prod.example.test", anchorsPem: [CA] }],
    [CRL_REVOKING_LEAF_DER_B64]
  );

  const first = manager.currentCrlSet();
  assert.equal(first.sequence, 1);
  assert.equal(first.crlsDerBase64.length, 1);
  assert.match(first.digest, /^[a-f0-9]{64}$/);

  const snapshot = structuredClone(manager.exportState());
  const restored = new SpiffeTrustBundleManager();
  restored.restoreState(snapshot);
  assert.equal(restored.currentCrlSet().sequence, 1);
  assert.deepEqual(restored.currentCrlSet().crlsDerBase64, [CRL_REVOKING_LEAF_DER_B64]);

  const redacted = restored.observeCrlSet([]);
  assert.equal(redacted.changed, true);
  assert.equal(redacted.sequence, 2);
  assert.deepEqual(restored.currentCrlSet().crlsDerBase64, []);
});

test("CRL restore rejects digest tampering and rollback", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.observeSet(
    [{ trustDomain: "prod.example.test", anchorsPem: [CA] }],
    [CRL_REVOKING_LEAF_DER_B64]
  );
  const snapshot = structuredClone(manager.exportState());

  const tampered = structuredClone(snapshot);
  tampered.crlState.digest = "0".repeat(64);
  assert.throws(
    () => new SpiffeTrustBundleManager().restoreState(tampered),
    /CRL state digest mismatch/
  );

  manager.observeCrlSet([]);
  assert.throws(
    () => manager.restoreState(snapshot),
    /CRL rollback or replay detected/
  );
});

test("verifier config carries current CRL snapshot metadata and bytes", () => {
  const manager = new SpiffeTrustBundleManager();
  manager.observeSet(
    [{ trustDomain: "prod.example.test", anchorsPem: [CA] }],
    [CRL_REVOKING_LEAF_DER_B64]
  );
  const config = manager.verifierConfig("prod.example.test");
  assert.equal(config.crlSequence, 1);
  assert.match(config.crlDigest, /^[a-f0-9]{64}$/);
  assert.deepEqual(config.crlsDerBase64, [CRL_REVOKING_LEAF_DER_B64]);
});
