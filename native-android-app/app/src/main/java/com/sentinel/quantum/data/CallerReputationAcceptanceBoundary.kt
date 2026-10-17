package com.sentinel.quantum.data

/** Serializes local reputation acceptance with changes to the consent that authorizes it. */
internal class CallerReputationAcceptanceBoundary {
    private val lock = Any()

    fun <T : Any> accept(
        isEligible: () -> Boolean,
        result: T,
        persist: () -> Boolean,
        publish: () -> Unit
    ): T? = synchronized(lock) {
        if (!isEligible() || !persist() || !isEligible()) {
            null
        } else {
            publish()
            result
        }
    }

    fun <T> change(action: () -> T): T = synchronized(lock) { action() }
}
