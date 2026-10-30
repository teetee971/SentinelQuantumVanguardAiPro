# Assisted contact profiles Implementation Plan

> Execute inline; preserve correction branch and untracked Room schemas. Follow TDD and fresh final code review.

**Goal:** Deliver the usable browser-assisted search and explicit profile-link confirmation path when the owner has no developer/API access. This is not automated API enrichment.
**Architecture:** Pure validation/search policy; bounded app-private association book keyed by a hashed Android lookup key; actual Compose card in D1ContactProfileContent. Android provider remains read-only. No Social Intelligence imports, bulk exports, credentials or paid calls.
**Spec:** DESIGN.md plus owner's confirmation of name/company search and subsequent statement that no developer/API access is known.

## Rulings
- Owner lacks API access. Implement browser-assisted discovery/manual association with honest labels; API collection remains unimplemented. This is an implementation choice to make progress, not evidence of official provider integration.
- Search preview includes editable name/company only, explicit per-network browser action. No search on open, no lookup using private numbers.
- Keep association data in app-private noBackupFilesDir, atomically replaced; do not rely on the SharedPreferences backup domain. No new encryption/signing key or Room migration. Do not claim separate application-level encryption.
- Stable lookup-key hash scopes links; missing lookup key disables persistence. No fallback by name or phone. Provider lookup key may change after aggregation; missing links then require manual reassociation, never guessing.

## Tasks
1. RED tests: canonical profile URLs/rejected routes and hosts, bounded query encoding, explicit confirmation, owner isolation, timestamps, duplicate/upsert/removal, malformed/full/failing storage.
2. GREEN policy and persistence: ContactSocialProfiles.kt, ContactSocialProfileStore.kt. Store only canonical HTTPS URL/network, association time and optional user-entered public summary; never label summary as API-collected.
3. Wire LOOKUP_KEY into LocalContactLookup and actual Contacts sheet. Add ContactSocialProfilesCard with collapsed form, preview/search, typed URL, explicit confirmation, open/remove and visible capability limits. Bind async loads/drafts/results to owner; permission and current lookup checked around I/O.
4. Instrumentation coverage with synthetic owner and injected in-memory storage where production boundary supports it; compile, report NON EXECUTED if no runtime. Full Kotlin/app unit suite and relevant Node checks, versionCode14.
5. Fresh review permission/identity/storage/URL boundaries, fix important findings, source commit then debug/unsigned/test APK generation, hash/signature/alignment checks and report. Push dedicated branch with skip-ci; maintain draft and no install.

## Review focus
- Different contacts/homonyms never share associations; reused IDs do not reuse lookup keys.
- External search and profile opening require explicit gestures; no API claims, verified badges or automatic data extraction.
- Malformed/cross-domain URLs, shared routes, duplicate keys and corrupt/full storage fail without overwriting existing book.
- Pause/recreation/revocation and old async callbacks do not publish into current owner.
- Long summaries/forms remain scrollable; existing end-of-page freeze remains open pending physical test13/14.

## Review ledger
- Fresh reviewer found SharedPreferences.commit false can still change its memory map, and root-only backup exclusion does not cover sharedpref on some device-transfer paths. Replaced unshipped preferences implementation with a bounded atomic file in noBackupFilesDir; no original data or preference migrated/deleted.
- Added real-file replacement-failure test with a failing lowest-level rename port and oversize preservation test; API-absent RED observed before file implementation. Existing storage tests preserve corrupt/full book.
- Canonical validation RED:10tests/2failed for Instagram encoded dot routes and numeric JSON timestamp coercion; fixed explicitly, whole suite rerun pending.

## Verification ledger
- API-missing RED observed before implementation. Initial core GREEN8/8. Validation regression RED10tests/2failures, dot paths and timestamp type fixed. Atomic-file API absent RED before implementation; final real-file preservation and roundtrip checks GREEN.
- Fresh reviewer’s two P2s fixed by noBackupFilesDir atomic file, before any shipping of preferences version. Failing rename leaves old file unchanged, oversized write rejected, own temporary file removed;12/12 social unit tests pass. No old user preference migrated/deleted.
- Final app suite1434/1434,0failure/error/skipped,306XML. Wearable26/26 unchanged/cached. Node product69/69 after correcting stale version baseline in RELEASE_STATUS.md; initial product run68passed/1failed kept as evidence. Node release/debug/AAB15/15.
- Three social UI tests compiled for explicit gestures/confirmation, changing contact, restoration. NON EXECUTED: no Android runtime. Original7 profile tests remain compiled; no physical PASS.
- Actual wiring: D1ContactProfileContent invokes ContactSocialProfilesCard; pause hides content under existing access guard, saveable holder retains draft for same lookup key; pending confirmation is not restored. Browser launch/canonical profile links are explicit gestures only.
- End-of-page freeze remains OPEN; v14 retains v13 overscroll mitigation and adds this form, so use v13 for a one-variable comparison if needed.
