package com.sentinel.quantum.navigation

import android.net.Uri

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Accueil")
    object OsintFeed : Screen("osint_feed", "Flux OSINT")
    object SecurityAudit : Screen("security_audit", "Audit sécurité")
    object LocalLogs : Screen("local_logs", "Journal SOC local")
    object PhoneSecurity : Screen("phone_security", "Sécurité téléphone")
    object CallBlocking : Screen("call_blocking", "Blocage d'appels")
    object EmailSecurity : Screen("email_security", "Analyse email")
    object AppPermissionAnalyzer : Screen("app_permission_analyzer", "Analyseur de permissions")
    object About : Screen("about", "À propos")
    object Compliance : Screen("compliance", "Conformité")
    object Settings : Screen("settings", "Paramètres")

    object OsintDetail : Screen("osint_detail/{$ARG_ITEM_ID}", "Détail de l'alerte") {
        /** Builds a concrete route; the identifier is a URL, so it must be encoded. */
        fun createRoute(itemId: String): String = "osint_detail/${Uri.encode(itemId)}"
    }

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
