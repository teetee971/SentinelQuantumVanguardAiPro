package com.sentinel.quantum.ui.screens

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.security.*
import com.sentinel.quantum.ui.design.SentinelTopBar

/** Scan never deletes. Only the confirmed immutable selection reaches the guarded executor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>().first()
    }
    val model = remember(activity) { ViewModelProvider(activity)[SystemCleanerViewModel::class.java] }
    val session = model.session
    val state by session.state.collectAsState()
    var confirmation by remember { mutableStateOf<List<SentinelCleanupPolicy.Candidate>?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val busy = state.busy
    val selected = state.selected
    val candidates = state.discovery?.candidates.orEmpty()
    val chosen = candidates.filter { it.stableId in selected && SentinelCleanupPolicy.canExecuteDirectly(it) }

    Scaffold(topBar = {
        SentinelTopBar(title = "Nettoyeur Sentinel", subtitle = "Scanner · sélectionner · vérifier", onBack = { navController.popBackStack() })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Copies temporaires de Sentinel", style = MaterialTheme.typography.headlineSmall)
                Text("Seuls les exports temporaires et copies d’inspection APK datant d’au moins 24 heures sont proposés. Les conversations, bases, MMS en cours et données des autres applications sont exclus.")
            }
            item {
                Button(onClick = { session.scan() }, enabled = !busy && confirmation == null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Traitement…" else "Scanner les fichiers temporaires")
                }
                Text(state.status, style = MaterialTheme.typography.bodyMedium)
                shareError?.let { Text(it) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item {
                val summary = SentinelCleanupPlan.summarize(chosen)
                Text("${chosen.size} sélectionné(s) · ${Formatter.formatFileSize(context, summary.knownReclaimableBytes)} de copies")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { session.select(candidates.filter(SentinelCleanupPolicy::canExecuteDirectly).map { it.stableId }.toSet()) },
                        enabled = !busy && confirmation == null && candidates.isNotEmpty()) { Text("Tout sélectionner") }
                    TextButton(onClick = { session.select(emptySet()) }, enabled = !busy && confirmation == null && selected.isNotEmpty()) { Text("Désélectionner") }
                }
                Button(onClick = { if (!busy && chosen.isNotEmpty()) confirmation = chosen.toList() },
                    enabled = !busy && chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Nettoyer la sélection") }
            }
            state.results?.let { completed ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Rapport vérifié", style = MaterialTheme.typography.titleMedium)
                            Text(SentinelCleanupReport.render(completed))
                            TextButton(enabled = !busy, onClick = {
                                try {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, SentinelCleanupReport.render(completed))
                                    }, "Partager le rapport"))
                                } catch (_: Exception) { shareError = "Aucune application disponible pour partager le rapport." }
                            }) { Text("Partager le rapport") }
                        }
                    }
                }
            }
            items(candidates, key = { it.stableId }) { candidate ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp)) {
                        Checkbox(checked = candidate.stableId in selected,
                            enabled = !busy && confirmation == null && SentinelCleanupPolicy.canExecuteDirectly(candidate),
                            onCheckedChange = { checked -> session.select(if (checked) selected + candidate.stableId else selected - candidate.stableId) })
                        Column(Modifier.weight(1f).padding(8.dp)) {
                            Text(candidate.displayName)
                            Text(Formatter.formatFileSize(context, candidate.bytes ?: 0L))
                            if (!SentinelCleanupPolicy.canExecuteDirectly(candidate)) Text("Traité ou protégé — relancez le scan avant de réessayer.")
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Examiner le reste de l’appareil", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { navController.navigate(Screen.SystemDoctor.route) }, enabled = !busy) { Text("Diagnostic système") }
                OutlinedButton(onClick = { navController.navigate(Screen.SecurityLabPro.route) }, enabled = !busy) { Text("Analyser les applications") }
                OutlinedButton(onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                    catch (_: Exception) { shareError = "Les paramètres de stockage ne sont pas disponibles sur cet appareil." }
                }, enabled = !busy) { Text("Stockage Android") }
            }
        }
    }
    confirmation?.let { snapshot ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null },
            title = { Text("Supprimer ${snapshot.size} copie(s) temporaire(s) ?") },
            text = { Text("Cette suppression est définitive. Seules les copies sélectionnées seront traitées. Les messages et journaux originaux sont conservés. Chaque fichier sera revérifié avant suppression.") },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Annuler") } },
            confirmButton = {
                TextButton(onClick = {
                    if (!busy) {
                        session.clean(snapshot)
                        confirmation = null
                    }
                }) { Text("Supprimer les copies") }
            })
    }
}
