package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.*

/** Local-only security inspection. A self-inspection is always visible even with package visibility limits. */
@Composable
fun SecurityLabProScreen(navController: NavController) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<AppIntegrityReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var firstApk by remember { mutableStateOf<ApkEvidence?>(null) }
    var secondApk by remember { mutableStateOf<ApkEvidence?>(null) }
    var comparison by remember { mutableStateOf<ApkComparison?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            selectionBusy = true
            error = null
            try { firstApk = SelectedApkInspector(context.applicationContext).inspect(uri); comparison = null }
            catch (e: Exception) { error = "Premier APK non lisible : ${e.message ?: "erreur"}" }
            finally { selectionBusy = false }
        }
    }
    val secondPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            selectionBusy = true
            error = null
            try { secondApk = SelectedApkInspector(context.applicationContext).inspect(uri); comparison = null }
            catch (e: Exception) { error = "Second APK non lisible : ${e.message ?: "erreur"}" }
            finally { selectionBusy = false }
        }
    }
    LaunchedEffect(context) {
        busy = true
        report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            InstalledAppIntegrityInspector(context.applicationContext).inspect(context.packageName)
        }
        busy = false
    }
    Scaffold(topBar = { Text("Security Lab Pro", style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(20.dp)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Inspection locale en lecture seule — aucune modification d'application.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { navController.popBackStack() }) { Text("Retour") }
                if (busy || selectionBusy) CircularProgressIndicator()
                Text("Comparer deux APK sélectionnés sur ce téléphone (sans installation).")
                Button(enabled = !selectionBusy, onClick = { firstPicker.launch(arrayOf("*/*")) }) {
                    Text("Choisir APK de référence")
                }
                Button(enabled = !selectionBusy, onClick = { secondPicker.launch(arrayOf("*/*")) }) {
                    Text("Choisir APK à comparer")
                }
                firstApk?.let { Text("Référence : ${it.packageName ?: "Non identifié"} / build ${it.versionCode ?: "?"}") }
                secondApk?.let { Text("Comparaison : ${it.packageName ?: "Non identifié"} / build ${it.versionCode ?: "?"}") }
                Button(
                    enabled = firstApk != null && secondApk != null && !selectionBusy,
                    onClick = { comparison = ApkComparisonEngine.compare(
                        firstApk!!, secondApk!!, System.currentTimeMillis()
                    ) }
                ) { Text("Comparer les deux APK") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            comparison?.let { result ->
                item { Text("Résultats — divergence ne prouve pas un piratage",
                    style = MaterialTheme.typography.titleMedium) }
                items(result.findings, key = { "apk_" + it.id }) { finding ->
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("${finding.status}: ${finding.title}")
                        Text(finding.explanation)
                    } }
                }
            }
            val current = report
            if (current != null) {
                item {
                    Text("Application : ${current.packageName ?: "Inconnue"}")
                    Text("Build : ${current.versionCode ?: "Non vérifiable"}")
                    Text("État : ${current.overallStatus.name} — preuve limitée à l'inspection locale")
                }
                items(current.findings, key = { it.id }) { finding ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("${finding.status.name} • ${finding.title}",
                                style = MaterialTheme.typography.titleMedium)
                            Text(finding.explanation)
                            finding.evidence?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text("Source : ${finding.source.name}",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                Text("Les applications tierces peuvent être masquées par Android. Aucun verdict de sûreté n'est déduit de leur absence.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
