package com.sentinel.quantum.security

import android.provider.CallLog

/** Pure aggregation over call-log rows already read locally from Android. */
object CallHistoryInsights {
    data class Summary(
        val total: Int,
        val incoming: Int,
        val outgoing: Int,
        val missed: Int,
        val rejected: Int,
        val blocked: Int,
        val voicemail: Int,
        val other: Int,
        val totalDurationSeconds: Long
    )

    fun summarize(entries: List<SystemCallLogReader.Entry>): Summary {
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var rejected = 0
        var blocked = 0
        var voicemail = 0
        var other = 0
        var duration = 0L

        entries.forEach { entry ->
            duration = safeAdd(duration, entry.durationSeconds.coerceAtLeast(0L))
            when (entry.type) {
                CallLog.Calls.INCOMING_TYPE -> incoming++
                CallLog.Calls.OUTGOING_TYPE -> outgoing++
                CallLog.Calls.MISSED_TYPE -> missed++
                CallLog.Calls.REJECTED_TYPE -> rejected++
                CallLog.Calls.BLOCKED_TYPE -> blocked++
                CallLog.Calls.VOICEMAIL_TYPE -> voicemail++
                else -> other++
            }
        }
        return Summary(
            total = entries.size,
            incoming = incoming,
            outgoing = outgoing,
            missed = missed,
            rejected = rejected,
            blocked = blocked,
            voicemail = voicemail,
            other = other,
            totalDurationSeconds = duration
        )
    }

    fun typeLabelFr(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "Entrant"
        CallLog.Calls.OUTGOING_TYPE -> "Sortant"
        CallLog.Calls.MISSED_TYPE -> "Manqué"
        CallLog.Calls.REJECTED_TYPE -> "Rejeté"
        CallLog.Calls.BLOCKED_TYPE -> "Bloqué"
        CallLog.Calls.VOICEMAIL_TYPE -> "Messagerie vocale"
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "Répondu ailleurs"
        else -> "Autre"
    }

    fun durationLabelFr(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3_600
        val minutes = (safe % 3_600) / 60
        val remainingSeconds = safe % 60
        return when {
            hours > 0 -> "${hours} h ${minutes} min"
            minutes > 0 -> "${minutes} min ${remainingSeconds} s"
            else -> "${remainingSeconds} s"
        }
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
