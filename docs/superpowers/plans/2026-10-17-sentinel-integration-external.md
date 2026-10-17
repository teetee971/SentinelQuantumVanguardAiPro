# Sentinel External Dependency Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert dormant/server-side modules into truthful runtime states, activating only those with real deployable dependencies and leaving the rest explicitly locked instead of simulated.

**Architecture:** Each external subsystem gets a small runtime availability adapter that proves its own backend/credential/store/transport readiness. The Integration Registry consumes those adapters; no subsystem is promoted to `CONNECTED` from code presence alone.

**Tech Stack:** Node.js, existing backend adapters, Redis/PostgreSQL interfaces, VPN provisioning code, Android/runtime diagnostics where applicable.

**Spec:** `docs/superpowers/specs/2026-10-17-sentinel-integration-core-design.md`

## Global Constraints

- No fake credentials, localhost substitutes, hardcoded READY, or test fixtures as production evidence.
- Secrets stay outside the repository.
- Privileged/autonomous Decision Plane actions remain disabled unless existing policy and identity boundaries are genuinely satisfied.
- External network checks must be bounded and fail closed.
- `LOCKED_EXTERNAL_DEPENDENCY` is a valid and truthful production state.

## Review Focus

- Backend reachable but unauthenticated must remain locked.
- Credential present but backing store unavailable must remain locked.
- Stale health evidence must not preserve CONNECTED.
- Partial multi-component readiness must not collapse into READY.
- Test doubles must be distinguishable from production runtime evidence.

---

### Task 1: Map all dormant inventory entries to product capability IDs

**Files:**
- Modify: `config/integration-capabilities.json`
- Modify: `config/module-continuity-inventory.json` only where product mapping metadata is needed.
- Test: `scripts/check-integration-product-truth.test.js`

- [ ] Add RED assertions that every runtime-relevant dormant inventory entry maps to a capability ID and a truthful integration state/reason.
- [ ] Run integration truth tests; expect failures for unmapped dormant entries.
- [ ] Add mappings without changing module runtime behavior.
- [ ] Re-run tests; expect PASS.
- [ ] Commit `chore(integration): map dormant modules to product truth`.

### Task 2: VPN gateway/catalog/provisioning readiness adapter

**Files:**
- Create: `ops/vpn-gateway/runtime-readiness.js`
- Test: `ops/vpn-gateway/runtime-readiness.test.js`
- Modify Integration Registry external-state bridge.

**Interfaces:**
- Produces a bounded readiness result requiring real listener/config, accepted gateway evidence, signing/publication path, and required durable state.

- [ ] Write RED tests for missing listener, missing signing material, stale acceptance evidence, and valid complete runtime.
- [ ] Implement fail-closed readiness composition around existing gateway modules.
- [ ] Register result as `CONNECTED` only for a complete real runtime; otherwise `LOCKED_EXTERNAL_DEPENDENCY`.
- [ ] Run VPN gateway and integration truth suites.
- [ ] Commit `feat(vpn): expose truthful gateway runtime readiness`.

### Task 3: Community moderation and phone-reputation backend readiness

**Files:**
- Create: `security/phone-intelligence/backend-readiness.js`
- Test: `security/phone-intelligence/backend-readiness.test.js`
- Modify Integration Registry mapping.

- [ ] Write RED tests covering missing moderation store, missing rate limiter, missing identity binding, and complete backend composition.
- [ ] Implement bounded readiness using existing PostgreSQL/Redis adapter contracts without embedding credentials.
- [ ] Keep community client context usable while backend moderation capability remains locked if infrastructure is absent.
- [ ] Run phone-intelligence and integration truth suites.
- [ ] Commit `feat(community): expose backend integration readiness`.

### Task 4: Threat Intelligence / regulatory / OSINT readiness

**Files:**
- Create: `security/threat-intelligence/runtime-readiness.js`
- Create/modify source-specific readiness tests for MalwareBazaar, VirusShare, FSMA and reviewed regulatory sources.
- Modify Integration Registry mapping.

- [ ] Write RED tests for missing API key/auth, missing scheduler/ingestion path, stale source evidence, and advisory-only sources.
- [ ] Implement source-by-source readiness; do not conflate a working adapter library with an active feed.
- [ ] Mark analyst-only/attribution-governed modules `INTENTIONALLY_DISABLED` or locked as appropriate.
- [ ] Run threat-intel, security-governance, and integration truth tests.
- [ ] Commit `feat(intel): expose source-specific runtime readiness`.

### Task 5: SOC / Decision Plane / owner identity boundaries

**Files:**
- Create: `decision-plane/integration-readiness.js`
- Test: `decision-plane/integration-readiness.test.js`
- Modify Integration Registry mapping.

- [ ] Write RED tests proving missing trusted IdP/device registry/replay store/attestation keeps privileged capability non-connected.
- [ ] Compose existing policy/replay/owner contracts into a side-effect-free readiness report.
- [ ] Keep autonomous privileged execution `INTENTIONALLY_DISABLED` unless all explicitly approved production dependencies exist.
- [ ] Run decision-plane/security suites.
- [ ] Commit `feat(decision-plane): expose policy readiness without enabling autonomy`.

### Task 6: Mesh, Wearable, Voice/RTC/PTT transport truth

**Files:**
- Add bounded transport-readiness adapters alongside existing modules.
- Add targeted tests and Integration Registry mappings.

- [ ] Write RED tests that code/library presence alone cannot make Mesh, Wearable, LiveKit/PTT CONNECTED.
- [ ] Implement readiness from real session/channel/token/runtime observation only.
- [ ] Mark missing transport/token/backend dependencies `LOCKED_EXTERNAL_DEPENDENCY` and physical peripheral verification `PHYSICAL_REQUIRED` where applicable.
- [ ] Run targeted suites.
- [ ] Commit `feat(integration): expose transport-backed capability truth`.

## Acceptance

Every dormant or external-service-backed capability is either backed by real bounded runtime evidence or explicitly locked/disabled with a stable reason. No dormant library contributes to a 100% integration claim by mere existence.