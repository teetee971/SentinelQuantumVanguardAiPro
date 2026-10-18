package com.sentinel.quantum.security

import android.content.Context
import android.provider.CallLog

/**
 * Resolves local call-history context for one number without exposing history rows to consumers.
 *
 * Raw numbers remain transient inputs. The returned model contains counts and the last local
 * interaction timestamp only, and [CallHistoryPrivacy] keeps those values bounded.
 */
class LocalCallHistoryContextResolver(
    context: Context,
    private val defaultCallingCode: String? = null
) {
    private val reader = SystemCallLogReader(context.applicationContext)

    fun resolve(rawNumber: String?): CallHistoryPrivacy.LocalCallContext =
        fromEntries(rawNumber, reader.recent(CallHistoryPrivacy.MAX_RECENT_EVENTS), defaultCallingCode)

    companion object {
        internal fun fromEntries(
            rawNumber: String?,
            entries: List<SystemCallLogReader.Entry>,
            defaultCallingCode: String? = null
        ): CallHistoryPrivacy.LocalCallContext {
            val target = CallRuleEngine.normalizeNumber(rawNumber, defaultCallingCode)
                ?: return CallHistoryPrivacy.boundedContext(0, 0, null)
            val matches = entries.asSequence()
                .filter { entry ->
                    CallRuleEngine.normalizeNumber(entry.number, defaultCallingCode) == target
                }
                .take(CallHistoryPrivacy.MAX_RECENT_EVENTS)
                .toList()
            return CallHistoryPrivacy.boundedContext(
                recentCalls = matches.size,
                missedCalls = matches.count { it.type == CallLog.Calls.MISSED_TYPE },
                lastInteractionEpochMillis = matches.maxOfOrNull { it.dateMillis }
            )
        }
    }
}
