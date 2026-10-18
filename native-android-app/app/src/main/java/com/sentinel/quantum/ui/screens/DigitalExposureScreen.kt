package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.PwnedPasswordClient
import com.sentinel.quantum.ui.design.SentinelEvidenceEdge
import com.sentinel.quantum.ui.design.SentinelEvidenceGraph
import com.sentinel.quantum.ui.design.SentinelEvidenceNode
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalExposureScreen(navController: NavController) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PwnedPasswordClient.Result?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val client = remember { PwnedPasswordClient() }
    val motionLevel = remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        SentinelMotionPolicy.resolve(animatorScale, powerSave)
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Empreinte numérique",
                subtitle = "Preuves d’exposition & capacités réelles",
                onBack = { navController.navigateUp() }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SentinelHeroVisual(
                status = SentinelStatus.LIMITED,
                title = "Empreinte numérique",
                subtitle = "Sentinel vérifie actuellement les mots de passe compromis par k-anonymat. La veille e-mail et domaine reste verrouillée tant qu’un backend autorisé n’est pas configuré.",
                modifier = Modifier.testTag("exposure-command-center"),
                motionLevel = motionLevel
            )

            ExposureCapabilityGraph(result = result)

            SentinelSectionHeader(
                title = "Mot de passe compromis",
                subtitle = "Contrôle disponible · source externe interrogée sans transmettre le secret."
            )

            SentinelGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exposure-password-check")
            ) {
                Text(
                    "Vérification k-anonyme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Le mot de passe est haché localement. Sentinel transmet uniquement les 5 premiers caractères du SHA-1 au service Pwned Passwords, puis compare le suffixe sur l’appareil. Le mot de passe et son hash complet ne sont jamais envoyés.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(256) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mot de passe à vérifier") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotEmpty() && !running,
                    onClick = {
                        val candidate = password
                        password = ""
                        result = null
                        status = null
                        running = true
                        scope.launch {
                            val checked = withContext(Dispatchers.IO) {
                                runCatching { client.check(candidate) }
                            }
                            candidate.toCharArray().fill('\u0000')
                            running = false
                            checked.onSuccess { result = it }
                                .onFailure {
                                    status = "Service d’exposition indisponible : " +
                                        (it.message ?: "erreur réseau")
                                }
                        }
                    }
                ) {
                    Text(if (running) "Vérification…" else "Vérifier maintenant")
                }
            }

            result?.let { exposure ->
                val resultStatus = if (exposure.exposed) SentinelStatus.BAD else SentinelStatus.UNKNOWN
                SentinelGlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (exposure.exposed) "Mot de passe exposé" else "Aucune correspondance trouvée",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        SentinelStatusChip(
                            status = resultStatus,
                            label = if (exposure.exposed) "EXPOSÉ" else "NON PROUVÉ"
                        )
                    }
                    if (exposure.exposed) {
                        Text("Occurrences connues : ${exposure.occurrenceCount}")
                        Text(
                            "Action recommandée : remplacez ce mot de passe partout où il est utilisé, choisissez un secret unique et activez la MFA ou une passkey lorsque disponible.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Aucune correspondance dans la base interrogée. Cela ne prouve pas que le mot de passe est sûr, secret ou absent d’autres sources de fuite.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            SentinelGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exposure-email-watch")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Veille e-mail / domaine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    SentinelStatusChip(status = SentinelStatus.LOCKED, label = "LOCKED")
                }
                Text(
                    "Backend autorisé requis. Aucun scan d’adresse e-mail, de domaine ou de source dite « dark web » n’est simulé depuis l’APK.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "L’activation future devra utiliser un fournisseur autorisé, une vérification explicite de l’adresse et des secrets conservés côté serveur.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExposureCapabilityGraph(result: PwnedPasswordClient.Result?) {
    val passwordStatus = when {
        result?.exposed == true -> SentinelStatus.BAD
        result != null -> SentinelStatus.UNKNOWN
        else -> SentinelStatus.LIMITED
    }
    val nodes = listOf(
        SentinelEvidenceNode(
            id = "identity",
            label = "Identité locale",
            detail = "Point de convergence local",
            status = SentinelStatus.LIMITED,
            source = "appareil",
            freshness = "session"
        ),
        SentinelEvidenceNode(
            id = "password",
            label = "Mot de passe compromis",
            detail = if (result == null) "Contrôle k-anonyme disponible" else "Résultat observé ci-dessous",
            status = passwordStatus,
            source = "Pwned Passwords",
            freshness = if (result == null) "non exécuté" else "session"
        ),
        SentinelEvidenceNode(
            id = "email",
            label = "E-mail / domaine",
            detail = "Fournisseur autorisé requis",
            status = SentinelStatus.LOCKED,
            source = "aucune",
            freshness = "non configuré"
        ),
        SentinelEvidenceNode(
            id = "profiles",
            label = "Téléphone / profils publics",
            detail = "Capacité non raccordée sur cet écran",
            status = SentinelStatus.UNKNOWN,
            source = "aucune",
            freshness = "non analysé"
        )
    )
    val edges = listOf(
        SentinelEvidenceEdge(
            from = "identity",
            to = "password",
            proofReference = result?.let { "pwned-password-result" }
        ),
        SentinelEvidenceEdge(from = "identity", to = "email"),
        SentinelEvidenceEdge(from = "identity", to = "profiles")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Graphe des preuves",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Une liaison cyan n’apparaît que lorsqu’une référence de preuve existe. Une capacité verrouillée reste séparée de toute exposition supposée.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SentinelEvidenceGraph(
            nodes = nodes,
            edges = edges,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
