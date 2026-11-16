# Controlled Adversary Simulation Architecture

> Legacy filename retained for compatibility. This document describes a defensive, controlled simulation capability; it does not define Sentinel as an offensive-security product.

## Scope

Sentinel Quantum Vanguard AI Pro may model adversary techniques for SOC/CERT/RSSI training, detection engineering, audit support and evaluation. These simulations are intended to generate synthetic events, indicators and scenarios inside a controlled boundary.

This architecture does not authorize or implement unauthorized access to third-party systems. It does not establish that any use is legally compliant, certified, approved by a public authority, or operationally safe in a specific environment.

## Non-goals

The controlled simulation capability is not intended to:

- obtain unauthorized access;
- execute real exploits against third-party targets;
- deploy malware or persistence mechanisms;
- perform real credential theft, exfiltration, destructive impact or denial of service;
- alter external systems without explicit authorization;
- make attribution or certification claims from simulated output.

## Repository components

The repository currently contains simulation-related components including:

- `public/offensive-simulation-engine.js` — legacy compatibility path for client-side synthetic scenario/event generation;
- `core/redteam/redteamEngine.ts` — logical Red Team scenario simulation and generated SOC events;
- `core/adversary-emulation/adversaryEngine.ts` — logical adversary-emulation scenarios;
- `public/red-team-simulator.html` — defensive Red Team user surface.

The presence of a component in the repository is evidence of source code, not evidence that it is deployed, independently validated, or operationally approved.

## Simulation boundary

Simulation records and generated indicators should be explicitly marked as simulated. Validation helpers in these modules are local software guardrails: they can reject inputs that violate expected simulation structure or contain forbidden markers, but they are not legal-compliance engines and cannot determine whether a real-world activity is lawful or authorized.

Any real operational action must remain outside this simulation boundary and is subject to the repository's separate authorization, policy, human-validation and execution controls.

## MITRE ATT&CK usage

MITRE ATT&CK is used as a taxonomy/reference for adversary tactics and techniques. References to MITRE ATT&CK, ANSSI, NIST, OWASP or other frameworks do not imply certification, endorsement, qualification or conformity.

Counts of tactics or techniques change over time and may also differ from the subset implemented locally. This document therefore does not treat a fixed count as proof of coverage. Coverage claims require a versioned inventory and reproducible evidence tied to the exact implementation being evaluated.

## Synthetic events and indicators

A controlled scenario may produce synthetic artifacts such as:

- SOC-style events and timelines;
- fictitious IP addresses, domains, hashes or process indicators;
- simulated tactic/technique references;
- detection-oriented summaries and metrics.

These artifacts must not be presented as observations of a real incident unless separately supported by real evidence.

## Metrics

Metrics such as detection rate, mean time to detect, tactic coverage or scenario score are simulation/evaluation outputs. Their meaning depends on the input model and measurement method. They are not production-security guarantees.

A metric is suitable for a release or governance decision only when its calculation, inputs, version and provenance are explicit and reproducible.

## Security and evidence limits

Repository and CI evidence can demonstrate that specified checks executed successfully on a specific commit. It does not by itself prove:

- absence of unknown vulnerabilities;
- production deployment safety;
- legal compliance;
- certification by ANSSI, NIST, MITRE or another body;
- effectiveness against a real adversary;
- completeness of threat coverage.

## Compatibility

Legacy file names, class names and identifiers may retain words such as `offensive`, `redteam` or `adversary` where changing them would break compatibility or where they accurately identify a controlled simulation primitive. Product positioning and user-facing claims should remain defensive and evidence-based.

## Governance rule

When simulation terminology is used, documentation and UI should clearly distinguish:

1. the adversary behavior being modeled;
2. the synthetic simulation output;
3. the defensive purpose of the evaluation;
4. any separate authorization required for real operations.

This distinction is mandatory for avoiding confusion between adversary emulation and offensive operational capability.
