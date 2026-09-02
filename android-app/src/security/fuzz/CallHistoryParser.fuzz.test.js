import { parseStoredCallHistory } from '../../modules/phone/CallHistoryParser';

// Deterministic, dependency-free property fuzzing. Keeping the generator local
// avoids introducing another supply-chain dependency into the security test path.
function xorshift32(seed) {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return state >>> 0;
  };
}

function arbitraryString(next) {
  const length = next() % 256;
  let value = '';
  for (let i = 0; i < length; i += 1) {
    value += String.fromCodePoint(next() % 0x110000);
  }
  return value;
}

function arbitraryJson(next, depth = 0) {
  if (depth > 3) return next() % 2 ? null : arbitraryString(next);

  switch (next() % 7) {
    case 0: return null;
    case 1: return next() % 2 === 0;
    case 2: return (next() % 100000) - 50000;
    case 3: return arbitraryString(next);
    case 4: return Array.from({ length: next() % 12 }, () => arbitraryJson(next, depth + 1));
    case 5: {
      const object = {};
      for (let i = 0; i < next() % 12; i += 1) object[arbitraryString(next)] = arbitraryJson(next, depth + 1);
      return object;
    }
    default: return next() / 0xffffffff;
  }
}

function validEvent(next, index) {
  const types = ['INCOMING', 'OUTGOING', 'MISSED', 'BLOCKED'];
  const actions = ['ALLOWED', 'BLOCKED', 'FLAGGED', 'AI_HANDLED'];
  const risks = ['SAFE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  return {
    id: `fuzz-${index}-${next()}`,
    phoneNumber: `+590${String(next() % 10000000).padStart(7, '0')}`,
    timestamp: next() % 2000000000000,
    type: types[next() % types.length],
    duration: next() % 36000,
    action: actions[next() % actions.length],
    riskLevel: risks[next() % risks.length],
  };
}

describe('CallHistoryParser defensive fuzzing', () => {
  test('never throws on 12,000 arbitrary untrusted inputs', () => {
    const next = xorshift32(0x51e17e11);

    for (let i = 0; i < 4000; i += 1) {
      expect(() => parseStoredCallHistory(arbitraryString(next))).not.toThrow();
    }

    for (let i = 0; i < 4000; i += 1) {
      const value = arbitraryJson(next);
      const serialized = (() => {
        try { return JSON.stringify(value); } catch { return ''; }
      })();
      expect(() => parseStoredCallHistory(serialized)).not.toThrow();
    }

    for (let i = 0; i < 4000; i += 1) {
      const entries = Array.from({ length: next() % 1205 }, (_, index) =>
        next() % 3 === 0 ? validEvent(next, index) : arbitraryJson(next)
      );
      const serialized = JSON.stringify(entries);
      expect(() => parseStoredCallHistory(serialized)).not.toThrow();
    }
  }, 30000);

  test('preserves the parser invariants under oversized valid arrays', () => {
    const next = xorshift32(0x9e3779b9);
    const entries = Array.from({ length: 5000 }, (_, index) => validEvent(next, index));
    const result = parseStoredCallHistory(JSON.stringify(entries));

    expect(result).toHaveLength(1000);
    expect(result.every((entry) => typeof entry.id === 'string')).toBe(true);
  });
});
