package com.sentinel.quantum.security

/**
 * Vérification locale de numéros contre une petite liste de préfixes connus.
 * Cette classe n'intercepte pas les appels et ne consulte aucune base distante.
 */
class PhoneMonitor(private val logger: LocalLogger) {

    private val knownSpamPrefixes = listOf(
        "+1900",
        "001900",
        "+338",
        "00338",
        "0899",
        "0897"
    )

    fun checkNumber(phoneNumber: String): SpamCheckResult {
        val normalized = phoneNumber.filter { it.isDigit() || it == '+' }
        logger.log(LocalLogger.LogLevel.INFO, "PhoneMonitor", "Vérification numéro: ${normalized.take(4)}***")

        val digitCount = normalized.count(Char::isDigit)
        val isKnownPrefix = knownSpamPrefixes.any { prefix ->
            normalized.startsWith(prefix) || normalized.startsWith(prefix.replace("+", "00"))
        }
        val isMalformed = digitCount !in 7..15

        val riskLevel = when {
            isKnownPrefix -> RiskLevel.HIGH
            isMalformed -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val reason = when {
            isKnownPrefix -> "Préfixe présent dans la liste locale de numéros à vigilance élevée"
            isMalformed -> "Format de numéro inhabituel (7 à 15 chiffres attendus)"
            else -> "Aucun indicateur correspondant à la liste locale"
        }

        logger.log(
            LocalLogger.LogLevel.SECURITY,
            "PhoneMonitor",
            "Résultat vérification: $riskLevel - $reason"
        )

        return SpamCheckResult(
            phoneNumber = normalized,
            riskLevel = riskLevel,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
    }

    fun getStats(): MonitorStats = MonitorStats(
        totalChecks = 0,
        spamDetected = 0,
        lastCheck = null
    )

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class SpamCheckResult(
        val phoneNumber: String,
        val riskLevel: RiskLevel,
        val reason: String,
        val timestamp: Long
    )

    data class MonitorStats(
        val totalChecks: Int,
        val spamDetected: Int,
        val lastCheck: Long?
    )
}
