package com.sentinel.quantum.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object OsintFeed : Screen("osint_feed")
    object About : Screen("about")
    object Compliance : Screen("compliance")
}
