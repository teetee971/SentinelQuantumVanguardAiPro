package com.sentinel.quantum.security

import android.content.Context
import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationTruthRuntime
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
    ): Boolean {
        val clean = synchronized(LOCK) {
            val provenance = PhoneCoreCertificationScopeProvider.current(appContext)
                ?: return@synchronized null
            val sanitized = PhonePrivateTimeline.sanitize(
                event.copy(provenance = provenance),
                nowMs
            ) ?: return@synchronized null
            val next = PhonePrivateTimeline.summarize(readInternal() + sanitized, nowMs).events
            if (!write(next)) return@synchronized null
            sanitized
        } ?: return false

        publishIntegrationTruth(clean)
        return true
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

    private fun publishIntegrationTruth(event: PhonePrivateTimeline.Event) {
        when (event.kind) {
            PhonePrivateTimeline.Kind.CALL -> {
                when {
                    event.signal == PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE ->
                        IntegrationTruthRuntime.publishConnected(
                            IntegrationCapabilityId.INCALL,
                            event.timestampMs
                        )
                    event.signal?.startsWith(PhoneCorePhysicalValidation.SIGNAL_CALL_SCREENED_PREFIX) == true ->
                        IntegrationTruthRuntime.publishConnected(
                            IntegrationCapabilityId.CALL_SCREENING,
                            event.timestampMs
                        )
                    event.signal == PhoneCorePhysicalValidation.SIGNAL_CALLER_ID_UI_SHOWN ->
                        IntegrationTruthRuntime.publishConnected(
                            IntegrationCapabilityId.CALLER_ID_LOCAL,
                            event.timestampMs
                        )
                    event.signal == PhoneCorePhysicalValidation.SIGNAL_INCALL_UI_SHOWN ->
                        IntegrationTruthRuntime.publishConnected(
                            IntegrationCapabilityId.INCALL,
                            event.timestampMs
                        )
                }
            }
            PhonePrivateTimeline.Kind.SMS ->
                IntegrationTruthRuntime.publishConnected(
                    IntegrationCapabilityId.SMS,
                    event.timestampMs
                )
            PhonePrivateTimeline.Kind.MMS ->
                IntegrationTruthRuntime.publishConnected(
                    IntegrationCapabilityId.MMS,
                    event.timestampMs
                )
            // Wi-Fi observations publish their integration truth through NetworkTimelineStore.
            // Keep the private evidence append-only here without duplicating that signal.
            PhonePrivateTimeline.Kind.WIFI -> Unit
        }
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
