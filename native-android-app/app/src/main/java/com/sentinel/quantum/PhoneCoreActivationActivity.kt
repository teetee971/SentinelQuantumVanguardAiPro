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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sentinel.quantum.security.PhoneCoreDiagnostics
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SmsNotificationHelper
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.SystemCallLogReader
import com.sentinel.quantum.security.MmsSafePreviewReadiness
import com.sentinel.quantum.security.SmsActivationActions
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsActivationUiModel
import com.sentinel.quantum.security.WifiScanner
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

    private fun currentInstallTimestamp(): Long = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).lastUpdateTime
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }
    }.getOrDefault(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SentinelCallNotificationHelper.ensureChannel(applicationContext)
        SmsNotificationHelper.ensureChannel(applicationContext)
        setContent {
            SentinelQuantumTheme {
                var epoch by remember { mutableStateOf(0) }
                var permissionBlocked by remember { mutableStateOf(false) }
                val smsDiagnostics = remember { SmsActivationDiagnostics(applicationContext) }
                val wifiScanner = remember { WifiScanner(applicationContext) }
                val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val fullScreenIntentReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                val smsActions = remember { SmsActivationActions(applicationContext) }
                val installTimestampMs = remember { currentInstallTimestamp() }
                val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { epoch++ }
                val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { epoch++ }
                val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    permissionBlocked = grants.isNotEmpty() && grants.values.any { !it }
                    epoch++
                }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) epoch++
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val state = remember(epoch) { readState(smsDiagnostics, wifiScanner) }
                val smsModel = remember(state.smsSnapshot) { SmsActivationUiModel.from(state.smsSnapshot) }
                val smsRoleHeld = SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED !in state.smsSnapshot.blockers
                val mmsSafePreviewValidated = remember { MmsSafePreviewReadiness.softwareValidated }
                val physicalEvidence = remember(epoch) {
                    val contactsReady =
                        LocalContactLookup(applicationContext).listWithState(1).state ==
                            LocalContactLookup.ContactAccessState.READY
                    val callHistoryReady =
                        SystemCallLogReader(applicationContext).accessState() ==
                            SystemCallLogReader.AccessState.READY
                    PhoneCorePhysicalValidation.evaluate(
                        events = PhonePrivateTimelineStore(applicationContext).read().events,
                        notBeforeMs = installTimestampMs,
                        contactsProviderReady = contactsReady,
                        callHistoryProviderReady = callHistoryReady
                    )
                }
                val readiness = remember(state, physicalEvidence) {
                    PhoneCoreDiagnostics.readiness(
                        PhoneCoreDiagnostics.RuntimeFacts(
                            dialerRoleHeld = state.dialerRole,
                            callScreeningRoleHeld = state.callScreeningRole,
                            smsRoleHeld = smsRoleHeld,
                            callPermissionGranted = state.callPermission,
                            readPhoneStatePermissionGranted = state.phoneStatePermission,
                            callLineAvailable = state.callLineAvailable,
                            sendSmsPermissionGranted = state.smsSnapshot.blockers.none { it == SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED },
                            readSmsPermissionGranted = state.readSmsPermission,
                            receiveSmsPermissionGranted = hasPermission(Manifest.permission.RECEIVE_SMS),
                            notificationsReady = state.notificationPermissionReady &&
                                state.notificationChannelsReady &&
                                fullScreenIntentReady,
                            contactsPermissionGranted = state.contactsPermission,
                            callLogPermissionGranted = state.callLogPermission,
                            activeSimVerified = state.smsSnapshot.activeSubscriptionIds.isNotEmpty(),
                            receiveMmsPermissionGranted = state.receiveMmsPermission,
                            receiveWapPushPermissionGranted = state.receiveWapPushPermission,
                            mmsSafePreviewValidated = mmsSafePreviewValidated,
                            wifiScanServiceAvailable = state.wifiScanServiceAvailable,
                            wifiScanPermissionGranted = state.wifiScanPermissionGranted,
                            wifiEnabled = state.wifiEnabled,
                            locationEnabledForWifiScan = state.wifiLocationEnabled,
                            physicalDeviceValidated = physicalEvidence.fullyValidated
                        )
                    )
                }

                Scaffold(topBar = {
                    CenterAlignedTopAppBar(
                        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TÉLÉPHONIE", fontWeight = FontWeight.ExtraBold)
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
                                Text("TÉLÉPHONIE SENTINEL", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Préparer le téléphone pour un test réel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text("Chaque état est calculé depuis les rôles, permissions et capacités réellement observés sur cet appareil.", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusChip(if (state.callsReady) "APPELS PRÊTS" else "APPELS À ACTIVER", state.callsReady)
                                    StatusChip("SMS ${PhoneCoreFrenchLabels.smsState(smsModel.state)}", smsModel.state == SmsActivationDiagnostics.State.READY)
                                    StatusChip(
                                        if (readiness.softwarePrerequisitesReady) "LOGICIEL 100 %" else "LOGICIEL À FINALISER",
                                        readiness.softwarePrerequisitesReady
                                    )
                                    StatusChip(
                                        if (physicalEvidence.fullyValidated) "APPAREIL LOCAL ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}"
                                        else "PHYSIQUE LOCAL ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}",
                                        physicalEvidence.fullyValidated
                                    )
                                }
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Validation de la téléphonie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            when {
                                                physicalEvidence.fullyValidated -> "LOCAL VALIDÉ"
                                                readiness.softwarePrerequisitesReady -> "PRÊT TEST"
                                                else -> "ACTIVATION"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Text(
                                    when {
                                        physicalEvidence.fullyValidated && readiness.softwarePrerequisitesReady ->
                                            "Validation de cet appareil complète : ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount} preuves locales observées. Cela ne vaut pas encore « Téléphonie Sentinel 100 % fonctionnelle » : la matrice finale multi-version Android, double-SIM et réversibilité doit encore réussir."
                                        readiness.softwarePrerequisitesReady ->
                                            "100 % des prérequis logiciels observés. Validation physique locale ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}."
                                        else ->
                                            "Prérequis logiciels incomplets : aucun statut 100 % fonctionnel n’est annoncé."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                readiness.capabilities.filter { it.id != "PHYSICAL_DEVICE" }.forEach {
                                    Text("• ${PhoneCoreFrenchLabels.capability(it.id)} : ${PhoneCoreFrenchLabels.diagnosticState(it.state)}", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    "• Preuves sur cet appareil : " + if (physicalEvidence.fullyValidated) "VALIDÉES" else "${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text("  ${if (physicalEvidence.incomingCallConnected) "✓" else "○"} Appel entrant connecté", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.outgoingCallConnected) "✓" else "○"} Appel sortant connecté", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.callScreeningObserved) "✓" else "○"} Filtrage d’appel réellement invoqué", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.contactsProviderReady) "✓" else "○"} Répertoire Android interrogeable", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.callHistoryProviderReady) "✓" else "○"} Historique Android interrogeable", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.incomingSmsReceived) "✓" else "○"} SMS entrant enregistré", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.outgoingSmsSubmitted) "✓" else "○"} SMS sortant : toutes les parties envoyées avec succès", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.outgoingSmsDeliveredSuccessfully) "✓" else "○"} SMS livré : toutes les parties confirmées avec succès", style = MaterialTheme.typography.bodySmall)
                                Text("  ${if (physicalEvidence.incomingMmsSafePreview) "✓" else "○"} MMS entrant aperçu sécurisé", style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress = {
                                        when {
                                            readiness.fullyValidated -> 1f
                                            readiness.softwarePrerequisitesReady -> 0.66f
                                            else -> 0.33f
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "1. Activer les prérequis → 2. Installer l’APK candidate → 3. Observer ${physicalEvidence.requiredCount}/${physicalEvidence.requiredCount} preuves locales → 4. Valider la matrice multi-version + double-SIM → seulement ensuite 100 % fonctionnel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!state.notificationPermissionReady || !state.notificationChannelsReady) {
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
                                        ) {
                                            Text(
                                                if (!state.notificationChannelsReady)
                                                    "Réactiver les canaux Appels & SMS"
                                                else
                                                    "Ouvrir les réglages de notifications"
                                            )
                                        }
                                    }
                                    if (!state.notificationChannelsReady) {
                                        Text(
                                            "Au moins un canal système de téléphonie (appels entrants ou SMS) est désactivé. Le statut logiciel reste bloqué.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
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
                            state.callsReady,
                            when {
                                !state.dialerRole -> "Rôle Téléphone requis"
                                !state.callPermission -> "Permission d’appel requise"
                                !state.phoneStatePermission -> "Permission de détection des lignes requise"
                                !state.callLineAvailable -> "Aucune ligne d’appel active détectée"
                                else -> "Prêt pour test appareil"
                            },
                            when {
                                !state.dialerRole -> "Choisir Sentinel comme téléphone"
                                !state.callPermission -> "Autoriser les appels"
                                !state.phoneStatePermission -> "Autoriser la détection des lignes"
                                !state.callLineAvailable -> "Actualiser les lignes"
                                else -> null
                            }
                        ) {
                            when {
                                !state.dialerRole -> roleIntent(RoleManager.ROLE_DIALER)?.let(roleLauncher::launch)
                                !state.callPermission -> permissionsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                !state.phoneStatePermission -> permissionsLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                                !state.callLineAvailable -> epoch++
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
                        ElevatedCard(
                            Modifier.fillMaxWidth().semantics {
                                stateDescription = if (smsModel.state == SmsActivationDiagnostics.State.READY) "SMS prêt" else "SMS à activer"
                            }
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Message, null)
                                    Column(Modifier.weight(1f)) {
                                        Text("SMS par défaut", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(smsModel.title, style = MaterialTheme.typography.labelMedium,
                                            color = if (smsModel.state == SmsActivationDiagnostics.State.READY) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (smsModel.state == SmsActivationDiagnostics.State.READY) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
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

                        CapabilityCard(
                            Icons.Default.Message, "Réception MMS",
                            "Android doit autoriser la réception des MMS et des messages WAP Push. Le même décodeur sécurisé et limité est auto-testé puis utilisé sur les messages entrants ; les formats non sûrs restent en quarantaine.",
                            smsRoleHeld && state.receiveMmsPermission && state.receiveWapPushPermission && mmsSafePreviewValidated,
                            when {
                                !smsRoleHeld -> "Rôle SMS requis avant les autorisations MMS"
                                !state.receiveMmsPermission || !state.receiveWapPushPermission -> "Autorisations Android MMS/WAP Push manquantes"
                                !mmsSafePreviewValidated -> "Décodeur MMS sécurisé indisponible : le module Téléphonie reste verrouillé"
                                else -> "Réception MMS et aperçu sécurisé prêts logiciellement"
                            },
                            if (smsRoleHeld && (!state.receiveMmsPermission || !state.receiveWapPushPermission)) "Autoriser la réception MMS" else null
                        ) {
                            val required = buildList {
                                if (!state.receiveMmsPermission) add(Manifest.permission.RECEIVE_MMS)
                                if (!state.receiveWapPushPermission) add(Manifest.permission.RECEIVE_WAP_PUSH)
                            }.toTypedArray()
                            if (required.isNotEmpty()) permissionsLauncher.launch(required)
                        }

                        SectionTitle("Scanner Wi-Fi local")
                        CapabilityCard(
                            Icons.Default.Wifi, "Scanner Wi-Fi",
                            "Validation locale et défensive uniquement. Android peut exiger la position précise et l’activation de la localisation pour permettre la détection des réseaux Wi-Fi.",
                            state.wifiScanServiceAvailable && state.wifiScanPermissionGranted && state.wifiEnabled && state.wifiLocationEnabled,
                            when {
                                !state.wifiScanServiceAvailable -> "Service Wi-Fi indisponible sur cet appareil"
                                !state.wifiScanPermissionGranted -> "Autorisation Position précise requise"
                                !state.wifiLocationEnabled -> "Localisation Android à activer"
                                !state.wifiEnabled -> "Wi-Fi à activer"
                                else -> "Prérequis scanner Wi-Fi prêts"
                            },
                            when {
                                !state.wifiScanServiceAvailable -> null
                                !state.wifiScanPermissionGranted -> "Autoriser la position précise"
                                !state.wifiLocationEnabled -> "Activer la localisation"
                                !state.wifiEnabled -> "Activer le Wi-Fi"
                                else -> null
                            }
                        ) {
                            when {
                                !state.wifiScanPermissionGranted -> permissionsLauncher.launch(wifiScanner.requiredPermissions)
                                !state.wifiLocationEnabled -> settingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                !state.wifiEnabled -> settingsLauncher.launch(
                                    Intent(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
                                        else Settings.ACTION_WIFI_SETTINGS
                                    )
                                )
                            }
                        }

                        SectionTitle("Données locales requises pour la téléphonie complète")
                        CapabilityCard(
                            Icons.Default.Contacts, "Contacts & historique",
                            "Requis pour valider le module Téléphonie complet : affichage local des contacts et des appels récents dans le composeur Sentinel.",
                            state.contactsPermission && state.callLogPermission,
                            when { state.contactsPermission && state.callLogPermission -> "Accès local prêt"; !state.dialerRole -> "Contacts séparés · rôle Téléphone requis pour l’historique"; else -> "Autorisations de téléphonie manquantes" },
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
                        Text("L’état « SMS prêt » exige le rôle SMS, les autorisations système requises et au moins une SIM active vérifiée. Sur appareil double-SIM, chaque ligne devra être testée physiquement. Une validation locale complète ne déclenche jamais à elle seule le statut « 100 % fonctionnel ».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    private fun readState(smsDiagnostics: SmsActivationDiagnostics, wifiScanner: WifiScanner): RuntimeState {
        val dialer = holdsRole(RoleManager.ROLE_DIALER)
        val screening = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && holdsRole(RoleManager.ROLE_CALL_SCREENING)
        val phoneStatePermission = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val callLineAvailable = if (phoneStatePermission) {
            try {
                getSystemService(TelecomManager::class.java)
                    .callCapablePhoneAccounts
                    .orEmpty()
                    .isNotEmpty()
            } catch (_: SecurityException) {
                false
            }
        } else {
            false
        }
        return RuntimeState(
            dialerRole = dialer,
            callScreeningRole = screening,
            callPermission = hasPermission(Manifest.permission.CALL_PHONE),
            phoneStatePermission = phoneStatePermission,
            callLineAvailable = callLineAvailable,
            contactsPermission = hasPermission(Manifest.permission.READ_CONTACTS),
            callLogPermission = hasPermission(Manifest.permission.READ_CALL_LOG),
            readSmsPermission = hasPermission(Manifest.permission.READ_SMS),
            receiveMmsPermission = hasPermission(Manifest.permission.RECEIVE_MMS),
            receiveWapPushPermission = hasPermission(Manifest.permission.RECEIVE_WAP_PUSH),
            wifiScanServiceAvailable = wifiScanner.isSupported(),
            wifiScanPermissionGranted = wifiScanner.hasPermissions(),
            wifiEnabled = wifiScanner.isWifiEnabled(),
            wifiLocationEnabled = wifiScanner.isLocationEnabled(),
            notificationPermissionReady = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)) &&
                NotificationManagerCompat.from(this).areNotificationsEnabled(),
            notificationChannelsReady =
                SentinelCallNotificationHelper.isChannelEnabled(this) &&
                    SmsNotificationHelper.isChannelEnabled(this),
            smsSnapshot = smsDiagnostics.snapshot()
        )
    }

    private data class RuntimeState(
        val dialerRole: Boolean,
        val callScreeningRole: Boolean,
        val callPermission: Boolean,
        val phoneStatePermission: Boolean,
        val callLineAvailable: Boolean,
        val contactsPermission: Boolean,
        val callLogPermission: Boolean,
        val readSmsPermission: Boolean,
        val receiveMmsPermission: Boolean,
        val receiveWapPushPermission: Boolean,
        val wifiScanServiceAvailable: Boolean,
        val wifiScanPermissionGranted: Boolean,
        val wifiEnabled: Boolean,
        val wifiLocationEnabled: Boolean,
        val notificationPermissionReady: Boolean,
        val notificationChannelsReady: Boolean,
        val smsSnapshot: SmsActivationDiagnostics.Snapshot
    ) {
        val callsReady: Boolean
            get() = dialerRole && callPermission && phoneStatePermission && callLineAvailable
    }
}

@Composable private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

@Composable private fun CapabilityCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, ready: Boolean, status: String, actionLabel: String?, onAction: () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().semantics { stateDescription = if (ready) "Prêt" else status }
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(status, color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
            if (ready) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall)
        if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
    } }
}

@Composable private fun StatusChip(label: String, ready: Boolean) {
    val containerColor = if (ready) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (ready) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = Modifier.semantics { stateDescription = if (ready) "Prêt" else "À activer" },
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
