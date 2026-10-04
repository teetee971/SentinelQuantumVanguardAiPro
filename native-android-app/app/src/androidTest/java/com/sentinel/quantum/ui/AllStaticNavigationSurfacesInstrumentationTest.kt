package com.sentinel.quantum.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.navigation.NavGraph
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import org.junit.Rule
import org.junit.Test

/**
 * Application-wide emulator smoke coverage for every static NavGraph destination.
 *
 * The purpose is deliberately broader than screenshot/UI copy testing: each destination must be
 * composable on a clean Android runtime without crashing merely because a permission, provider,
 * service, backend, radio or optional capability is unavailable. Feature-specific behavior remains
 * covered by its dedicated unit/instrumentation tests.
 */
class AllStaticNavigationSurfacesInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun homeRenders() = render(Screen.Home.route)
    @Test fun searchRenders() = render(Screen.Search.route)
    @Test fun osintFeedRenders() = render(Screen.OsintFeed.route)
    @Test fun securityAuditRenders() = render(Screen.SecurityAudit.route)
    @Test fun systemDoctorRenders() = render(Screen.SystemDoctor.route)
    @Test fun localLogsRenders() = render(Screen.LocalLogs.route)
    @Test fun phoneSecurityRenders() = render(Screen.PhoneSecurity.route)
    @Test fun communicationsHubRenders() = render(Screen.CommunicationsHub.route)
    @Test fun callBlockingRenders() = render(Screen.CallBlocking.route)
    @Test fun phoneListsRenders() = render(Screen.PhoneLists.route)
    @Test fun callFilterHistoryRenders() = render(Screen.CallFilterHistory.route)
    @Test fun emailSecurityRenders() = render(Screen.EmailSecurity.route)
    @Test fun smsScannerRenders() = render(Screen.SmsScanner.route)
    @Test fun appPermissionAnalyzerRenders() = render(Screen.AppPermissionAnalyzer.route)
    @Test fun networkSurveillanceRenders() = render(Screen.NetworkSurveillance.route)
    @Test fun digitalExposureRenders() = render(Screen.DigitalExposure.route)
    @Test fun collectiveDefenseRenders() = render(Screen.CollectiveDefense.route)
    @Test fun smartHomeRenders() = render(Screen.SmartHome.route)
    @Test fun vpnRenders() = render(Screen.Vpn.route)
    @Test fun aboutRenders() = render(Screen.About.route)
    @Test fun complianceRenders() = render(Screen.Compliance.route)
    @Test fun settingsRenders() = render(Screen.Settings.route)

    private fun render(route: String) {
        composeRule.setContent {
            SentinelQuantumTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController, startDestination = route)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    }
}
