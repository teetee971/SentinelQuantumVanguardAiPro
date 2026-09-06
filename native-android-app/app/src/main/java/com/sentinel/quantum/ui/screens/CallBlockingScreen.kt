package com.sentinel.quantum.ui.screens

import android.app.Activity
import android.app.role.RoleManager
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { CallBlocklistStore(context) }
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService(RoleManager::class.java) else null
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var number by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var roleHeld by remember { mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleHeld = result.resultCode == Activity.RESULT_OK && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Blocage d'appels local") }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Text("←") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Les règles restent sur l'appareil. Les numéros exacts sont stockés sous empreinte SHA-256. Aucun contact ni journal d'appels n'est collecté.")
            Text(if (roleHeld) "Protection système activée" else "Protection système non activée", fontWeight = FontWeight.Bold)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roleHeld) {
                Button(onClick = { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Activer le filtrage Android") }
            } else if (roleManager == null) {
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
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Les préfixes de vigilance embarqués sont seulement mis en silencieux. Seules vos règles explicites bloquent automatiquement.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
