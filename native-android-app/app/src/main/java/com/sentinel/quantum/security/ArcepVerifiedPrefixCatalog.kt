package com.sentinel.quantum.security

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
}
