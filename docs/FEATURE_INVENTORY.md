# Sentinel — Feature Evidence Inventory

This inventory is a control against feature drift between code, UI and documentation.

| Capability | Status | Evidence required |
|---|---|---|
| PWA/web interface | IMPLEMENTED | Source + successful build |
| Local Android security checks | IMPLEMENTED | Android source + successful build/test |
| Public OSINT feed retrieval | IMPLEMENTED | Source + parser tests + observed network execution |
| AI governance controls | IMPLEMENTED | Automated test suite |
| Decision-plane validation | IMPLEMENTED | Unit/fuzz tests |
| Sentinel/A KI PRI SA YÉ isolation | IMPLEMENTED | Isolation scanner + negative fixtures |
| Public threat intelligence | READ-ONLY | Source-backed feeds; no claim of live SOC operation |
| Precompiled APK distribution | NOT PUBLISHED | Requires signed artifact and release evidence |
| Autonomous incident response | NOT DEMONSTRATED | Do not advertise as operational |
| Continuous human-equivalent monitoring | NOT DEMONSTRATED | Do not advertise as operational |
| Legal/regulatory certification | NOT CLAIMED | Requires external legal/compliance evidence |

Status definitions:

- `IMPLEMENTED`: source exists; validation still requires execution evidence where applicable.
- `READ-ONLY`: intentionally limited to observation/display.
- `NOT PUBLISHED`: intentionally unavailable until release evidence exists.
- `NOT DEMONSTRATED`: concept or historical material must not be presented as a current capability.

Any new public feature should be added here with its implementation and validation evidence before being advertised.
