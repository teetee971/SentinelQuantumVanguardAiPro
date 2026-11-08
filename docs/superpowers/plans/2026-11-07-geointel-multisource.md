# GeoIntel multisource

Scope: existing web console. Preserve Android/source artifacts and main. Add NASA EONET natural hotspots/storms, NWS actual alerts (US coverage), IODA outage observations, UN peace/security publications and OFAC recent-action publications alongside USGS.

Data truth: publication signals are not confirmed conflict incidents or a consolidated sanctions registry. Missing coordinates remain explicitly unlocated and list-only. Preserve source dates/IDs/URLs; do not substitute acquisition time for occurrence/update. Unknown confidence/risk stay unknown. Separate per-source health, limits, errors and coverage; no automatic fake/demo fallback.

Architecture: fixed official source registry, bounded/time-limited transport, Cloudflare Pages advanced-mode worker restricted to those feeds (no open proxy), independent adapters, concurrent partial-result coordinator and existing canvas/list inspector. XML parsed as data in browser, never injected HTML. Worker copied by web build; no deployment performed.

Validation: failing fixture tests first, then source adapters/core/transport/proxy/coordinator tests, existing product/build regressions, actual provider acquisition snapshots, real headless Chromium UI/XML/race/error tests and screenshot. Record actual access/CORS failures and deployment gap. Keep customer/runtime availability false until deployed/browser end-to-end observed. Review and draft PR only.
