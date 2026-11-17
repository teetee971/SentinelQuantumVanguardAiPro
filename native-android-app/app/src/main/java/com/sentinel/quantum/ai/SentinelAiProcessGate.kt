package com.sentinel.quantum.ai

import java.util.concurrent.atomic.AtomicReference

/** Additional process-wide revocation barrier, NOT authentication or action authorization.
 * Trusted Android composition owns the single instance; engines never receive it or a lease.
 * revokeAll returns only after an ongoing synchronous effect finishes. It cannot undo that effect.
 * Reentrant revocation is refused rather than falsely acknowledging completion inside an effect.
 */
internal class SentinelAiProcessGate {
    class Lease internal constructor(internal val stamp: Any)
    private val stamp = AtomicReference(Any())
    private val lock = Any()
    private var insideEffect = false
    fun capture(): Lease = Lease(stamp.get())
    fun isActive(lease: Lease): Boolean = stamp.get() === lease.stamp
    fun <T> execute(lease: Lease, effect: () -> T): T = synchronized(lock) {
        check(!insideEffect) { "Reentrant effect" }
        check(isActive(lease)) { "Process session revoked" }
        insideEffect = true
        try { effect() } finally { insideEffect = false }
    }
    fun revokeAll() = synchronized(lock) {
        check(!insideEffect) { "Reentrant revocation cannot complete" }
        stamp.set(Any())
    }
}
