import fc from 'fast-check';
import { CallIdentificationService } from '../../modules/phone/CallIdentification';

describe('CallIdentification fuzz campaign', () => {
  const service = new CallIdentificationService();

  test('never throws on arbitrary UTF-16 phone input', () => {
    fc.assert(
      fc.property(fc.string(), input => {
        const result = service.identifyCall(input);
        expect(typeof result.phoneNumber).toBe('string');
        expect(result.riskScore.total).toBeGreaterThanOrEqual(0);
        expect(result.riskScore.total).toBeLessThanOrEqual(100);
        expect(result.riskScore.reasons.length).toBeLessThanOrEqual(20);
      }),
      { numRuns: 5000, endOnFailure: true }
    );
  });

  test('risk factors remain bounded for adversarial numeric strings', () => {
    fc.assert(
      fc.property(fc.stringMatching(/^[0-9+().\\s-]{0,80}$/), input => {
        const result = service.identifyCall(input);
        const factors = result.riskScore.factors;
        for (const value of Object.values(factors)) {
          expect(Number.isFinite(value)).toBe(true);
          expect(value).toBeGreaterThanOrEqual(0);
          expect(value).toBeLessThanOrEqual(20);
        }
        expect(result.riskScore.total).toBeLessThanOrEqual(100);
      }),
      { numRuns: 5000, endOnFailure: true }
    );
  });

  test('normalization is stable for equivalent punctuation variants', () => {
    fc.assert(
      fc.property(fc.integer({ min: 100000000, max: 999999999 }), digits => {
        const raw = `0${digits}`;
        const punctuated = raw.replace(/(.{2})(.{2})(.{2})(.{2})(.{2})/, '$1 $2-$3.$4 $5');
        expect(service.identifyCall(raw).phoneNumber).toBe(
          service.identifyCall(punctuated).phoneNumber
        );
      }),
      { numRuns: 2000, endOnFailure: true }
    );
  });
});
