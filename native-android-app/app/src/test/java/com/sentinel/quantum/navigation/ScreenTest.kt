package com.sentinel.quantum.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    private val screens = listOf(
        Screen.Home,
        Screen.OsintFeed,
        Screen.SecurityAudit,
        Screen.LocalLogs,
        Screen.PhoneSecurity,
        Screen.CallBlocking,
        Screen.CallFilterHistory,
        Screen.EmailSecurity,
        Screen.SmsScanner,
        Screen.AppPermissionAnalyzer,
        Screen.NetworkSurveillance,
        Screen.About,
        Screen.Compliance,
        Screen.Settings,
        Screen.OsintDetail
    )

    @Test
    fun routesAreUniqueAndNonBlank() {
        val routes = screens.map { it.route }

        assertTrue(routes.all { it.isNotBlank() })
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun osintDetailRouteDeclaresItemIdArgument() {
        assertTrue(Screen.OsintDetail.route.contains("{${Screen.ARG_ITEM_ID}}"))
    }

    @Test
    fun titlesAreNonBlank() {
        assertTrue(screens.all { it.title.isNotBlank() })
    }

    @Test
    fun routeContractRemainsStable() {
        assertEquals(
            setOf(
                "home",
                "osint_feed",
                "security_audit",
                "local_logs",
                "phone_security",
                "call_blocking",
                "call_filter_history",
                "email_security",
                "sms_scanner",
                "app_permission_analyzer",
                "network_surveillance",
                "about",
                "compliance",
                "settings",
                "osint_detail/{itemId}"
            ),
            screens.map { it.route }.toSet()
        )
    }
}
