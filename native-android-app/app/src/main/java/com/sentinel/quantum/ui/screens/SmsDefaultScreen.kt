package com.sentinel.quantum.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.sentinel.quantum.sms.SmsRepository
import com.sentinel.quantum.sms.SmsRoleController
import com.sentinel.quantum.security.SmsRoleMigrationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsDefaultScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember(context) { SmsRepository(context) }
    val scope = rememberCoroutineScope()
    var isDefault by remember { mutableStateOf(SmsRoleController.isHeld(context)) }
    var messages by remember { mutableStateOf<List<SmsRepository.Message>>(emptyList()) }
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val migrationAssessment = SmsRoleMigrationPolicy.assess(
        availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
        physicalDeviceValidationPassed = false,
        playPolicyReviewReady = false,
        isDefaultSmsHandler = isDefault
    )

    fun refresh() {
        scope.launch {
            messages = withContext(Dispatchers.IO) { repository.recent(50) }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        status = "Autorisations SMS mises à jour."
        refresh()
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isDefault = result.resultCode == Activity.RESULT_OK && SmsRoleController.isHeld(context)
        status = if (isDefault) "Sentinel est maintenant l’application SMS par défaut." else "Rôle SMS non accordé."
        if (isDefault) {
            val activeAssessment = SmsRoleMigrationPolicy.assess(
                availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
                physicalDeviceValidationPassed = false,
                playPolicyReviewReady = false,
                isDefaultSmsHandler = true
            )
            if (activeAssessment.smsPermissionsAllowed) {
                val permissions = buildList {
                    add(Manifest.permission.READ_SMS)
                    add(Manifest.permission.RECEIVE_SMS)
                    add(Manifest.permission.RECEIVE_MMS)
                    add(Manifest.permission.SEND_SMS)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
                permissionsLauncher.launch(permissions)
            }
        }
        refresh()
    }

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
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isDefault) "Rôle SMS : ACTIF" else "Rôle SMS : non actif",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Le rôle est toujours choisi ou retiré par Android. Sentinel n’accède au provider SMS que lorsqu’il est le gestionnaire par défaut."
            )

            if (!SmsRoleController.isTelephonyAvailable(context)) {
                Text("Cet appareil ne déclare pas de fonction téléphonie/SMS.")
            } else if (!isDefault) {
                Button(
                    onClick = {
                        SmsRoleController.createRequestIntent(context)?.let(roleLauncher::launch)
                    },
                    enabled = migrationAssessment.roleRequestAllowed,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choisir Sentinel comme app SMS par défaut") }
            } else {
                val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
                val sendGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                    PackageManager.PERMISSION_GRANTED
                if (!readGranted || !sendGranted) {
                    Button(
                        onClick = {
                            val permissions = buildList {
                                add(Manifest.permission.READ_SMS)
                                add(Manifest.permission.RECEIVE_SMS)
                                add(Manifest.permission.RECEIVE_MMS)
                                add(Manifest.permission.SEND_SMS)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                            permissionsLauncher.launch(permissions)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Autoriser lecture et envoi SMS") }
                }

                HorizontalDivider()
                Text("Nouveau SMS", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    recipient,
                    { recipient = it.take(64) },
                    label = { Text("Destinataire") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    body,
                    { body = it.take(5_000) },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        val sent = repository.send(recipient, body)
                        status = if (sent) "SMS remis au système pour envoi." else "Envoi impossible."
                        if (sent) body = ""
                        refresh()
                    },
                    enabled = sendGranted && recipient.isNotBlank() && body.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Envoyer le SMS") }

                Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Actualiser les conversations")
                }

                HorizontalDivider()
                Text("Messages récents", style = MaterialTheme.typography.titleMedium)
                if (messages.isEmpty()) {
                    Text("Aucun message chargé ou permission de lecture indisponible.")
                }
                messages.take(50).forEach { message ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(message.address.ifBlank { "Expéditeur inconnu" }, style = MaterialTheme.typography.titleSmall)
                            Text(message.body)
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(message.date)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(onClick = {
                                    if (repository.delete(message.id)) {
                                        status = "Message supprimé."
                                        refresh()
                                    }
                                }) { Text("Supprimer") }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MMS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "La réception WAP/MMS requise par Android est déclarée et les PDU entrants sont conservés de façon bornée dans le stockage privé. L’affichage complet des MMS et pièces jointes reste en validation et n’est pas présenté comme terminé."
                    )
                }
            }
            status?.let { Text(it) }
        }
    }
}
