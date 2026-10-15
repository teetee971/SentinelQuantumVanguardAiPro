package com.sentinel.quantum.ui.screens

import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.ui.design.SentinelStatus

data class SmsThreatPresentation(
    val status: SentinelStatus,
    val label: String,
    val detail: String
) {
    companion object {
        fun from(analysis: SmsLinkAnalyzer.Analysis): SmsThreatPresentation {
            if (!analysis.accepted) {
                return SmsThreatPresentation(
                    status = SentinelStatus.UNKNOWN,
                    label = "ANALYSE NON DISPONIBLE",
                    detail = "Le message n’a pas produit de résultat exploitable."
                )
            }
            return when (analysis.riskLevel) {
                SmsLinkAnalyzer.RiskLevel.HIGH -> SmsThreatPresentation(
                    status = SentinelStatus.BAD,
                    label = "RISQUE ÉLEVÉ",
                    detail = "Plusieurs signaux locaux justifient de ne pas ouvrir les liens sans vérification."
                )
                SmsLinkAnalyzer.RiskLevel.MEDIUM -> SmsThreatPresentation(
                    status = SentinelStatus.WARN,
                    label = "SIGNAUX À VÉRIFIER",
                    detail = "Des indices locaux nécessitent une vérification avant interaction."
                )
                SmsLinkAnalyzer.RiskLevel.LOW -> SmsThreatPresentation(
                    status = SentinelStatus.UNKNOWN,
                    label = "AUCUN SIGNAL DÉTECTÉ",
                    detail = "Aucun signal connu n’a été détecté. Cela ne prouve pas que le message ou le lien est sûr."
                )
                SmsLinkAnalyzer.RiskLevel.UNKNOWN -> SmsThreatPresentation(
                    status = SentinelStatus.UNKNOWN,
                    label = "RÉSULTAT INCONNU",
                    detail = "Sentinel ne dispose pas d’assez d’éléments pour qualifier ce message."
                )
            }
        }
    }
}
