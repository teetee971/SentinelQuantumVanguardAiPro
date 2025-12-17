package com.sentinel.quantum.security

import android.content.Context

/**
 * PhoneMonitor - Détection de SPAM via sources publiques
 * Analyse locale sans interception ni écoute
 */
class PhoneMonitor(private val context: Context, private val logger: LocalLogger) {
    
    // Liste locale de préfixes connus pour SPAM (sources publiques)
    private val knownSpamPrefixes = listOf(
        "+1-900", // Services payants US
        "+33-8", // Numéros surtaxés FR
        "0899", // Services payants FR
        "0897" // Services payants FR
    )
    
    /**
     * Vérifie si un numéro correspond à des patterns de SPAM connus
     */
    fun checkNumber(phoneNumber: String): SpamCheckResult {
        logger.log(LocalLogger.LogLevel.INFO, "PhoneMonitor", "Vérification numéro: ${phoneNumber.take(4)}***")
        
        val isKnownSpamPrefix = knownSpamPrefixes.any { prefix ->
            phoneNumber.startsWith(prefix) || phoneNumber.replace("+", "00").startsWith(prefix.replace("+", "00"))
        }
        
        val riskLevel = when {
            isKnownSpamPrefix -> RiskLevel.HIGH
            phoneNumber.startsWith("0") && phoneNumber.length < 10 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        
        val reason = when {
            isKnownSpamPrefix -> "Préfixe connu pour services payants/SPAM"
            phoneNumber.length < 10 -> "Numéro court suspect"
            else -> "Aucun indicateur de risque détecté"
        }
        
        logger.log(
            LocalLogger.LogLevel.SECURITY,
            "PhoneMonitor",
            "Résultat vérification: $riskLevel - $reason"
        )
        
        return SpamCheckResult(
            phoneNumber = phoneNumber,
            riskLevel = riskLevel,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Récupère les statistiques des vérifications
     */
    fun getStats(): MonitorStats {
        // Dans une vraie implémentation, on stockerait l'historique
        return MonitorStats(
            totalChecks = 0,
            spamDetected = 0,
            lastCheck = null
        )
    }
    
    enum class RiskLevel {
        LOW, MEDIUM, HIGH
    }
    
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
