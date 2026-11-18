package com.sentinel.quantum.ai

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide barrier for synchronous effects.
 *
 * Requesting revocation immediately invalidates old leases.
 * Completing revocation waits for already-running effects.
 */
internal class SentinelAiProcessGate {
    class Lease internal constructor(internal val stamp: Any)

    class Revocation internal constructor(
        internal val stamp: Any
    )

    private sealed interface State {
        class Active(val stamp: Any) : State
        class Revoking(val stamp: Any) : State
    }

    private val state = AtomicReference<State>(State.Active(Any()))
    private val lock = Any()
    private var insideEffect = false

    fun capture(): Lease {
        val current = state.get()
        check(current is State.Active) {
            "Process revocation pending"
        }
        return Lease(current.stamp)
    }

    fun isActive(lease: Lease): Boolean {
        val current = state.get()
        return current is State.Active &&
            current.stamp === lease.stamp
    }

    fun <T> execute(lease: Lease, effect: () -> T): T =
        synchronized(lock) {
            check(!insideEffect) { "Reentrant effect" }
            check(isActive(lease)) { "Process session revoked" }
            insideEffect = true
            try {
                effect()
            } finally {
                insideEffect = false
            }
        }

    fun requestRevocation(): Revocation {
        while (true) {
            when (val current = state.get()) {
                is State.Revoking -> return Revocation(current.stamp)
                is State.Active -> {
                    val next = State.Revoking(current.stamp)
                    if (state.compareAndSet(current, next)) {
                        return Revocation(current.stamp)
                    }
                }
            }
        }
    }

    fun awaitRevocation(revocation: Revocation) =
        synchronized(lock) {
            check(!insideEffect) {
                "Reentrant revocation cannot complete"
            }
            val current = state.get()
            check(
                current is State.Revoking &&
                    current.stamp === revocation.stamp
            ) {
                "Revocation ticket no longer active"
            }
            state.set(State.Active(Any()))
        }

    fun revokeAll() {
        synchronized(lock) {
            check(!insideEffect) {
                "Reentrant revocation cannot complete"
            }
            val revocation = requestRevocation()
            awaitRevocation(revocation)
        }
    }
}
