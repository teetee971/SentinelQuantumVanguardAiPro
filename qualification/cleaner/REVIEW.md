# Review and correction

Independent read-only reviewer found two Important issues: screen-scoped jobs could discard reports after deletion on rotation/navigation, and canonicalization/metadata errors were classified as ordinary protected paths with complete coverage. Both were reproduced by real-file/control-error regressions16 tests/4 failures. Activity-owned ViewModel and a session publishing each result before the next deletion fix the first; per-file failures are retained and cancellation preserves completed results. Explicit INSPECTION_FAILED and partial propagation fix the second.

Focused recheck confirms both addressed and reports no remaining concrete Important issue in those fixes. Reviewer executed no tests or device actions. Host tests actually executed by implementer are stored in execution proofs. Reports contain logical sizes, not claimed physical disk gains. No claim of malware removal, whole-device cleaning or persistent report history after process death.
