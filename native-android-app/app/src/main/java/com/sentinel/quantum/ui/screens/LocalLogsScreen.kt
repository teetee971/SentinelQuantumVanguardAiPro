package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.LogEntry

@Composable
fun LocalLogsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logger = remember { LocalLogger(context) }
    var logsList by remember { mutableStateOf(emptyList<LogEntry>()) }

    // Charger les logs au démarrage de l'écran
    LaunchedEffect(Unit) {
        logsList = logger.getLogs()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Security Logs",
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(onClick = {
                    logger.clearLogs()
                    logsList = emptyList()
                }) {
                    Text("Clear")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (logsList.isEmpty()) {
                Text(
                    text = "No security logs recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logsList) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = log.level.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (log.level == LocalLogger.LogLevel.ERROR) 
                                            MaterialTheme.colorScheme.error 
                                        else 
                                            MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = log.timestamp,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "[${log.tag}] ${log.message}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
