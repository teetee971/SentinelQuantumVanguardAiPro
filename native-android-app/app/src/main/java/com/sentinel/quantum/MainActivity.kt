package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.sentinel.quantum.background.OsintNotificationHelper
import com.sentinel.quantum.background.WorkScheduler
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.navigation.NavGraph
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        // Local-only background watch: the notification channel and the periodic worker are
        // (re)synchronised from the user preference on every start. No remote push is involved.
        OsintNotificationHelper.ensureChannel(this)
        WorkScheduler.sync(this)
        val openOsintFeed = intent?.getBooleanExtra(
            OsintNotificationHelper.EXTRA_OPEN_OSINT_FEED,
            false
        ) == true
        setContent {
            SentinelQuantumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = when {
                        SharedTextHolder.hasPending() -> Screen.EmailSecurity.route
                        openOsintFeed -> Screen.OsintFeed.route
                        else -> Screen.Home.route
                    }
                    NavGraph(navController = navController, startDestination = startDestination)
                }
            }
        }
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

