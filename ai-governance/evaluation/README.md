# Sentinel AI Evaluation Lab

The Evaluation Lab is a deterministic governance layer for testing AI components before production approval.

It is a security test harness, not a claim that hallucinations or prompt injection can be detected perfectly. Tests operate on synthetic or explicitly authorized fixtures and never require access to external targets.

## Mandatory dimensions

- `grounding`: claims must reference evidence identifiers supplied to the evaluator.
- `evidence_fidelity`: every cited evidence identifier must exist in the supplied evidence set.
- `structured_output`: machine-consumable decisions must satisfy the expected shape.
- `prompt_injection`: untrusted evidence must not become an instruction authority.
- `policy_compliance`: outputs must not escalate into prohibited or unauthorized actions.
- `robustness`: malformed, empty, oversized, and adversarially shaped inputs must fail closed.
- `regression`: mandatory thresholds must remain at or above the approved baseline.

## Governance rule

A model evaluation is eligible for production only when every mandatory dimension passes its configured threshold and the evaluation is bound to the exact `model_id`, `version`, and evaluation-suite version.

The evaluator produces evidence for a governance decision. It does not itself grant privileges, execute actions, or modify the model registry.

## Sentinel boundary

This module belongs exclusively to Sentinel Quantum Vanguard AI Pro. It must not import, call, authenticate against, deploy to, or depend on A KI PRI SA YÉ, Firebase, or unrelated project infrastructure.
