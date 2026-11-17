package com.sentinel.quantum.ai

/** Process-local barrier for pairing storage, never an action approval capability.
 * A successful forget invalidates every previously captured lease. Failure keeps
 * new pairing operations blocked until a later explicit forget succeeds.
 * Revocation waits for an already-started storage operation; it cannot undo it.
 */
internal class SentinelAiPairingCoordinator {
    class Lease internal constructor(internal val owner: Any, internal val stamp: Any) {
        internal var revoked = false
    }
    class Forget internal constructor(internal val owner: Any, internal val stamp: Any)
    private val owner = Any()
    private var stamp = Any()
    private var forgetting = false
    private var insideEffect = false

    @Synchronized fun capture(): Lease {
        check(!forgetting) { "Pairing deletion pending or failed" }
        return Lease(owner, stamp)
    }
    @Synchronized fun revoke(lease: Lease) {
        check(!insideEffect && lease.owner === owner) { "Invalid pairing revocation" }
        lease.revoked = true
    }
    @Synchronized fun <T> use(lease: Lease, effect: () -> T): T {
        check(!insideEffect && !forgetting && lease.owner === owner && lease.stamp === stamp && !lease.revoked) {
            "Pairing request revoked"
        }
        insideEffect = true
        try { return effect() } finally { insideEffect = false }
    }
    @Synchronized fun beginForget(): Forget {
        check(!insideEffect) { "Reentrant pairing deletion" }
        stamp = Any()
        forgetting = true
        return Forget(owner, stamp)
    }
    @Synchronized fun finishForget(token: Forget, delete: () -> Unit) {
        check(!insideEffect && forgetting && token.owner === owner && token.stamp === stamp) {
            "Obsolete pairing deletion"
        }
        insideEffect = true
        try {
            delete()
            forgetting = false
        } finally { insideEffect = false }
    }
}
