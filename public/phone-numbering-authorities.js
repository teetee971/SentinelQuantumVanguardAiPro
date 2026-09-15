/**
 * Official numbering source catalogue.
 *
 * Coverage describes what the linked public source can establish. It must never
 * be presented as the current serving operator or as caller identity.
 */
export const NUMBERING_COVERAGE = Object.freeze({
  BLOCK_ALLOCATION: 'block-allocation',
  NUMBERING_PLAN: 'numbering-plan'
});

const ITU_PLAN_DIRECTORY = Object.freeze({
  authority: 'UIT / ITU',
  sourceUrl: 'https://www.itu.int/itu-t/inr/nnp/',
  coverage: NUMBERING_COVERAGE.NUMBERING_PLAN,
  sourceKind: 'international-plan-directory'
});

const NATIONAL_AUTHORITIES = Object.freeze({
  AT: Object.freeze({
    authority: 'RTR',
    sourceUrl: 'https://www.rtr.at/TKP/service/rufnummernsuche/Rufnummernsuche.de.html',
    coverage: NUMBERING_COVERAGE.BLOCK_ALLOCATION,
    sourceKind: 'national-regulator'
  }),
  BE: Object.freeze({
    authority: 'IBPT / BIPT',
    sourceUrl: 'https://www.bipt.be/',
    coverage: NUMBERING_COVERAGE.NUMBERING_PLAN,
    sourceKind: 'national-regulator'
  }),
  CA: Object.freeze({
    authority: 'CNAC',
    sourceUrl: 'https://cnac.ca/',
    coverage: NUMBERING_COVERAGE.NUMBERING_PLAN,
    sourceKind: 'national-numbering-administrator'
  }),
  CH: Object.freeze({
    authority: 'OFCOM / BAKOM',
    sourceUrl: 'https://www.bakom.admin.ch/',
    coverage: NUMBERING_COVERAGE.NUMBERING_PLAN,
    sourceKind: 'national-regulator'
  }),
  FR: Object.freeze({
    authority: 'ARCEP',
    sourceUrl: 'https://www.data.gouv.fr/datasets/ressources-en-numerotation-telephonique',
    coverage: NUMBERING_COVERAGE.BLOCK_ALLOCATION,
    sourceKind: 'national-regulator'
  }),
  GB: Object.freeze({
    authority: 'Ofcom',
    sourceUrl: 'https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbering-data',
    coverage: NUMBERING_COVERAGE.BLOCK_ALLOCATION,
    sourceKind: 'national-regulator'
  }),
  US: Object.freeze({
    authority: 'NANPA',
    sourceUrl: 'https://www.nationalnanpa.com/reports/reports_npa.html',
    coverage: NUMBERING_COVERAGE.NUMBERING_PLAN,
    sourceKind: 'national-numbering-administrator'
  })
});

export function getNumberingAuthority(countryIso) {
  const iso = typeof countryIso === 'string' ? countryIso.trim().toUpperCase() : '';
  const source = NATIONAL_AUTHORITIES[iso] || ITU_PLAN_DIRECTORY;
  return Object.freeze({ country: iso, ...source });
}

export function validateNumberingAuthority(source) {
  if (!source || !/^[A-Z]{2}$/.test(source.country || '')) return false;
  if (!Object.values(NUMBERING_COVERAGE).includes(source.coverage)) return false;
  try {
    const url = new URL(source.sourceUrl);
    return url.protocol === 'https:' && Boolean(source.authority);
  } catch {
    return false;
  }
}
