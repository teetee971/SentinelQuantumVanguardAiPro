package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class CisaThreatItem(
    val cveId: String,
    val vendorProject: String,
    val product: String,
    val vulnerabilityName: String,
    val dateAdded: String
)

@Composable
fun OsintFeedScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var threatList by remember { mutableStateOf(emptyList<CisaThreatItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isOfflineMode by remember { mutableStateOf(false) }

    // Fichier de cache local
    val cacheFile = remember { File(context.cacheDir, "cisa_threat_cache.json") }

    // Fonction de parsing réutilisable pour le réseau et le cache
    fun parseJsonToThreats(bodyString: String): List<CisaThreatItem> {
        val json = JSONObject(bodyString)
        val vulnerabilities = json.getJSONArray("vulnerabilities")
        val items = mutableListOf<CisaThreatItem>()
        val limit = minOf(vulnerabilities.length(), 20)
        for (i in 0 until limit) {
            val obj = vulnerabilities.getJSONObject(i)
            items.add(
                CisaThreatItem(
                    cveId = obj.optString("cveID", "N/A"),
                    vendorProject = obj.optString("vendorProject", "Inconnu"),
                    product = obj.optString("product", "Inconnu"),
                    vulnerabilityName = obj.optString("vulnerabilityName", "Aucune description"),
                    dateAdded = obj.optString("dateAdded", "N/A")
                )
            )
        }
        return items
    }

    // Moteur d'acquisition asynchrone principal
    fun fetchCisaKev(forceRefresh: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            
            // 1. Stratégie de mise en cache locale : Si non-forcé et cache existant, charger le cache
            if (!forceRefresh && cacheFile.exists()) {
                try {
                    val cachedData = cacheFile.readText(Charsets.UTF_8)
                    val parsedItems = parseJsonToThreats(cachedData)
                    if (parsedItems.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            threatList = parsedItems
                            isOfflineMode = true
                            isLoading = false
                        }
                        return@launch
                    }
                } catch (_: Exception) {}
            }

            // 2. Acquisition Réseau Réelle
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://cisa.gov")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Réseau indisponible")
                    val bodyString = response.body?.string() ?: ""
                    
                    // Écriture immédiate dans le cache persistant hors-ligne
                    cacheFile.writeText(bodyString, Charsets.UTF_8)
                    val parsedItems = parseJsonToThreats(bodyString)

                    withContext(Dispatchers.Main) {
                        threatList = parsedItems
                        isOfflineMode = false
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                // 3. Fallback d'urgence : Si le réseau échoue, essayer de charger le dernier cache existant
                if (cacheFile.exists()) {
                    try {
                        val cachedData = cacheFile.readText(Charsets.UTF_8)
                        val parsedItems = parseJsonToThreats(cachedData)
                        withContext(Dispatchers.Main) {
                            threatList = parsedItems
                            isOfflineMode = true
                            isLoading = false
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isOfflineMode = true // Signale le manque de données hors-ligne
                        isLoading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchCisaKev(forceRefresh = false)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            // En-tête avec bouton de rafraîchissement manuel aligné
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "CISA Intel Feed", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = if (isOfflineMode) "Mode Hors-ligne (Données en cache)" else "Flux de menaces synchronisé en direct",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOfflineMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                
                Button(
                    onClick = { fetchCisaKev(forceRefresh = true) },
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "..." else "Rafraîchir")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading && threatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (threatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Aucune donnée disponible. Connectez-vous à internet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(threatList) { threat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = threat.cveId, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                    Text(text = threat.dateAdded, style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "${threat.vendorProject} — ${threat.product}", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = threat.vulnerabilityName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
