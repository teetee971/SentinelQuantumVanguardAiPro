package com.sentinel.quantum.security

import android.content.Context

/**
 * ExplainableAI - Explications locales des décisions
 * Fournit des explications textuelles sans cloud, entièrement local
 */
class ExplainableAI(private val context: Context, private val logger: LocalLogger) {
    
    /**
     * Génère une explication pour un résultat d'audit de sécurité
     */
    fun explainSecurityAudit(result: SecurityAudit.SecurityAuditResult): Explanation {
        logger.log(LocalLogger.LogLevel.INFO, "ExplainableAI", "Génération explication audit sécurité")
        
        val summary = buildString {
            append("Audit de sécurité effectué le ${formatTimestamp(result.timestamp)}.\n\n")
            
            append("Permissions:\n")
            append("- État téléphone: ${if (result.permissions.phoneStateGranted) "✓ Accordée" else "✗ Non accordée"}\n")
            append("- Journal appels: ${if (result.permissions.callLogGranted) "✓ Accordée" else "✗ Non accordée"}\n\n")
            
            if (result.warnings.isNotEmpty()) {
                append("Avertissements (${result.warnings.size}):\n")
                result.warnings.forEach { warning ->
                    append("⚠ $warning\n")
                }
            } else {
                append("✓ Aucun avertissement\n")
            }
        }
        
        val recommendations = generateSecurityRecommendations(result)
        
        return Explanation(
            title = "Audit de Sécurité",
            summary = summary,
            recommendations = recommendations,
            confidence = if (result.warnings.isEmpty()) 1.0f else 0.7f
        )
    }
    
    /**
     * Génère une explication pour un résultat de vérification SPAM
     */
    fun explainSpamCheck(result: PhoneMonitor.SpamCheckResult): Explanation {
        logger.log(LocalLogger.LogLevel.INFO, "ExplainableAI", "Génération explication vérification SPAM")
        
        val summary = buildString {
            append("Vérification du numéro effectuée le ${formatTimestamp(result.timestamp)}.\n\n")
            append("Numéro: ${result.phoneNumber}\n")
            append("Niveau de risque: ${getRiskEmoji(result.riskLevel)} ${result.riskLevel.name}\n\n")
            append("Raison: ${result.reason}\n")
        }
        
        val recommendations = generateSpamRecommendations(result)
        
        return Explanation(
            title = "Vérification Numéro",
            summary = summary,
            recommendations = recommendations,
            confidence = when (result.riskLevel) {
                PhoneMonitor.RiskLevel.HIGH -> 0.9f
                PhoneMonitor.RiskLevel.MEDIUM -> 0.6f
                PhoneMonitor.RiskLevel.LOW -> 0.95f
            }
        )
    }
    
    private fun generateSecurityRecommendations(result: SecurityAudit.SecurityAuditResult): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!result.permissions.phoneStateGranted) {
            recommendations.add("Accorder la permission READ_PHONE_STATE pour activer les fonctionnalités de sécurité téléphone")
        }
        
        if (!result.permissions.callLogGranted) {
            recommendations.add("Accorder la permission READ_CALL_LOG pour améliorer la détection de SPAM")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Configuration optimale - continuez à surveiller les mises à jour de sécurité")
        }
        
        return recommendations
    }
    
    private fun generateSpamRecommendations(result: PhoneMonitor.SpamCheckResult): List<String> {
        return when (result.riskLevel) {
            PhoneMonitor.RiskLevel.HIGH -> listOf(
                "⚠ Ne pas répondre à ce numéro",
                "Vérifier s'il s'agit d'un numéro surtaxé",
                "Bloquer le numéro si nécessaire"
            )
            PhoneMonitor.RiskLevel.MEDIUM -> listOf(
                "Vérifier l'identité de l'appelant avant de répondre",
                "Consulter des bases de données publiques de SPAM"
            )
            PhoneMonitor.RiskLevel.LOW -> listOf(
                "Aucune action particulière requise",
                "Numéro semble légitime"
            )
        }
    }
    
    private fun getRiskEmoji(level: PhoneMonitor.RiskLevel): String {
        return when (level) {
            PhoneMonitor.RiskLevel.HIGH -> "🔴"
            PhoneMonitor.RiskLevel.MEDIUM -> "🟡"
            PhoneMonitor.RiskLevel.LOW -> "🟢"
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(date)
    }
    
    data class Explanation(
        val title: String,
        val summary: String,
        val recommendations: List<String>,
        val confidence: Float // 0.0 to 1.0
    )
}
