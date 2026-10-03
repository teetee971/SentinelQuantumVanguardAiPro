package com.sentinel.quantum.security

import kotlin.math.abs

/**
 * Presentation-only de-duplication for Android call-log rows.
 *
 * Some OEM/Telecom stacks can expose two nearly identical rows for one call transition. Sentinel
 * collapses only rows that match on number, type and duration and are almost simultaneous. This
 * never edits the Android call log and never changes filtering evidence.
 */
object CallLogDeduplicationPolicy {
    const val MAX_TIME_DELTA_MS: Long = 2_500L

    fun sameVisibleCall(
        firstNumber: String?,
        firstType: Int,
        firstDateMs: Long,
        firstDurationSeconds: Long,
        secondNumber: String?,
        secondType: Int,
        secondDateMs: Long,
        secondDurationSeconds: Long
    ): Boolean {
        if (firstType != secondType) return false
        if (firstDurationSeconds != secondDurationSeconds) return false
        if (canonical(firstNumber) != canonical(secondNumber)) return false
        return abs(firstDateMs - secondDateMs) <= MAX_TIME_DELTA_MS
    }

    private fun canonical(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }
}
