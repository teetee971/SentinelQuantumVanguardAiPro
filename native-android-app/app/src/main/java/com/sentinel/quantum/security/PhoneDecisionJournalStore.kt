package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for sanitized Phone Core decisions.
 * Only fields accepted by PhoneDecisionJournal are stored.
 */
class PhoneDecisionJournalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun append(entry: PhoneDecisionJournal.Entry, nowMs: Long = System.currentTimeMillis()): Boolean {
        val clean = PhoneDecisionJournal.sanitize(entry, nowMs) ?: return false
        val next = PhoneDecisionJournal.bounded(readInternal() + clean, nowMs)
        write(next)
        return true
    }

    @Synchronized
    fun read(nowMs: Long = System.currentTimeMillis()): List<PhoneDecisionJournal.Entry> =
        PhoneDecisionJournal.bounded(readInternal(), nowMs)

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun readInternal(): List<PhoneDecisionJournal.Entry> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val action = runCatching { PhoneDecisionJournal.Action.valueOf(o.optString("action")) }.getOrNull() ?: continue
                val confidence = runCatching { SentinelConfidence.valueOf(o.optString("confidence")) }.getOrNull() ?: continue
                add(PhoneDecisionJournal.Entry(
                    timestampMs = o.optLong("timestampMs", -1L),
                    action = action,
                    ruleCode = o.optString("ruleCode"),
                    confidence = confidence,
                    provenance = o.optString("provenance"),
                    localOnly = o.optBoolean("localOnly", true)
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<PhoneDecisionJournal.Entry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject()
                .put("timestampMs", e.timestampMs)
                .put("action", e.action.name)
                .put("ruleCode", e.ruleCode)
                .put("confidence", e.confidence.name)
                .put("provenance", e.provenance)
                .put("localOnly", e.localOnly))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "phone_decision_journal"
        private const val KEY = "entries"
    }
}
