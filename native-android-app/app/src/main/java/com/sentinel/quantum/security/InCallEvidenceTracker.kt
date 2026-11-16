package com.sentinel.quantum.security

/**
 * Identity- and lifecycle-scoped state for evidence writes owned by the in-call service.
 *
 * A completion token is valid only for the exact call object, call generation, service
 * lifecycle and write attempt that created it. This lets a storage coroutine finish without
 * being able to resurrect state after onCallRemoved() or onDestroy().
 */
internal class InCallEvidenceTracker<K : Any> {
    enum class Kind { INCOMING_NOTIFICATION, CONNECTED }

    enum class Completion { APPLIED, STALE }

    class Token<K : Any> internal constructor(
        internal val call: K,
        internal val callGeneration: Long,
        internal val lifecycleGeneration: Long,
        internal val kind: Kind,
        internal val attempt: Long
    )

    private class Entry(val generation: Long) {
        var incomingRecorded = false
        var incomingAttempt: Long? = null
        var connectedRecorded = false
        var connectedAttempt: Long? = null
    }

    private val entries = java.util.IdentityHashMap<K, Entry>()
    private var nextCallGeneration = 0L
    private var nextAttempt = 0L
    private var lifecycleGeneration = 0L
    private var active = true

    @Synchronized
    fun track(call: K) {
        if (active && !entries.containsKey(call)) {
            entries[call] = Entry(++nextCallGeneration)
        }
    }

    @Synchronized
    fun remove(call: K) {
        entries.remove(call)
    }

    @Synchronized
    fun destroy() {
        active = false
        lifecycleGeneration++
        entries.clear()
    }

    @Synchronized
    fun begin(call: K, kind: Kind): Token<K>? {
        if (!active) return null
        val entry = entries[call] ?: return null
        if (isRecorded(entry, kind) || isInFlight(entry, kind)) return null

        val attempt = ++nextAttempt
        setAttempt(entry, kind, attempt)
        return Token(call, entry.generation, lifecycleGeneration, kind, attempt)
    }

    @Synchronized
    fun complete(token: Token<K>, stored: Boolean): Completion {
        val entry = entries[token.call]
        if (
            !active ||
            entry == null ||
            entry.generation != token.callGeneration ||
            lifecycleGeneration != token.lifecycleGeneration ||
            attempt(entry, token.kind) != token.attempt
        ) return Completion.STALE

        setAttempt(entry, token.kind, null)
        if (stored) setRecorded(entry, token.kind)
        return Completion.APPLIED
    }

    @Synchronized
    fun isRecorded(call: K, kind: Kind): Boolean =
        entries[call]?.let { isRecorded(it, kind) } == true

    @Synchronized
    fun isInFlight(call: K, kind: Kind): Boolean = entries[call]?.let { isInFlight(it, kind) } == true

    private fun isRecorded(entry: Entry, kind: Kind): Boolean = when (kind) {
        Kind.INCOMING_NOTIFICATION -> entry.incomingRecorded
        Kind.CONNECTED -> entry.connectedRecorded
    }

    private fun isInFlight(entry: Entry, kind: Kind): Boolean = when (kind) {
        Kind.INCOMING_NOTIFICATION -> entry.incomingAttempt != null
        Kind.CONNECTED -> entry.connectedAttempt != null
    }

    private fun attempt(entry: Entry, kind: Kind): Long? = when (kind) {
        Kind.INCOMING_NOTIFICATION -> entry.incomingAttempt
        Kind.CONNECTED -> entry.connectedAttempt
    }

    private fun setAttempt(entry: Entry, kind: Kind, attempt: Long?) {
        when (kind) {
            Kind.INCOMING_NOTIFICATION -> entry.incomingAttempt = attempt
            Kind.CONNECTED -> entry.connectedAttempt = attempt
        }
    }

    private fun setRecorded(entry: Entry, kind: Kind) {
        when (kind) {
            Kind.INCOMING_NOTIFICATION -> entry.incomingRecorded = true
            Kind.CONNECTED -> entry.connectedRecorded = true
        }
    }
}

/** Default-dialer outgoing calls and explicit Telecom foreground requests may open call UI. */
internal object InCallActivityLaunchPolicy {
    enum class Trigger {
        TELECOM_BRING_TO_FOREGROUND,
        CALL_ADDED,
        INCOMING_NOTIFICATION_FALLBACK
    }

    fun shouldRequest(trigger: Trigger, hasActiveCall: Boolean, outgoingCall: Boolean = false): Boolean =
        hasActiveCall && (trigger == Trigger.TELECOM_BRING_TO_FOREGROUND ||
            (trigger == Trigger.CALL_ADDED && outgoingCall))
}

internal enum class InCallActivityLaunchResult { NOT_REQUESTED, REQUESTED, FAILED }
