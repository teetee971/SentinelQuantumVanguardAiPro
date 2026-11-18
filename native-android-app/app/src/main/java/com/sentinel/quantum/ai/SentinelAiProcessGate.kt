package com.sentinel.quantum.ai

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Process-wide barrier for synchronous effects.
 *
 * Requesting revocation immediately invalidates old leases, even while an effect is running.
 * Completing revocation drains admitted effects and permits a new lease from a new consent.
 * Trusted Android composition owns this barrier. Approval and authentication are separate.
 */
internal class SentinelAiProcessGate {
    class Lease internal constructor(internal val stamp: Any)

    class Revocation internal constructor(
        internal val stamp: Any
    )

    private sealed interface State {
        class Active(val stamp: Any, val completed: Revocation? = null) : State
        class Revoking(val ticket: Revocation) : State
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    @Volatile private var state: State = State.Active(Any())
    private var effectOwner: Thread? = null

    fun capture(): Lease {
        val current = state
        check(current is State.Active) {
            "Process revocation pending"
        }
        return Lease(current.stamp)
    }

    fun isActive(lease: Lease): Boolean {
        val current = state
        return current is State.Active &&
            current.stamp === lease.stamp
    }

    fun isRevoking(): Boolean = state is State.Revoking

    fun <T> execute(lease: Lease, effect: () -> T): T {
        val caller = Thread.currentThread()
        lock.withLock {
            check(effectOwner !== caller) { "Reentrant effect" }
            while (effectOwner != null && isActive(lease)) changed.await()
            check(isActive(lease)) { "Process session revoked" }
            effectOwner = caller
        }
        // Admission and revocation are ordered by the lock. The callback may run without it.
        return try {
            effect()
        } finally {
            lock.withLock {
                effectOwner = null
                changed.signalAll()
            }
        }
    }

    fun requestRevocation(): Revocation = lock.withLock {
        check(effectOwner !== Thread.currentThread()) { "Reentrant revocation cannot complete" }
        when (val current = state) {
            is State.Revoking -> current.ticket
            is State.Active -> {
                val ticket = Revocation(current.stamp)
                state = State.Revoking(ticket)
                changed.signalAll()
                ticket
            }
        }
    }

    /** Concurrent requests share a ticket. Its acknowledgement is valid until the next request. */
    fun awaitRevocation(revocation: Revocation) {
        lock.withLock {
            check(effectOwner !== Thread.currentThread()) { "Reentrant revocation cannot complete" }
            while (true) {
                when (val current = state) {
                    is State.Active -> {
                        check(current.completed === revocation) { "Revocation ticket no longer active" }
                        return
                    }
                    is State.Revoking -> {
                        check(current.ticket === revocation) { "Revocation ticket no longer active" }
                        if (effectOwner == null) {
                            state = State.Active(Any(), revocation)
                            return
                        }
                    }
                }
                changed.await()
            }
        }
    }

    fun revokeAll() = awaitRevocation(requestRevocation())
}
