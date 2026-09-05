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
        Screen.About,
        Screen.Compliance
    )

    @Test
    fun routesAreUniqueAndNonBlank() {
        val routes = screens.map { it.route }

        assertTrue(routes.all { it.isNotBlank() })
        assertEquals(routes.size, routes.toSet().size)
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
                "about",
                "compliance"
            ),
            screens.map { it.route }.toSet()
        )
    }
}
