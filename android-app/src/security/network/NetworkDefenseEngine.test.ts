import { NetworkDefenseEngine } from './NetworkDefenseEngine';

describe('NetworkDefenseEngine safety states', () => {
  test('blocks all evaluations while VPN is degraded', () => {
    const engine = new NetworkDefenseEngine();
    expect(engine.setVpnHealth(false)).toBe('VPN_DEGRADED');

    const decision = engine.evaluate({
      peerId: 'peer-1',
      destination: 'example.com',
      timestampMs: 1,
    });

    expect(decision).toEqual({
      action: 'BLOCK',
      state: 'VPN_DEGRADED',
      reason: 'vpn_degraded_fail_closed',
    });
    expect(engine.getTrackedPeerCount()).toBe(0);
  });

  test('blocks all evaluations while an upstream attack is active', () => {
    const engine = new NetworkDefenseEngine();
    expect(engine.setUpstreamAttack(true)).toBe('UPSTREAM_ATTACK');

    const decision = engine.evaluate({
      peerId: 'peer-1',
      destination: 'example.com',
      timestampMs: 1,
    });

    expect(decision).toEqual({
      action: 'BLOCK',
      state: 'UPSTREAM_ATTACK',
      reason: 'upstream_attack_fail_closed',
    });
    expect(engine.getTrackedPeerCount()).toBe(0);
  });

  test('explicit VPN recovery is required before normal traffic is allowed', () => {
    const engine = new NetworkDefenseEngine();
    engine.setVpnHealth(false);
    engine.setVpnHealth(true);

    expect(engine.getState()).toBe('RECOVERY');
    const decision = engine.evaluate({
      peerId: 'peer-1',
      destination: 'example.com',
      timestampMs: 1,
    });

    expect(decision.action).toBe('ALLOW');
    expect(decision.state).toBe('NORMAL');
  });

  test('explicit upstream attack recovery is required before normal traffic is allowed', () => {
    const engine = new NetworkDefenseEngine();
    engine.setUpstreamAttack(true);
    engine.setUpstreamAttack(false);

    expect(engine.getState()).toBe('RECOVERY');
    const decision = engine.evaluate({
      peerId: 'peer-1',
      destination: 'example.com',
      timestampMs: 1,
    });

    expect(decision.action).toBe('ALLOW');
    expect(decision.state).toBe('NORMAL');
  });
});
