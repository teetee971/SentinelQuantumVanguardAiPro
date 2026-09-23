# MMS decoder selection record

Status: bounded internal safe-preview decoder integrated; physical carrier/device validation remains required.

## Requirements

Any concrete MMS/PDU decoder used by Sentinel must:

- accept only the already bounded raw PDU input from `MmsDecodePipeline`;
- return only `MmsDecodeBoundary.DecodedPart` values;
- never render, execute, persist externally, or upload decoded content;
- preserve the existing part-count, aggregate-size, MIME, signature and defensive-copy checks;
- fail closed on malformed input and parser exceptions;
- have a compatible license and an auditable maintenance/security posture;
- pass adversarial unit tests and physical-device MMS validation before `MMS_ATTACHMENTS` can be declared implemented.

## Candidates reviewed

### Android/AOSP PDU parser

AOSP contains `com.google.android.mms.pdu.PduParser` under Apache-2.0 source. It is a useful behavioral reference, but Sentinel must not depend on Android hidden/internal APIs or assume that framework-internal classes are a stable application API.

### Third-party forks/libraries

Existing MMS libraries and AOSP-derived forks can provide parsing code, but importing a full sending/transaction stack would unnecessarily enlarge Sentinel's attack surface. A decoder dependency must therefore be isolated and justified rather than added only to obtain `PduParser`.

## Decision

Sentinel now uses a small dependency-free decoder behind the existing `MmsPduDecoder` interface. It accepts only bounded WSP multipart messages needed by the safe-preview path, rejects ambiguous or malformed framing, and still passes every decoded part through `MmsDecodeBoundary` before preview eligibility is reported.

No hidden Android MMS parser API and no third-party MMS transaction stack is used. Unsupported content remains quarantined. `MMS_ATTACHMENTS` is considered implemented at the software-capability layer only; the separate physical-device/carrier validation gate remains mandatory before Phone Core can be described as fully functional.
