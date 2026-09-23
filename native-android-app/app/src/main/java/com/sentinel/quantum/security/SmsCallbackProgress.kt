package com.sentinel.quantum.security

/**
 * Pure multipart callback reducer. It carries no phone number or message content.
 */
object SmsCallbackProgress {
    data class State(
        val partCount: Int,
        val sentOk: Set<Int> = emptySet(),
        val deliveredOk: Set<Int> = emptySet(),
        val failed: Boolean = false
    )

    data class Outcome(
        val state: State,
        val failed: Boolean,
        val allSent: Boolean,
        val allDelivered: Boolean
    )

    fun record(
        current: State?,
        partIndex: Int,
        partCount: Int,
        stage: SmsDeliveryStatusBus.Stage,
        successful: Boolean
    ): Outcome? {
        if (partCount !in 1..MAX_PARTS || partIndex !in 0 until partCount) return null
        if (current != null && current.partCount != partCount) return null

        val base = current ?: State(partCount = partCount)
        val sent = base.sentOk.toMutableSet()
        val delivered = base.deliveredOk.toMutableSet()
        var failed = base.failed

        if (!successful) {
            failed = true
        } else {
            when (stage) {
                SmsDeliveryStatusBus.Stage.SENT -> sent += partIndex
                SmsDeliveryStatusBus.Stage.DELIVERED -> delivered += partIndex
            }
        }

        val next = State(
            partCount = partCount,
            sentOk = sent,
            deliveredOk = delivered,
            failed = failed
        )
        return Outcome(
            state = next,
            failed = failed,
            allSent = !failed && sent.size == partCount,
            allDelivered = !failed && delivered.size == partCount
        )
    }

    const val MAX_PARTS = 256
}
