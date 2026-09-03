# Sentinel — Feature Evidence Inventory

This inventory is the anti-drift control between source code, tests, CI evidence, deployment evidence and public claims.

A capability is not considered operational merely because a source file exists. Evidence is recorded separately so that a CI outage cannot be mistaken for a code failure, and a code path cannot be mistaken for a validated production capability.

| Capability | Source state | Validation state | Public claim allowed |
|---|---|---|---|
| PWA/web interface | IMPLEMENTED | Build/deployment evidence exists for recent changes; full CI regression currently blocked by runner failures | YES, with current-scope wording |
| Cloudflare Pages deployment path | IMPLEMENTED | Successful deployment check observed on an earlier security change; each release still requires fresh evidence | YES, without implying security certification |
| Local Android security checks | IMPLEMENTED | Automated manifest gate exists; Android CI has repeatedly failed before steps, so current build is NOT VALIDATED | YES, as source capability only |
| Public OSINT feed retrieval | IMPLEMENTED | Parser/source controls exist; live network execution is not treated as continuously verified | YES, read-only/public-feed wording |
| AI governance controls | IMPLEMENTED | Test suites exist; current GitHub runner prevents fresh execution evidence | YES, as governance/control code |
| Decision-plane validation | IMPLEMENTED | Unit and fuzz suites exist; fresh CI execution currently blocked | YES, as validation logic, not autonomous enforcement |
| Sentinel / A KI PRI SA YÉ isolation | IMPLEMENTED | Dedicated scanner and negative fixtures exist; fixtures are intentionally allowed to contain forbidden references | YES, strict separation requirement |
| Red Team simulation | IMPLEMENTED | Synthetic scenarios and simulation engine exist; no real attack execution is claimed | YES, simulation/training wording |
| Public threat intelligence | READ-ONLY | Public-feed implementation exists; not a live SOC | YES, read-only/public-source wording |
| Bounded local logging | IMPLEMENTED | Source-level limits and failure isolation exist | YES, as local bounded logging |
| Deterministic explainability | IMPLEMENTED | Local deterministic implementation exists | YES, as explainability support; not model certainty |
| Precompiled APK distribution | NOT PUBLISHED | No signed release artifact currently demonstrated | NO |
| Autonomous incident response | NOT DEMONSTRATED | No evidence of an operational autonomous response plane | NO |
| Continuous human-equivalent monitoring | NOT DEMONSTRATED | No evidence of such a service | NO |
| Active protection of external infrastructure | NOT DEMONSTRATED | No operational enforcement channel | NO |
| Security certification | NOT CLAIMED | Requires independent certification/audit evidence | NO |

## Evidence vocabulary

- `IMPLEMENTED`: relevant source code exists.
- `READ-ONLY`: deliberately limited to observation or display.
- `TESTED`: automated tests have actually executed successfully for the relevant revision.
- `DEPLOYED`: the relevant deployment has actually been observed successfully.
- `NOT VALIDATED`: source exists, but current execution evidence is missing or blocked.
- `NOT PUBLISHED`: deliberately unavailable until release evidence exists.
- `NOT DEMONSTRATED`: a concept or historical material must not be presented as a current operational capability.
- `NOT CLAIMED`: no certification or legal conclusion is asserted.

## Release rule

The minimum evidence chain for a security-sensitive release is:

`source → tests → fuzzing/static analysis → dependency checks → build → signing → checksum/provenance → artifact → observed release`

If one link is missing, the documentation must state that gap instead of filling it with an assumption.

## Separation rule

Sentinel Quantum Vanguard AI Pro remains strictly independent from **A KI PRI SA YÉ**. Negative isolation fixtures may contain forbidden strings by design; those fixtures are test controls and must not be removed merely because they reference the other project.

Any new public capability must be added here with its source state, validation state and permitted claim before it is advertised.
