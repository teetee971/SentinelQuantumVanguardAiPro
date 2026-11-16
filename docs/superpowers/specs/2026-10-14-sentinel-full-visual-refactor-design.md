# Sentinel Quantum Vanguard AI Pro — Full Visual Refactor & Product Finalization Design

Date: 2026-10-14
Status: Approved architecture, implementation not started
Branch: feat/full-visual-refactor

## 1. Intent

Sentinel must stop looking like a collection of technically capable screens and become one coherent premium security product. The objective is not to rewrite Phone Core, SMS/MMS, Intelligence Core, VPN, Collective Defense, contacts, or existing security engines. The objective is to redesign the visible product layer around those engines while preserving their current safety boundaries, then close the P0/P1 gaps identified by the full-system review.

Success means:

- the user can immediately understand current protection state, uncertainty, freshness and provenance;
- Phone, SMS, contacts, reputation, network and VPN share one visual language and one risk vocabulary;
- Intelligence becomes visible and useful without becoming an enforcement authority;
- no UI refactor changes the CallScreening critical path before respondToCall();
- no screen invents green/pass/verified state when evidence is missing, stale, limited or unavailable;
- the application remains usable with missing permissions, no network, unavailable provider data, process restart, role revocation or partial backend availability;
- Samsung Galaxy S24+ remains the primary physical validation target;
- release readiness is earned through exact-head CI, physical-device evidence and release-signing/legal/Play evidence rather than presentation alone.

## 2. Non-goals

This project does not:

- rewrite Telecom, InCallService, SMS/MMS transports, WireGuard backend or Intelligence aggregation;
- add permissions solely for visual features;
- introduce remote lookups before CallScreeningService responds to Android;
- promote dormant/R&D modules to production without operational evidence;
- add fabricated contact, caller, community or threat data for demo purposes;
- claim public production readiness before signed release and physical validation exist.

## 3. Product architecture

The refactor introduces a presentation layer over existing domain services. Existing engines remain authoritative for their own concerns.

Data path:

`existing engine/service -> bounded presentation mapper -> stable UI state -> composable screen`

The presentation layer may translate technical states, combine already-available read-only evidence and localize labels. It may not silently change enforcement decisions.

Core presentation concepts:

- `SentinelStatus`: OK, WARN, BAD, UNKNOWN, LIMITED, LOCKED.
- `TrustPresentation`: user-facing trust/risk state with confidence, freshness and provenance.
- `EvidencePresentation`: bounded list of evidence sources, freshness and limitations.
- `ReadinessPresentation`: Android role/permission/provider readiness without false promotion.
- `LiveProtectionPresentation`: current VPN/network/Phone Core state with explicit runtime truth.

These should be implemented as focused mappers/components rather than duplicating interpretation logic screen by screen.

## 4. Visual direction — D.1 systemized

The approved D.1 language becomes the global design system.

### 4.1 Foundations

- Near-black primary background.
- Graphite/glass surfaces with restrained translucency.
- Cyan/white as primary identity accents.
- Green reserved for confirmed healthy/ready states.
- Amber/orange reserved for degraded/attention states.
- Red reserved for verified danger, blocked state or destructive action.
- Neutral gray for unknown/not-executed/insufficient evidence.
- Convergence lines/rings may appear as low-contrast decorative structure, never as visual noise over actionable content.
- Rounded surfaces and strong hierarchy; no cartoon styling.
- Minimum readable user-facing text: 12sp; critical labels larger.
- Motion must be short and non-blocking. Incoming-call controls must never wait for animation.

### 4.2 Universal state language

Technical enums must map to one consistent user vocabulary:

- OK — confirmed and ready.
- WARN — usable with a known limitation.
- BAD — verified problem or high-risk condition.
- UNK — unknown or insufficient evidence.
- LIMITED — explicitly incomplete platform/device/backend capability.
- LOCKED — unavailable until a role, permission, consent or prerequisite is satisfied.

