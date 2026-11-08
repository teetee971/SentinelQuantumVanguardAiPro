# JPEG entropy validation — v23

## Choice

The platform decoder is insufficient: the active regression reproduced its silent recovery of a half-truncated Android-generated JPEG with an artificial EOI. A SOI/EOI parser cannot validate entropy. Reimplementing baseline/progressive Huffman, arithmetic, restart and scan state in Kotlin would create a second decoder to maintain and security-audit. No such partial implementation is introduced.

The selected validator uses the proven libjpeg-turbo decoder, pinned to3.2.1 with source, licenses and provenance vendored for offline builds. Its warning callback is treated as failure, including premature entropy termination. Full entropy is consumed with scaled1/8 pixel output and finish_decompress; inspecting headers alone is never considered validation. Progressive and restart fixtures are tested. Resource-limit refusals are distinct from successful validation; not every mathematically valid JPEG is promised acceptance under bounded resources.

## Contracts

- Independent decoder state per call, no telephony, provider, filesystem or network effects.
- Input<=5MiB, source<=20MP,8-bit samples, progressive coefficient estimate based on actual component blocks and sampling;60MiB estimate threshold and64MiB no-backing-store decoder memory setting. Maximum128scans. Limits are per call, not a process-wide quota.
- JNI input copy failures translate to MEMORY_LIMIT. Heap-owned state is safe across longjmp. Decoder destruction and JNI_ABORT release happen on success and failure.
- Native progress callbacks check cancellation without clearing the Java interrupt flag.
- Validation runs before pass-through and before Android sampled decoding; generated JPEG output is validated again. Missing native library fails closed.
- API24–27 use BitmapFactory plus explicit EXIF matrix transforms; API28+ use ImageDecoder's orientation handling. Sampled bitmap<=4MP. Original conforming JPEG bytes remain unchanged.
- Original sender ordering, selected-subscription carrier budget, exact final PDU check and exact PDU/provider attachment identity remain intact. No recovery, role or callback mechanism is rewritten.
- Consumer shrinking rules retain JNI symbols and cancellation callback. License texts and IJG attribution are included as library assets.

## Tooling

Android NDK r28c (`28.2.13676358`) and official SDK CMake3.22.1. The host JNI tests also require CMake and a C compiler. `:mms-jpeg-validator:buildHostValidator` builds the same source for Robolectric; the dedicated runner shares the JNI bridge class loader. Validation is never mocked in the JPEG cases. Other sender tests continue to use controlled side-effect doubles.

Bootstrap tools once, then build with `--offline --no-daemon --no-build-cache --max-workers=1`. Set local.properties SDK/NDK/CMake paths or install matching SDK packages. No credentials or absolute cloud paths are committed. `sentinelHostCmake` can select a local executable. Offline Robolectric SDK24/27/28/35 jars must be supplied using `sentinelRobolectricSdkDir`.

Official NDK/CMake host binaries target Linuxx86_64. Rebuilding this native module inside UbuntuARM64/Termux has **not** been qualified; do not claim the old Kotlin-only phone build pipeline remains sufficient. The S24+ install remains forbidden pending explicit authorization. A cloud APK build is not proof of physical JPEG behavior or MMS delivery.
