package com.sentinel.quantum.security

/**
 * Pure multipart callback reducer. It carries no phone number or message content.
 *
 * Radio submission failures and delivery-report failures are intentionally separate:
 * a message can be sent successfully even when the carrier later reports delivery failure.
 * Multipart send failures remain non-terminal until every SENT callback is accounted for so
 * later callbacks cannot be discarded merely because one part failed first.
 */
object SmsCallbackProgress {
    data class State(
        val partCount: Int,
        val sentOk: Set<Int> = emptySet(),
        val sentFailed: Set<Int> = emptySet(),
        val deliveredOk: Set<Int> = emptySet(),
        val deliveryFailed: Set<Int> = emptySet()
    )

    data class Outcome(
        val state: State,
        val sendFailed: Boolean,
        val deliveryFailed: Boolean,
        val allSent: Boolean,
        val allDelivered: Boolean,
        val terminal: Boolean
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
        val sentOk = base.sentOk.toMutableSet()
        val sentFailed = base.sentFailed.toMutableSet()
        val deliveredOk = base.deliveredOk.toMutableSet()
        val deliveryFailed = base.deliveryFailed.toMutableSet()

        when (stage) {
            SmsDeliveryStatusBus.Stage.SENT -> {
                if (successful) {
                    sentOk += partIndex
                    sentFailed -= partIndex
                } else {
                    sentFailed += partIndex
                    sentOk -= partIndex
                }
            }
            SmsDeliveryStatusBus.Stage.DELIVERED -> {
                if (successful) {
                    deliveredOk += partIndex
                    deliveryFailed -= partIndex
                } else {
                    deliveryFailed += partIndex
                    deliveredOk -= partIndex
                }
            }
        }

        val next = State(
            partCount = partCount,
            sentOk = sentOk,
            sentFailed = sentFailed,
            deliveredOk = deliveredOk,
            deliveryFailed = deliveryFailed
        )
        val sentComplete = sentOk.size + sentFailed.size == partCount
        val sendFailed = sentFailed.isNotEmpty()
        val allSent = sentComplete && !sendFailed
        val deliveryComplete = deliveredOk.size + deliveryFailed.size == partCount
        val allDelivered = allSent && deliveryComplete && deliveryFailed.isEmpty()
        val terminal = (sentComplete && sendFailed) || (allSent && deliveryComplete)

        return Outcome(
            state = next,
            sendFailed = sendFailed,
            deliveryFailed = deliveryFailed.isNotEmpty(),
            allSent = allSent,
            allDelivered = allDelivered,
            terminal = terminal
        )
    }

    const val MAX_PARTS = 256
}
