package com.sentinel.quantum.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreRuntimeFacts
import com.sentinel.quantum.security.ArcepVerifiedPrefixCatalog
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.PhoneProtectionListTruth
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelTopBar
import java.text.DateFormat
import java.util.Date

@Composable
fun PhoneProtectionListsScreen(navController: NavController) {
    val context = LocalContext.current
    val hostActivity = context as? ComponentActivity
    val store = remember(context) { CallBlocklistStore(context.applicationContext) }
    var postureEpoch by remember { mutableIntStateOf(0) }
    var actionStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) postureEpoch++
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    val now = System.currentTimeMillis()
    var snapshot by remember(postureEpoch) { mutableStateOf(store.snapshot(now)) }
    var signedMetadata by remember(postureEpoch) { mutableStateOf(store.signedRuleMetadata()) }
    val runtimeFacts = remember(postureEpoch) { PhoneCoreRuntimeFacts.read(context.applicationContext) }
    val contactsReady = remember(postureEpoch) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val manualPrefixes = remember(snapshot) { store.manualBlockedPrefixes() }

    val arcepStatus = PhoneProtectionListTruth.status(
        PhoneProtectionListTruth.SourceFacts(
            packagePresent = true,
            verified = true,
            enabledByUser = snapshot.arcepVerifiedBlockingEnabled,
            itemCount = ArcepVerifiedPrefixCatalog.entries.size
        ),
        now
    )
    val signedStatus = PhoneProtectionListTruth.status(
        PhoneProtectionListTruth.SourceFacts(
            packagePresent = signedMetadata.persistedAfterVerification,
            verified = signedMetadata.persistedAfterVerification,
            enabledByUser = true,
            itemCount = signedMetadata.storedPrefixCount,
            expiresAtMs = signedMetadata.expiresAtMs
        ),
        now
    )

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Listes de protection",
                subtitle = "Règles, provenance et état réel",
                onBack = { navController.navigateUp() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SentinelHero(
                eyebrow = "Phone Core",
                title = "Ce qui protège réellement vos appels",
                body = "Chaque état ci-dessous est calculé depuis les données locales et les rôles Android observés. Une source absente, expirée ou non vérifiée n'est jamais affichée comme active.",
                badges = listOf(
                    "Zéro faux vert" to SentinelD1.Cyan,
                    "Données privées locales" to SentinelD1.Success
                )
            )

            SectionTitle("Vos règles privées")
            ProtectionListCard(
                title = "Numéros bloqués par vous",
                type = "BLOQUER",
                status = if (snapshot.blockedNumberHashes.isEmpty()) "VIDE" else "LOCAL",
                itemCount = snapshot.blockedNumberHashes.size,
                details = "Les numéros exacts ne sont pas conservés en clair. Sentinel n'affiche ici que le nombre d'empreintes actives."
            )
            ProtectionListCard(
                title = "Préfixes bloqués par vous",
                type = "BLOQUER",
                status = if (manualPrefixes.isEmpty()) "VIDE" else "LOCAL",
                itemCount = manualPrefixes.size,
                details = "Règles explicitement créées sur cet appareil. Elles peuvent bloquer les appels correspondants."
            )

            SectionTitle("Sources de confiance")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("ARCEP · numéros polyvalents vérifiés", fontWeight = FontWeight.Bold)
                            Text("BLOQUER · Téléphone · source officielle intégrée", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusText(arcepStatus)
                    }
                    Text("Version source : plan de numérotation effectif au 01/01/2026", style = MaterialTheme.typography.bodySmall)
                    Text("${ArcepVerifiedPrefixCatalog.entries.size} préfixe(s) officiel(s)", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "L'appartenance à une tranche ARCEP ne prouve ni une fraude ni l'identité de l'appelant. Le blocage reste volontaire.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (snapshot.arcepVerifiedBlockingEnabled) "Blocage ARCEP activé" else "Blocage ARCEP désactivé")
                        Switch(
                            checked = snapshot.arcepVerifiedBlockingEnabled,
                            onCheckedChange = { enabled ->
                                val changed = store.setArcepVerifiedBlockingEnabled(enabled)
                                snapshot = store.snapshot()
                                actionStatus = if (changed) {
                                    if (enabled) "Liste ARCEP activée." else "Liste ARCEP désactivée."
                                } else {
                                    "Impossible de modifier la liste ARCEP."
                                }
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Réputation Sentinel signée", fontWeight = FontWeight.Bold)
                            Text("SILENCIER · Téléphone · paquet signé", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusText(signedStatus)
                    }
                    Text("Éléments stockés : ${signedMetadata.storedPrefixCount}", style = MaterialTheme.typography.bodySmall)
                    signedMetadata.acceptedSequence?.let { Text("Séquence acceptée : $it", style = MaterialTheme.typography.bodySmall) }
                    signedMetadata.packageId?.let { Text("Paquet : $it", style = MaterialTheme.typography.bodySmall) }
                    signedMetadata.issuerId?.let { Text("Émetteur : $it", style = MaterialTheme.typography.bodySmall) }
                    signedMetadata.keyId?.let { Text("Clé : $it", style = MaterialTheme.typography.bodySmall) }
                    signedMetadata.issuedAtMs?.let { Text("Émis : ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                    signedMetadata.expiresAtMs?.let { Text("Expire : ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        if (CallRuleSyncConfig.SYNC_ENABLED && CallRuleSyncConfig.TRUSTED_KEYS.isNotEmpty()) {
                            "Canal de mise à jour configuré."
                        } else {
                            "Canal de mise à jour non provisionné : aucune source distante n'est présentée comme active."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Une réputation signée peut seulement mettre en silencieux. Elle ne devient jamais, à elle seule, un blocage automatique.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle("Réputation communautaire")
            ProtectionListCard(
                title = "Consensus communautaire",
                type = "IDENTIFIER / ALERTER",
                status = "NON ÉVALUÉ",
                itemCount = 0,
                details = "Le consensus n'est affiché que pour un numéro réellement interrogé. Un signal isolé reste 'Signalement insuffisant' et ne peut jamais déclencher un blocage automatique."
            )

            HorizontalDivider()
            SectionTitle("Dépendances Android observées")
            SystemFactRow("Rôle Dialer", runtimeFacts.dialerRoleHeld)
            SystemFactRow("Rôle Call Screening", runtimeFacts.callScreeningRoleHeld)
            SystemFactRow("Rôle SMS", runtimeFacts.smsRoleHeld)
            SystemFactRow("Contacts", contactsReady)
            SystemFactRow("Notifications Phone Core", runtimeFacts.notificationChannelsReady)
            Text(
                "Optimisation batterie : non utilisée comme prérequis pour le filtrage CallScreening de cet écran.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configurer les prérequis Phone Core")
            }

            actionStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ProtectionListCard(
    title: String,
    type: String,
    status: String,
    itemCount: Int,
    details: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelMedium)
            }
            Text("$type · Téléphone · $itemCount élément(s)", style = MaterialTheme.typography.bodySmall)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusText(status: PhoneProtectionListTruth.ListStatus) {
    val label = when (status) {
        PhoneProtectionListTruth.ListStatus.ACTIVE -> "ACTIF"
        PhoneProtectionListTruth.ListStatus.DISABLED -> "DÉSACTIVÉ"
        PhoneProtectionListTruth.ListStatus.EXPIRED -> "EXPIRÉ"
        PhoneProtectionListTruth.ListStatus.UNAVAILABLE -> "INDISPONIBLE"
    }
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SystemFactRow(label: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            if (ready) "PRÊT" else "À CONFIGURER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
