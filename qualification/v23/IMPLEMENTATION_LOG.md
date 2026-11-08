# MMS v23 execution ledger

- Archive verified: 762 regular files, 3430178 uncompressed bytes; internal digests all match. Base and HEAD match fafe9f5b. Six changes imported, no other base files changed.
- Baseline full unit command: --offline --no-daemon --max-workers=1; BUILD SUCCESSFUL. JUnit counts will be archived separately.
- Ruling: continue inline under the explicit user mandate; no intermediate approval gate or production install. Detailed user specification is the authority.
- Interfaces: compressor returns bounded JPEG bytes; preparation returns exact PDU and parts; sender must use both together. Pure limits may be shared with the lab to avoid importing application state/roles.

- Codec RED: malformed 3-byte fitting JPEG accepted; expanded native suite 10 tests/5 failures. First corrected codec exposed fixture budget/resolution assumptions; bounded fallback extended down to240px. EXIF fixture now has valid4KiB APP1 padding so rotation is tested independently of an impossible unchanged-resolution budget. Actual 10 native host cases passed in integration-first-green-junit.
- Preparation RED:14 tests/8 failures (allocation, mixed PNG, mutation, masked errors, codec exceptions/budget contract);14 passed after exact counting encoder and water filling. Shared pure limits extracted solely to compile the actual composer in the isolated lab.
- Sender RED:15 tests/8 failures;15 passed with dependency-boundary fakes after integrating preparation and exact provider parts. Expanded21 cases found4 further failures (provider/default-SIM exceptions, OOM and role loss after persistence); fixes implemented, next run pending source-set correction.
- First integration green:39 actual host tests, zero skipped/failures. Later shared-case source-set build failed (unresolved test base classes); not a test PASS. Diagnosing Gradle Java vs Kotlin test directories.
- Ruling: native Robolectric4.16.1 at API35 supplies executable host Bitmap tests. This is not Samsung/API37 physical evidence. Added AndroidX EXIF1.4.1 for bounded, maintained orientation parsing rather than hand-writing TIFF parsing.
- Ruling: lab applicationId com.sentinel.quantum.mmslab.v23 isolates it from BOTH installed v22 and the existing historical lab with another signer. No installs performed; ADB remains unavailable.

- Shared source discovery fixed through Android Kotlin variant directories. First complete host run app1478/0fail/1skip fixture, lab28/0fail/0skip. Later entropy regression FAILS despite ImageDecoder partial refusal. Active blocker retained.
- Review found role-revocation READY journal defect. Real preferences/provider-denial red tests5/3fail; durable result precedes role-dependent provider access. Journal/sender/preparation/PDU neighbors54/0fail/0skip.
- Draft snapshot red4/3fail: captures SIM and preserves edits made during asynchronous preparation.

- First exact-SHA reproduction8dd0ab28: app1495/1fail/0skip with signed APK fixture; lab32/1fail/0skip. Sole test failure is retained entropy blocker. Lint found MissingPermission in Android SIM adapter; explicitly catches platform SecurityException and refuses with empty active SIMs. No suppression/baseline added.
- AndroidTest first build missing two cached lab transitive dependencies (lifecycle2.3.1/coroutines1.8.1). Bootstrap from configured Maven repositories, then repeat qualification offline. No phone connection/installation.

- Second reproductionbb2acff5: all4 APKs built, signatures/badging/16KiB alignment passed; app1495/1fail/0skip, lab32/1fail/0skip, wearable28/0fail/0skip. App lint0errors/223warnings/19hints. Lab lint incorrectly inspected unrelated telephony sources because lint ignores Java include filters on a shared source root. Replaced parent roots with generated Sync subsets of exactly4 production files and selected test wrappers; no permission added or lint rule suppressed.
