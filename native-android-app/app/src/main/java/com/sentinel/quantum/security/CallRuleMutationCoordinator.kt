package com.sentinel.quantum.security

/**
 * Process-wide coordinator for CallBlocklistStore persistence transactions.
 *
 * This is deliberately a JVM monitor, not an inter-process lock. It serializes all store
 * instances in one application process; another process or an external writer is outside this
 * guarantee and must not share these preferences without an equivalent storage protocol.
 */
object CallRuleMutationCoordinator {
    private val PROCESS_LOCK = Any()

    fun <T> withLock(block: () -> T): T = synchronized(PROCESS_LOCK, block)
}
