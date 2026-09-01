export type DefenseAction = 'ALLOW' | 'THROTTLE' | 'BLOCK' | 'ALERT';
export type DefenseState = 'NORMAL' | 'SUSPICIOUS' | 'RATE_LIMITED' | 'BLOCKED' | 'VPN_DEGRADED' | 'UPSTREAM_ATTACK' | 'RECOVERY';

export interface NetworkDefensePolicy {
  windowMs: number;
  maxEventsPerPeer: number;
  maxUniqueDestinationsPerPeer: number;
  maxFailedHandshakesPerPeer: number;
  maxPeers: number;
  blockDurationMs: number;
  maxDomainLength: number;
}

export interface NetworkSecurityEvent {
  peerId: string;
  destination: string;
  timestampMs: number;
  failedHandshake?: boolean;
  bytes?: number;
}

export interface DefenseDecision { action: DefenseAction; state: DefenseState; reason: string; }

interface PeerWindow {
  firstSeenMs: number;
  events: number;
  failedHandshakes: number;
  destinations: Set<string>;
  blockedUntilMs: number;
  lastSeenMs: number;
}

const DEFAULT_POLICY: NetworkDefensePolicy = {
  windowMs: 10_000,
  maxEventsPerPeer: 100,
  maxUniqueDestinationsPerPeer: 30,
  maxFailedHandshakesPerPeer: 10,
  maxPeers: 512,
  blockDurationMs: 30_000,
  maxDomainLength: 253,
};

function positive(value: number | undefined, fallback: number): number {
  return Number.isFinite(value) && (value as number) > 0 ? (value as number) : fallback;
}

function normalizeDestination(value: string, maxLength: number): string | null {
  if (typeof value !== 'string') return null;
  const normalized = value.trim().toLowerCase();
  if (!normalized || normalized.length > maxLength) return null;
  if (/\s|[\u0000-\u001f\u007f]/.test(normalized)) return null;
  return normalized;
}

function normalizePolicy(policy: Partial<NetworkDefensePolicy>): NetworkDefensePolicy {
  return {
    windowMs: Math.min(positive(policy.windowMs, DEFAULT_POLICY.windowMs), 86_400_000),
    maxEventsPerPeer: Math.min(Math.floor(positive(policy.maxEventsPerPeer, DEFAULT_POLICY.maxEventsPerPeer)), 1_000_000),
    maxUniqueDestinationsPerPeer: Math.min(Math.floor(positive(policy.maxUniqueDestinationsPerPeer, DEFAULT_POLICY.maxUniqueDestinationsPerPeer)), 100_000),
    maxFailedHandshakesPerPeer: Math.min(Math.floor(positive(policy.maxFailedHandshakesPerPeer, DEFAULT_POLICY.maxFailedHandshakesPerPeer)), 100_000),
    maxPeers: Math.min(Math.floor(positive(policy.maxPeers, DEFAULT_POLICY.maxPeers)), 10_000),
    blockDurationMs: Math.min(positive(policy.blockDurationMs, DEFAULT_POLICY.blockDurationMs), 86_400_000),
    maxDomainLength: Math.min(Math.floor(positive(policy.maxDomainLength, DEFAULT_POLICY.maxDomainLength)), 253),
  };
}

/** Pure, bounded policy engine. It detects suspicious event bursts but never generates traffic. */
export class NetworkDefenseEngine {
  private readonly policy: NetworkDefensePolicy;
  private readonly peers = new Map<string, PeerWindow>();
  private state: DefenseState = 'NORMAL';
  private lastTimestampMs = 0;

  constructor(policy: Partial<NetworkDefensePolicy> = {}) { this.policy = normalizePolicy(policy); }

  evaluate(event: NetworkSecurityEvent): DefenseDecision {
    if (!event || typeof event.peerId !== 'string' || event.peerId.length === 0) return this.decision('BLOCK', 'BLOCKED', 'invalid_peer');
    if (!Number.isFinite(event.timestampMs) || event.timestampMs < this.lastTimestampMs) return this.decision('BLOCK', 'BLOCKED', 'non_monotonic_timestamp');
    if (!Number.isFinite(event.bytes ?? 0) || (event.bytes ?? 0) < 0) return this.decision('BLOCK', 'BLOCKED', 'invalid_byte_counter');

    this.lastTimestampMs = event.timestampMs;
    this.evict(event.timestampMs);
    const destination = normalizeDestination(event.destination, this.policy.maxDomainLength);
    if (!destination) return this.decision('BLOCK', 'BLOCKED', 'invalid_destination');

    let peer = this.peers.get(event.peerId);
    if (!peer) {
      if (this.peers.size >= this.policy.maxPeers) this.evictOldest();
      peer = { firstSeenMs: event.timestampMs, events: 0, failedHandshakes: 0, destinations: new Set(), blockedUntilMs: 0, lastSeenMs: event.timestampMs };
      this.peers.set(event.peerId, peer);
    }

    if (event.timestampMs - peer.firstSeenMs >= this.policy.windowMs) {
      peer.firstSeenMs = event.timestampMs;
      peer.events = 0;
      peer.failedHandshakes = 0;
      peer.destinations.clear();
    }
    peer.lastSeenMs = event.timestampMs;

    if (peer.blockedUntilMs > event.timestampMs) return this.decision('BLOCK', 'BLOCKED', 'peer_quarantined');

    peer.events += 1;
    peer.destinations.add(destination);
    if (event.failedHandshake) peer.failedHandshakes += 1;

    if (peer.events > this.policy.maxEventsPerPeer) {
      peer.blockedUntilMs = event.timestampMs + this.policy.blockDurationMs;
      this.state = 'RATE_LIMITED';
      return this.decision('THROTTLE', this.state, 'peer_event_rate_exceeded');
    }
    if (peer.destinations.size > this.policy.maxUniqueDestinationsPerPeer) {
      peer.blockedUntilMs = event.timestampMs + this.policy.blockDurationMs;
      this.state = 'SUSPICIOUS';
      return this.decision('BLOCK', this.state, 'destination_fanout_exceeded');
    }
    if (peer.failedHandshakes > this.policy.maxFailedHandshakesPerPeer) {
      peer.blockedUntilMs = event.timestampMs + this.policy.blockDurationMs;
      this.state = 'SUSPICIOUS';
      return this.decision('BLOCK', this.state, 'failed_handshake_rate_exceeded');
    }

    this.state = 'NORMAL';
    return this.decision('ALLOW', this.state, 'policy_pass');
  }

  setVpnHealth(healthy: boolean): DefenseState { this.state = healthy ? 'RECOVERY' : 'VPN_DEGRADED'; return this.state; }
  setUpstreamAttack(active: boolean): DefenseState { this.state = active ? 'UPSTREAM_ATTACK' : 'RECOVERY'; return this.state; }
  getState(): DefenseState { return this.state; }
  getTrackedPeerCount(): number { return this.peers.size; }

  private decision(action: DefenseAction, state: DefenseState, reason: string): DefenseDecision { return { action, state, reason }; }

  private evict(nowMs: number): void {
    for (const [peerId, peer] of this.peers) if (nowMs - peer.lastSeenMs > this.policy.windowMs) this.peers.delete(peerId);
  }

  private evictOldest(): void {
    let oldestId: string | null = null;
    let oldestTimestamp = Number.POSITIVE_INFINITY;
    for (const [peerId, peer] of this.peers) {
      if (peer.lastSeenMs < oldestTimestamp) { oldestTimestamp = peer.lastSeenMs; oldestId = peerId; }
    }
    if (oldestId !== null) this.peers.delete(oldestId);
  }
}
