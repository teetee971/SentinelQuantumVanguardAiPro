package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for the bounded Phone Core timeline.
 *
 * This store accepts only the metadata represented by [PhonePrivateTimeline.Event].
 * Raw phone numbers, message bodies, URLs, sender identifiers and OTP values are
 * intentionally outside this model.
 */
class PhonePrivateTimelineStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // The lock must be shared by all instances: Android call, SMS and MMS callbacks
    // construct independent stores that otherwise lose events during read-modify-write.
    fun append(
        event: PhonePrivateTimeline.Event,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        val provenance = PhoneCoreCertificationScopeProvider.current(appContext)
            ?: return@synchronized false
        val clean = PhonePrivateTimeline.sanitize(
            event.copy(provenance = provenance),
            nowMs
        ) ?: return@synchronized false
        val next = PhonePrivateTimeline.summarize(readInternal() + clean, nowMs).events
        write(next)
    }

    fun read(nowMs: Long = System.currentTimeMillis()): PhonePrivateTimeline.Summary =
        synchronized(LOCK) {
            PhonePrivateTimeline.summarize(
                readInternal().mapNotNull { PhonePrivateTimeline.sanitize(it, nowMs) },
                nowMs
            )
        }

    fun clear(): Boolean = synchronized(LOCK) {
        prefs.edit().remove(KEY).commit()
    }

    private fun readInternal(): List<PhonePrivateTimeline.Event> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val kind = runCatching {
                    PhonePrivateTimeline.Kind.valueOf(o.optString("kind"))
                }.getOrNull() ?: continue
                add(
                    PhonePrivateTimeline.Event(
                        kind = kind,
                        timestampMs = o.optLong("timestampMs", -1L),
                        direction = o.optString("direction"),
                        signal = if (o.isNull("signal")) null else o.optString("signal"),
                        provenance = readProvenance(o.optJSONObject("provenance"))
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(events: List<PhonePrivateTimeline.Event>): Boolean {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("kind", event.kind.name)
                    .put("timestampMs", event.timestampMs)
                    .put("direction", event.direction)
                    .put("signal", event.signal)
                    .put("provenance", event.provenance?.let(::writeProvenance) ?: JSONObject.NULL)
            )
        }
        return prefs.edit().putString(KEY, array.toString()).commit()
    }

    private fun readProvenance(o: JSONObject?): PhoneCoreCertificationProvenance.Scope? {
        if (o == null) return null
        return PhoneCoreCertificationProvenance.normalize(PhoneCoreCertificationProvenance.Scope(
            installationId = o.optString("installationId"), versionCode = o.optLong("versionCode", -1L),
            versionName = o.optString("versionName"), lastUpdateTimeMs = o.optLong("lastUpdateTimeMs", -1L),
            sessionId = o.optString("sessionId")
        ))
    }

    private fun writeProvenance(s: PhoneCoreCertificationProvenance.Scope): JSONObject = JSONObject()
        .put("installationId", s.installationId).put("versionCode", s.versionCode)
        .put("versionName", s.versionName).put("lastUpdateTimeMs", s.lastUpdateTimeMs)
        .put("sessionId", s.sessionId)

    companion object {
        private val LOCK = Any()
        private const val PREFS = "phone_private_timeline"
        private const val KEY = "events"
    }
}

