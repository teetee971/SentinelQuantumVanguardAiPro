package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.CollectiveDefenseClient
import com.sentinel.quantum.security.CollectiveDefenseWatchStore
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CollectiveDefenseScreen(navController: NavController) {
    val context = LocalContext.current
    val client = remember { CollectiveDefenseClient() }
    val store = remember(context) { CollectiveDefenseWatchStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var type by rememberSaveable { mutableStateOf(CollectiveDefenseClient.IndicatorType.DOMAIN) }
    var value by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable {
        mutableStateOf(CollectiveDefenseClient.ReportCategory.PHISHING)
    }
    var result by remember { mutableStateOf<CollectiveDefenseClient.ReputationResult?>(null) }
    var watchItems by remember { mutableStateOf(store.snapshot()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun refreshWatch() {
        watchItems = store.snapshot()
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Défense collective",
                subtitle = "Analyse, signalement et veille privée",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = SentinelD1.Cyan)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Collective Defense Network",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "Sentinel compare votre indicateur aux signaux techniques modérés du réseau collectif. Un signal communautaire ne déclenche jamais seul un blocage automatique.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Confidentialité : la valeur saisie est envoyée uniquement quand vous appuyez sur Analyser ou Signaler. La veille locale conserve seulement un fingerprint HMAC opaque.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SentinelSectionHeader(
                title = "Analyser un indicateur",
                subtitle = "Domaine, URL, e-mail ou empreinte SHA-256."
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CollectiveDefenseClient.IndicatorType.entries.chunked(2).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowTypes.forEach { candidate ->
                            FilterChip(
                                selected = type == candidate,
                                onClick = {
                                    type = candidate
                                    result = null
                                    status = null
                                },
                                label = { Text(typeLabel(candidate)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(4096) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(inputLabel(type)) },
                placeholder = { Text(inputExample(type)) },
                minLines = if (type == CollectiveDefenseClient.IndicatorType.URL) 2 else 1,
                maxLines = 4
            )

            Button(
                onClick = {
                    val candidate = value.trim()
                    if (candidate.isBlank() || busy) return@Button
                    busy = true
                    status = "Analyse du réseau collectif…"
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { client.lookup(type, candidate) }
                        }.onSuccess {
                            result = it
                            status = networkStatusText(it.communityIntelligence)
                        }.onFailure {
                            result = null
                            status = friendlyError(it)
                        }
                        busy = false
                    }
                },
                enabled = value.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "Analyse…" else "Analyser avec Sentinel")
            }

            status?.let {
                AssistChip(
                    onClick = {},
                    label = { Text(it) },
                    leadingIcon = {
                        Icon(
                            if (result?.communityIntelligence == "available") {
                                Icons.Default.CloudDone
                            } else {
                                Icons.Default.CloudOff
                            },
                            contentDescription = null
                        )
                    }
                )
            }

            result?.let { reputation ->
                ReputationCard(
                    result = reputation,
                    onWatch = {
                        val saved = store.upsert(reputation)
                        status = if (saved) {
                            "Ajouté à la veille locale. Seul le fingerprint est conservé."
                        } else {
                            "Impossible d’enregistrer la veille locale."
                        }
                        refreshWatch()
                    }
                )
            }

            SentinelSectionHeader(
                title = "Signaler au réseau",
                subtitle = "Le signalement reste en attente de modération et ne modifie pas immédiatement la réputation."
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    CollectiveDefenseClient.ReportCategory.PHISHING,
                    CollectiveDefenseClient.ReportCategory.MALWARE,
                    CollectiveDefenseClient.ReportCategory.CREDENTIAL_THEFT,
                    CollectiveDefenseClient.ReportCategory.OTHER
                ).chunked(2).forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowCategories.forEach { candidate ->
                            FilterChip(
                                selected = category == candidate,
                                onClick = { category = candidate },
                                label = { Text(categoryLabel(candidate)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val candidate = value.trim()
                    if (candidate.isBlank() || busy) return@OutlinedButton
                    busy = true
                    status = "Envoi du signalement pour modération…"
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                client.report(type, candidate, category)
                            }
                        }.onSuccess { report ->
                            status = when (report.status) {
                                "pending" -> "Signalement reçu. Il attend la modération Sentinel."
                                "duplicate" -> "Ce signalement a déjà été pris en compte récemment."
                                else -> "Signalement reçu avec état : " + report.status + "."
                            }
                        }.onFailure {
                            status = friendlyError(it)
                        }
                        busy = false
                    }
                },
                enabled = value.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Report, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Signaler cet indicateur")
            }

            SentinelSectionHeader(
                title = "Veille locale",
                subtitle = if (watchItems.isEmpty()) {
                    "Aucun indicateur surveillé. Ajoutez un résultat après une analyse."
                } else {
                    watchItems.size.toString() +
                        " fingerprint(s) surveillé(s), sans valeur brute enregistrée."
                }
            )

            watchItems.forEach { item ->
                WatchItemCard(
                    item = item,
                    busy = busy,
                    onRecheck = {
                        if (busy) return@WatchItemCard
                        busy = true
                        status = "Recontrôle du fingerprint…"
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.lookupFingerprint(item.indicatorType, item.fingerprint)
                                }
                            }.onSuccess { refreshed ->
                                store.upsert(refreshed)
                                refreshWatch()
                                status = networkStatusText(refreshed.communityIntelligence)
                            }.onFailure {
                                status = friendlyError(it)
                            }
                            busy = false
                        }
                    },
                    onRemove = {
                        store.remove(item.indicatorType, item.fingerprint)
                        refreshWatch()
                    }
                )
            }

            Text(
                "Limite actuelle : 100 fingerprints surveillés localement. Les données collectives sont des signaux techniques et ne constituent pas une preuve d’identité, d’intention ou de compromission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReputationCard(
    result: CollectiveDefenseClient.ReputationResult,
    onWatch: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (result.riskState) {
                        "HIGH_CONFIDENCE" -> Icons.Default.Warning
                        "SUSPICIOUS" -> Icons.Default.ReportProblem
                        "OBSERVED" -> Icons.Default.Visibility
                        else -> Icons.Default.CheckCircleOutline
                    },
                    contentDescription = null,
                    tint = if (result.riskState in setOf("HIGH_CONFIDENCE", "SUSPICIOUS")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        SentinelD1.Cyan
                    }
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        riskLabel(result.riskState),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        result.signals.toString() + " signal(aux) modéré(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (result.categories.isNotEmpty()) {
                Text(
                    "Catégories : " +
                        result.categories.joinToString(", ") { categoryCodeLabel(it) },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Fingerprint : " + shortFingerprint(result.indicatorFingerprint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (result.enforcementAllowed) {
                    "Une action automatique est autorisée par le serveur."
                } else {
                    "Aucune action automatique : Sentinel vous informe, la décision reste locale."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (result.warning.isNotBlank()) {
                Text(
                    result.warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter à la veille locale")
            }
        }
    }
}

