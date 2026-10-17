package com.sentinel.quantum.intelligence

import android.content.Context
import com.sentinel.quantum.security.CallNumberFingerprinter
import com.sentinel.quantum.security.CallRuleEngine
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.SmsDeliveryStatusBus
import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.security.SmsProviderIdentityLookup
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.intelligence.adapters.ContactIntelligenceAdapter
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
    private const val SMS_EVENT_TTL_MS = 30L * 24L * 60L * 60L * 1000L
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

    /**
     * Publishes a validated Android SMS transport callback without persisting the destination.
     *
     * The provider row id is used only to resolve the transient address. That value is normalized,
     * converted immediately into the existing device-bound fingerprint, and discarded before the
     * [IntelligenceEvent] is constructed. Replayed callbacks use a deterministic PII-free event id,
     * so the snapshot store's event-id idempotence prevents duplicate evidence.
     */
    fun publishSmsDelivery(
        context: Context,
        providerMessageId: Long,
        event: SmsDeliveryStatusBus.Event,
        observedAtMs: Long = System.currentTimeMillis()
    ): PublishResult = runCatching {
        if (
            observedAtMs < 0L ||
            providerMessageId <= 0L ||
            event.providerMessageId != providerMessageId ||
            event.sendToken <= 0 ||
            event.partCount <= 0 ||
            event.partIndex !in 0 until event.partCount
        ) return@runCatching PublishResult.REJECTED

        val rawAddress = SmsProviderIdentityLookup(context.applicationContext)
            .addressForProviderMessage(providerMessageId)
            ?: return@runCatching PublishResult.REJECTED
        val normalizedNumber = CallRuleEngine.normalizeNumber(rawAddress)
            ?: return@runCatching PublishResult.REJECTED
        val runtime = stateFor(context.applicationContext)
        val fingerprint = runtime.fingerprinter.fingerprint(normalizedNumber)
            ?: return@runCatching PublishResult.REJECTED
        val identityKey = identityKeyForFingerprint(fingerprint)
            ?: return@runCatching PublishResult.REJECTED

        val evidence = SmsMmsEvidenceAdapter.fromSmsDelivery(
            event = event,
            direction = IntelligenceEvidence.Direction.OUTGOING
        )
        val intelligenceEvent = IntelligenceEvent(
            eventId = smsStatusEventId(event),
            identityKey = identityKey,
            evidence = evidence,
            observedAtMs = observedAtMs,
            expiresAtMs = safeExpiry(observedAtMs, SMS_EVENT_TTL_MS),
            source = EvidenceSource.SMS,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.SMS_CORE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.LOCAL_PLATFORM_ASSERTION
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
        runtime.sink.publish(intelligenceEvent)
    }.getOrDefault(PublishResult.REJECTED)

    /**
     * Publishes an explicitly consented post-screening reputation result into the shared core.
     *
     * The caller number is transient input only. The persisted event contains the existing
     * device-bound fingerprint and bounded reputation evidence; remote flags, warning copy and
     * raw number are not stored. This method must never be called from the Telecom callback before
     * respondToCall().
     */
    fun publishRemoteReputation(
        context: Context,
        rawNumber: String?,
        result: CallerReputationClient.Result
    ): PublishResult = runCatching {
        val normalizedNumber = CallRuleEngine.normalizeNumber(rawNumber)
            ?: return@runCatching PublishResult.REJECTED
        val runtime = stateFor(context.applicationContext)
        val fingerprint = runtime.fingerprinter.fingerprint(normalizedNumber)
            ?: return@runCatching PublishResult.REJECTED
        val identityKey = identityKeyForFingerprint(fingerprint)
            ?: return@runCatching PublishResult.REJECTED
        val intelligenceEvent = ReputationEvidenceAdapter.fromRemoteResult(
            eventId = reputationEventId(fingerprint, result),
            identityKey = identityKey,
            result = result,
            sourceLabel = REMOTE_REPUTATION_SOURCE
        )
        runtime.sink.publish(intelligenceEvent)
    }.getOrDefault(PublishResult.REJECTED)

    /**
     * Publishes the bounded result of local incoming-SMS analysis.
     *
     * The message body and extracted URLs are transient inputs to SmsLinkAnalyzer and never enter
     * this contract. The provider row id gives replayed delivery callbacks a stable event id.
     */
    fun publishIncomingSmsAnalysis(
        context: Context,
        providerMessageId: Long,
        sender: String?,
        analysis: SmsLinkAnalyzer.Analysis,
        observedAtMs: Long = analysis.analyzedAt
    ): PublishResult = runCatching {
        if (
            providerMessageId <= 0L ||
            observedAtMs < 0L ||
            !analysis.accepted
        ) return@runCatching PublishResult.REJECTED

        val normalizedNumber = CallRuleEngine.normalizeNumber(sender)
            ?: return@runCatching PublishResult.REJECTED
        val runtime = stateFor(context.applicationContext)
        val fingerprint = runtime.fingerprinter.fingerprint(normalizedNumber)
            ?: return@runCatching PublishResult.REJECTED
        val identityKey = identityKeyForFingerprint(fingerprint)
            ?: return@runCatching PublishResult.REJECTED
        val intelligenceEvent = IntelligenceEvent(
            eventId = "sms-analysis:$providerMessageId",
            identityKey = identityKey,
            evidence = SmsMmsEvidenceAdapter.fromIncomingAnalysis(analysis),
            observedAtMs = observedAtMs,
            expiresAtMs = safeExpiry(observedAtMs, SMS_EVENT_TTL_MS),
            source = EvidenceSource.SMS,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.SMS_CORE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.LOCAL_DERIVED
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
        runtime.sink.publish(intelligenceEvent)
    }.getOrDefault(PublishResult.REJECTED)

    /**
     * Publishes a bounded, permission-gated Android contact observation after a non-critical UI
     * lookup. Contact display data stays in the existing durable local-identity contract; the raw
     * phone number is used only to derive the device-bound fingerprint.
     */
    fun publishContactObservation(
        context: Context,
        contactList: LocalContactLookup.ContactListResult,
        rawNumber: String,
        identity: LocalContactLookup.Identity?,
        observedAtMs: Long = System.currentTimeMillis()
    ): PublishResult {
        if (observedAtMs < 0L) return PublishResult.REJECTED
        val runtime = runCatching { stateFor(context.applicationContext) }.getOrNull()
            ?: return PublishResult.REJECTED
        return runCatching {
            ContactIntelligenceAdapter(
                sink = runtime.sink,
                canonicalize = { value -> CallRuleEngine.normalizeNumber(value).orEmpty() },
                fingerprint = { canonical -> runtime.fingerprinter.fingerprint(canonical) }
            ).publish(
                contactList = contactList,
                rawNumber = rawNumber,
                identity = identity,
                observedAtMs = observedAtMs
            ).publishResult
        }.getOrDefault(PublishResult.REJECTED)
    }

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

    private fun smsStatusEventId(event: SmsDeliveryStatusBus.Event): String =
        "sms-status:${event.sendToken}:${event.providerMessageId}:${event.stage.name}:${event.partIndex}:${event.partCount}"

    private fun reputationEventId(
        fingerprint: String,
        result: CallerReputationClient.Result
    ): String = listOf(
        "reputation",
        fingerprint,
        result.reputationObservedAtMs,
        result.reputationTtlMs,
        result.riskScore,
        result.signals,
        result.categories.joinToString(",").hashCode(),
        REMOTE_REPUTATION_SOURCE
    ).joinToString(":")

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

    private const val REMOTE_REPUTATION_SOURCE = "caller-reputation-api"
}
