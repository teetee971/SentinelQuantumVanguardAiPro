package com.sentinel.quantum.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.SmsClientCapability
import com.sentinel.quantum.security.SmsRoleMigrationPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultSmsStatusScreen(navController: NavController) {
    val context = LocalContext.current
    val roleHeld = remember(context) { holdsSmsRole(context) }
    val assessment = remember(roleHeld) {
        SmsRoleMigrationPolicy.assess(
            availableCapabilities = SmsRoleMigrationPolicy.implementedCapabilities,
            physicalDeviceValidationPassed = false,
            playPolicyReviewReady = false,
            isDefaultSmsHandler = roleHeld
        )
    }

    val labels = remember {
        mapOf(
            SmsClientCapability.RECEIVE_SMS to "Réception SMS",
            SmsClientCapability.READ_CONVERSATIONS to "Lecture des conversations",
            SmsClientCapability.SEND_SMS to "Envoi SMS",
            SmsClientCapability.NOTIFICATIONS to "Notifications locales",
            SmsClientCapability.MMS_ATTACHMENTS to "Pièces jointes MMS",
            SmsClientCapability.EMERGENCY_MESSAGES to "Comportement numéros d’urgence",
            SmsClientCapability.MULTI_SIM to "Sélection multi-SIM",
            SmsClientCapability.LOCAL_RETENTION to "Conservation locale",
            SmsClientCapability.EXPORT_AND_DELETE to "Export et suppression",
            SmsClientCapability.OFFLINE_ANALYSIS to "Analyse anti-fraude hors ligne"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application SMS par défaut") },
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
            Text(
                "État du client SMS Sentinel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Rôle Android : " + if (roleHeld) "ACTIF" else "NON ACTIVÉ",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Étape de migration : ${assessment.stage}")
                    Text(
                        "Sentinel ne demandera pas le rôle SMS tant que toutes les capacités requises, les tests physiques et la préparation Play ne sont pas validés.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                "Capacités",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            SmsClientCapability.entries.forEach { capability ->
                val implemented = capability in SmsRoleMigrationPolicy.implementedCapabilities
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(labels[capability] ?: capability.name)
                        Text(
                            if (implemented) "INTÉGRÉ" else "BLOQUANT",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!assessment.roleRequestAllowed) {
                Text(
                    "Activation système indisponible : aucun bouton ne contourne la politique de sécurité.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Les permissions SMS restent liées au rôle Android choisi par l’utilisateur. Sentinel ne doit jamais devenir l’application SMS par défaut silencieusement.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun holdsSmsRole(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val manager = context.getSystemService(RoleManager::class.java)
        manager.isRoleAvailable(RoleManager.ROLE_SMS) &&
            manager.isRoleHeld(RoleManager.ROLE_SMS)
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}
