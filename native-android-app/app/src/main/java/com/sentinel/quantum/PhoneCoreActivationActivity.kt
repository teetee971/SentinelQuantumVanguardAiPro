package com.sentinel.quantum

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

/**
 * User-driven activation and device-test center for Phone Core.
 *
 * Roles and dangerous permissions are never silently granted. Every transition goes through
 * Android's system UI and the screen reports only runtime state observed after the user returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
class PhoneCoreActivationActivity : ComponentActivity() {

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun holdsRole(role: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return when (role) {
            RoleManager.ROLE_DIALER ->
                getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
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
                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { epoch++ }
                val permissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { epoch++ }

                val state = remember(epoch) { readState() }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PHONE CORE", fontWeight = FontWeight.ExtraBold)
                                    Text("Centre d’activation & test", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF17232D))
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SENTINEL PHONE CORE", color = Color(0xFF66C7FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Préparer le téléphone pour un test réel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Activez les rôles Android ci-dessous. Sentinel ne marque une capacité comme prête que lorsque le rôle et les permissions nécessaires sont réellement observés sur cet appareil.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusChip(if (state.callsReady) "APPELS PRÊTS" else "APPELS À ACTIVER", state.callsReady)
                                    StatusChip(if (state.smsReady) "SMS PRÊTS" else "SMS À ACTIVER", state.smsReady)
                                }
                            }
                        }

                        SectionTitle("Appels")
                        CapabilityCard(
                            icon = Icons.Default.Call,
                            title = "Téléphone par défaut",
                            detail = "Permet à Sentinel de composer les appels et d’afficher son interface pendant les appels entrants/sortants.",
                            ready = state.dialerRole && state.callPermission,
                            status = if (!state.dialerRole) "Rôle Téléphone requis" else if (!state.callPermission) "Permission d’appel requise" else "Prêt pour test appareil",
                            actionLabel = when {
                                !state.dialerRole -> "Choisir Sentinel comme téléphone"
                                !state.callPermission -> "Autoriser les appels"
                                else -> null
                            },
                            onAction = {
                                when {
                                    !state.dialerRole -> roleIntent(RoleManager.ROLE_DIALER)?.let(roleLauncher::launch)
                                    !state.callPermission -> permissionsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                }
                            }
                        )
                        CapabilityCard(
                            icon = Icons.Default.Security,
                            title = "Filtrage des appels",
                            detail = "Active CallScreeningService pour appliquer les règles locales avant l’affichage de l’appel.",
                            ready = state.callScreeningRole,
                            status = when {
                                state.callScreeningRole -> "Filtrage système actif"
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Activation guidée disponible à partir d’Android 10"
                                else -> "Rôle de filtrage requis"
                            },
                            actionLabel = if (!state.callScreeningRole && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Activer le filtrage" else null,
                            onAction = {
                                roleIntent(RoleManager.ROLE_CALL_SCREENING)?.let(roleLauncher::launch)
                            }
                        )

                        SectionTitle("Messages")
                        CapabilityCard(
                            icon = Icons.Default.Message,
                            title = "SMS par défaut",
                            detail = "Permet l’envoi via SmsManager, la réception SMS_DELIVER et l’accès local aux conversations.",
                            ready = state.smsRole && state.smsPermissions,
                            status = when {
                                !state.smsRole -> "Rôle SMS requis"
                                !state.smsPermissions -> "Permissions SMS requises"
                                else -> "Prêt pour test SMS réel"
                            },
                            actionLabel = when {
                                !state.smsRole -> "Choisir Sentinel pour les SMS"
                                !state.smsPermissions -> "Autoriser SMS et SIM"
                                else -> null
                            },
                            onAction = {
                                when {
                                    !state.smsRole -> roleIntent(RoleManager.ROLE_SMS)?.let(roleLauncher::launch)
                                    !state.smsPermissions -> permissionsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.SEND_SMS,
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.READ_SMS,
                                            Manifest.permission.READ_PHONE_STATE
                                        )
                                    )
                                }
                            }
                        )

                        SectionTitle("Données locales facultatives")
                        CapabilityCard(
                            icon = Icons.Default.Contacts,
                            title = "Contacts & historique",
                            detail = "Utilisés uniquement localement pour afficher vos contacts et vos appels récents dans le composeur Sentinel.",
                            ready = state.contactsPermission && state.callLogPermission,
                            status = when {
                                state.contactsPermission && state.callLogPermission -> "Accès local prêt"
                                !state.dialerRole -> "Contacts disponibles séparément · rôle Téléphone requis pour l’historique"
                                else -> "Autorisations facultatives manquantes"
                            },
                            actionLabel = if (!state.contactsPermission || !state.callLogPermission) "Autoriser localement" else null,
                            onAction = {
                                permissionsLauncher.launch(
                                    buildList {
                                        if (!state.contactsPermission) add(Manifest.permission.READ_CONTACTS)
                                        if (state.dialerRole && !state.callLogPermission) add(Manifest.permission.READ_CALL_LOG)
                                    }.toTypedArray()
                                )
                            }
                        )

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Test immédiat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Après activation, ouvrez le composeur et la messagerie pour effectuer les essais réels.", style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, SentinelDialerActivity::class.java)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tester appels & contacts")
                                }
                                OutlinedButton(
                                    onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, SmsComposeActivity::class.java)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tester SMS")
                                }
                            }
                        }

                        Text(
                            "Réception d’appels : possible lorsque Sentinel détient réellement le rôle Téléphone et qu’Android lie SentinelInCallService. Envoi/réception SMS : possible lorsque Sentinel est l’application SMS par défaut avec les permissions accordées. Les MMS avec pièces jointes complètes restent en validation et ne sont pas présentés comme finalisés.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    private fun readState(): RuntimeState {
        val dialer = holdsRole(RoleManager.ROLE_DIALER)
        val screening = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) holdsRole(RoleManager.ROLE_CALL_SCREENING) else false
        val sms = holdsRole(RoleManager.ROLE_SMS)
        val call = hasPermission(Manifest.permission.CALL_PHONE)
        val smsPermissions = hasPermission(Manifest.permission.SEND_SMS) &&
            hasPermission(Manifest.permission.RECEIVE_SMS) &&
            hasPermission(Manifest.permission.READ_SMS) &&
            hasPermission(Manifest.permission.READ_PHONE_STATE)
        return RuntimeState(
            dialerRole = dialer,
            callScreeningRole = screening,
            smsRole = sms,
            callPermission = call,
            smsPermissions = smsPermissions,
            contactsPermission = hasPermission(Manifest.permission.READ_CONTACTS),
            callLogPermission = hasPermission(Manifest.permission.READ_CALL_LOG)
        )
    }

    private data class RuntimeState(
        val dialerRole: Boolean,
        val callScreeningRole: Boolean,
        val smsRole: Boolean,
        val callPermission: Boolean,
        val smsPermissions: Boolean,
        val contactsPermission: Boolean,
        val callLogPermission: Boolean
    ) {
        val callsReady: Boolean get() = dialerRole && callPermission
        val smsReady: Boolean get() = smsRole && smsPermissions
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun CapabilityCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    ready: Boolean,
    status: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(status, color = if (ready) Color(0xFF32D6A0) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                if (ready) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF32D6A0))
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
            if (actionLabel != null) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = (if (ready) Color(0xFF32D6A0) else Color(0xFFFFB74D)).copy(alpha = 0.14f)
    ) {
        Text(
            label,
            color = if (ready) Color(0xFF32D6A0) else Color(0xFFFFB74D),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