No screen may use green merely because a request completed successfully when the underlying evidence is insufficient.

### 4.3 Common components

Create reusable components for:

- `SentinelIdentityTrustCard`
- `SentinelRiskBadge`
- `SentinelEvidenceSheet`
- `SentinelReadinessCard`
- `SentinelLiveStatusCard`
- `SentinelActionPanel`
- `SentinelSectionHeader`
- `SentinelEmptyOrLimitedState`
- `SentinelFreshnessLabel`
- `SentinelSourceLabel`

Source labels must be user-friendly French strings rather than enum names.

## 5. Navigation model

Keep four primary destinations but make their purpose explicit:

1. Accueil
2. Communications
3. Protection
4. Plus

### Accueil

Becomes a security dashboard, not a menu. It shows:

- Phone Core state;
- active protection state;
- highest-priority current warning;
- recent local security events;
- VPN/network status;
- key actions requiring user attention.

### Communications

Contains:

- Dialer
- In-call experience
- Caller ID / number profile
- Contacts
- SMS/MMS
- Call/message history
- communication intelligence and reputation

### Protection

Contains:

- Network Center
- Wi-Fi/Bluetooth
- VPN
- Private Mesh when operational
- Exposure / OSINT surfaces
- Collective Defense

### Plus

Contains settings, diagnostics, permissions/roles, logs, experimental surfaces, about/legal and advanced tooling. Technical diagnostics must not dominate the normal customer journey.

## 6. Phone Core refactor

### 6.1 In-call screen

Replace the current stacked technical cards with one primary identity/trust hero.

Primary hierarchy:

1. call state + direction;
2. identity/name/number;
3. verbal trust state (`Connu`, `Inconnu`, `À surveiller`, `Suspect`, `Données insuffisantes`);
4. provenance/freshness line;
5. call duration;
6. expandable evidence/details;
7. fixed call controls.

The numeric risk score is secondary and explicitly labelled indicative.

The screen must refresh Intelligence after post-screening publication without polling indefinitely. Preferred bounded behavior: immediate lookup plus bounded refresh attempts around +500 ms and +1500 ms, or an observable revision/Flow if one already exists. The UI must not remain permanently `Données insuffisantes` merely because the post-response event arrived after initial composition.

Call controls remain stable at the bottom and retain test IDs/semantics.

### 6.2 Ended-call summary

Display:

- answered/missed/rejected/disconnected outcome where known;
- direction;
- accurate duration based on a captured end timestamp, not only the last active UI tick;
- identity/trust state;
- freshness/provenance;
- contextual actions: number profile, allow/block/report when those existing actions are legally and technically available.

### 6.3 Caller ID / number profile

Unify local identity, ARCEP/public allocation, network verification, local rules, community evidence and Intelligence into one coherent profile. Remote enrichment remains opt-in and post-screening only.

## 7. Contacts refactor

Contacts becomes a first-class Sentinel domain instead of a thin Android provider reader.

Target model supports Sentinel-owned contacts with:

- first/last/display name;
- phone numbers;
- emails;
- birthday;
- organization;
- photo/avatar reference;
- social/profile links;
- user notes;
- provenance per field;
- confidence/suggestion state;
- link to Android contact when one exists.

Android Contacts synchronization is explicit and permission-gated. Sentinel-only contacts must work without an Android counterpart. `WRITE_CONTACTS` is added only when the sync implementation exists and has a clear user action; the visual refactor alone must not request it.

Potential profile suggestions from public/remote sources must be presented as suggestions, never merged as fact without user confirmation.

## 8. SMS/MMS refactor

Use the same identity/trust header as calls.

Conversation surfaces must clearly distinguish:

- sender identity;
- local/community risk context;
- link/phishing analysis;
- transport status;
- message content;
- uncertainty/freshness.

Security metadata should not visually overpower message content unless risk is high. MMS quarantine/download/provider states must become clear user states instead of internal pipeline terminology.

