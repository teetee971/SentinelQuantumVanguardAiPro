package com.sentinel.quantum.ui.screens
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun ArcepSearchScreen(modifier: Modifier = Modifier) {
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 var query by remember { mutableStateOf("") }
 var hasMatch by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("") }
 val target = query.filter { it.isDigit() }
 LaunchedEffect(target) {
 hasMatch = target.startsWith("094848") || target.startsWith("94848")
 }
 Surface(modifier = modifier.fillMaxSize()) {
 LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
 item { Text("Phone Intelligence & ARCEP", style = MaterialTheme.typography.headlineMedium) }
 item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Numero ou prefixe ARCEP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()) }
 if (status.isNotBlank()) { item { Card { Text(status, modifier = Modifier.padding(12.dp)) } } }
 if (hasMatch) {
 item {
 Card(modifier = Modifier.fillMaxWidth()) {
 Column(modifier = Modifier.padding(16.dp)) {
 Text("Attribution de tranche ARCEP", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
 Text("Attributaire: Manifone code LGC", style = MaterialTheme.typography.bodyLarge)
 Text("Tranche: 0948480000 a 0948489999", style = MaterialTheme.typography.bodyMedium)
 Text("Territoire: Metropole", style = MaterialTheme.typography.bodyMedium)
 Spacer(modifier = Modifier.height(8.dp))
 Text("Fiche entreprise officielle (SIRENE)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
 Text("SIRET: 49248601400037", style = MaterialTheme.typography.bodyMedium)
 Text("Registre: VANNES", style = MaterialTheme.typography.bodyMedium)
 Text("Adresse: PIBS 2 RUE HENRI BECQUEREL 56000 VANNES", style = MaterialTheme.typography.bodyMedium)
 Spacer(modifier = Modifier.height(12.dp))
 Button(onClick = { scope.launch(Dispatchers.IO) { runCatching { SentinelRoomDatabase.get(context).callFilterDecisionDao().insert(CallFilterDecisionEntity(occurredAtMs = System.currentTimeMillis(), action = "ALLOWED", reason = "Liste Blanche", source = target)) }; withContext(Dispatchers.Main) { status = "Ajoute a la liste blanche" } } }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter a la liste blanche") }
 Spacer(modifier = Modifier.height(4.dp))
 Button(onClick = { scope.launch(Dispatchers.IO) { runCatching { SentinelRoomDatabase.get(context).callFilterDecisionDao().insert(CallFilterDecisionEntity(occurredAtMs = System.currentTimeMillis(), action = "BLOCKED", reason = "Blocage Intel", source = target)) }; withContext(Dispatchers.Main) { status = "Ajoute au blocage" } } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Ajouter au blocage") }
 }
 }
 }
 }
 }
 }
}
