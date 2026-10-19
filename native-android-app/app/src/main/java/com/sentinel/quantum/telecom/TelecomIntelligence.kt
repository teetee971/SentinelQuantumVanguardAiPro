package com.sentinel.quantum.telecom

import java.net.URI
import java.security.MessageDigest

enum class TelecomRegion {
    INTERNATIONAL,
    EUROPE,
    NORTH_AMERICA,
    CARIBBEAN,
    LATIN_AMERICA,
    AFRICA,
    MIDDLE_EAST,
    ASIA,
    OCEANIA
}

enum class TelecomSourceCategory {
    REGULATOR_DIRECTORY,
    REGULATOR_NETWORK,
    NUMBERING_REGULATOR
}

enum class ConnectorStatus {
    REFERENCE_ONLY,
    PROBE_READY,
    SYNCED
}

enum class LicenseReview {
    PENDING,
    VERIFIED,
    UNKNOWN
}

enum class EnforcementEligibility {
    NONE
}

data class TelecomSourceDescriptor(
    val id: String,
    val jurisdiction: String,
    val region: TelecomRegion,
    val publisher: String,
    val officialUrl: String,
    val category: TelecomSourceCategory,
    val connectorStatus: ConnectorStatus = ConnectorStatus.REFERENCE_ONLY,
    val licenseReview: LicenseReview = LicenseReview.PENDING,
    val enforcementEligibility: EnforcementEligibility = EnforcementEligibility.NONE
)

