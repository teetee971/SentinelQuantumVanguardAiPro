package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.background.CollectiveDefenseNotificationHelper
import com.sentinel.quantum.background.CollectiveDefenseWorkScheduler
import com.sentinel.quantum.background.OsintNotificationHelper
import com.sentinel.quantum.background.WorkScheduler
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.navigation.NavGraph
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.SentinelD1
import kotlinx.coroutines.delay

private enum class BottomNavTarget {
    HOME,
    COMMUNICATIONS,
    PROTECTION,
    MORE
}

private data class BottomNavEntry(
    val target: BottomNavTarget,
    val icon: ImageVector,
    val labelRes: Int
)

private val bottomNavEntries = listOf(
    BottomNavEntry(BottomNavTarget.HOME, Icons.Default.Home, R.string.nav_home),
    BottomNavEntry(BottomNavTarget.COMMUNICATIONS, Icons.Default.Chat, R.string.nav_communications),
    BottomNavEntry(BottomNavTarget.PROTECTION, Icons.Default.Security, R.string.nav_protection),
    BottomNavEntry(BottomNavTarget.MORE, Icons.Default.MoreHoriz, R.string.nav_more)
)

private fun androidx.navigation.NavHostController.navigateBottomDestination(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

class MainActivity : ComponentActivity() {
    private val phoneCoreSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val wizard = PhoneCoreSetupWizardStore(applicationContext)
        val runtimeFacts = PhoneCoreRuntimeFacts.read(applicationContext)
        if (PhoneCoreSetupWizardStore.softwarePrerequisitesReady(runtimeFacts)) {
            wizard.markCompleted()
        } else {
            // Returning from the user-visible setup without satisfying every Android fact is a
            // deliberate defer, never success. A process death while setup is open produces no
            // callback, leaving IN_PROGRESS so the next cold launch can resume automatically.
            wizard.markDeferred()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        // Local-only background watch: the notification channel and the periodic worker are
        // (re)synchronised from the user preference on every start. No remote push is involved.
        OsintNotificationHelper.ensureChannel(this)
        WorkScheduler.sync(this)
        CollectiveDefenseNotificationHelper.ensureChannel(this)
        CollectiveDefenseWorkScheduler.sync(this)
        // First-run setup is user-visible and Android-controlled: Sentinel never grants a
        // permission or default-handler role silently. Opening the existing activation center
        // early removes the need to hunt through the app before configuring Phone Core.
        maybeOpenPhoneCoreFirstRunSetup()
        val openOsintFeed = intent?.getBooleanExtra(
            OsintNotificationHelper.EXTRA_OPEN_OSINT_FEED,
            false
        ) == true
        val openCollectiveDefense = intent?.getBooleanExtra(
            CollectiveDefenseNotificationHelper.EXTRA_OPEN_COLLECTIVE_DEFENSE,
            false
        ) == true
        setContent {
            val settingsStore = remember { SettingsStore(applicationContext) }
            var themeMode by remember { mutableStateOf(settingsStore.getThemeMode()) }
            var showBrandLoading by rememberSaveable { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                // Keep the branded surface long enough to avoid a one-frame flash on fast devices.
                // No network request or artificial application dependency is hidden behind it.
                delay(900)
                showBrandLoading = false
            }

            SentinelQuantumTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = when {
                        SharedTextHolder.hasPending() -> Screen.EmailSecurity.route
                        openCollectiveDefense -> Screen.CollectiveDefense.route
                        openOsintFeed -> Screen.OsintFeed.route
                        else -> Screen.Home.route
                    }
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = SentinelD1.Panel,
                                tonalElevation = 0.dp
                            ) {
                                bottomNavEntries.forEach { entry ->
                                    val selected = when (entry.target) {
                                        BottomNavTarget.HOME -> currentRoute == Screen.Home.route
                                        BottomNavTarget.PROTECTION -> currentRoute == Screen.PhoneSecurity.route
                                        BottomNavTarget.MORE -> currentRoute == Screen.Settings.route
                                        BottomNavTarget.COMMUNICATIONS -> currentRoute == Screen.CommunicationsHub.route
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            when (entry.target) {
                                                BottomNavTarget.HOME ->
                                                    navController.navigateBottomDestination(Screen.Home)
                                                BottomNavTarget.COMMUNICATIONS ->
                                                    navController.navigateBottomDestination(Screen.CommunicationsHub)
                                                BottomNavTarget.PROTECTION ->
                                                    navController.navigateBottomDestination(Screen.PhoneSecurity)
                                                BottomNavTarget.MORE ->
                                                    navController.navigateBottomDestination(Screen.Settings)
                                            }
                                        },
                                        icon = { Icon(entry.icon, contentDescription = null) },
                                        label = { Text(stringResource(entry.labelRes)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = SentinelD1.Cyan,
                                            selectedTextColor = SentinelD1.Cyan,
                                            indicatorColor = SentinelD1.Card,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            NavGraph(
                                navController = navController,
                                startDestination = startDestination,
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    settingsStore.setThemeMode(mode)
                                }
                            )
                        }
                    }
                    if (showBrandLoading) SentinelBrandLoading()
                    }
                }
            }
        }
    }

    private fun maybeOpenPhoneCoreFirstRunSetup() {
        if (intent?.action == Intent.ACTION_SEND) return
        val wizard = PhoneCoreSetupWizardStore(applicationContext)
        val runtimeFacts = PhoneCoreRuntimeFacts.read(applicationContext)
        val lifecycleState = wizard.lifecycleState()
        if (!PhoneCoreSetupWizardStore.shouldAutoOpenSetup(lifecycleState, runtimeFacts)) return

        // Runtime truth remains authoritative. COMPLETED only suppresses the setup while Android
        // still reports every prerequisite as ready. A later revocation enters repair mode again.
        // DEFERRED suppresses automatic reopening but never changes any readiness fact. If the
        // process dies while the activation screen is open, IN_PROGRESS remains persisted and the
        // next cold launch resumes the assistant from the first fact Android still reports missing.
        if (lifecycleState == PhoneCoreSetupWizardStore.LifecycleState.NOT_STARTED) {
            wizard.markOffered()
        }
        wizard.markInProgress()
        phoneCoreSetupLauncher.launch(
            Intent(this, PhoneCoreActivationActivity::class.java).apply {
                putExtra(PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP, true)
            }
        )
    }

    /**
     * Accepts text shared from other apps (e.g. a mail client) via the Android Share Sheet.
     * Only plain text is accepted; the content is handed to [SharedTextHolder] and consumed
     * once by [com.sentinel.quantum.ui.screens.EmailSecurityScreen], which applies the same
     * bounded local analysis as manually pasted text. No network access is triggered here.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            SharedTextHolder.offer(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}

@androidx.compose.runtime.Composable
private fun SentinelBrandLoading() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.sentinel_soldier_loading),
            contentDescription = stringResource(R.string.loading_sentinel_image_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .9f))
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.loading_sentinel_title),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(stringResource(R.string.loading_sentinel_status), color = Color(0xFF9DDFFF))
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(
                modifier = Modifier.width(30.dp).height(30.dp),
                color = Color(0xFF42C8FF),
                strokeWidth = 3.dp
            )
        }
    }
}
