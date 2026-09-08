package com.sentinel.quantum.ui.screens

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncClient
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.OkHttpCallRulePackageTransport
import com.sentinel.quantum.security.SignedCallRulePackageVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { CallBlocklistStore(context) }
    val roleSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var number by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var roleHeld by remember { mutableStateOf(isCallScreeningRoleHeld(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleHeld = result.resultCode == Activity.RESULT_OK && isCallScreeningRoleHeld(context)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Blocage d'appels local") }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Text("←") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Les règles restent sur l'appareil. Les numéros exacts sont protégés par une empreinte HMAC liée au Keystore Android. Aucun contact ni journal d'appels n'est collecté.")
            Text(if (roleHeld) "Protection système activée" else "Protection système non activée", fontWeight = FontWeight.Bold)
            if (isCallScreeningRoleAvailable(context) && !roleHeld) {
                Button(onClick = { requestCallScreeningRole(context)?.let { intent -> roleLauncher.launch(intent) } },
                    modifier = Modifier.fillMaxWidth()) { Text("Activer le filtrage Android") }
            } else if (!roleSupported) {
                Text("L'activation guidée nécessite Android 10 ou une version ultérieure.")
            }

            HorizontalDivider()
            Text("Bloquer un numéro exact", fontWeight = FontWeight.Bold)
            OutlinedTextField(number, { number = it.take(64) }, label = { Text("Numéro") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                status = if (store.addBlockedNumber(number)) "Numéro ajouté sous forme d'empreinte." else "Numéro invalide ou limite atteinte."
                snapshot = store.snapshot(); number = ""
            }, enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Ajouter") }
            Text("${snapshot.blockedNumberHashes.size} règle(s) exacte(s) locale(s)")
            if (snapshot.blockedNumberHashes.isNotEmpty()) {
                TextButton(onClick = {
                    status = if (store.clearBlockedNumbers()) "Toutes les règles exactes ont été effacées." else "Échec de l'effacement."
                    snapshot = store.snapshot()
                }, modifier = Modifier.fillMaxWidth()) { Text("Effacer toutes les règles exactes") }
            }

            HorizontalDivider()
            Text("Bloquer un préfixe personnalisé", fontWeight = FontWeight.Bold)
            OutlinedTextField(prefix, { prefix = it.take(24) }, label = { Text("Préfixe") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                status = if (store.addBlockedPrefix(prefix)) "Préfixe ajouté." else "Préfixe invalide ou limite atteinte."
                snapshot = store.snapshot(); prefix = ""
            }, enabled = prefix.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Ajouter le préfixe") }
            snapshot.blockedPrefixes.sorted().forEach { value ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(value)
                    TextButton(onClick = { store.removeBlockedPrefix(value); snapshot = store.snapshot() }) { Text("Retirer") }
                }
            }
            Text("${snapshot.signedSilencePrefixes.size} règle(s) de vigilance signée(s) active(s)",
                style = MaterialTheme.typography.bodySmall)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Les listes de réputation valides sont seulement mises en silencieux. Seules vos règles explicites bloquent automatiquement.",
                style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Mises à jour de vigilance signées", fontWeight = FontWeight.Bold)
            if (CallRuleSyncConfig.SYNC_ENABLED) {
                Button(onClick = {
                    scope.launch {
                        isSyncing = true
                        try {
                            syncStatus = withContext(Dispatchers.IO) {
                                runCatching {
                                    val verifier = SignedCallRulePackageVerifier(
                                        CallRuleSyncConfig.TRUSTED_KEYS,
                                        CallRuleSyncConfig.EXPECTED_ISSUER_ID
                                    )
                                    val transport = OkHttpCallRulePackageTransport(
                                        CallRuleSyncConfig.ENDPOINT,
                                        CallRuleSyncConfig.ALLOWED_HOSTS
                                    )
                                    CallRuleSyncClient(transport, store, verifier).synchronize()
                                }.fold(
                                    onSuccess = { result -> result.reason },
                                    onFailure = { "Échec de la synchronisation." }
                                )
                            }
                            snapshot = store.snapshot()
                        } finally {
                            isSyncing = false
                        }
                    }
                }, enabled = !isSyncing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSyncing) "Vérification en cours..." else "Vérifier les mises à jour de vigilance")
                }
                syncStatus?.let { Text("Résultat : $it", style = MaterialTheme.typography.bodySmall) }
            } else {
                Text(
                    "Cette synchronisation n'est pas encore activée : aucun émetteur ni clé de confiance " +
                        "de production n'est provisionné pour l'instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return context.getSystemService(RoleManager::class.java)
        .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private fun isCallScreeningRoleAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return context.getSystemService(RoleManager::class.java)
        .isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
}

private fun requestCallScreeningRole(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return context.getSystemService(RoleManager::class.java)
        .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
