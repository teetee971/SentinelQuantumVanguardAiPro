import { CallIdentificationService } from '../../modules/phone/CallIdentification';

function pseudoRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 0x100000000;
  };
}

function fuzzString(random: () => number, maxLength = 128): string {
  const length = Math.floor(random() * maxLength);
  let value = '';
  for (let i = 0; i < length; i += 1) {
    const code = Math.floor(random() * 0x10000);
    value += String.fromCharCode(code);
  }
  return value;
}

describe('CallIdentification fuzz campaign', () => {
  const service = new CallIdentificationService();

  test('never throws on 12000 arbitrary UTF-16 inputs', () => {
    const random = pseudoRandom(0x50484f4e);
    for (let i = 0; i < 12000; i += 1) {
      const result = service.identifyCall(fuzzString(random));
      expect(typeof result.phoneNumber).toBe('string');
      expect(result.riskScore.total).toBeGreaterThanOrEqual(0);
      expect(result.riskScore.total).toBeLessThanOrEqual(100);
      expect(result.riskScore.reasons.length).toBeLessThanOrEqual(20);
    }
  });

  test('risk factors remain bounded for adversarial numeric strings', () => {
    const random = pseudoRandom(0x5249534b);
    const alphabet = '0123456789+(). -';
    for (let i = 0; i < 10000; i += 1) {
      const length = Math.floor(random() * 81);
      let input = '';
      for (let j = 0; j < length; j += 1) {
        input += alphabet[Math.floor(random() * alphabet.length)];
      }
      const result = service.identifyCall(input);
      for (const value of Object.values(result.riskScore.factors)) {
        expect(Number.isFinite(value)).toBe(true);
        expect(value).toBeGreaterThanOrEqual(0);
        expect(value).toBeLessThanOrEqual(20);
      }
      expect(result.riskScore.total).toBeLessThanOrEqual(100);
    }
  });

  test('normalization is stable for punctuation variants', () => {
    const random = pseudoRandom(0x4e4f524d);
    for (let i = 0; i < 3000; i += 1) {
      let digits = '';
      for (let j = 0; j < 9; j += 1) digits += Math.floor(random() * 10);
      const raw = `0${digits}`;
      const punctuated = raw.replace(/(.{2})(.{2})(.{2})(.{2})(.{2})/, '$1 $2-$3.$4 $5');
      expect(service.identifyCall(raw).phoneNumber).toBe(
        service.identifyCall(punctuated).phoneNumber
      );
    }
  });
});
