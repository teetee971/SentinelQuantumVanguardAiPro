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
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.navigation.NavGraph
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        setContent {
            SentinelQuantumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (SharedTextHolder.hasPending()) {
                        Screen.EmailSecurity.route
                    } else {
                        Screen.Home.route
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

