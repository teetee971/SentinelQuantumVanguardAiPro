package com.sentinel.quantum

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.sentinel.quantum.security.PhoneCoreDiagnostics
import com.sentinel.quantum.security.SmsActivationActions
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsActivationUiModel
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

/** User-driven activation and device-test center for Phone Core. */
@OptIn(ExperimentalMaterial3Api::class)
class PhoneCoreActivationActivity : ComponentActivity() {
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun holdsRole(role: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return when (role) {
            RoleManager.ROLE_DIALER -> getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
            RoleManager.ROLE_SMS -> Telephony.Sms.getDefaultSmsPackage(this) == packageName
            else -> false
        }
        val manager = getSystemService(RoleManager::class.java)
        return manager.isRoleAvailable(role) && manager.isRoleHeld(role)
    }

    private fun roleIntent(role: String): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return when (role) {
                RoleManager.ROLE_DIALER -> Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                RoleManager.ROLE_SMS -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                else -> null
            }
        }
        val manager = getSystemService(RoleManager::class.java)
        if (!manager.isRoleAvailable(role) || manager.isRoleHeld(role)) return null
        return manager.createRequestRoleIntent(role)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
                var epoch by remember { mutableStateOf(0) }
                var permissionBlocked by remember { mutableStateOf(false) }
                val smsDiagnostics = remember { SmsActivationDiagnostics(applicationContext) }
                val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val fullScreenIntentReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                val smsActions = remember { SmsActivationActions(applicationContext) }
                val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { epoch++ }
                val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { epoch++ }
                val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    permissionBlocked = grants.isNotEmpty() && grants.values.any { !it }
                    epoch++
                }
                val state = remember(epoch) { readState(smsDiagnostics) }
                val smsModel = remember(state.smsSnapshot) { SmsActivationUiModel.from(state.smsSnapshot) }
                val readiness = remember(state) {
                    PhoneCoreDiagnostics.readiness(
                        PhoneCoreDiagnostics.RuntimeFacts(
                            dialerRoleHeld = state.dialerRole,
                            callScreeningRoleHeld = state.callScreeningRole,
                            smsRoleHeld = state.smsSnapshot.blockers.none { it == SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED },
                            callPermissionGranted = state.callPermission,
                            sendSmsPermissionGranted = state.smsSnapshot.blockers.none { it == SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED },
                            readSmsPermissionGranted = state.readSmsPermission,
                            receiveSmsPermissionGranted = hasPermission(Manifest.permission.RECEIVE_SMS),
                            notificationsReady = state.notificationPermissionReady && fullScreenIntentReady,
                            contactsPermissionGranted = state.contactsPermission,
                            callLogPermissionGranted = state.callLogPermission,
                            activeSimVerified = state.smsSnapshot.activeSubscriptionIds.isNotEmpty(),
                            mmsSafePreviewValidated = false,
                            physicalDeviceValidated = false
                        )
                    )
                }

                Scaffold(topBar = {
                    CenterAlignedTopAppBar(
                        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PHONE CORE", fontWeight = FontWeight.ExtraBold)
                            Text("Centre d’activation & test", style = MaterialTheme.typography.labelSmall)
                        } },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "Retour") } }
                    )
                }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SENTINEL PHONE CORE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Préparer le téléphone pour un test réel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text("Chaque état est calculé depuis les rôles, permissions et capacités réellement observés sur cet appareil.", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusChip(if (state.callsReady) "APPELS PRÊTS" else "APPELS À ACTIVER", state.callsReady)
                                    StatusChip("SMS ${smsModel.state.name}", smsModel.state == SmsActivationDiagnostics.State.READY)
                                    StatusChip(
                                        if (readiness.softwarePrerequisitesReady) "LOGICIEL 100 %" else "LOGICIEL À FINALISER",
                                        readiness.softwarePrerequisitesReady
                                    )
                                }
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Validation Phone Core", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            if (readiness.softwarePrerequisitesReady) "ÉTAPE 2/3" else "ÉTAPE 1/3",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Text(
                                    if (readiness.softwarePrerequisitesReady)
                                        "100 % des prérequis logiciels observés. Validation physique encore requise."
                                    else
                                        "Prérequis logiciels incomplets : aucun statut 100 % n’est annoncé.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                readiness.capabilities.filter { it.id != "PHYSICAL_DEVICE" }.forEach {
                                    Text("• ${it.id}: ${it.state.name}", style = MaterialTheme.typography.labelMedium)
                                }
                                 Text("• PHYSICAL_DEVICE: À TESTER SUR APPAREIL", style = MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(
                                    progress = { if (readiness.softwarePrerequisitesReady) 0.66f else 0.33f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "1. Activer les prérequis  →  2. Installer l’APK  →  3. Valider appels/SMS sur appareil",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!state.notificationPermissionReady) {
                                    if (notificationPermissionRequired && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                                        OutlinedButton(
                                            onClick = { permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Autoriser les notifications appels & SMS") }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                settingsLauncher.launch(
                                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Ouvrir les réglages de notifications") }
                                    }
                                }
                                if (!fullScreenIntentReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    OutlinedButton(
                                        onClick = {
                                            settingsLauncher.launch(
                                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                                    .setData(Uri.parse("package:$packageName"))
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Autoriser l’affichage plein écran des appels") }
                                    Text(
                                        "Requis pour présenter de façon fiable l’interface d’appel entrant lorsque l’écran est verrouillé.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        SectionTitle("Appels")
                        CapabilityCard(
                            Icons.Default.Call, "Téléphone par défaut",
                            "Permet à Sentinel de composer les appels et d’afficher son interface pendant les appels entrants/sortants.",
                            state.dialerRole && state.callPermission,
                            if (!state.dialerRole) "Rôle Téléphone requis" else if (!state.callPermission) "Permission d’appel requise" else "Prêt pour test appareil",
                            when { !state.dialerRole -> "Choisir Sentinel comme téléphone"; !state.callPermission -> "Autoriser les appels"; else -> null }
                        ) {
                            when {
                                !state.dialerRole -> roleIntent(RoleManager.ROLE_DIALER)?.let(roleLauncher::launch)
                                !state.callPermission -> permissionsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                            }
                        }
                        CapabilityCard(
                            Icons.Default.Security, "Filtrage des appels",
                            "Active CallScreeningService pour appliquer les règles locales avant l’affichage de l’appel.",
                            state.callScreeningRole,
                            when { state.callScreeningRole -> "Filtrage système actif"; Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Disponible à partir d’Android 10"; else -> "Rôle de filtrage requis" },
                            if (!state.callScreeningRole && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Activer le filtrage" else null
                        ) { roleIntent(RoleManager.ROLE_CALL_SCREENING)?.let(roleLauncher::launch) }

                        SectionTitle("Messages")
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Message, null)
                                    Column(Modifier.weight(1f)) {
                                        Text("SMS par défaut", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(smsModel.title, style = MaterialTheme.typography.labelMedium,
                                            color = if (smsModel.state == SmsActivationDiagnostics.State.READY) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (smsModel.state == SmsActivationDiagnostics.State.READY) Icon(Icons.Default.CheckCircle, "Prêt", tint = MaterialTheme.colorScheme.tertiary)
                                }
                                Text(smsModel.detail, style = MaterialTheme.typography.bodySmall)
                                if (SmsActivationUiModel.Action.REQUEST_SMS_ROLE in smsModel.actions) {
                                    Button(onClick = {
                                        val request = smsActions.roleRequestIntent() ?: smsActions.legacyDefaultAppsIntent()
                                        if (request != null) roleLauncher.launch(request)
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Choisir Sentinel pour les SMS") }
                                }
                                if (SmsActivationUiModel.Action.REQUEST_RUNTIME_PERMISSIONS in smsModel.actions) {
                                    OutlinedButton(onClick = {
                                        val required = smsActions.permissionsFor(state.smsSnapshot)
                                        if (required.isNotEmpty()) permissionsLauncher.launch(required) else epoch++
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Autoriser uniquement les permissions nécessaires") }
                                }
                                if (SmsActivationUiModel.Action.RETRY_SIM_LOOKUP in smsModel.actions) {
                                    OutlinedButton(onClick = { epoch++ }, modifier = Modifier.fillMaxWidth()) { Text("Réessayer la détection SIM") }
                                }
                            }
                        }

                        SectionTitle("Données locales requises pour Phone Core complet")
                        CapabilityCard(
                            Icons.Default.Contacts, "Contacts & historique",
                            "Requis pour valider le Phone Core complet : affichage local des contacts et des appels récents dans le composeur Sentinel.",
                            state.contactsPermission && state.callLogPermission,
                            when { state.contactsPermission && state.callLogPermission -> "Accès local prêt"; !state.dialerRole -> "Contacts séparés · rôle Téléphone requis pour l’historique"; else -> "Autorisations Phone Core manquantes" },
                            if (!state.contactsPermission || !state.callLogPermission) "Autoriser les données locales" else null
                        ) {
                            val optional = buildList {
                                if (!state.contactsPermission) add(Manifest.permission.READ_CONTACTS)
                                if (state.dialerRole && !state.callLogPermission) add(Manifest.permission.READ_CALL_LOG)
                            }.toTypedArray()
                            if (optional.isNotEmpty()) permissionsLauncher.launch(optional)
                        }

                        if (permissionBlocked) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Autorisation bloquée par Android", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                Text("Sentinel ne contourne pas ce contrôle. Vérifiez les autorisations dans les paramètres Android.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les paramètres de Sentinel") }
                            }
                        }

                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Test immédiat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, SentinelDialerActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PhoneInTalk, null); Spacer(Modifier.width(8.dp)); Text("Tester appels & contacts")
                            }
                            OutlinedButton(onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, SmsComposeActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("Tester SMS")
                            }
                        } }
                        Text("READY SMS exige le rôle SMS, les seules permissions runtime requises par le diagnostic et au moins une SIM active vérifiée. Avec plusieurs SIM, la messagerie conserve le sélecteur explicite de ligne. La validation finale reste à effectuer sur appareil physique.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    private fun readState(smsDiagnostics: SmsActivationDiagnostics): RuntimeState {
        val dialer = holdsRole(RoleManager.ROLE_DIALER)
        val screening = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && holdsRole(RoleManager.ROLE_CALL_SCREENING)
        return RuntimeState(
            dialerRole = dialer,
            callScreeningRole = screening,
            callPermission = hasPermission(Manifest.permission.CALL_PHONE),
            contactsPermission = hasPermission(Manifest.permission.READ_CONTACTS),
            callLogPermission = hasPermission(Manifest.permission.READ_CALL_LOG),
            readSmsPermission = hasPermission(Manifest.permission.READ_SMS),
            notificationPermissionReady = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)) &&
                NotificationManagerCompat.from(this).areNotificationsEnabled(),
            smsSnapshot = smsDiagnostics.snapshot()
        )
    }

    private data class RuntimeState(
        val dialerRole: Boolean,
        val callScreeningRole: Boolean,
        val callPermission: Boolean,
        val contactsPermission: Boolean,
        val callLogPermission: Boolean,
        val readSmsPermission: Boolean,
        val notificationPermissionReady: Boolean,
        val smsSnapshot: SmsActivationDiagnostics.Snapshot
    ) { val callsReady: Boolean get() = dialerRole && callPermission }
}

@Composable private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

@Composable private fun CapabilityCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, ready: Boolean, status: String, actionLabel: String?, onAction: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(status, color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
            if (ready) Icon(Icons.Default.CheckCircle, "Prêt", tint = MaterialTheme.colorScheme.tertiary)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall)
        if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
    } }
}

@Composable private fun StatusChip(label: String, ready: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = (if (ready) Color(0xFF32D6A0) else Color(0xFFFFB74D)).copy(alpha = 0.14f)) {
        Text(label, color = if (ready) Color(0xFF32D6A0) else Color(0xFFFFB74D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}
