package com.sentinel.quantum.intelligence

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * Small app-private persistence boundary for already-sanitized intelligence evidence.
 *
 * The store deliberately has no Android dependency: production callers provide an app-private
 * directory (for example Context.filesDir). It persists only fields present in IntelligenceEvent;
 * raw phone numbers, message bodies, URLs, sender addresses and OTP material have no storage slot.
 *
 * Reads fail closed. Corrupt payloads, unsupported schemas and unknown enum/evidence values produce
 * an empty state rather than partially reconstructed or fabricated intelligence.
 */
class IntelligenceSnapshotStore(
    private val directory: File,
    private val maxEntries: Int,
    private val maxAgeMs: Long
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxAgeMs >= 0L) { "maxAgeMs must be non-negative" }
    }

    @Synchronized
    fun append(event: IntelligenceEvent, nowMs: Long) {
        require(nowMs >= 0L) { "nowMs must be non-negative" }
        val existing = prune(loadAll(), nowMs)
        if (existing.any { it.eventId == event.eventId }) {
            writeAll(existing)
            return
        }
        writeAll(prune(existing + event, nowMs))
    }

    @Synchronized
    fun read(identityKey: SentinelIdentityKey, nowMs: Long): List<IntelligenceEvent> {
        require(nowMs >= 0L) { "nowMs must be non-negative" }
        return prune(loadAll(), nowMs).filter { it.identityKey == identityKey }
    }

    fun rebuild(identityKey: SentinelIdentityKey, nowMs: Long): IntelligenceSnapshot =
        IntelligenceAggregator().aggregate(identityKey, read(identityKey, nowMs), nowMs)

    private fun prune(events: List<IntelligenceEvent>, nowMs: Long): List<IntelligenceEvent> =
        events.asSequence()
            .filter { event ->
                event.observedAtMs > nowMs || nowMs - event.observedAtMs <= maxAgeMs
            }
            .sortedWith(compareBy<IntelligenceEvent>({ it.observedAtMs }, { it.eventId }))
            .distinctBy { it.eventId }
            .toList()
            .takeLast(maxEntries)

    private fun loadAll(): List<IntelligenceEvent> {
        val file = File(directory, FILE_NAME)
        if (!file.isFile) return emptyList()
        return runCatching {
            val lines = file.readLines(StandardCharsets.UTF_8)
            if (lines.firstOrNull() != SCHEMA_HEADER) return emptyList()
            lines.drop(1)
                .filter { it.isNotBlank() }
                .map(::decodeEvent)
        }.getOrElse { emptyList() }
    }

    private fun writeAll(events: List<IntelligenceEvent>) {
        if (!directory.exists() && !directory.mkdirs()) return
        val target = File(directory, FILE_NAME)
        val temp = File(directory, "$FILE_NAME.tmp")
        val payload = buildString {
            append(SCHEMA_HEADER).append('\n')
            events.forEach { append(encodeEvent(it)).append('\n') }
        }
        runCatching {
            temp.writeText(payload, StandardCharsets.UTF_8)
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure {
            temp.delete()
        }
    }

    private fun encodeEvent(event: IntelligenceEvent): String {
        val identity = event.identityKey
        val fields = mutableListOf(
            enc(event.eventId),
            identity?.namespace?.name ?: NULL,
            identity?.value?.let(::enc) ?: NULL,
            identity?.version?.toString() ?: NULL,
            event.observedAtMs.toString(),
            event.expiresAtMs?.toString() ?: NULL,
            event.source.name,
            event.provenance.producer.name,
            event.provenance.producerVersion.toString(),
            event.provenance.trust.name,
            event.provenance.certification?.let(::enc) ?: NULL,
            event.provenance.issuer?.let(::enc) ?: NULL,
            event.privacyClass.name
        )
        fields += encodeEvidence(event.evidence)
        return fields.joinToString(SEPARATOR)
    }

    private fun decodeEvent(line: String): IntelligenceEvent {
        val fields = line.split(SEPARATOR)
        require(fields.size >= BASE_FIELD_COUNT + 1)
        var index = 0
        val eventId = dec(fields[index++])
        val namespace = fields[index++]
        val identityValue = fields[index++]
        val identityVersion = fields[index++]
        val identity = if (namespace == NULL && identityValue == NULL && identityVersion == NULL) {
            null
        } else {
            SentinelIdentityKey(
                SentinelIdentityKey.Namespace.valueOf(namespace),
                dec(identityValue),
                identityVersion.toInt()
            )
        }
        val observedAtMs = fields[index++].toLong()
        val expiresAtMs = fields[index++].let { if (it == NULL) null else it.toLong() }
        val source = EvidenceSource.valueOf(fields[index++])
        val provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.valueOf(fields[index++]),
            producerVersion = fields[index++].toInt(),
            trust = EvidenceProvenance.TrustClass.valueOf(fields[index++]),
            certification = fields[index++].let { if (it == NULL) null else dec(it) },
            issuer = fields[index++].let { if (it == NULL) null else dec(it) }
        )
        val privacyClass = PrivacyClass.valueOf(fields[index++])
        val evidence = decodeEvidence(fields.drop(index))
        return IntelligenceEvent(
            eventId = eventId,
            identityKey = identity,
            evidence = evidence,
            observedAtMs = observedAtMs,
            expiresAtMs = expiresAtMs,
            source = source,
            provenance = provenance,
            privacyClass = privacyClass
        )
    }

    private fun encodeEvidence(evidence: IntelligenceEvidence): List<String> = when (evidence) {
        is IntelligenceEvidence.ContactIdentity -> listOf(
            "CONTACT",
            evidence.contactState.name,
            nullable(evidence.displayLabel),
            nullable(evidence.organisationLabel),
            nullable(evidence.contactAlias)
        )
        is IntelligenceEvidence.CallDecision -> listOf(
            "CALL",
            evidence.action.name,
            enc(evidence.ruleSource),
            enc(evidence.reasonCode),
            evidence.networkVerification.name
        )
        is IntelligenceEvidence.SmsSecurity -> listOf(
            "SMS",
            evidence.direction.name,
            enc(evidence.transportState),
            nullable(evidence.categoryCode),
            nullable(evidence.analysisOutcome)
        )
        is IntelligenceEvidence.MmsSecurity -> listOf(
            "MMS",
            evidence.direction.name,
            enc(evidence.transportState),
            nullable(evidence.categoryCode),
            nullable(evidence.analysisOutcome)
        )
        is IntelligenceEvidence.Reputation -> listOf(
            "REPUTATION",
            evidence.riskScore?.toString() ?: NULL,
            evidence.communitySignals.toString(),
            enc(evidence.categoryCodes.joinToString(CATEGORY_SEPARATOR)),
            nullable(evidence.sourceLabel),
            evidence.signedPackageVerified.toString()
        )
    }

    private fun decodeEvidence(fields: List<String>): IntelligenceEvidence {
        require(fields.isNotEmpty())
        return when (fields[0]) {
            "CONTACT" -> {
                require(fields.size == 5)
                IntelligenceEvidence.ContactIdentity(
                    contactState = IntelligenceEvidence.ContactState.valueOf(fields[1]),
                    displayLabel = decodeNullable(fields[2]),
                    organisationLabel = decodeNullable(fields[3]),
                    contactAlias = decodeNullable(fields[4])
                )
            }
            "CALL" -> {
                require(fields.size == 5)
                IntelligenceEvidence.CallDecision(
                    action = IntelligenceEvidence.CallAction.valueOf(fields[1]),
                    ruleSource = dec(fields[2]),
                    reasonCode = dec(fields[3]),
                    networkVerification = IntelligenceEvidence.NetworkVerification.valueOf(fields[4])
                )
            }
            "SMS" -> {
                require(fields.size == 5)
                IntelligenceEvidence.SmsSecurity(
                    direction = IntelligenceEvidence.Direction.valueOf(fields[1]),
                    transportState = dec(fields[2]),
                    categoryCode = decodeNullable(fields[3]),
                    analysisOutcome = decodeNullable(fields[4])
                )
            }
            "MMS" -> {
                require(fields.size == 5)
                IntelligenceEvidence.MmsSecurity(
                    direction = IntelligenceEvidence.Direction.valueOf(fields[1]),
                    transportState = dec(fields[2]),
                    categoryCode = decodeNullable(fields[3]),
                    analysisOutcome = decodeNullable(fields[4])
                )
            }
            "REPUTATION" -> {
                require(fields.size == 6)
                IntelligenceEvidence.Reputation(
                    riskScore = fields[1].let { if (it == NULL) null else it.toInt() },
                    communitySignals = fields[2].toInt(),
                    categoryCodes = dec(fields[3]).let { decoded ->
                        if (decoded.isEmpty()) emptyList() else decoded.split(CATEGORY_SEPARATOR)
                    },
                    sourceLabel = decodeNullable(fields[4]),
                    signedPackageVerified = fields[5].toBooleanStrict()
                )
            }
            else -> error("Unsupported evidence type")
        }
    }

    private fun nullable(value: String?): String = value?.let(::enc) ?: NULL
    private fun decodeNullable(value: String): String? = if (value == NULL) null else dec(value)
    private fun enc(value: String): String = ENCODER.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun dec(value: String): String = String(DECODER.decode(value), StandardCharsets.UTF_8)

    companion object {
        const val FILE_NAME = "sentinel-intelligence-v1.store"
        private const val SCHEMA_HEADER = "schema=1"
        private const val NULL = "-"
        private const val SEPARATOR = "|"
        private const val CATEGORY_SEPARATOR = "\u001f"
        private const val BASE_FIELD_COUNT = 13
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }
}
