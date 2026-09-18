package com.sentinel.quantum.ui.screens

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.security.SmsMigrationStage
import com.sentinel.quantum.security.SmsRoleMigrationPolicy
import com.sentinel.quantum.sms.SmsProviderStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultSmsScreen(navController: NavController) {
    val context = LocalContext.current
    val analyzer = remember(context) { SmsLinkAnalyzer(LocalLogger(context)) }
    var roleHeld by remember { mutableStateOf(SmsProviderStore.isDefaultSmsHandler(context)) }
    var permissionsGranted by remember { mutableStateOf(SmsProviderStore.hasRuntimeSmsPermissions(context)) }
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(if (roleHeld && permissionsGranted) SmsProviderStore.recentMessages(context, 50) else emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsGranted = SmsProviderStore.hasRuntimeSmsPermissions(context)
        status = if (permissionsGranted) {
            messages = SmsProviderStore.recentMessages(context, 50)
            "Permissions SMS actives tant que Sentinel reste l’application SMS par défaut."
        } else {
            "Permissions SMS incomplètes. Réessayez depuis les paramètres Android si vous souhaitez utiliser le client SMS."
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleHeld = result.resultCode == Activity.RESULT_OK && SmsProviderStore.isDefaultSmsHandler(context)
        if (roleHeld) {
            permissionLauncher.launch(SMS_RUNTIME_PERMISSIONS)
        } else {
            status = "Sentinel n’a pas obtenu le rôle SMS. Le scanner manuel reste disponible sans accès à la boîte SMS."
        }
    }

    val assessment = SmsRoleMigrationPolicy.assess(
        availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
        physicalDeviceValidationPassed = false,
        playPolicyReviewReady = false,
        isDefaultSmsHandler = roleHeld
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messagerie SMS Sentinel") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
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
            Text("Mode application SMS par défaut", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Sentinel peut recevoir et envoyer des SMS uniquement après votre choix explicite comme application SMS par défaut. " +
                    "Les permissions SMS ne sont demandées qu’après ce choix."
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rôle Android : " + if (roleHeld) "ACTIF" else "INACTIF", fontWeight = FontWeight.Bold)
                    Text("Permissions SMS : " + if (permissionsGranted) "ACTIVES" else "INACTIVES")
                    Text("Étape : ${assessment.stage}")
                    Text(
                        "Publication Play : non validée tant que les tests physiques SMS/MMS, double SIM et la déclaration Play ne sont pas terminés.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!roleHeld && assessment.stage == SmsMigrationStage.ELIGIBLE_FOR_ROLE_REQUEST) {
                Button(
                    onClick = {
                        val intent = requestSmsRoleIntent(context)
                        if (intent != null) roleLauncher.launch(intent)
                        else status = "Le rôle SMS n’est pas disponible sur cet appareil."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Définir Sentinel comme application SMS") }
            } else if (roleHeld && !permissionsGranted) {
                Button(
                    onClick = { permissionLauncher.launch(SMS_RUNTIME_PERMISSIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Autoriser les permissions SMS") }
            }

            if (roleHeld && permissionsGranted) {
                OutlinedTextField(
                    recipient,
                    { recipient = it.take(64) },
                    label = { Text("Destinataire") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    body,
                    { body = it.take(SmsProviderStore.MAX_BODY_LENGTH) },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        status = if (SmsProviderStore.sendText(context, recipient, body)) {
                            body = ""
                            messages = SmsProviderStore.recentMessages(context, 50)
                            "Message remis au système radio pour envoi."
                        } else {
                            "Échec d’envoi."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = recipient.isNotBlank() && body.isNotBlank()
                ) { Text("Envoyer le SMS") }

                OutlinedButton(
                    onClick = { messages = SmsProviderStore.recentMessages(context, 50) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Actualiser les messages") }

                Text("Messages récents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                messages.forEach { message ->
                    val analysis = remember(message.id, message.body) { analyzer.analyze(message.body) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(message.address.ifBlank { "Inconnu" }, fontWeight = FontWeight.Bold)
                                Text(DateFormat.getDateTimeInstance().format(Date(message.timestamp)), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(message.body.take(1000))
                            Text(
                                "Analyse locale : ${analysis.riskLevel} — score ${analysis.score}/100",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MMS", fontWeight = FontWeight.Bold)
                    Text(
                        "Le récepteur WAP/MMS requis par Android est présent et conserve les PDU reçus de façon bornée dans l’espace privé de Sentinel. " +
                            "La restitution complète des pièces jointes MMS reste à valider avant publication Play."
                    )
                }
            }
        }
    }
}

private fun requestSmsRoleIntent(context: Context): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
            manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else null
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }
}

private val SMS_RUNTIME_PERMISSIONS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.RECEIVE_MMS,
    Manifest.permission.RECEIVE_WAP_PUSH
)
