# MMS v23 design

Specification: user mission SENTINEL V23 and archive SHA256 138aa86bfcce3d1d0221178b0b347a697f0991a1642dab4b8a7d4a68f9998e8d, based on fafe9f5b1c57b21f276ca62189acab625a490ec9. Worktree /workspace/sentinel-mms-v23; branch fix/mms-image-compression-v23. The other worktree and installed v22 remain untouched.

JPEG processing: validate inputs and dimensions before allocation, preserve conforming originals, sample once and bound bitmap allocations, handle all eight EXIF orientations, bound output buffers and encode attempts, recycle owned bitmaps. Non-JPEG data remains untouched. Conservative source-pixel limits are explicit refusals, not an invitation to allocate a full bitmap.

Preparation: keep the real composer authoritative; measure its exact overhead without allocating a rejected PDU, reserve unchanged non-JPEG bytes, distribute remaining budget across JPEGs with water filling so small originals retain bytes. Compress each JPEG at most once per preparation, then compose and enforce the actual selected-SIM limit again. No provider or telephony calls here. Preserve original arrays, destination, text, order and transaction ID.

Sender: preconditions precede preparation, which precedes staging and persistence. Persist the exact prepared attachments and submit its exact PDU. Keep recovery journal and callback behavior; test effects with controlled doubles and never actual MMS in automation. UI remains asynchronous and reports factual rejection/submission, not delivery.

Alternatives rejected: repeated fractional whole-PDU budgets waste capacity and repeatedly decode; transcoding all formats is outside scope. No image-editing dashboard or unrelated refactor. Use existing Kotlin/Android graphics; independent lab targets com.sentinel.quantum.mmslab. Host runtime tests do not establish physical S24 delivery. No production-package install without explicit authorization.