object GlobalTelecomSourceRegistry {
    /** Reference metadata only; no item in this list can authorize a call disposition. */
    val sources: List<TelecomSourceDescriptor> = listOf(
        source("ITU", "INT", TelecomRegion.INTERNATIONAL, "International Telecommunication Union", "https://www.itu.int", TelecomSourceCategory.REGULATOR_DIRECTORY),
        source("BEREC", "EU", TelecomRegion.EUROPE, "Body of European Regulators for Electronic Communications", "https://www.berec.europa.eu", TelecomSourceCategory.REGULATOR_NETWORK),
        source("ARCEP", "FR", TelecomRegion.EUROPE, "ARCEP", "https://www.arcep.fr", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("OFCOM", "GB", TelecomRegion.EUROPE, "Ofcom", "https://www.ofcom.org.uk", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("FCC", "US", TelecomRegion.NORTH_AMERICA, "Federal Communications Commission", "https://www.fcc.gov", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("CRTC", "CA", TelecomRegion.NORTH_AMERICA, "Canadian Radio-television and Telecommunications Commission", "https://crtc.gc.ca", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("ANATEL", "BR", TelecomRegion.LATIN_AMERICA, "Anatel", "https://www.gov.br/anatel", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("ACMA", "AU", TelecomRegion.OCEANIA, "Australian Communications and Media Authority", "https://www.acma.gov.au", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("TRAI", "IN", TelecomRegion.ASIA, "Telecom Regulatory Authority of India", "https://www.trai.gov.in", TelecomSourceCategory.NUMBERING_REGULATOR),
        source("IMDA", "SG", TelecomRegion.ASIA, "Infocomm Media Development Authority", "https://www.imda.gov.sg", TelecomSourceCategory.NUMBERING_REGULATOR)
    )

    fun find(id: String): TelecomSourceDescriptor? = sources.firstOrNull { it.id == id }

    private fun source(
        id: String,
        jurisdiction: String,
        region: TelecomRegion,
        publisher: String,
        officialUrl: String,
        category: TelecomSourceCategory
    ) = TelecomSourceDescriptor(id, jurisdiction, region, publisher, officialUrl, category)
}

data class TelecomValidationResult(
    val isValid: Boolean,
    val reasonCode: String
)

object TelecomSourceValidator {
    fun validate(descriptor: TelecomSourceDescriptor): TelecomValidationResult {
        if (!descriptor.id.matches(ID_PATTERN)) return invalid("SOURCE_ID_INVALID")
        if (!descriptor.jurisdiction.matches(JURISDICTION_PATTERN)) {
            return invalid("JURISDICTION_INVALID")
        }
        val uri = runCatching { URI(descriptor.officialUrl) }.getOrNull()
            ?: return invalid("OFFICIAL_URL_INVALID")
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            return invalid("OFFICIAL_URL_NOT_HTTPS")
        }
        if (descriptor.enforcementEligibility != EnforcementEligibility.NONE) {
            return invalid("ENFORCEMENT_AUTHORITY_FORBIDDEN")
        }
        return TelecomValidationResult(true, "VALID")
    }

    private fun invalid(reason: String) = TelecomValidationResult(false, reason)

    private val ID_PATTERN = Regex("[A-Z0-9_-]{2,32}")
    private val JURISDICTION_PATTERN = Regex("[A-Z0-9-]{2,12}")
}

enum class TelecomObservation {
    REACHABLE_METADATA_ONLY,
    VALIDATED_DATASET,
    STALE,
    UNAVAILABLE,
    INVALID
}

enum class TelecomSyncOutcome {
    REACHABLE_METADATA_ONLY,
    VALIDATED_DATASET,
    UNAVAILABLE,
    INVALID_SOURCE
}

data class TelecomNumberingRecord(
    val prefix: String,
    val jurisdiction: String,
    val numberType: String
)

data class TelecomEvidenceRecord(
    val sourceId: String,
    val observation: TelecomObservation,
    val fetchedAtMs: Long,
    val expiresAtMs: Long,
    val contentSha256: String?,
    val contentType: String?,
    val payloadBytes: Int,
    val numberingRecords: List<TelecomNumberingRecord> = emptyList(),
    val errorCode: String? = null
)

data class TransportResponse(
    val statusCode: Int,
    val contentType: String?,
    val body: ByteArray
)

fun interface OfficialSourceTransport {
    fun fetch(officialUrl: String, maxBytes: Int): TransportResponse
}

data class TelecomFetchResult(
    val outcome: TelecomSyncOutcome,
    val fetchedAtMs: Long,
    val contentSha256: String? = null,
    val contentType: String? = null,
    val payloadBytes: Int = 0,
    val numberingRecords: List<TelecomNumberingRecord> = emptyList(),
    val errorCode: String? = null
)

interface TelecomSourceAdapter {
    val descriptor: TelecomSourceDescriptor
    fun fetch(nowMs: Long): TelecomFetchResult
}

/**
 * First real connector: performs a bounded HTTPS read against an official site and records only
 * reachability/provenance. It intentionally does not mistake arbitrary HTML for a numbering
 * dataset. A source-specific parser must be added and licensed before records can be emitted.
 */
class OfficialSourceProbeAdapter(
    override val descriptor: TelecomSourceDescriptor,
    private val transport: OfficialSourceTransport,
    private val maxBytes: Int = DEFAULT_MAX_BYTES
) : TelecomSourceAdapter {
    init {
        require(maxBytes in 1..MAX_ALLOWED_BYTES)
    }

    override fun fetch(nowMs: Long): TelecomFetchResult {
        require(nowMs >= 0L)
        val validation = TelecomSourceValidator.validate(descriptor)
        if (!validation.isValid) {
            return TelecomFetchResult(
                outcome = TelecomSyncOutcome.INVALID_SOURCE,
                fetchedAtMs = nowMs,
                errorCode = validation.reasonCode
            )
        }
        return runCatching {
            val response = transport.fetch(descriptor.officialUrl, maxBytes)
            if (response.body.size > maxBytes) {
                return@runCatching TelecomFetchResult(
                    outcome = TelecomSyncOutcome.UNAVAILABLE,
                    fetchedAtMs = nowMs,
                    errorCode = "PAYLOAD_TOO_LARGE"
                )
            }
            if (response.statusCode !in 200..299) {
                return@runCatching TelecomFetchResult(
                    outcome = TelecomSyncOutcome.UNAVAILABLE,
                    fetchedAtMs = nowMs,
                    errorCode = "HTTP_${response.statusCode}"
                )
            }
            TelecomFetchResult(
                outcome = TelecomSyncOutcome.REACHABLE_METADATA_ONLY,
                fetchedAtMs = nowMs,
                contentSha256 = sha256(response.body),
                contentType = response.contentType?.take(MAX_CONTENT_TYPE_LENGTH),
                payloadBytes = response.body.size
            )
        }.getOrElse {
            TelecomFetchResult(
                outcome = TelecomSyncOutcome.UNAVAILABLE,
                fetchedAtMs = nowMs,
                errorCode = "TRANSPORT_FAILURE"
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 512 * 1024
        const val MAX_ALLOWED_BYTES = 2 * 1024 * 1024
        const val MAX_CONTENT_TYPE_LENGTH = 128

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class TelecomSourceSyncCoordinator(
    private val store: TelecomEvidenceStore,
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    init {
        require(ttlMs > 0L)
    }

    fun sync(adapter: TelecomSourceAdapter, nowMs: Long): TelecomSyncResult {
        require(nowMs >= 0L)
        val result = adapter.fetch(nowMs)
        if (result.outcome == TelecomSyncOutcome.INVALID_SOURCE ||
            result.outcome == TelecomSyncOutcome.UNAVAILABLE
        ) {
            return TelecomSyncResult(result.outcome, null, result.errorCode)
        }
        val record = TelecomEvidenceRecord(
            sourceId = adapter.descriptor.id,
            observation = when (result.outcome) {
                TelecomSyncOutcome.REACHABLE_METADATA_ONLY -> TelecomObservation.REACHABLE_METADATA_ONLY
                TelecomSyncOutcome.VALIDATED_DATASET -> TelecomObservation.VALIDATED_DATASET
                TelecomSyncOutcome.UNAVAILABLE -> TelecomObservation.UNAVAILABLE
                TelecomSyncOutcome.INVALID_SOURCE -> TelecomObservation.INVALID
            },
            fetchedAtMs = result.fetchedAtMs,
            expiresAtMs = safeExpiry(nowMs, ttlMs),
            contentSha256 = result.contentSha256,
            contentType = result.contentType,
            payloadBytes = result.payloadBytes,
            numberingRecords = result.numberingRecords.take(MAX_NUMBERING_RECORDS)
        )
        store.put(record)
        return TelecomSyncResult(result.outcome, record, null)
    }

    private fun safeExpiry(nowMs: Long, ttl: Long): Long =
        if (Long.MAX_VALUE - nowMs < ttl) Long.MAX_VALUE else nowMs + ttl

    companion object {
        const val DEFAULT_TTL_MS = 24L * 60L * 60L * 1000L
        const val MAX_NUMBERING_RECORDS = 10_000
    }
}

data class TelecomSyncResult(
    val outcome: TelecomSyncOutcome,
    val record: TelecomEvidenceRecord?,
    val errorCode: String?
)

interface TelecomEvidenceStore {
    fun put(record: TelecomEvidenceRecord)
    fun get(sourceId: String, nowMs: Long): TelecomEvidenceRecord?
    fun lastKnown(sourceId: String): TelecomEvidenceRecord?
    fun all(): List<TelecomEvidenceRecord>
}

class InMemoryTelecomEvidenceStore(
    private val maxEntries: Int,
    private val defaultTtlMs: Long = TelecomSourceSyncCoordinator.DEFAULT_TTL_MS
) : TelecomEvidenceStore {
    private val values = linkedMapOf<String, TelecomEvidenceRecord>()

    init {
        require(maxEntries > 0)
        require(defaultTtlMs > 0L)
    }

    @Synchronized
    override fun put(record: TelecomEvidenceRecord) {
        values.remove(record.sourceId)
        values[record.sourceId] = record
        while (values.size > maxEntries) values.remove(values.keys.first())
    }

    @Synchronized
    override fun get(sourceId: String, nowMs: Long): TelecomEvidenceRecord? {
        require(nowMs >= 0L)
        val record = values[sourceId] ?: return null
        if (nowMs >= record.expiresAtMs) {
            values[sourceId] = record.copy(observation = TelecomObservation.STALE)
            return null
        }
        return record
    }

    @Synchronized
    override fun lastKnown(sourceId: String): TelecomEvidenceRecord? = values[sourceId]

    @Synchronized
    override fun all(): List<TelecomEvidenceRecord> = values.values.toList()
}

data class GlobalTelecomDashboard(
    val referencedJurisdictions: Int,
    val indexedRegulators: Int,
    val verifiedSources: Int,
    val pendingSources: Int,
    val activeConnectors: Int,
    val lastUpdatedAtMs: Long?,
    val offlineNumberingAvailable: Boolean
)

/** Pure read model consumed by the D.1 screen; it never fabricates remote counts. */
class GlobalTelecomIntelligenceRepository(
    private val registry: List<TelecomSourceDescriptor> = GlobalTelecomSourceRegistry.sources,
    private val store: TelecomEvidenceStore = InMemoryTelecomEvidenceStore(maxEntries = 32)
) {
    fun probeOfficialSource(
        sourceId: String,
        transport: OfficialSourceTransport,
        nowMs: Long
    ): TelecomSyncResult? {
        val descriptor = registry.firstOrNull { it.id == sourceId } ?: return null
        return TelecomSourceSyncCoordinator(store).sync(
            OfficialSourceProbeAdapter(descriptor, transport),
            nowMs
        )
    }

    fun dashboard(nowMs: Long): GlobalTelecomDashboard {
        val fresh = registry.mapNotNull { source -> store.get(source.id, nowMs) }
        return GlobalTelecomDashboard(
            referencedJurisdictions = registry.map { it.jurisdiction }.distinct().size,
            indexedRegulators = registry.count { it.category != TelecomSourceCategory.REGULATOR_NETWORK },
            verifiedSources = fresh.count {
                it.observation == TelecomObservation.REACHABLE_METADATA_ONLY ||
                    it.observation == TelecomObservation.VALIDATED_DATASET
            },
            pendingSources = registry.count { it.connectorStatus == ConnectorStatus.REFERENCE_ONLY },
            activeConnectors = fresh.count { it.observation == TelecomObservation.VALIDATED_DATASET },
            lastUpdatedAtMs = fresh.maxOfOrNull { it.fetchedAtMs },
            offlineNumberingAvailable = fresh.any {
                it.observation == TelecomObservation.VALIDATED_DATASET &&
                    it.numberingRecords.isNotEmpty()
            }
        )
    }

    fun sources(nowMs: Long): List<TelecomSourceView> = registry.map { descriptor ->
        val evidence = store.get(descriptor.id, nowMs)
        val lastKnown = evidence ?: store.lastKnown(descriptor.id)
        TelecomSourceView(
            descriptor = descriptor,
            observation = evidence?.observation ?: lastKnown?.observation ?: TelecomObservation.INVALID,
            fetchedAtMs = lastKnown?.fetchedAtMs,
            contentSha256 = lastKnown?.contentSha256
        )
    }
}

data class TelecomSourceView(
    val descriptor: TelecomSourceDescriptor,
    val observation: TelecomObservation,
    val fetchedAtMs: Long?,
    val contentSha256: String?
)

