package com.sentinel.quantum.security

/** Local-only indicator screening. It does not intercept or block calls. */
class PhoneMonitor(
    private val audit: (LocalLogger.LogLevel, String, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(logger: LocalLogger) : this(logger::log)

    private val knownSpamPrefixes = setOf("+1900", "001900", "+338", "00338", "0899", "0897")
    private val statsLock = Any()
    private var totalChecks = 0
    private var elevatedRiskChecks = 0
    private var lastCheck: Long? = null

    fun checkNumber(phoneNumber: String): SpamCheckResult {
        val input = phoneNumber.trim().take(MAX_INPUT_LENGTH)
        val digitCount = input.count(Char::isDigit)
        val validCharacters = input.all { it.isDigit() || it in ALLOWED_FORMATTING }
        val validPlus = input.count { it == '+' } <= 1 && (!input.contains('+') || input.startsWith('+'))
        val normalized = buildString {
            if (input.startsWith('+')) append('+')
            input.filterTo(this, Char::isDigit)
        }
        val malformed = phoneNumber.length > MAX_INPUT_LENGTH || !validCharacters || !validPlus ||
            digitCount !in MIN_DIGITS..MAX_DIGITS
        val knownPrefix = !malformed && knownSpamPrefixes.any {
            normalized.startsWith(it) || normalized.startsWith(it.replace("+", "00"))
        }
        val risk = when {
            malformed -> RiskLevel.MEDIUM
            knownPrefix -> RiskLevel.HIGH
            else -> RiskLevel.LOW
        }
        val reason = when {
            malformed -> "Format de numéro invalide ou inhabituel"
            knownPrefix -> "Préfixe présent dans la liste locale de vigilance"
            else -> "Aucun indicateur correspondant aux règles locales"
        }
        val checkedAt = now()
        synchronized(statsLock) {
            totalChecks += 1
            if (risk != RiskLevel.LOW) elevatedRiskChecks += 1
            lastCheck = checkedAt
        }
        val masked = if (normalized.length >= 4) "${normalized.take(4)}***" else "***"
        audit(LocalLogger.LogLevel.INFO, TAG, "Vérification locale du numéro: $masked")
        audit(LocalLogger.LogLevel.SECURITY, TAG, "Résultat indicatif: $risk")
        return SpamCheckResult(normalized, risk, reason, checkedAt, !malformed)
    }

    fun getStats(): MonitorStats = synchronized(statsLock) {
        MonitorStats(totalChecks, elevatedRiskChecks, lastCheck)
    }

    enum class RiskLevel { LOW, MEDIUM, HIGH }
    data class SpamCheckResult(
        val phoneNumber: String,
        val riskLevel: RiskLevel,
        val reason: String,
        val timestamp: Long,
        val validFormat: Boolean
    )
    data class MonitorStats(val totalChecks: Int, val elevatedRiskChecks: Int, val lastCheck: Long?)

    private companion object {
        const val TAG = "PhoneMonitor"
        const val MAX_INPUT_LENGTH = 64
        const val MIN_DIGITS = 7
        const val MAX_DIGITS = 15
        val ALLOWED_FORMATTING = setOf('+', ' ', '-', '(', ')', '.')
    }
}
