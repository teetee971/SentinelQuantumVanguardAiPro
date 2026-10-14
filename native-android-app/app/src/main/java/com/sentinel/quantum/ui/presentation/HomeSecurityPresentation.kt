package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus

enum class HomeModuleId {
    PHONE_CORE,
    VPN,
    NETWORK,
    COLLECTIVE_DEFENSE
}

enum class HomeCondition {
    HEALTHY,
    DEGRADED,
    DANGER
}

data class HomeComponentFacts(
    val implemented: Boolean,
    val customerAvailable: Boolean,
    val evidenceObserved: Boolean,
    val condition: HomeCondition = HomeCondition.HEALTHY,
    val locked: Boolean = false
)

data class HomeRuntimeFacts(
    val phoneCore: HomeComponentFacts,
    val vpn: HomeComponentFacts,
    val network: HomeComponentFacts,
    val collectiveDefense: HomeComponentFacts
)

data class HomeModulePresentation(
    val id: HomeModuleId,
    val title: String,
    val status: SentinelStatus,
    val detail: String
)

data class HomeSecurityUiState(
    val overallStatus: SentinelStatus,
    val headline: String,
    val summary: String,
    val priorityMessage: String,
    val modules: List<HomeModulePresentation>
)

object HomeSecurityPresentation {
    fun from(facts: HomeRuntimeFacts): HomeSecurityUiState {
        val modules = listOf(
            module(HomeModuleId.PHONE_CORE, "Phone Core", facts.phoneCore),
            module(HomeModuleId.VPN, "VPN", facts.vpn),
            module(HomeModuleId.NETWORK, "Réseau", facts.network),
            module(HomeModuleId.COLLECTIVE_DEFENSE, "Défense collective", facts.collectiveDefense)
        )
        val overall = overallStatus(modules)
        return HomeSecurityUiState(
            overallStatus = overall,
            headline = headline(overall),
            summary = summary(overall),
            priorityMessage = priorityMessage(modules),
            modules = modules
        )
    }

    private fun module(
        id: HomeModuleId,
        title: String,
        facts: HomeComponentFacts
    ): HomeModulePresentation {
        val status = when {
            facts.locked -> SentinelStatus.LOCKED
            !facts.implemented -> SentinelStatus.LIMITED
            !facts.customerAvailable -> SentinelStatus.LIMITED
            !facts.evidenceObserved -> SentinelStatus.UNKNOWN
            facts.condition == HomeCondition.DANGER -> SentinelStatus.BAD
            facts.condition == HomeCondition.DEGRADED -> SentinelStatus.WARN
            else -> SentinelStatus.OK
        }
        val detail = when (status) {
            SentinelStatus.OK -> "État observé comme sain"
            SentinelStatus.WARN -> "Fonctionnel avec une attention requise"
            SentinelStatus.BAD -> "Problème confirmé à examiner"
            SentinelStatus.UNKNOWN -> "État non mesuré"
            SentinelStatus.LIMITED -> when {
                !facts.implemented -> "Fonction non implémentée"
                !facts.customerAvailable -> "Non disponible pour le client"
                else -> "Capacité limitée"
            }
            SentinelStatus.LOCKED -> "Action, rôle ou permission requis"
        }
        return HomeModulePresentation(id, title, status, detail)
    }

    private fun overallStatus(modules: List<HomeModulePresentation>): SentinelStatus = when {
        modules.any { it.status == SentinelStatus.BAD } -> SentinelStatus.BAD
        modules.any { it.status == SentinelStatus.LOCKED } -> SentinelStatus.WARN
        modules.any { it.status == SentinelStatus.WARN } -> SentinelStatus.WARN
        modules.any { it.status == SentinelStatus.LIMITED } -> SentinelStatus.LIMITED
        modules.any { it.status == SentinelStatus.UNKNOWN } -> SentinelStatus.UNKNOWN
        modules.all { it.status == SentinelStatus.OK } -> SentinelStatus.OK
        else -> SentinelStatus.UNKNOWN
    }

    private fun headline(status: SentinelStatus): String = when (status) {
        SentinelStatus.OK -> "Protection observée"
        SentinelStatus.WARN -> "Attention requise"
        SentinelStatus.BAD -> "Risque confirmé"
        SentinelStatus.UNKNOWN -> "Protection à vérifier"
        SentinelStatus.LIMITED -> "Protection partielle"
        SentinelStatus.LOCKED -> "Protection verrouillée"
    }

    private fun summary(status: SentinelStatus): String = when (status) {
        SentinelStatus.OK -> "Tous les modules principaux disposent d’un état sain observé."
        SentinelStatus.WARN -> "Au moins un module demande votre attention."
        SentinelStatus.BAD -> "Au moins un problème confirmé nécessite une action."
        SentinelStatus.UNKNOWN -> "Une ou plusieurs protections n’ont pas encore été mesurées."
        SentinelStatus.LIMITED -> "Certaines protections ne sont pas disponibles ou restent limitées."
        SentinelStatus.LOCKED -> "Une protection nécessite un rôle, une permission ou un consentement."
    }

    private fun priorityMessage(modules: List<HomeModulePresentation>): String {
        val priority = modules.firstOrNull { it.status == SentinelStatus.BAD }
            ?: modules.firstOrNull { it.status == SentinelStatus.LOCKED }
            ?: modules.firstOrNull { it.status == SentinelStatus.WARN }
            ?: modules.firstOrNull { it.status == SentinelStatus.LIMITED }
            ?: modules.firstOrNull { it.status == SentinelStatus.UNKNOWN }
        return priority?.let { "${it.title} · ${it.detail}" }
            ?: "Aucune action prioritaire détectée"
    }
}
