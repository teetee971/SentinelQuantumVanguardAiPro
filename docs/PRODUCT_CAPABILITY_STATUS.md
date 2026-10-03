# Product capability status — generated

Source: `config/product-capabilities.json` · updated 2026-10-03.

This file is generated. Do not promote a capability by editing this document; update the canonical registry with evidence and let CI validate the invariants.

| Capability | Status | Customer available | Open blockers |
|---|---|---:|---:|
| Android Phone Core | IMPLEMENTED_NOT_VERIFIED | NO | 2 |
| Sentinel VPN | IMPLEMENTED_NOT_CONFIGURED | NO | 3 |
| Voice Studio transformed calls | IMPLEMENTED_NOT_CONFIGURED | NO | 3 |
| GeoIntel USGS earthquakes | IMPLEMENTED_NOT_VERIFIED | NO | 1 |
| GeoIntel conflicts/hotspots/sanctions/weather/outages | NOT_IMPLEMENTED | NO | 1 |
| Collective Defense API | IMPLEMENTED_NOT_VERIFIED | NO | 1 |
| Collective Defense Android center | IMPLEMENTED_NOT_VERIFIED | NO | 2 |
| Signed Phone Intelligence reputation feed | IMPLEMENTED_NOT_CONFIGURED | NO | 3 |
| Phone Intelligence moderation service | IMPLEMENTED_NOT_CONFIGURED | NO | 2 |
| Repository-wide internationalization | IMPLEMENTED_NOT_CONFIGURED | NO | 2 |
| Public signed Android release | IMPLEMENTED_NOT_CONFIGURED | NO | 2 |

## Blocking details

### Android Phone Core

- 14/14 physical Phone Core evidence has not been recorded on the current install scope
- No signed public Android release artifact is demonstrated

Evidence:
- `native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt`
- `docs/PHONE_CORE_PHYSICAL_VALIDATION.md`
- `.github/workflows/build-native-android.yml`

### Sentinel VPN

- Production Sentinel exit gateway is not provisioned
- Redis-backed monotonic VPN lease sequence authority is implemented but not wired or deployed with production storage and credentials
- End-to-end tunnel, DNS, IPv4/IPv6, MTU and recovery evidence is missing

Evidence:
- `docs/security/SENTINEL-VPN-ARCHITECTURE.md`
- `ops/vpn-gateway/redis-lease-sequence-authority.js`
- `ops/vpn-gateway/provisioning-server.js`
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/VpnScreen.kt`

### Voice Studio transformed calls

- Production LiveKit signaling/token issuer is not provisioned
- VoIP/PSTN gateway is not provisioned
- No real transformed call has passed end-to-end physical validation

Evidence:
- `docs/VOICE-STUDIO-ADDON.md`
- `native-android-app/app/src/main/java/com/sentinel/quantum/voice/LiveVoiceTransformEngine.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/voice/SentinelVoipVoicePipeline.kt`

### GeoIntel USGS earthquakes

- Fresh deployment/runtime evidence is not bound to the current repository revision

Evidence:
- `public/geointel.html`
- `public/geointel-source-usgs.js`
- `.github/workflows/frontend-validation.yml`

### GeoIntel conflicts/hotspots/sanctions/weather/outages

- Verified production sources are not wired for conflicts, hotspots, sanctions, weather or outages

Evidence:
- `public/geointel.html`
- `public/geointel-core.js`

### Collective Defense API

- Historical Render evidence exists, but fresh deployment and live health evidence for the current revision is not recorded

Evidence:
- `backend/wangiri-api/README.md`
- `backend/wangiri-api/collective_intel.py`
- `render.yaml`

### Collective Defense Android center

- Merged signed Android release evidence is missing
- Real-device lookup/report/watch/background-notification evidence is missing

Evidence:
- `native-android-app/app/src/main/java/com/sentinel/quantum/security/CollectiveDefenseClient.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CollectiveDefenseScreen.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/background/CollectiveDefenseWorkScheduler.kt`

### Signed Phone Intelligence reputation feed

- Production signing keys and issuer mapping are not provisioned
- Durable sequence allocation, scheduler and publication endpoint are not operational
- Android end-to-end synchronization evidence is missing

Evidence:
- `security/phone-intelligence/call-rule-publication.js`
- `config/module-continuity-inventory.json`

### Phone Intelligence moderation service

- Durable PostgreSQL moderation storage is not deployed
- Distributed Redis rate limiting and production identity binding are not deployed

Evidence:
- `security/phone-intelligence/moderation-core.js`
- `security/phone-intelligence/postgres-moderation-store.js`
- `security/phone-intelligence/redis-rate-limiter.js`

### Repository-wide internationalization

- Android and other product surfaces still contain hard-coded user-facing strings
- Repository-wide locale coverage gate is not complete

Evidence:
- `native-android-app/app/src/main/res/values/strings.xml`
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt`

### Public signed Android release

- Release keystore custody and signed APK/AAB provenance are not demonstrated
- Observed publication channel evidence is missing

Evidence:
- `.github/workflows/android-release.yml`
- `.github/workflows/build-aab-playconsole.yml`
- `RELEASE_CHECKLIST.md`
