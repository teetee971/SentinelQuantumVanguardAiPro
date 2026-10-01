package com.sentinel.quantum.security

/** Provider writes are independent of the carrier outcome and must not be silently treated as success. */
internal object SmsProviderPersistence {
    fun persist(
        progress: SmsCallbackProgress.Outcome,
        markFailed: () -> Boolean,
        markSent: () -> Boolean,
        markDelivery: (Boolean) -> Boolean
    ): Boolean {
        return try {
            if (progress.sendFailed) {
                markFailed()
            } else {
                var written = true
                if (progress.allSent) written = markSent()
                if (progress.deliveryFailed || progress.allDelivered) {
                    val deliveryWritten = markDelivery(progress.allDelivered && !progress.deliveryFailed)
                    written = written && deliveryWritten
                }
                written
            }
        } catch (_: RuntimeException) {
            false
        }
    }
}

