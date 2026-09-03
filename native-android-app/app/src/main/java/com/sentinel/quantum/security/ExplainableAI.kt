package com.sentinel.quantum.security

/**
 * Génère des explications locales déterministes à partir des résultats fournis
 * par les contrôles de l'application. Aucun service distant n'est utilisé.
 */
class ExplainableAI(private val logger: LocalLogger) {

    fun explainSecurityAudit(result: SecurityAudit.SecurityAuditResult): Explanation {
        logger.log(LocalLogger.LogLevel.INFO, "ExplainableAI", "Génération explication audit sécurité")

        val summary = buildString {
            append("Audit de sécurité effectué le ${formatTimestamp(result.timestamp)}.\n\n")
            append("Permissions déclarées par l'application :\n")
            result.permissions.forEach { permission ->
                append("- ${permission.name}: ${if (permission.granted) "✓ Accordée" else "✗ Non accordée"}\n")
            }
            append("\n")
            if (result.warnings.isNotEmpty()) {
                append("Avertissements (${result.warnings.size}):\n")
                result.warnings.forEach { warning -> append("- $warning\n") }
            } else {
                append("Aucun avertissement détecté par les contrôles exécutés.\n")
            }
        }

        return Explanation(
            title = "Audit de sécurité",
            summary = summary,
            recommendations = if (result.warnings.isEmpty()) {
                listOf("Conserver les permissions minimales et maintenir l'application à jour.")
            } else {
                listOf("Examiner les avertissements affichés avant toute décision de sécurité.")
            }
        )
    }

    fun explainSpamCheck(result: PhoneMonitor.SpamCheckResult): Explanation {
        logger.log(LocalLogger.LogLevel.INFO, "ExplainableAI", "Génération explication vérification numéro")

        val summary = buildString {
            append("Vérification locale effectuée le ${formatTimestamp(result.timestamp)}.\n\n")
            append("Niveau indicatif : ${result.riskLevel.name}\n")
            append("Motif : ${result.reason}\n")
            append("Cette classification repose uniquement sur les règles locales actuellement embarquées.")
        }

        val recommendations = when (result.riskLevel) {
            PhoneMonitor.RiskLevel.HIGH -> listOf(
                "Ne pas considérer le résultat comme une preuve de fraude.",
                "Vérifier l'identité de l'appelant par un canal indépendant avant toute action sensible."
            )
            PhoneMonitor.RiskLevel.MEDIUM -> listOf(
                "Vérifier le numéro avec une source indépendante si nécessaire."
            )
            PhoneMonitor.RiskLevel.LOW -> listOf(
                "Aucun indicateur ne correspond aux règles locales ; cela ne garantit pas que le numéro soit légitime."
            )
        }

        return Explanation(
            title = "Vérification du numéro",
            summary = summary,
            recommendations = recommendations
        )
    }

    private fun formatTimestamp(timestamp: Long): String =
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

    data class Explanation(
        val title: String,
        val summary: String,
        val recommendations: List<String>
    )
}
