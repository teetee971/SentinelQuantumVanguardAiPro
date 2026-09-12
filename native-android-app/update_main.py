path = "app/src/main/java/com/sentinel/quantum/MainActivity.kt"

code = """package com.sentinel.quantum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sentinel.quantum.ui.screens.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(2500)
                showSplash = false
            }

            val cyberColorScheme = darkColorScheme(
                background = Color(0xFF0B0F19),
                surface = Color(0xFF161F30),
                primary = Color(0xFF00E5FF),
                error = Color(0xFFFF3366)
            )

            MaterialTheme(colorScheme = cyberColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val context = LocalContext.current
                            val resId = remember {
                                context.resources.getIdentifier("splash_bg", "drawable", context.packageName)
                            }
                            if (resId != 0) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(
                                modifier = Modifier.padding(bottom = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "INITIALIZING VANGUARD PROTOCOLS...",
                                    color = Color(0xFF00E5FF),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        var currentScreen by remember { mutableStateOf("blocking") }
                        Scaffold(
                            bottomBar = {
                                NavigationBar(containerColor = Color(0xFF161F30)) {
                                    NavigationBarItem(
                                        selected = currentScreen == "blocking",
                                        onClick = { currentScreen = "blocking" },
                                        label = { Text("Appels") },
                                        icon = { Text("🚫") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == "logs",
                                        onClick = { currentScreen = "logs" },
                                        label = { Text("Logs") },
                                        icon = { Text("📋") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == "arcep",
                                        onClick = { currentScreen = "arcep" },
                                        label = { Text("ARCEP") },
                                        icon = { Text("🔎") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == "audit",
                                        onClick = { currentScreen = "audit" },
                                        label = { Text("Audit") },
                                        icon = { Text("🛡️") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == "osint",
                                        onClick = { currentScreen = "osint" },
                                        label = { Text("OSINT") },
                                        icon = { Text("🌐") }
                                    )
                                }
                            }
                        ) { padding ->
                            val mod = Modifier.padding(padding)
                            when (currentScreen) {
                                "blocking" -> CallBlockingScreen(modifier = mod)
                                "logs" -> LocalLogsScreen(modifier = mod)
                                "arcep" -> ArcepSearchScreen(modifier = mod)
                                "audit" -> SecurityAuditScreen(modifier = mod)
                                "osint" -> OsintFeedScreen(modifier = mod)
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

with open(path, "w", encoding="utf-8") as f:
    f.write(code)

print("--- MAJ MainActivity EFFECTUÉE ---")
