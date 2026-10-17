package com.sentinel.quantum.security

import android.content.Context
import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded local persistence for network observations.
 *
 * Only device-bound pseudonyms and minimal observation metadata are stored. Raw MAC addresses,
 * BSSIDs, payloads and names never cross this boundary. Corrupt or unknown-schema data is treated
 * as empty evidence and can never produce a positive security conclusion.
 */
class NetworkTimelineStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val trustStore = NetworkTrustStore(appContext)

    fun recordObserved(
        source: NetworkTimelineSource,
        rawIdentifiers: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val recorded = synchronized(LOCK) {
            if (nowMs < 0L) return@synchronized false

            val current = readInternal()
            val currentSnapshot = NetworkChangeTimeline.build(current, nowMs)
            val known = currentSnapshot.acceptedEvents.mapTo(hashSetOf()) { it.subjectFingerprint }
            val fingerprints = rawIdentifiers
                .asSequence()
                .take(MAX_IDENTIFIERS_PER_OBSERVATION)
                .mapNotNull { trustStore.pseudonymousFingerprint(it) }
                .distinct()
                .toList()
            val events = NetworkObservationTimelineAdapter.events(
                source = source,
                fingerprints = fingerprints,
                knownFingerprints = known,
                observedAtMs = nowMs
            )
            if (events.isEmpty()) return@synchronized true

            write(NetworkChangeTimeline.build(current + events, nowMs).acceptedEvents)
        }

        if (recorded) {
            when (source) {
                NetworkTimelineSource.ANDROID_WIFI -> IntegrationTruthRuntime.publishConnected(
                    IntegrationCapabilityId.WIFI_OBSERVATION,
                    nowMs
                )
                NetworkTimelineSource.ANDROID_BLE -> IntegrationTruthRuntime.publishConnected(
                    IntegrationCapabilityId.BLUETOOTH_OBSERVATION,
                    nowMs
                )
                else -> Unit
            }
            IntegrationTruthRuntime.publishConnected(
                IntegrationCapabilityId.NETWORK_BEHAVIOR,
                nowMs
            )
        }
        return recorded
    }

    fun read(nowMs: Long = System.currentTimeMillis()): NetworkTimelineSnapshot = synchronized(LOCK) {
        if (nowMs < 0L) {
            return@synchronized NetworkTimelineSnapshot(
                acceptedEvents = emptyList(),
                rejectedEvents = 0,
                prunedEvents = 0,
                firstObservedAtMs = null,
                lastObservedAtMs = null,
                highSeverityCount = 0,
                warningCount = 0
            )
        }
        NetworkChangeTimeline.build(readInternal(), nowMs)
    }

    fun clear(): Boolean = synchronized(LOCK) {
        preferences.edit().remove(KEY).commit()
    }

    private fun readInternal(): List<NetworkTimelineEvent> = runCatching {
        val root = JSONObject(preferences.getString(KEY, null) ?: return emptyList())
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return emptyList()
        val array = root.optJSONArray("events") ?: return emptyList()
        if (array.length() > NetworkChangeTimeline.MAX_EVENTS) return emptyList()

        buildList {
            for (index in 0 until array.length()) {
                val event = array.optJSONObject(index) ?: continue
                val kind = runCatching {
                    NetworkTimelineEventKind.valueOf(event.optString("kind"))
                }.getOrNull() ?: continue
                val source = runCatching {
                    NetworkTimelineSource.valueOf(event.optString("source"))
                }.getOrNull() ?: continue
                val severity = runCatching {
                    NetworkTimelineSeverity.valueOf(event.optString("severity"))
                }.getOrNull() ?: continue
                add(
                    NetworkTimelineEvent(
                        subjectFingerprint = event.optString("subjectFingerprint"),
                        kind = kind,
                        source = source,
                        severity = severity,
                        observedAtMs = event.optLong("observedAtMs", -1L),
                        summary = event.optString("summary")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(events: List<NetworkTimelineEvent>): Boolean {
        val array = JSONArray()
        events.takeLast(NetworkChangeTimeline.MAX_EVENTS).forEach { event ->
            array.put(
                JSONObject()
                    .put("subjectFingerprint", event.subjectFingerprint)
                    .put("kind", event.kind.name)
                    .put("source", event.source.name)
                    .put("severity", event.severity.name)
                    .put("observedAtMs", event.observedAtMs)
                    .put("summary", event.summary)
            )
        }
        return preferences.edit()
            .putString(KEY, JSONObject().put("schemaVersion", SCHEMA_VERSION).put("events", array).toString())
            .commit()
    }

    private companion object {
        val LOCK = Any()
        const val PREFERENCES = "network_timeline"
        const val KEY = "bounded_events"
        const val SCHEMA_VERSION = 1
        const val MAX_IDENTIFIERS_PER_OBSERVATION = 512
    }
}
