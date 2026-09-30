package com.sentinel.quantum.security

/**
 * Local, heuristic organization of SMS conversation previews.
 *
 * The classifier only looks at the latest preview already read from Android's SMS provider.
 * It does not perform network lookups and its labels are navigation aids, not security verdicts.
 */
object SmsThreadOrganizer {
    enum class Category(val labelFr: String) {
        ALL("Tous"),
        CODE("Codes"),
        TRANSACTION("Transactions"),
        DELIVERY("Livraison"),
        OTHER("Autres")
    }

    fun classify(body: String): Category {
        val text = body.take(MAX_PREVIEW_CHARS)
        if (SmsOtpPrivacy.inspect(text).containsOtp) return Category.CODE
        if (DELIVERY_PATTERNS.any { it.containsMatchIn(text) }) return Category.DELIVERY
        if (TRANSACTION_PATTERNS.any { it.containsMatchIn(text) }) return Category.TRANSACTION
        return Category.OTHER
    }

    fun matches(category: Category, body: String): Boolean =
        category == Category.ALL || classify(body) == category

    private const val MAX_PREVIEW_CHARS = 1_000

    private val DELIVERY_PATTERNS = listOf(
        "colis", "livraison", "livré", "livree", "livrée", "expédié", "expedie",
        "transporteur", "point relais", "retrait", "tracking", "suivi de commande"
    ).map { Regex("(?i)\\b${Regex.escape(it)}\\b") }

    private val TRANSACTION_PATTERNS = listOf(
        "paiement", "payé", "paye", "achat", "carte", "débit", "debit", "crédit",
        "credit", "virement", "remboursement", "facture", "solde", "banque",
        "transaction", "prélèvement", "prelevement", "paypal"
    ).map { Regex("(?i)\\b${Regex.escape(it)}\\b") }
}
