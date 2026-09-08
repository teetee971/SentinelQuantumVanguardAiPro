package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.CallFilterLogStore
import com.sentinel.quantum.security.CallRuleEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFilterHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { CallFilterLogStore(context) }
    var history by remember { mutableStateOf(store.history()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Historique des appels filtrés") }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Text("←") }
    }, actions = {
        TextButton(onClick = { store.clear(); history = store.history() }) { Text("Effacer") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Les numéros sont masqués avant enregistrement. Aucun contact ni journal d'appels " +
                    "complet n'est collecté ; seules les décisions de filtrage locales sont conservées.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall
            )
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Aucune décision de filtrage enregistrée pour le moment.",
                        style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history) { entry -> CallFilterHistoryCard(entry) }
                }
            }
        }
    }
}

@Composable
private fun CallFilterHistoryCard(entry: CallFilterLogStore.Entry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (entry.action) {
                CallRuleEngine.Action.BLOCK -> MaterialTheme.colorScheme.errorContainer
                CallRuleEngine.Action.SILENCE -> MaterialTheme.colorScheme.tertiaryContainer
                CallRuleEngine.Action.ALLOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.action.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(entry.timestamp, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.maskedNumber, style = MaterialTheme.typography.bodyMedium)
            Text("Source : ${entry.source} — ${entry.reason}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
