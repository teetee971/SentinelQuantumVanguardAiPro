package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationStatus
import com.sentinel.quantum.integration.IntegrationTruthRuntime

/**
 * Official ARCEP "numéros polyvalents vérifiés" roots, expressed as canonical E.164 prefixes.
 *
 * These ranges may be used by automated calling/messaging systems. Membership in a range is not
 * evidence of fraud, identity, or caller location. Blocking is therefore opt-in and user-owned.
 * Source model: ARCEP national numbering plan, version effective 1 January 2026.
 */
object ArcepVerifiedPrefixCatalog {
    data class Entry(
        val territory: String,
        val nationalRoot: String,
        val e164Prefix: String,
        val flag: String = ""
    )

    val entries: List<Entry> = listOf(
        Entry("France métropolitaine", "0162", "+33162", "🇫🇷"),
        Entry("France métropolitaine", "0163", "+33163", "🇫🇷"),
        Entry("France métropolitaine", "0270", "+33270", "🇫🇷"),
        Entry("France métropolitaine", "0271", "+33271", "🇫🇷"),
        Entry("France métropolitaine", "0377", "+33377", "🇫🇷"),
        Entry("France métropolitaine", "0378", "+33378", "🇫🇷"),
        Entry("France métropolitaine", "0424", "+33424", "🇫🇷"),
        Entry("France métropolitaine", "0425", "+33425", "🇫🇷"),
        Entry("France métropolitaine", "0568", "+33568", "🇫🇷"),
        Entry("France métropolitaine", "0569", "+33569", "🇫🇷"),
        Entry("France métropolitaine", "0948", "+33948", "🇫🇷"),
        Entry("France métropolitaine", "0949", "+33949", "🇫🇷"),
        Entry("Guadeloupe · Saint-Martin · Saint-Barthélemy", "05987", "+5905987", "🌐"),
        Entry("Guadeloupe · Saint-Martin · Saint-Barthélemy", "09475", "+5909475", "🌐"),
        Entry("Guyane", "05988", "+5945988", "🇬🇫"),
        Entry("Guyane", "09476", "+5949476", "🇬🇫"),
        Entry("Martinique", "05989", "+5965989", "🇲🇶"),
        Entry("Martinique", "09477", "+5969477", "🇲🇶"),
        Entry("La Réunion", "02688", "+2622688", "🌐"),
        Entry("Mayotte", "02689", "+2622689", "🌐"),
        Entry("Mayotte", "09478", "+2629478", "🌐"),
        Entry("La Réunion", "09479", "+2629479", "🌐")
    )

    val e164Prefixes: Set<String> = entries.mapTo(linkedSetOf()) { it.e164Prefix }

    fun findByE164Prefix(prefix: String): Entry? =
        entries.firstOrNull { it.e164Prefix == CallRuleEngine.normalizePrefix(prefix) }

    /**
     * Validates the bundled reviewed catalog before it is treated as runtime regulatory evidence.
     * This performs only bounded in-memory checks and never runs from the call-screening callback.
     */
    internal fun isValidRuntimeCatalog(candidateEntries: List<Entry> = entries): Boolean {
        if (candidateEntries.isEmpty() || candidateEntries.size > CallRuleEngine.MAX_PREFIX_RULES) {
            return false
        }
        val seenNationalRoots = hashSetOf<String>()
        val seenE164Prefixes = hashSetOf<String>()
        return candidateEntries.all { entry ->
            entry.territory.isNotBlank() &&
                entry.nationalRoot.startsWith("0") &&
                entry.nationalRoot.length in 4..8 &&
                entry.nationalRoot.all(Char::isDigit) &&
                entry.e164Prefix == CallRuleEngine.normalizePrefix(entry.e164Prefix) &&
                seenNationalRoots.add(entry.nationalRoot) &&
                seenE164Prefixes.add(entry.e164Prefix)
        }
    }

    /**
     * Records fresh evidence that the reviewed ARCEP source is usable in this process. The aggregate
     * regulatory capability intentionally remains PARTIAL because FSMA is a separate web runtime.
     */
    internal fun observeValidatedRuntimeUse(
        observedAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isValidRuntimeCatalog()) return false
        return IntegrationTruthRuntime.publish(
            capabilityId = IntegrationCapabilityId.REGULATORY_INTELLIGENCE,
            status = IntegrationStatus(
                IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
                IntegrationDiagnosticReason.PARTIAL_PUBLIC_SOURCES_ONLY
            ),
            observedAtMs = observedAtMs
        )
    }
}