Notification privacy remains conservative and user-controlled.

## 9. Network Center and VPN refactor

### Network Center

Every observation must communicate freshness:

- observed now;
- Android cached result;
- stale/timeout;
- unavailable due to permission/platform restriction.

Do not imply active scanning beyond what Android exposes.

### VPN

Use a single live protection panel showing:

- disconnected;
- consent required;
- connecting;
- protected;
- degraded;
- failed;
- gateway unavailable.

The UI must not say protected unless the backend reports the tunnel up. DNS/gateway details live in an expandable technical section.

Process death/restart reconciliation with real tunnel/backend state is a P1 engineering requirement before production VPN claims.

## 10. Collective Defense and reputation

Community evidence is context, never sole auto-block authority.

The UI must distinguish:

- local evidence;
- signed reputation package evidence;
- community aggregate;
- public/regulatory source;
- unknown/unverified report.

The backend must be treated as preproduction until current-revision deployment, rate limiting, signing/publication and health evidence are bound to the release.

## 11. Home dashboard

Home becomes a concise command center.

Top section:

- Sentinel overall state based on explicit component truth, not an invented aggregate percentage.

Next:

- immediate action required;
- Phone Core status;
- communications risk highlights;
- network/VPN state;
- recent security events.

Avoid a dashboard full of percentages. Prefer actionable states with drill-down.

## 12. Activation and diagnostics

The first-run wizard remains sequential and Android-controlled. Redesign it visually, but preserve its persisted state machine and fail-closed role/permission truth.

Normal activation UI should explain what the user needs to do and why. Raw certification counts and diagnostic evidence belong in the advanced diagnostic screen, not the primary setup journey.

Diagnostics must continue to expose READY/LIMITED/LOCKED truth and must never convert emulator evidence into physical validation.

## 13. Settings and privacy

Settings groups:

- Communications protection
- Privacy / remote enrichment consent
- Notifications / lock-screen preview
- Network/VPN
- Collective Defense
- Appearance/language
- Diagnostics / advanced
- Legal/about

Any setting that permits raw phone-number egress must clearly explain the data class, destination/purpose and how to disable it.

## 14. Local storage and privacy hardening

P1 requirements from system review:

- remove durable personal labels from Intelligence store where they can be resolved on demand, or encrypt them with an Android Keystore-backed design;
- do not treat hexadecimal encoding as encryption;
- extend logging policy from secret redaction to structured PII-safe logging so phone numbers, e-mails, message bodies and OTP material cannot be logged accidentally;
- retain app-private storage, no-backup and bounded retention principles;
- preserve raw SMS/MMS content only where Android/provider functionality explicitly requires it.

## 15. Web/PWA harmonization

The public web experience should adopt the same visual tokens and state vocabulary, but does not need to mirror the mobile navigation exactly.

P1 web security work:

- migrate inline scripts/styles so CSP can remove `unsafe-inline`;
- preserve `frame-ancestors 'none'`, `nosniff`, restrictive Permissions Policy and HTTPS-only behavior;
- keep public product claims synchronized with capability truth.

## 16. Accessibility and device constraints

- Samsung Galaxy S24+ is the primary physical acceptance device.
- API 24 remains supported at the Android compatibility boundary.
- Respect system font scaling.
- Touch targets must meet Android accessibility guidance.
- All risk states need text/icon semantics, not color alone.
- Dark surfaces must meet contrast requirements.
- Critical call actions stay reachable without scrolling.
- No animation may delay answer/reject/hangup controls.

## 17. Error and partial-data behavior

Every screen must have explicit states for:

- loading;
- ready;
- empty;
- permission required;
- role required;
- stale;
- limited;
- provider unavailable;
- backend unavailable;
- failed.

A backend timeout must not erase local evidence. A stale cached result must remain visibly stale. Unknown state must not become success merely to simplify the UI.

## 18. Testing strategy

Each migrated screen requires:

