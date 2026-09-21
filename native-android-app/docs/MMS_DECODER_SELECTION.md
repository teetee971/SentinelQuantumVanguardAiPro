# MMS decoder selection record

Status: decoder integration remains locked.

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

Do not activate a third-party MMS dependency yet. Implement only through the existing `MmsPduDecoder` interface after the chosen parser has been pinned, license-reviewed, dependency-reviewed and covered by malformed-PDU fixtures.

`SmsRoleMigrationPolicy.Capability.MMS_ATTACHMENTS` remains locked until decoder, storage/rendering flow and physical-device evidence are complete.
