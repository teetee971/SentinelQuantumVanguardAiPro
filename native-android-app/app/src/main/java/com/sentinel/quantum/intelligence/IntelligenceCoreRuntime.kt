package com.sentinel.quantum.intelligence

import android.content.Context
import com.sentinel.quantum.security.CallNumberFingerprinter
import com.sentinel.quantum.security.CallRuleEngine
import java.io.File
import java.util.UUID

/**
 * Process-local orchestration seam for privacy-bounded Intelligence Core publication.
 *
 * Callers may provide raw/normalized phone material only as transient input. The runtime converts
 * it to the existing device-bound Sentinel fingerprint before an [IntelligenceEvent] is created,
 * so neither the event sink nor the snapshot store receives a raw telephone number.
 */
object IntelligenceCoreRuntime {
    private const val CALL_EVENT_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    private const val STORE_MAX_AGE_MS = 90L * 24L * 60L * 60L * 1000L
    private const val STORE_MAX_ENTRIES = 2_048
    private const val EVENT_QUEUE_CAPACITY = 256

    private val lock = Any()

    @Volatile
    private var state: RuntimeState? = null

    fun publishCallDecision(
        context: Context,
        decision: CallRuleEngine.Decision,
        verificationCode: String,
        observedAtMs: Long = System.currentTimeMillis()
    ): PublishResult = runCatching {
        if (observedAtMs < 0L) return@runCatching PublishResult.REJECTED

        val normalizedNumber = decision.normalizedNumber
            ?: return@runCatching PublishResult.REJECTED
        val runtime = stateFor(context.applicationContext)
        val fingerprint = runtime.fingerprinter.fingerprint(normalizedNumber)
            ?: return@runCatching PublishResult.REJECTED
        val identityKey = identityKeyForFingerprint(fingerprint)
            ?: return@runCatching PublishResult.REJECTED

        val baseEvidence = CallDecisionEvidenceAdapter.from(decision)
        val event = IntelligenceEvent(
            eventId = UUID.randomUUID().toString(),
            identityKey = identityKey,
            evidence = baseEvidence.copy(networkVerification = networkVerification(verificationCode)),
            observedAtMs = observedAtMs,
            expiresAtMs = safeExpiry(observedAtMs, CALL_EVENT_TTL_MS),
            source = EvidenceSource.CALL_SCREENING,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.PHONE_CORE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.LOCAL_DERIVED
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
        runtime.sink.publish(event)
    }.getOrDefault(PublishResult.REJECTED)

    fun snapshotForNumber(
        context: Context,
        rawNumber: String?,
        nowMs: Long = System.currentTimeMillis()
    ): IntelligenceSnapshot? = runCatching {
        if (nowMs < 0L) return@runCatching null
        val normalizedNumber = CallRuleEngine.normalizeNumber(rawNumber)
            ?: return@runCatching null
        val runtime = stateFor(context.applicationContext)
        val fingerprint = runtime.fingerprinter.fingerprint(normalizedNumber)
            ?: return@runCatching null
        val identityKey = identityKeyForFingerprint(fingerprint)
            ?: return@runCatching null
        runtime.store.rebuild(identityKey, nowMs)
    }.getOrNull()

    private fun stateFor(context: Context): RuntimeState {
        val directory = File(context.filesDir, "sentinel-intelligence")
        val path = directory.absolutePath
        state?.takeIf { it.directoryPath == path }?.let { return it }

        return synchronized(lock) {
            state?.takeIf { it.directoryPath == path }?.let { return@synchronized it }

            state?.sink?.close()
            val store = IntelligenceSnapshotStore(
                directory = directory,
                maxEntries = STORE_MAX_ENTRIES,
                maxAgeMs = STORE_MAX_AGE_MS
            )
            RuntimeState(
                directoryPath = path,
                store = store,
                sink = BoundedIntelligenceEventSink(
                    capacity = EVENT_QUEUE_CAPACITY,
                    consumer = { event -> store.append(event, System.currentTimeMillis()) }
                ),
                fingerprinter = CallNumberFingerprinter()
            ).also { state = it }
        }
    }

    private fun identityKeyForFingerprint(fingerprint: String): SentinelIdentityKey? {
        val version = fingerprint.substringBefore(':', missingDelimiterValue = "")
            .removePrefix("v")
            .toIntOrNull()
            ?: return null
        return SentinelIdentityKey.phoneFingerprint(fingerprint, version)
    }

    private fun networkVerification(code: String): IntelligenceEvidence.NetworkVerification = when (code) {
        "VERIFIED" -> IntelligenceEvidence.NetworkVerification.VERIFIED
        "FAILED" -> IntelligenceEvidence.NetworkVerification.FAILED
        "NOT_VERIFIED" -> IntelligenceEvidence.NetworkVerification.NOT_VERIFIED
        else -> IntelligenceEvidence.NetworkVerification.UNKNOWN
    }

    private fun safeExpiry(observedAtMs: Long, ttlMs: Long): Long =
        if (Long.MAX_VALUE - observedAtMs < ttlMs) Long.MAX_VALUE else observedAtMs + ttlMs

    private data class RuntimeState(
        val directoryPath: String,
        val store: IntelligenceSnapshotStore,
        val sink: BoundedIntelligenceEventSink,
        val fingerprinter: CallNumberFingerprinter
    )
}
