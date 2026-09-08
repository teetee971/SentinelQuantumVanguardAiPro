package com.sentinel.quantum.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Accueil")
    object OsintFeed : Screen("osint_feed", "Flux OSINT")
    object SecurityAudit : Screen("security_audit", "Audit sécurité")
    object LocalLogs : Screen("local_logs", "Journal SOC local")
    object PhoneSecurity : Screen("phone_security", "Sécurité téléphone")
    object CallBlocking : Screen("call_blocking", "Blocage d'appels")
    object EmailSecurity : Screen("email_security", "Analyse email")
    object AppPermissionAnalyzer : Screen("app_permission_analyzer", "Analyseur de permissions")
    object NetworkSurveillance : Screen("network_surveillance", "Surveillance réseau")
    object About : Screen("about", "À propos")
    object Compliance : Screen("compliance", "Conformité")
    object Settings : Screen("settings", "Paramètres")
}
