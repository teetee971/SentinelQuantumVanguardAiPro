export const THREAT_SOURCE_REGISTRY = Object.freeze({
  malwarebazaar: Object.freeze({
    source_id: 'malwarebazaar',
    provider: 'abuse.ch',
    source_kind: 'malware_repository',
    public_surface: 'community_api',
    metadata_query_supported: true,
    metadata_adapter_available: true,
    authentication_required: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'Bounded hash metadata queries are implemented through the MalwareBazaar adapter. Auth-Key is required. Malware sample retrieval remains outside the default Sentinel runtime.'
  }),
  virusshare: Object.freeze({
    source_id: 'virusshare',
    provider: 'VirusShare',
    source_kind: 'malware_repository',
    public_surface: 'api_v2',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'Metadata/hash intelligence may be integrated through a bounded adapter. File retrieval is not enabled by this registry.'
  }),
  av_atlas: Object.freeze({
    source_id: 'av_atlas',
    provider: 'AV-TEST',
    source_kind: 'vendor_telemetry',
    public_surface: 'statistics',
    metadata_query_supported: false,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'Statistical/trend reference only until a documented machine-consumable interface and usage terms are explicitly integrated.'
  }),
  kaspersky_cybermap: Object.freeze({
    source_id: 'kaspersky_cybermap',
    provider: 'Kaspersky',
    source_kind: 'threat_map',
    public_surface: 'live_map_and_statistics',
    metadata_query_supported: false,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'Provider telemetry reference. Detection geography must never be treated as actor nationality or responsibility.'
  }),
  checkpoint_threatmap: Object.freeze({
    source_id: 'checkpoint_threatmap',
    provider: 'Check Point',
    source_kind: 'threat_map',
    public_surface: 'live_threat_map',
    metadata_query_supported: false,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'Provider telemetry reference only unless a documented feed/API contract is configured.'
  }),
  radware_live_threat_map: Object.freeze({
    source_id: 'radware_live_threat_map',
    provider: 'Radware',
    source_kind: 'threat_map',
    public_surface: 'near_real_time_map',
    metadata_query_supported: false,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'Anonymized/sampled provider telemetry reference. Observed locations are not attribution.'
  }),
  netscout_threat_horizon: Object.freeze({
    source_id: 'netscout_threat_horizon',
    provider: 'NETSCOUT',
    source_kind: 'threat_map',
    public_surface: 'ddos_attack_map',
    metadata_query_supported: false,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    notes: 'DDoS situation-awareness reference with source/destination/event views. Network source geography is not actor attribution.'
  })
});

export function getThreatSourceProfile(sourceId) {
  if (typeof sourceId !== 'string' || sourceId.trim() === '') return null;
  return THREAT_SOURCE_REGISTRY[sourceId.trim().toLowerCase()] ?? null;
}

export function canSourceAutoDownloadSamples(sourceId) {
  return getThreatSourceProfile(sourceId)?.automatic_sample_download === true;
}

export function canSourceAttributeActors(sourceId) {
  return getThreatSourceProfile(sourceId)?.attribution_capability === true;
}

export function enabledAutomatedThreatSources() {
  return Object.values(THREAT_SOURCE_REGISTRY)
    .filter(source => source.automated_ingestion_enabled === true)
    .map(source => source.source_id)
    .sort();
}
