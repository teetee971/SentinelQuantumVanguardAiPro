package com.sentinel.quantum.security

/**
 * Acceptance rules for published numbering catalogs.
 *
 * A catalog may identify an allocation. It must never carry a block or silence instruction.
 * Remote spam prefixes stay on the signed vigilance feed and are not accepted here.
 */
internal object PublishedCatalogPolicy {
    const val MAX_ARCEP_CHARS = 8_000_000
    const val MAX_RTR_CHARS = 4_000_000
    const val MAX_AGE_MS = 8L * 24 * 60 * 60 * 1000

    enum class Kind { ARCEP, RTR }

    enum class SignedFeedPlan {
        NOT_PROVISIONED,
        USER_DISABLED,
        RUN
    }

    data class Decision(
        val accepted: Boolean,
        val reason: String,
        val generatedAt: String? = null
    )

    fun evaluate(kind: Kind, document: String): Decision {
        val max = if (kind == Kind.ARCEP) MAX_ARCEP_CHARS else MAX_RTR_CHARS
        if (document.isEmpty() || document.length > max) {
            return Decision(false, "CATALOG_SIZE")
        }
        if (document.contains("silence_prefix=") || document.contains("\"silence_prefix\"") ||
            document.contains("\"blockAction\"")
        ) {
            return Decision(false, "CATALOG_ACTION_FORBIDDEN")
        }
        val schema = SCHEMA.find(document)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val generatedAt = GENERATED.find(document)?.groupValues?.getOrNull(1)
        return when (kind) {
            Kind.ARCEP -> when {
                schema != 2 -> Decision(false, "CATALOG_SCHEMA")
                !document.contains("\"entries\"") -> Decision(false, "CATALOG_ENTRIES")
                else -> Decision(true, "CATALOG_ACCEPTED", generatedAt)
            }
            Kind.RTR -> when {
                schema != 1 -> Decision(false, "CATALOG_SCHEMA")
                !document.contains("\"country\":\"AT\"") && !document.contains("\"country\": \"AT\"") ->
                    Decision(false, "CATALOG_COUNTRY")
                !document.contains("\"groups\"") -> Decision(false, "CATALOG_GROUPS")
                else -> Decision(true, "CATALOG_ACCEPTED", generatedAt)
            }
        }
    }

    fun fresh(fetchedAtMs: Long, now: Long): Boolean =
        fetchedAtMs in 0..now && now - fetchedAtMs <= MAX_AGE_MS

    fun signedSpamFeedPlan(productionFeedReady: Boolean, userEnabled: Boolean): SignedFeedPlan = when {
        !productionFeedReady -> SignedFeedPlan.NOT_PROVISIONED
        !userEnabled -> SignedFeedPlan.USER_DISABLED
        else -> SignedFeedPlan.RUN
    }

    private val SCHEMA = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)")
    private val GENERATED = Regex("\"generatedAt\"\\s*:\\s*\"([^\"]{1,40})\"")
}
