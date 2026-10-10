package com.sentinel.quantum.security

/**
 * Serializes SMS/MMS submissions in one composer instance.
 *
 * Android may accept a telephony request before its sent/delivery callback arrives. A second
 * tap during that window would otherwise create a second provider row and a second network
 * submission. The permit identity also prevents a stale coroutine from releasing a newer send.
 */
class SmsSubmissionGate {
    private var nextPermitId = 0L
    private var activePermitId: Long? = null

    @Synchronized
    fun tryAcquire(): Permit? {
        if (activePermitId != null) return null
        val permitId = ++nextPermitId
        activePermitId = permitId
        return Permit(this, permitId)
    }

    @Synchronized
    private fun release(permitId: Long) {
        if (activePermitId == permitId) activePermitId = null
    }

    class Permit internal constructor(
        private val owner: SmsSubmissionGate,
        private val permitId: Long
    ) {
        fun release() = owner.release(permitId)
    }
}
