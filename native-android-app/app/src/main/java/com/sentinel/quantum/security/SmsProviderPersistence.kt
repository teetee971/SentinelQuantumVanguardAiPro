package com.sentinel.quantum.security

/** Provider writes are independent of the carrier outcome and must not be silently treated as success. */
internal object SmsProviderPersistence {
    fun persist(
        progress: SmsCallbackProgress.Outcome,
        markFailed: () -> Boolean,
        markSent: () -> Boolean,
        markDelivery: (Boolean) -> Boolean
    ): Boolean {
        fun write(action: () -> Boolean): Boolean = try {
            action()
        } catch (_: RuntimeException) {
            false
        }
        if (progress.sendFailed) return write(markFailed)
        var written = true
        if (progress.allSent) written = write(markSent)
        if (progress.deliveryFailed || progress.allDelivered) {
            val deliveryWritten = write { markDelivery(progress.allDelivered && !progress.deliveryFailed) }
            written = written && deliveryWritten
        }
        return written
    }
}
