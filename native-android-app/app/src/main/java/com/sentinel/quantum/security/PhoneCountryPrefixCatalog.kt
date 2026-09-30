package com.sentinel.quantum.security

/**
 * Curated quick choices for broad user-owned prefix blocking.
 *
 * These labels are navigation aids only. A telephone prefix does not prove the caller's
 * physical location or identity and can be spoofed. Manual prefix entry remains available
 * for regions not listed here.
 */
object PhoneCountryPrefixCatalog {
    data class Entry(
        val label: String,
        val prefix: String
    )

    val frequentEntries: List<Entry> = listOf(
        Entry("France", "+33"),
        Entry("Guadeloupe · Saint-Barthélemy · Saint-Martin", "+590"),
        Entry("Guyane française", "+594"),
        Entry("Martinique", "+596"),
        Entry("La Réunion · Mayotte", "+262"),
        Entry("Belgique", "+32"),
        Entry("Suisse", "+41"),
        Entry("Allemagne", "+49"),
        Entry("Espagne", "+34"),
        Entry("Italie", "+39"),
        Entry("Portugal", "+351"),
        Entry("Royaume-Uni", "+44"),
        Entry("Pays-Bas", "+31"),
        Entry("Irlande", "+353"),
        Entry("Luxembourg", "+352"),
        Entry("Autriche", "+43"),
        Entry("Pologne", "+48"),
        Entry("Roumanie", "+40"),
        Entry("Maroc", "+212"),
        Entry("Algérie", "+213"),
        Entry("Tunisie", "+216"),
        Entry("Sénégal", "+221"),
        Entry("Côte d’Ivoire", "+225"),
        Entry("Brésil", "+55"),
        Entry("Mexique", "+52")
    )

    fun find(prefix: String): Entry? {
        val normalized = CallRuleEngine.normalizePrefix(prefix) ?: return null
        return frequentEntries.firstOrNull { it.prefix == normalized }
    }
}
