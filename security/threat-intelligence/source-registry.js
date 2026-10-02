export const THREAT_SOURCE_REGISTRY = Object.freeze({
  cisa_kev: Object.freeze({
    source_id: 'cisa_kev',
    provider: 'CISA',
    source_kind: 'vulnerability_catalog',
    public_surface: 'official_github_json_mirror',
    metadata_query_supported: true,
    automated_ingestion_enabled: true,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: false,
    commercial_use_review_required: false,
    freshness_ttl_ms: 24 * 60 * 60 * 1000,
    notes: 'Official KEV data mirror. CC0. Use as evidence of known exploitation, not as proof that a specific device is compromised.'
  }),
  mitre_attack: Object.freeze({
    source_id: 'mitre_attack',
    provider: 'MITRE',
    source_kind: 'public_osint',
    public_surface: 'stix_2_1_release_bundles',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: false,
    commercial_use_review_required: false,
    freshness_ttl_ms: 7 * 24 * 60 * 60 * 1000,
    notes: 'ATT&CK technique, software, campaign and group context. Not an IOC blocklist and not actor attribution evidence by itself.'
  }),
  nvd: Object.freeze({
    source_id: 'nvd',
    provider: 'NIST',
    source_kind: 'vulnerability_catalog',
    public_surface: 'cve_api_2_0',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: false,
    commercial_use_review_required: false,
    freshness_ttl_ms: 24 * 60 * 60 * 1000,
    notes: 'CVE/CVSS/product applicability enrichment. API key is optional but recommended for sustained synchronization.'
  }),
  malwarebazaar: Object.freeze({
    source_id: 'malwarebazaar',
    provider: 'abuse.ch',
    source_kind: 'malware_repository',
    public_surface: 'community_api',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: true,
    commercial_use_review_required: true,
    freshness_ttl_ms: 24 * 60 * 60 * 1000,
    notes: 'Metadata/hash intelligence through a bounded adapter. Auth-Key required. Commercial/for-profit usage may require the enhanced abuse.ch commercial API. Malware sample retrieval stays disabled.'
  }),
  threatfox: Object.freeze({
    source_id: 'threatfox',
    provider: 'abuse.ch',
    source_kind: 'public_osint',
    public_surface: 'community_api_and_exports',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: true,
    commercial_use_review_required: true,
    freshness_ttl_ms: 6 * 60 * 60 * 1000,
    notes: 'Recent vetted IOC feed. Auth-Key required. IOCs older than the provider expiration window must not be retained as active enforcement indicators.'
  }),
  urlhaus: Object.freeze({
    source_id: 'urlhaus',
    provider: 'abuse.ch',
    source_kind: 'public_osint',
    public_surface: 'community_api_and_exports',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: true,
    commercial_use_review_required: true,
    freshness_ttl_ms: 60 * 60 * 1000,
    notes: 'Malware distribution URL intelligence. Auth-Key required for current exports. Active/recency state must be preserved to reduce stale URL false positives.'
  }),
  feodo_tracker: Object.freeze({
    source_id: 'feodo_tracker',
    provider: 'abuse.ch',
    source_kind: 'public_osint',
    public_surface: 'botnet_c2_json_blocklist',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: false,
    commercial_use_review_required: false,
    freshness_ttl_ms: 30 * 60 * 1000,
    notes: 'Active botnet C2 context. Prefer the recommended current blocklist over aggressive historical data to reduce recycled-IP false positives.'
  }),
  malpedia: Object.freeze({
    source_id: 'malpedia',
    provider: 'Fraunhofer FKIE',
    source_kind: 'public_osint',
    public_surface: 'malware_family_and_yara_api',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: false,
    commercial_use_review_required: true,
    freshness_ttl_ms: 24 * 60 * 60 * 1000,
    notes: 'Family naming, references and YARA context. Some API surfaces require registration; redistribution/licensing must be reviewed before bundling rules.'
  }),
  misp_warninglists: Object.freeze({
    source_id: 'misp_warninglists',
    provider: 'MISP Project',
    source_kind: 'public_osint',
    public_surface: 'warninglists_repository',
    metadata_query_supported: true,
    automated_ingestion_enabled: false,
    automatic_sample_download: false,
    attribution_capability: false,
    auth_required: false,
    commercial_use_review_required: false,
    freshness_ttl_ms: 7 * 24 * 60 * 60 * 1000,
    notes: 'False-positive suppression context. Warning-list matches must reduce or qualify enforcement confidence rather than create malicious verdicts.'
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
    auth_required: true,
    commercial_use_review_required: true,
    freshness_ttl_ms: 24 * 60 * 60 * 1000,
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
    auth_required: false,
    commercial_use_review_required: true,
    freshness_ttl_ms: 24 * 60 * 60 * 1000,
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
    auth_required: false,
    commercial_use_review_required: true,
    freshness_ttl_ms: 60 * 60 * 1000,
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
    auth_required: false,
    commercial_use_review_required: true,
    freshness_ttl_ms: 60 * 60 * 1000,
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
    auth_required: false,
    commercial_use_review_required: true,
    freshness_ttl_ms: 60 * 60 * 1000,
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
    auth_required: false,
    commercial_use_review_required: true,
    freshness_ttl_ms: 60 * 60 * 1000,
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

export function sourceRequiresCommercialReview(sourceId) {
  return getThreatSourceProfile(sourceId)?.commercial_use_review_required === true;
}

export function sourceRequiresAuth(sourceId) {
  return getThreatSourceProfile(sourceId)?.auth_required === true;
}
