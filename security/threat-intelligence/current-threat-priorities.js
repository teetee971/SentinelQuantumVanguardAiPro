export const CURRENT_THREAT_PRIORITIES_AS_OF = '2026-10-02';

export const CURRENT_THREAT_PRIORITIES = Object.freeze([
  Object.freeze({
    id: 'android-streamrat-2026',
    family: 'StreamRat',
    platform: 'android',
    first_publicly_observed: '2026-07',
    source_uri: 'https://www.threatfabric.com/blogs/from-meta-ads-to-full-device-takeover-uncovering-streamrat',
    behaviors: Object.freeze([
      'accessibility_service_abuse',
      'media_projection_abuse',
      'remote_device_control',
      'vnc_hidden_screen',
      'ui_tree_collection',
      'keylogging',
      'credential_overlay',
      'network_blocking',
      'screen_blocking',
      'sideload_delivery'
    ]),
    sentinel_detection_targets: Object.freeze([
      'accessibility_plus_overlay',
      'media_projection_risk',
      'unknown_source_install',
      'overlay_credential_risk',
      'remote_control_capability'
    ]),
    autonomous_blocking: false
  }),
  Object.freeze({
    id: 'android-trickmo-ton-2026',
    family: 'TrickMo',
    platform: 'android',
    first_publicly_observed: '2026-01',
    source_uri: 'https://www.threatfabric.com/blogs/new-trickmo-variant-device-take-over-malware-targeting-banking-fintech-wallet-auth-app',
    behaviors: Object.freeze([
      'banking_trojan',
      'device_takeover',
      'resilient_c2',
      'ton_network_c2',
      'credential_theft',
      'persistence'
    ]),
    sentinel_detection_targets: Object.freeze([
      'accessibility_abuse',
      'overlay_credential_risk',
      'unusual_persistent_networking',
      'known_hash_reputation',
      'package_capability_correlation'
    ]),
    autonomous_blocking: false
  }),
  Object.freeze({
    id: 'android-perseus-2026',
    family: 'Perseus',
    platform: 'android',
    first_publicly_observed: '2026-03',
    source_uri: 'https://www.threatfabric.com/blogs/perseus-dto-malware-that-takes-notes',
    behaviors: Object.freeze([
      'banking_trojan',
      'device_takeover',
      'sensitive_note_hunting',
      'social_ad_delivery',
      'streaming_lure'
    ]),
    sentinel_detection_targets: Object.freeze([
      'sideload_delivery',
      'sensitive_app_access_correlation',
      'accessibility_abuse',
      'known_hash_reputation'
    ]),
    autonomous_blocking: false
  }),
  Object.freeze({
    id: 'android-massiv-2026',
    family: 'Massiv',
    platform: 'android',
    first_publicly_observed: '2026-02',
    source_uri: 'https://www.threatfabric.com/blogs/massiv-when-your-iptv-app-terminates-your-savings',
    behaviors: Object.freeze([
      'banking_trojan',
      'device_takeover',
      'sideload_delivery',
      'iptv_lure',
      'remote_control'
    ]),
    sentinel_detection_targets: Object.freeze([
      'unknown_source_install',
      'accessibility_abuse',
      'remote_control_capability',
      'known_hash_reputation'
    ]),
    autonomous_blocking: false
  }),
  Object.freeze({
    id: 'supply-chain-shai-hulud-2026',
    family: 'Shai-Hulud',
    platform: 'software_supply_chain',
    first_publicly_observed: '2025-09',
    source_uri: 'https://attack.mitre.org/software/S9008/',
    behaviors: Object.freeze([
      'supply_chain_compromise',
      'npm_package_propagation',
      'github_token_theft',
      'ci_cd_credential_theft',
      'malicious_package_publication'
    ]),
    sentinel_detection_targets: Object.freeze([
      'ci_dependency_integrity',
      'secret_exposure_detection',
      'oidc_token_anomaly',
      'package_provenance_validation',
      'workflow_integrity'
    ]),
    autonomous_blocking: false
  }),
  Object.freeze({
    id: 'identity-kali365-2026',
    family: 'Kali365',
    platform: 'identity_cloud',
    first_publicly_observed: '2026-04',
    source_uri: 'https://attack.mitre.org/software/S9044/',
    behaviors: Object.freeze([
      'phishing_as_a_service',
      'device_code_phishing',
      'oauth_token_theft',
      'session_cookie_theft',
      'adversary_in_the_middle',
      'malicious_copy_paste'
    ]),
    sentinel_detection_targets: Object.freeze([
      'oauth_device_code_warning',
      'session_token_risk',
      'aitm_domain_intelligence',
      'copy_paste_execution_warning',
      'phishing_url_reputation'
    ]),
    autonomous_blocking: false
  })
]);

export function currentThreatDetectionTargets() {
  return [...new Set(
    CURRENT_THREAT_PRIORITIES.flatMap(item => item.sentinel_detection_targets)
  )].sort();
}
