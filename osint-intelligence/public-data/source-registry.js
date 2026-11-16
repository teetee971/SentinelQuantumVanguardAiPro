const ALLOWED_HOSTS = new Set([
  'www.data.gouv.fr',
  'annuaire-entreprises.data.gouv.fr',
  'recherche-entreprises.api.gouv.fr',
  'cadastre.data.gouv.fr'
]);

export const PUBLIC_DATA_SOURCES = Object.freeze({
  data_gouv_recent: Object.freeze({
    source_id: 'data_gouv_recent',
    provider: 'data.gouv.fr',
    source_kind: 'dataset_change_feed',
    url: 'https://www.data.gouv.fr/api/1/datasets/recent.atom',
    ingestion_mode: 'atom',
    refresh_policy: 'hourly',
    purpose: 'Discover newly published or updated public datasets for analyst review.',
    automatic_operational_action: false
  }),
  annuaire_entreprises: Object.freeze({
    source_id: 'annuaire_entreprises',
    provider: 'DINUM',
    source_kind: 'business_search',
    url: 'https://recherche-entreprises.api.gouv.fr',
    ingestion_mode: 'query_api',
    refresh_policy: 'on_demand',
    purpose: 'Resolve French company identifiers and open registry metadata from the official public search API.',
    automatic_operational_action: false
  }),
  pci_cadastre: Object.freeze({
    source_id: 'pci_cadastre',
    provider: 'DGFiP / data.gouv.fr',
    source_kind: 'cadastral_reference',
    url: 'https://cadastre.data.gouv.fr/datasets/plan-cadastral-informatise',
    latest_download_base: 'https://cadastre.data.gouv.fr/data/dgfip-pci-vecteur/latest/edigeo/departements/',
    ingestion_mode: 'metadata_and_explicit_download',
    refresh_policy: 'monthly_check',
    purpose: 'Reference official cadastral geometry where a lawful, explicit geospatial workflow requires it.',
    automatic_operational_action: false
  })
});

export function getPublicDataSource(sourceId) {
  if (typeof sourceId !== 'string' || sourceId.trim() === '') return null;
  return PUBLIC_DATA_SOURCES[sourceId.trim().toLowerCase()] ?? null;
}

export function assertApprovedPublicDataUrl(rawUrl) {
  const url = new URL(rawUrl);
  if (url.protocol !== 'https:' || !ALLOWED_HOSTS.has(url.hostname)) {
    throw new Error('PUBLIC_DATA_SOURCE_NOT_APPROVED');
  }
  return url.href;
}

export function publicDataSourceIds() {
  return Object.keys(PUBLIC_DATA_SOURCES).sort();
}
