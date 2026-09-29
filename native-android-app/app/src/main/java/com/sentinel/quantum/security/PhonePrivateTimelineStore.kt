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
 *
 * Reads are truth-preserving: malformed persisted evidence is reported as partial
 * or unreadable and is never silently replaced by an empty history.
 */
class PhonePrivateTimelineStore(context: Context) {
    enum class ReadState { EMPTY, COMPLETE, PARTIAL, UNREADABLE }

    data class ReadResult(
        val summary: PhonePrivateTimeline.Summary,
        val state: ReadState,
        val rejectedEntryCount: Int
    ) {
        val isReliable: Boolean
            get() = state == ReadState.EMPTY || state == ReadState.COMPLETE
    }

    internal data class StoredRead(
        val events: List<PhonePrivateTimeline.Event>,
        val state: ReadState,
        val rejectedEntryCount: Int
    )

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

        val stored = readStored()
        if (stored.state == ReadState.PARTIAL || stored.state == ReadState.UNREADABLE) {
            logPersistenceWarning("Timeline locale partielle ou illisible : nouvelle preuve non écrite.")
            return false
        }

        val existing = ArrayList<PhonePrivateTimeline.Event>(stored.events.size)
        for (candidate in stored.events) {
            val sanitized = sanitize(candidate, nowMs)
            if (sanitized == null) {
                logPersistenceWarning("Timeline locale contient une preuve invalide : écriture bloquée.")
                return false
            }
            existing += sanitized
        }

        val next = PhonePrivateTimeline.summarize(existing + clean, nowMs).events
        return write(next)
    }

    @Synchronized
    fun read(nowMs: Long = System.currentTimeMillis()): PhonePrivateTimeline.Summary =
        readResult(nowMs).summary

    @Synchronized
    fun readResult(nowMs: Long = System.currentTimeMillis()): ReadResult {
        val stored = readStored()
        if (stored.state == ReadState.UNREADABLE) {
            return ReadResult(
                summary = PhonePrivateTimeline.summarize(emptyList(), nowMs),
                state = ReadState.UNREADABLE,
                rejectedEntryCount = stored.rejectedEntryCount.coerceAtLeast(1)
            )
        }

        var rejected = stored.rejectedEntryCount
        val sanitized = buildList {
            stored.events.forEach { event ->
                val clean = sanitize(event, nowMs)
                if (clean == null) rejected += 1 else add(clean)
            }
        }
        val state = when {
            stored.state == ReadState.EMPTY -> ReadState.EMPTY
            stored.state == ReadState.PARTIAL || rejected > 0 -> ReadState.PARTIAL
            else -> ReadState.COMPLETE
        }
        return ReadResult(
            summary = PhonePrivateTimeline.summarize(sanitized, nowMs),
            state = state,
            rejectedEntryCount = rejected
        )
    }

    @Synchronized
    fun clear(): Boolean =
        runCatching { prefs.edit().remove(KEY).commit() }.getOrDefault(false)

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

    private fun readStored(): StoredRead =
        runCatching { decodeStored(prefs.getString(KEY, null)) }
            .getOrElse {
                StoredRead(emptyList(), ReadState.UNREADABLE, rejectedEntryCount = 1)
            }

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
        val committed = runCatching {
            prefs.edit().putString(KEY, array.toString()).commit()
        }.getOrDefault(false)
        if (!committed) {
            logPersistenceWarning("Échec de persistance de la timeline locale : preuve non confirmée.")
        }
        return committed
    }

    private fun logPersistenceWarning(message: String) {
        runCatching {
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "PhoneTimeline",
                message
            )
        }
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

        internal fun decodeStored(raw: String?): StoredRead {
            if (raw == null) {
                return StoredRead(emptyList(), ReadState.EMPTY, rejectedEntryCount = 0)
            }
            return try {
                val array = JSONArray(raw)
                var rejected = 0
                val events = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.optJSONObject(i)
                        if (o == null) {
                            rejected += 1
                            continue
                        }
                        val kind = runCatching {
                            PhonePrivateTimeline.Kind.valueOf(o.optString("kind"))
                        }.getOrNull()
                        if (kind == null) {
                            rejected += 1
                            continue
                        }
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
                StoredRead(
                    events = events,
                    state = if (rejected == 0) ReadState.COMPLETE else ReadState.PARTIAL,
                    rejectedEntryCount = rejected
                )
            } catch (_: Exception) {
                StoredRead(emptyList(), ReadState.UNREADABLE, rejectedEntryCount = 1)
            }
        }

        private fun readProvenance(
            o: JSONObject?
        ): PhoneCoreCertificationProvenance.Scope? {
            if (o == null) return null
            return PhoneCoreCertificationProvenance.normalize(
                PhoneCoreCertificationProvenance.Scope(
                    installationId = o.optString("installationId"),
                    versionCode = o.optLong("versionCode", -1L),
                    versionName = o.optString("versionName"),
                    lastUpdateTimeMs = o.optLong("lastUpdateTimeMs", -1L),
                    sessionId = o.optString("sessionId")
                )
            )
        }
    }
}
