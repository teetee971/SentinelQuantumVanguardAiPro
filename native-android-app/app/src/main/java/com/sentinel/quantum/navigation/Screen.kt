package com.sentinel.quantum.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object OsintFeed : Screen("osint_feed")
    object SecurityAudit : Screen("security_audit")
    object LocalLogs : Screen("local_logs")
    object PhoneSecurity : Screen("phone_security")
    object About : Screen("about")
    object Compliance : Screen("compliance")
}
