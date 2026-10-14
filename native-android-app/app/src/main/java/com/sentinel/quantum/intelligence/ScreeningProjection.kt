package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleEngine
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable, process-memory-only projection for call-screening consumers.
 *
 * Intelligence evidence is deliberately non-authoritative here. The projection copies only rules
 * that have already crossed the existing CallBlocklistStore authorization boundary: user-owned
 * exact/prefix rules, explicitly enabled verified ARCEP prefixes, and still-valid signed silence
 * rules. IntelligenceSnapshot contributes revision context only and can never mint enforcement.
 */
data class ScreeningProjection private constructor(
    val blockedNumberHashes: Set<String>,
    val blockedPrefixes: Set<String>,
    val signedSilencePrefixes: Set<String>,
    val intelligenceRevision: Long?
) {
    companion object {
        fun from(
            callRules: CallBlocklistStore.Snapshot,
            intelligenceSnapshot: IntelligenceSnapshot?,
            nowMs: Long
        ): ScreeningProjection {
            require(nowMs >= 0L)
            val active = callRules.activeAt(nowMs)
            return ScreeningProjection(
                blockedNumberHashes = active.blockedNumberHashes
                    .take(CallRuleEngine.MAX_EXACT_RULES)
                    .toSet(),
                blockedPrefixes = active.effectiveBlockedPrefixes
                    .take(CallRuleEngine.MAX_PREFIX_RULES)
                    .toSet(),
                signedSilencePrefixes = active.signedSilencePrefixes
                    .take(CallRuleEngine.MAX_REPUTATION_RULES)
                    .toSet(),
                intelligenceRevision = intelligenceSnapshot?.revision
            )
        }
    }
}

/** Atomic replacement keeps screening readers on one complete immutable generation. */
class ScreeningProjectionCache(initial: ScreeningProjection) {
    private val reference = AtomicReference(initial)

    fun current(): ScreeningProjection = reference.get()

    fun replace(projection: ScreeningProjection) {
        reference.set(projection)
    }
}
