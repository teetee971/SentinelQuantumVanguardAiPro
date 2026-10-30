# Complete local contact profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Execute inline, preserve the existing correction branch.

**Goal:** Complete the read-only contact sheet from Android data and make its D.1 layout usable on the S24+.
**Architecture:** Load only the selected contact's Data rows off the UI thread. Normalize and deduplicate in a pure policy; bind asynchronous results to contact/revision/resume generation and recheck permission after I/O. Display real local data in an expanded, scrollable sheet with a fixed close action and integrated photo hero.
**Tech Stack:** Kotlin, ContactsContract, Compose Material3, JUnit, coroutine tests.
**Spec:** User's “tout” following the reported incomplete sheet; existing D.1 R03 reference in docs/d1-requalification-20261029/VISUAL_REFERENCES.md.

## Global Constraints
- No provider writes, Room schema changes, private screenshots, keys, uninstall, automatic installation, merge or paid Actions.
- Existing phone/message action callbacks retain their behavior.
- Original signing key remains exclusively on owner's phone; next candidate versionCode 12.
- Never invent identity verification, linked social accounts or communications history.

## Review Focus
- Revoked READ_CONTACTS while I/O completes: return no details.
- Rapid contact switch: old result never renders in another profile.
- Duplicate numbers from merged Android sources: one visible row, source unmodified.
- Long notes/large fonts: scroll to all sections and close without hidden controls.
- Null provider/missing fields: explicit unavailable/empty state, no fake entries.

### Task 1: Details read and asynchronous ownership
- [x] Write JUnit tests for normalization/dedup, permission before/after I/O, unavailable provider, cancellation and request identity.
- [x] Run new tests RED before implementation.
- [x] Create ContactProfileDetails.kt pure policy/load boundary and LocalContactProfileReader.kt ContactsContract adapter (selected ID, bounded rows, read-only).
- [x] Run new tests GREEN.

### Task 2: Real sheet integration and D.1 layout
- [x] Integrate reader/state into D1ContactProfile.kt; use deduplicated numbers, real email/address/company/events/notes/websites/nicknames/relations/IM/SIP fields.
- [x] Open expanded, fixed accessible close action, scrollable hero/photo/name and all sections. Explicit local cyan surface palette for sheet/actions.
- [x] Add instrumented tests of actual sheet numbers, imported detail sections and scroll/close; compile them even if no emulator available.
- [x] Increment versionCode to12, run Kotlin compilation, complete Android unit suite, debug/unsigned/test APK builds and relevant Node contracts.

### Task 3: Review and delivery
- [x] Fresh reviewer checks privacy, permission/lifecycle, layout and source wiring; address important defects with regression tests.
- [x] Commit with skip-ci, build unsigned APK from committed source, hash/align/package controls, document counts and limitations.
- [ ] Push only the correction branch, keep PR1748 draft. Signed APK compatibility remains owner-side verification; physical and visual comparison pending.

## Execution ledger

- Base: 1be459199c5171ddf716b0002a91edac620b0ba5, matching remote PR head. Only pre-existing untracked Room schemas; preserved.
- User scope already authorized by “tout”; existing correction branch retained, inline execution.
- Initial RED: missing profile API; first implementation compile failed on unsupported Website/Nickname.getTypeLabel, corrected with explicit Android type mappings. A preliminary UI compile found a nonexistent palette symbol, corrected to onSurface. Preliminary full runs overlapped while sources changed and failed; they are not qualifying delivery evidence. Final qualifying run used stabilized sources and completed successfully.
- Unit GREEN: 1422 passed, 0 failed/error/skipped, 305 XML suites. Node contracts 15 passed, 0 failed/skipped.
- Fresh reviewer: two important findings (structured/organization mapping gaps, stale seed numbers); mapping extended, fresh PHONE rows authoritative. Parent actually also refreshes on resume at Activity1004; stale directory fallback nevertheless removed defensively.
- Additional real RED: cancellation followed by SecurityException allowed old result assignment; 10 tests,1 failed. ensureActive in denied catch fixes it; final 10/10 profile tests and whole app suite green.
- Mapping/phone freshness/large-text sheet tests compiled, NOT EXECUTED without Android runtime. No claim of physical or visual pass.
- Ruling: common Android data MIME fields supported; proprietary provider columns, account groups, verified social integration and communication history remain outside this contact-sheet change. Cost: not a universal complete export of every third-party address-book field; disclosed in design/report.
- Ruling: keep phone fields inactive while details reload/fail, with no stale directory fallback. Cost: brief loading delay rather than actions on removed numbers.

- Delivery source commit:06f913c057b9b1814f4dea7c436047df3dd2dfd4. Post-commit compile/test/build exit0 (application incremental), wearable suite26/26 newly executed. APK unsigned e0d779cf5601fae9ceeecb609d5d38bc497d09a671f47d2c6a73d3f33efa021b, alignment4/16KiB exit0, package/code12 correct. Owner signing/physical validation pending.
