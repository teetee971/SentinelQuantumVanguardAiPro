import { NetworkDefenseEngine } from '../network/NetworkDefenseEngine';

function rng(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 0x100000000;
  };
}

describe('NetworkDefenseEngine adversarial campaign', () => {
  test('never throws and keeps bounded peer state under hostile churn', () => {
    const random = rng(0x534e4554);
    const engine = new NetworkDefenseEngine({ maxPeers: 64, maxEventsPerPeer: 20 });
    for (let i = 0; i < 10_000; i += 1) {
      const peerId = `peer-${Math.floor(random() * 1000)}`;
      const destination = i % 17 === 0 ? '\u0000bad' : `host-${Math.floor(random() * 500)}.example`;
      const decision = engine.evaluate({
        peerId,
        destination,
        timestampMs: i,
        failedHandshake: i % 11 === 0,
        bytes: Math.floor(random() * 1_000_000),
      });
      expect(['ALLOW', 'THROTTLE', 'BLOCK']).toContain(decision.action);
      expect(engine.getTrackedPeerCount()).toBeLessThanOrEqual(64);
    }
  });

  test('rejects oversized peer identifiers and remains bounded', () => {
    const engine = new NetworkDefenseEngine();
    const oversizedPeer = 'p'.repeat(257);
    const oversizedDecision = engine.evaluate({
      peerId: oversizedPeer,
      destination: 'safe.example',
      timestampMs: 1,
    });
    expect(oversizedDecision.reason).toBe('invalid_peer');
    expect(engine.getTrackedPeerCount()).toBe(0);
  });

  test('blocks excessive destination fan-out and quarantines the peer', () => {
    const engine = new NetworkDefenseEngine({ maxUniqueDestinationsPerPeer: 3, maxEventsPerPeer: 100 });
    expect(engine.evaluate({ peerId: 'p', destination: 'a.example', timestampMs: 1 }).action).toBe('ALLOW');
    expect(engine.evaluate({ peerId: 'p', destination: 'b.example', timestampMs: 2 }).action).toBe('ALLOW');
    expect(engine.evaluate({ peerId: 'p', destination: 'c.example', timestampMs: 3 }).action).toBe('ALLOW');
    expect(engine.evaluate({ peerId: 'p', destination: 'd.example', timestampMs: 4 }).action).toBe('BLOCK');
    expect(engine.evaluate({ peerId: 'p', destination: 'a.example', timestampMs: 5 }).reason).toBe('peer_quarantined');
  });

  test('rejects non-monotonic time and invalid counters', () => {
    const engine = new NetworkDefenseEngine();
    expect(engine.evaluate({ peerId: 'p', destination: 'a.example', timestampMs: 10 }).action).toBe('ALLOW');
    expect(engine.evaluate({ peerId: 'p', destination: 'a.example', timestampMs: 9 }).reason).toBe('non_monotonic_timestamp');
    expect(engine.evaluate({ peerId: 'p', destination: 'a.example', timestampMs: 11, bytes: -1 }).reason).toBe('invalid_byte_counter');
  });

  test('fails safely on VPN degradation and upstream attack state', () => {
    const engine = new NetworkDefenseEngine();
    expect(engine.setVpnHealth(false)).toBe('VPN_DEGRADED');
    expect(engine.setUpstreamAttack(true)).toBe('UPSTREAM_ATTACK');
    expect(engine.setUpstreamAttack(false)).toBe('RECOVERY');
  });
});
