package com.sentinel.quantum.security

/**
 * Evidence-first diagnostic contract for the future Sentinel System Doctor.
 *
 * A missing or inaccessible observation is never promoted to OK. Collection and
 * remediation are separate layers so the UI cannot claim that an issue was fixed
 * merely because an action was offered.
 */
object SentinelDeviceDiagnostic {
    enum class Status {
        OK,
        WARNING,
        CRITICAL,
        UNKNOWN,
        NOT_ACCESSIBLE
    }

    data class Evidence(
        val id: String,
        val status: Status,
        val summary: String,
        val observedValue: String? = null,
        val observedAtEpochMillis: Long
    ) {
        init {
            require(id.isNotBlank()) { "Diagnostic evidence id must not be blank" }
            require(summary.isNotBlank()) { "Diagnostic evidence summary must not be blank" }
            require(observedAtEpochMillis >= 0L) { "Diagnostic evidence timestamp must be non-negative" }
        }
    }

    data class Report(
        val evidence: List<Evidence>
    ) {
        val criticalCount: Int get() = evidence.count { it.status == Status.CRITICAL }
        val warningCount: Int get() = evidence.count { it.status == Status.WARNING }
        val unresolvedCount: Int get() = evidence.count {
            it.status == Status.UNKNOWN || it.status == Status.NOT_ACCESSIBLE
        }

        val isFullyObservedAndHealthy: Boolean
            get() = evidence.isNotEmpty() && evidence.all { it.status == Status.OK }

        /** True only when every requested observation produced a concrete OK/WARNING/CRITICAL result. */
        val isObservationComplete: Boolean
            get() = evidence.isNotEmpty() && evidence.none {
                it.status == Status.UNKNOWN || it.status == Status.NOT_ACCESSIBLE
            }

        /** Highest observed risk, independent from whether other evidence is missing or inaccessible. */
        val highestObservedRisk: Status
            get() = when {
                evidence.any { it.status == Status.CRITICAL } -> Status.CRITICAL
                evidence.any { it.status == Status.WARNING } -> Status.WARNING
                evidence.any { it.status == Status.OK } -> Status.OK
                else -> Status.UNKNOWN
            }

        val overallStatus: Status
            get() = when {
                evidence.any { it.status == Status.CRITICAL } -> Status.CRITICAL
                evidence.any { it.status == Status.WARNING } -> Status.WARNING
                evidence.any { it.status == Status.UNKNOWN } -> Status.UNKNOWN
                evidence.any { it.status == Status.NOT_ACCESSIBLE } -> Status.NOT_ACCESSIBLE
                evidence.isEmpty() -> Status.UNKNOWN
                else -> Status.OK
            }
    }
}
