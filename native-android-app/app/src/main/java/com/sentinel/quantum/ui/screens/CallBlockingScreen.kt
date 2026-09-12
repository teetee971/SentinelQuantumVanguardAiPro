package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.SentinelRoomDatabase
import com.sentinel.quantum.security.CallFilterDecisionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CallBlockingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var phoneNumber by remember { mutableStateOf("") }
    var blockReason by remember { mutableStateOf("") }
    var blacklist by remember { mutableStateOf(emptyList<CallFilterDecisionEntity>()) }

    fun loadBlacklist() {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val db = SentinelRoomDatabase.get(context)
                val items = db.callFilterDecisionDao().latest(500).filter { it.action == "BLOCKED" }
                withContext(Dispatchers.Main) {
                    blacklist = items
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadBlacklist() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Call Blocking Engine", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Bloquer un nouveau numéro", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Numéro") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                var expandedReason by remember { mutableStateOf(false) }
                    val reasonsList = listOf("Hameçonnage", "Usurpation", "Paiement demandé", "Spam", "Autre")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { expandedReason = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (blockReason.isBlank()) "Sélectionner la Raison du Signalement" else "Raison : " + blockReason)
                        }
                        DropdownMenu(expanded = expandedReason, onDismissRequest = { expandedReason = false }) {
                            reasonsList.forEach { r ->
                                DropdownMenuItem(text = { Text(r) }, onClick = { blockReason = r; expandedReason = false })
                            }
                        }
                    }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    val clean = phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "")
                    if (clean.isNotBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                val db = SentinelRoomDatabase.get(context)
                                db.callFilterDecisionDao().insert(
                                    CallFilterDecisionEntity(
                                        occurredAtMs = System.currentTimeMillis(),
                                        action = "BLOCKED",
                                        reason = blockReason.ifBlank { "Manuel" },
                                        source = clean,
                                        numberFingerprint = ""
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    phoneNumber = ""
                                    blockReason = ""
                                    loadBlacklist()
                                }
                            }
                        }
                    }
                }, modifier = Modifier.align(Alignment.End)) { Text("Bloquer") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Numéros bloqués (${blacklist.size})", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (blacklist.isEmpty()) {
            Text(text = "Aucun numéro bloqué localement.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(blacklist) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(text = item.source, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(text = "Motif : ${item.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}