@Composable
private fun WatchItemCard(
    item: CollectiveDefenseWatchStore.WatchItem,
    busy: Boolean,
    onRecheck: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = SentinelD1.Cyan)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        typeLabel(item.indicatorType) + " · " + riskLabel(item.riskState),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        shortFingerprint(item.fingerprint) + " · " +
                            item.signals.toString() + " signal(aux)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRecheck,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Recontrôler")
                }
                TextButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Retirer")
                }
            }
        }
    }
}

private fun typeLabel(type: CollectiveDefenseClient.IndicatorType): String = when (type) {
    CollectiveDefenseClient.IndicatorType.DOMAIN -> "Domaine"
    CollectiveDefenseClient.IndicatorType.URL -> "URL"
    CollectiveDefenseClient.IndicatorType.EMAIL -> "E-mail"
    CollectiveDefenseClient.IndicatorType.SHA256 -> "SHA-256"
}

private fun inputLabel(type: CollectiveDefenseClient.IndicatorType): String = when (type) {
    CollectiveDefenseClient.IndicatorType.DOMAIN -> "Nom de domaine"
    CollectiveDefenseClient.IndicatorType.URL -> "URL complète"
    CollectiveDefenseClient.IndicatorType.EMAIL -> "Adresse e-mail suspecte"
    CollectiveDefenseClient.IndicatorType.SHA256 -> "Empreinte SHA-256"
}

private fun inputExample(type: CollectiveDefenseClient.IndicatorType): String = when (type) {
    CollectiveDefenseClient.IndicatorType.DOMAIN -> "exemple.com"
    CollectiveDefenseClient.IndicatorType.URL -> "https://exemple.com/page"
    CollectiveDefenseClient.IndicatorType.EMAIL -> "contact@exemple.com"
    CollectiveDefenseClient.IndicatorType.SHA256 -> "64 caractères hexadécimaux"
}

private fun riskLabel(code: String): String = when (code) {
    "HIGH_CONFIDENCE" -> "Risque fortement corroboré"
    "SUSPICIOUS" -> "Signal suspect"
    "OBSERVED" -> "Signal observé"
    "UNKNOWN" -> "Aucun signal confirmé"
    else -> "État non déterminé"
}

private fun networkStatusText(code: String): String = when (code) {
    "available" -> "Réseau collectif disponible"
    "degraded" -> "Réseau collectif dégradé : résultat non concluant"
    "disabled" -> "Réseau collectif indisponible"
    else -> "État réseau collectif inconnu"
}

private fun categoryLabel(category: CollectiveDefenseClient.ReportCategory): String = when (category) {
    CollectiveDefenseClient.ReportCategory.PHISHING -> "Phishing"
    CollectiveDefenseClient.ReportCategory.MALWARE -> "Malware"
    CollectiveDefenseClient.ReportCategory.CREDENTIAL_THEFT -> "Identifiants"
    CollectiveDefenseClient.ReportCategory.OTHER -> "Autre"
    else -> category.name.replace('_', ' ').lowercase()
}

private fun categoryCodeLabel(code: String): String =
    runCatching { categoryLabel(CollectiveDefenseClient.ReportCategory.valueOf(code)) }
        .getOrDefault(code.replace('_', ' ').lowercase())

private fun shortFingerprint(value: String): String =
    if (value.length == 64) value.take(12) + "…" + value.takeLast(8) else value.take(24)

private fun friendlyError(error: Throwable): String {
    val code = error.message.orEmpty()
    return when {
        code.contains("HTTP_422") -> "Indicateur invalide. Vérifiez le format."
        code.contains("HTTP_429") -> "Trop de requêtes. Réessayez plus tard."
        code.contains("HTTP_503") -> "Le réseau collectif est temporairement indisponible."
        code.contains("NETWORK", ignoreCase = true) -> "Connexion réseau indisponible."
        error is SecurityException -> "Connexion Sentinel refusée par la politique de sécurité."
        else -> "Analyse impossible pour le moment."
    }
}