- mapper/state unit tests;
- Compose contract or instrumentation tests for critical states;
- accessibility semantics for primary actions;
- screenshot/manual physical review on S24+ for high-value flows;
- existing Phone Core exact-head emulator qualification to remain green;
- no regression in CallScreening timing boundary;
- product-truth tests preventing unsupported claims.

For Phone Core screens specifically, test at least:

- known contact;
- unknown number;
- fresh Intelligence;
- Intelligence arriving after initial composition;
- stale Intelligence;
- community-only evidence;
- no evidence;
- role revoked;
- active call;
- ringing call;
- disconnected call;
- multi-call/conference state.

## 19. Delivery program

The global project is decomposed into bounded PR waves. Do not create one giant UI PR.

### Phase A — Phone Core + design foundations (P0)

1. D.1 tokens/status components.
2. InCall dynamic Intelligence refresh and hero redesign.
3. Caller ID/number profile redesign.
4. Dialer/history consistency.
5. S24+ physical validation campaign.

Exit criterion: Phone Core visually coherent and physically evidenced without changing screening safety boundaries.

### Phase B — Privacy/storage + Contacts (P1)

1. Intelligence durable-data minimization/encryption.
2. Structured PII-safe logger contract.
3. Sentinel-owned contact model.
4. Android contact linking/sync with explicit permission.
5. Contact suggestion review workflow.

### Phase C — SMS/MMS (P1)

1. Conversation identity/trust component.
2. Risk/details hierarchy.
3. MMS quarantine/download/provider states.
4. Physical carrier/multi-SIM verification.

### Phase D — Network/VPN (P1)

1. Network Center freshness/truth redesign.
2. Bluetooth consistency.
3. VPN live-state redesign.
4. Process-death reconciliation.
5. Production gateway/control-plane evidence.

### Phase E — Collective Defense/backend (P1)

1. UI/provenance redesign.
2. Current-revision backend health/deploy evidence.
3. signed feed publication/synchronization.
4. production-grade storage/rate limiting/identity where required.

### Phase F — Home, settings, diagnostics, Web/PWA (P1/P2)

1. Home command center.
2. Settings hierarchy.
3. Diagnostic separation.
4. Web visual harmonization.
5. CSP hardening.
6. repository-wide i18n cleanup.

### Phase G — Release closure (P0)

1. final physical regression campaign;
2. release signing provenance;
3. legal/publisher/support metadata;
4. Play Data Safety/permissions/content rating/territories;
5. signed APK/AAB exact-head evidence;
6. staged preproduction rollout;
7. public release only after release gate evidence is complete.

## 20. Pull request discipline

- Keep PRs bounded by one user-visible flow or one cross-cutting primitive.
- A design-system primitive PR may be shared by several later screens but must not rewrite unrelated screens.
- Preserve protected branch policy.
- Exact-head CI evidence is required before calling a PR qualified.
- Physical validation requirements cannot be waived by green emulator CI.
- R&D/dormant modules remain explicitly non-production until connected to a deployed runtime with current evidence.

## 21. Acceptance criteria for the full program

The refactor is complete only when:

- all primary Android screens use the shared D.1 system;
- Phone, SMS, Contacts and caller profile use one identity/trust vocabulary;
- Network/VPN uses explicit freshness/live-state truth;
- primary flows no longer expose raw technical enum names;
- all user-facing critical strings are resource-backed/localizable;
- Intelligence updates visibly after post-response publication;
- critical actions remain reachable and accessible on S24+;
- no new pre-response I/O/network dependency exists in CallScreening;
- privacy storage/logging P1s are closed;
- physical Phone Core/SMS/MMS/VPN validations required by product claims are recorded;
- release signing, legal and Play evidence are bound to the exact release artifact;
- product capability registry reflects only capabilities actually available to customers.

## 22. Sequencing rule

Until Phase A P0 and release-blocking physical validation work are closed, new large product modules should not be added. Engineering effort should prioritize coherence, truth, privacy hardening and production evidence over feature count.
