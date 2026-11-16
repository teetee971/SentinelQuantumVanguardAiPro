# Adversary Emulation Simulation Boundary

`core/adversary-emulation/adversaryEngine.ts` is a logical simulation component. It models adversary behaviors and emits synthetic events for authorized audit, training, detection engineering and evaluation.

It does not perform real exploitation, intrusion, compromise, privileged execution or access to external targets.

References to MITRE ATT&CK, ANSSI or NIST identify frameworks and guidance used for terminology or design context. They do not constitute certification, accreditation, legal approval or institutional compliance.

The existing public TypeScript interfaces and method names are preserved for compatibility. In particular, `validateAdversaryCompliance` is a legacy internal method name; its checks enforce this module's simulation boundary and must not be interpreted as a legal or certification determination.

Randomized simulation behavior and hard-coded metric assumptions are separate technical issues and are intentionally not changed in this documentation-only pass.
