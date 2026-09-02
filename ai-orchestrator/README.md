# Sentinel AIP — Security AI Orchestrator

This module defines the governance boundary between Sentinel security evidence and AI-assisted reasoning.

## Architecture

```text
Security Data Fabric
        ↓
Security Digital Twin
        ↓
Evidence Fusion
        ↓
Sentinel AIP
  ├─ Model Router
  ├─ Agent Runtime
  └─ Retrieval / RAG
        ↓
Structured Decision Proposal
        ↓
Policy Guardian
        ↓
Simulation / Defensive Action
        ↓
Immutable Audit
```

## Non-negotiable controls

- LLM output is untrusted input and must be schema-validated.
- Models cannot directly execute privileged shell, network, identity, or destructive operations.
- Tools are explicitly allowlisted and policy-gated.
- Critical actions require an authorization decision outside the model.
- Every AI-assisted decision records model/provider/version, prompt or policy provenance, evidence references, confidence, and policy outcome.
- Evidence is separated into observation, inference, and hypothesis.
- Personal/private data is not ingested by default; sources must be public or explicitly authorized.
- Sentinel remains completely independent from A KI PRI SA YÉ: no shared identity, data, secrets, deployment, Firebase dependency, or pipeline.

## Initial implementation scope

The first implementation is deterministic: model registry validation, evidence fusion, decision schema, and policy gating. LLM providers can be added behind the model router without changing the security boundary.
