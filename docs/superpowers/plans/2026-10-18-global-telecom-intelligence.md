# Global Telecom Intelligence + Phone Core Safety

## Scope

Extend the existing D.1 Android branch with bounded, provenance-aware
international telecom intelligence while preserving the local, fail-open Phone
Core decision path. The official D.1 references remain design inputs only; no
illustrative value from a reference image may become runtime evidence.

## Baseline discovered

- Working base: `origin/feat/global-telecom-regulator-registry` at
  `74019b2ccbd1da86fddb90216c5061c9b9a149c3`.
- This base contains the open Phone Core guard work from PR #1705 and the
  reference-only registry work from PR #1706. Neither PR is merged.
- D.1 Phase 4 is inherited from PR #1703 at `0944a1f46f05217e1a82e8f890e707377e277738`.
- The accessible official archive is
  `1-Sentinel_D1_13_Visuels_Officiels.zip`, SHA-256
  `b1c84e0aa8ea6186bd82d282dea59e2119b91026c2ae46de0b93751aee8c905a`.
  It contains exactly 13 reference images; the repository already preserves
  their original bytes and manifest.
- There is no Android emulator, `adb`, or physical Galaxy S24+ in this
  environment. Device and visual capture gates therefore remain open.

## Implementation order

1. Add this ledger and keep all work on the dedicated branch
   `feat/global-telecom-intelligence-d1-phone-core`.
2. TDD the telecom domain contracts: source metadata, provenance, bounded
   cache, validation, and a deterministic official-registry adapter. A source
   is not marked synchronized unless a real adapter run returns validated data.
3. TDD the international number resolver: E.164 normalization, `00` input,
   explicit default-region handling, shared calling-code ambiguity, overseas
   territories, withheld/incomplete values, and fail-open semantics.
4. TDD the local Phone Core integration boundary. Enrichment can annotate a
   number, but it cannot grant block/silence authority and cannot perform I/O
   in `CallScreeningService`.
5. Add typed Intelligence Core evidence/provenance projection and a native D.1
   global telecom screen using the existing holographic scene/design system.
6. Harmonize the priority D.1 surfaces only where a concrete regression is
   found; preserve existing runtime truth and accessibility behavior.
7. Run focused tests first, then the Android unit/instrumentation compile,
   lint, isolation checks, and debug build. Produce an APK only from the final
   verified commit. Do not claim device or visual validation without captures.

## Explicit non-goals

- No automatic call blocking from regulator data.
- No network access from the Telecom callback path.
- No assertion of worldwide live coverage.
- No fabricated regulator API, license, sync timestamp, risk score, or visual
  capture.
- No merge, force-push, or modification of PR #1699/#1701/#1702/#1703/#1705/#1706.

## Verification ledger

| Task | Status | Evidence / next action |
|---|---|---|
| Audit Git state, remote branches, PRs, issue #1704 | done | Commands run on 2026-10-18; findings above and in session log |
| Resolve official D.1 archive and hashes | done | Archive SHA/count/manifest match repository originals |
| Dedicated branch and ledger | done | This branch and document |
| Telecom domain contracts | done | Red tests captured first; focused connector suite passes |
| International number resolver | done | E.164/00, explicit national context and shared-prefix tests pass |
| Phone Core boundary | done | Fail-open policy and callback-boundary contracts pass |
| Intelligence Core/UI integration | done | Typed annotation-only evidence and native D.1 route compile |
| Build/lint/isolation/APK | in progress | APK assembled; final-commit rebuild and publication remain |
| Independent counter-review | in progress | Self-review because no subagent tool is exposed |

## Review ruling

The environment exposes no subagent execution tool. The final review will be a
self-review and will be labeled as such; it is not equivalent to an
independent human or agent approval.
