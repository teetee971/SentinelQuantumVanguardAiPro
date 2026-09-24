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

    @Synchronized
    fun append(
        event: PhonePrivateTimeline.Event,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val clean = sanitize(
            event.copy(provenance = PhoneCoreCertificationScopeProvider.current(appContext)),
            nowMs
        ) ?: return false
        val next = PhonePrivateTimeline.summarize(readInternal() + clean, nowMs).events
        write(next)
        return true
    }

    @Synchronized
    fun read(nowMs: Long = System.currentTimeMillis()): PhonePrivateTimeline.Summary =
        PhonePrivateTimeline.summarize(
            readInternal().mapNotNull { sanitize(it, nowMs) },
            nowMs
        )

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun sanitize(
        event: PhonePrivateTimeline.Event,
        nowMs: Long
    ): PhonePrivateTimeline.Event? {
        if (event.timestampMs !in 0..nowMs) return null
        val direction = token(event.direction, MAX_DIRECTION) ?: return null
        val signal = event.signal?.let { token(it, MAX_SIGNAL) ?: return null }
        return event.copy(direction = direction, signal = signal)
    }

    private fun token(value: String, maxLength: Int): String? {
        val clean = value.trim()
            .take(maxLength)
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ':' }
        return clean.takeIf { it.isNotBlank() }
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

    private fun write(events: List<PhonePrivateTimeline.Event>) {
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
        prefs.edit().putString(KEY, array.toString()).apply()
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
        private const val PREFS = "phone_private_timeline"
        private const val KEY = "events"
        private const val MAX_DIRECTION = 24
        private const val MAX_SIGNAL = 160
    }
}
