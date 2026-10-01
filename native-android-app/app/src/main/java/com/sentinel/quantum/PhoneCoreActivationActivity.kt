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
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.PhoneCoreDiagnostics
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SmsNotificationHelper
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhoneCoreCertificationScopeProvider
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.SystemCallLogReader
import com.sentinel.quantum.security.MmsSafePreviewReadiness
import com.sentinel.quantum.security.SmsActivationActions
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsActivationUiModel
import com.sentinel.quantum.security.WifiScanner
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** User-driven activation and device-test center for Phone Core. */
@OptIn(ExperimentalMaterial3Api::class)
class PhoneCoreActivationActivity : ComponentActivity() {
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun holdsRole(role: String): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                when (role) {
                    RoleManager.ROLE_DIALER ->
                        getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
                    RoleManager.ROLE_SMS ->
                        Telephony.Sms.getDefaultSmsPackage(this) == packageName
                    else -> false
                }
            } else {
                val manager = getSystemService(RoleManager::class.java)
                manager.isRoleAvailable(role) && manager.isRoleHeld(role)
            }
        }

    private fun isRoleAvailable(role: String): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                role == RoleManager.ROLE_DIALER || role == RoleManager.ROLE_SMS
            } else {
                getSystemService(RoleManager::class.java).isRoleAvailable(role)
            }
        }

    private fun roleIntent(role: String): Intent? =
        AndroidRoleReadPolicy.readOrNull {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                when (role) {
                    RoleManager.ROLE_DIALER -> Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    RoleManager.ROLE_SMS -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    else -> null
                }
            } else {
                val manager = getSystemService(RoleManager::class.java)
                if (!manager.isRoleAvailable(role) || manager.isRoleHeld(role)) {
                    null
                } else {
                    manager.createRequestRoleIntent(role)
                }
            }
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
                var validationDetailsExpanded by remember { mutableStateOf(false) }
                var deniedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
                val smsDiagnostics = remember { SmsActivationDiagnostics(applicationContext) }
                val wifiScanner = remember { WifiScanner(applicationContext) }
                val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val fullScreenIntentReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                val smsActions = remember { SmsActivationActions(applicationContext) }
                val setupWizard = remember { PhoneCoreSetupWizardStore(applicationContext) }
                val installTimestampMs = remember { currentInstallTimestamp() }
                val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { epoch++ }
                val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { epoch++ }
                val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    deniedPermissions = grants.filterValues { !it }.keys
                    epoch++
                }
                var setupPermissionInFlight by remember { mutableStateOf<String?>(null) }
                var allowWizardAutoAdvance by remember { mutableStateOf(false) }
                val setupPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    deniedPermissions = if (granted) emptySet() else setOfNotNull(setupPermissionInFlight)
                    setupPermissionInFlight = null
                    allowWizardAutoAdvance = granted
                    epoch++
                }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            allowWizardAutoAdvance = false
                            epoch++
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val state = remember(epoch) { readState(smsDiagnostics, wifiScanner) }
                LaunchedEffect(epoch) {
                    deniedPermissions = deniedPermissions.filterNot(::hasPermission).toSet()
                }
                val smsModel = remember(state.smsSnapshot) { SmsActivationUiModel.from(state.smsSnapshot) }
                val smsRoleHeld = state.smsSnapshot.smsRoleState == SmsActivationDiagnostics.SmsRoleState.HELD
                val mmsSafePreviewValidated = remember { MmsSafePreviewReadiness.softwareValidated }
                val physicalEvidence by produceState(
                    initialValue = PhoneCorePhysicalValidation.evaluate(emptyList()),
                    key1 = epoch
                ) {
                    value = withContext(Dispatchers.IO) {
                        val contactsReady =
                            LocalContactLookup(applicationContext).listWithState(1).state ==
                                LocalContactLookup.ContactAccessState.READY
                        val callHistoryReady =
                            SystemCallLogReader(applicationContext).accessState() ==
                                SystemCallLogReader.AccessState.READY
                        PhoneCorePhysicalValidation.evaluateCertification(
                            events = PhonePrivateTimelineStore(applicationContext).read().events,
                            activeScope = PhoneCoreCertificationScopeProvider.current(applicationContext),
                            notBeforeMs = installTimestampMs,
                            contactsProviderReady = contactsReady,
                            callHistoryProviderReady = callHistoryReady
                        )
                    }
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

                val firstRunSetup = intent?.getBooleanExtra(EXTRA_FIRST_RUN_SETUP, false) == true
                val smsRuntimePermissions = remember(state.smsSnapshot, smsRoleHeld) {
                    if (smsRoleHeld) smsActions.permissionsFor(state.smsSnapshot) else emptyArray()
                }
                val setupFacts = remember(epoch) {
                    PhoneCoreRuntimeFacts.read(applicationContext)
                }
                val setupStep = remember(setupFacts) {
                    PhoneCoreSetupWizardStore.nextStep(setupFacts)
                }
                val setupAtomicPermission = when (setupStep) {
                    PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS ->
                        PhoneCoreSetupWizardStore.firstMissingPermission(
                            listOf(
                                Manifest.permission.CALL_PHONE to state.callPermission,
                                Manifest.permission.READ_PHONE_STATE to state.phoneStatePermission,
                                Manifest.permission.READ_CONTACTS to state.contactsPermission,
                                Manifest.permission.POST_NOTIFICATIONS to
                                    (!notificationPermissionRequired || hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                            )
                        )
                    PhoneCoreSetupWizardStore.Step.CALL_LOG_PERMISSION -> Manifest.permission.READ_CALL_LOG
                    PhoneCoreSetupWizardStore.Step.SMS_PERMISSIONS ->
                        smsRuntimePermissions.firstOrNull { !hasPermission(it) }
                    PhoneCoreSetupWizardStore.Step.MMS_PERMISSIONS ->
                        PhoneCoreSetupWizardStore.firstMissingPermission(
                            listOf(
                                Manifest.permission.RECEIVE_MMS to state.receiveMmsPermission,
                                Manifest.permission.RECEIVE_WAP_PUSH to state.receiveWapPushPermission
                            )
                        )
                    else -> null
                }
                val setupAtomicProgress: Pair<Int, Int>? = when (setupStep) {
                    PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS -> {
                        val checks = buildList {
                            add(state.callPermission)
                            add(state.phoneStatePermission)
                            add(state.contactsPermission)
                            if (notificationPermissionRequired) {
                                add(hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                        checks.count { it } to checks.size
                    }
                    PhoneCoreSetupWizardStore.Step.CALL_LOG_PERMISSION ->
                        (if (state.callLogPermission) 1 else 0) to 1
                    PhoneCoreSetupWizardStore.Step.SMS_PERMISSIONS ->
                        smsRuntimePermissions.count { hasPermission(it) } to smsRuntimePermissions.size
                    PhoneCoreSetupWizardStore.Step.MMS_PERMISSIONS -> {
                        val checks = listOf(state.receiveMmsPermission, state.receiveWapPushPermission)
                        checks.count { it } to checks.size
                    }
                    else -> null
                }?.takeIf { it.second > 0 }
                val setupTargetKey = PhoneCoreSetupWizardStore.targetKey(setupStep, setupAtomicPermission)
                val attemptedSetupTargetKey = remember(epoch) { setupWizard.attemptedTargetKey() }

                fun launchSetupStep(step: PhoneCoreSetupWizardStore.Step) {
                    setupWizard.markAttemptedTarget(setupTargetKey)
                    when (step) {
                        PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS -> {
                            val permission = PhoneCoreSetupWizardStore.firstMissingPermission(
                                listOf(
                                    Manifest.permission.CALL_PHONE to state.callPermission,
                                    Manifest.permission.READ_PHONE_STATE to state.phoneStatePermission,
                                    Manifest.permission.READ_CONTACTS to state.contactsPermission,
                                    Manifest.permission.POST_NOTIFICATIONS to
                                        (!notificationPermissionRequired || hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                                )
                            )
                            if (permission != null) run {
                                setupPermissionInFlight = permission
                                setupPermissionLauncher.launch(permission)
                            } else epoch++
                        }
                        PhoneCoreSetupWizardStore.Step.DIALER_ROLE ->
                            roleIntent(RoleManager.ROLE_DIALER)?.let(roleLauncher::launch) ?: run { epoch++ }
                        PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE ->
                            roleIntent(RoleManager.ROLE_CALL_SCREENING)?.let(roleLauncher::launch) ?: run { epoch++ }
                        PhoneCoreSetupWizardStore.Step.CALL_LOG_PERMISSION ->
                            run {
                                setupPermissionInFlight = Manifest.permission.READ_CALL_LOG
                                setupPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            }
                        PhoneCoreSetupWizardStore.Step.SMS_ROLE -> {
                            val request = smsActions.roleRequestIntent() ?: smsActions.legacyDefaultAppsIntent()
                            if (request != null) roleLauncher.launch(request) else epoch++
                        }
                        PhoneCoreSetupWizardStore.Step.SMS_PERMISSIONS -> {
                            val permission = smsRuntimePermissions.firstOrNull { !hasPermission(it) }
                            if (permission != null) run {
                                setupPermissionInFlight = permission
                                setupPermissionLauncher.launch(permission)
                            } else epoch++
                        }
                        PhoneCoreSetupWizardStore.Step.MMS_PERMISSIONS -> {
                            val permission = PhoneCoreSetupWizardStore.firstMissingPermission(
                                listOf(
                                    Manifest.permission.RECEIVE_MMS to state.receiveMmsPermission,
                                    Manifest.permission.RECEIVE_WAP_PUSH to state.receiveWapPushPermission
                                )
                            )
                            if (permission != null) run {
                                setupPermissionInFlight = permission
                                setupPermissionLauncher.launch(permission)
                            } else epoch++
                        }
                        PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS -> {
                            settingsLauncher.launch(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fullScreenIntentReady) {
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                                } else {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                }
                            )
                        }
                        PhoneCoreSetupWizardStore.Step.COMPLETE -> setupWizard.markCompleted()
                    }
                }

                LaunchedEffect(firstRunSetup, setupTargetKey, attemptedSetupTargetKey) {
                    if (!firstRunSetup) return@LaunchedEffect
                    if (setupStep == PhoneCoreSetupWizardStore.Step.COMPLETE) {
                        setupWizard.markCompleted()
                    } else if (
                        PhoneCoreSetupWizardStore.shouldAutoLaunch(
                            setupTargetKey,
                            attemptedSetupTargetKey,
                            allowTargetAdvance = allowWizardAutoAdvance
                        )
                    ) {
                        allowWizardAutoAdvance = false
                        launchSetupStep(setupStep)
                    }
                }


                Scaffold(topBar = {
                    SentinelTopBar(
                        title = "Téléphonie",
                        subtitle = "Centre d’activation & test",
                        onBack = { finish() }
                    )
                }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhoneCoreBrand(
                            context = "Activation & tests",
                            status = if (state.callsReady && smsModel.state == SmsActivationDiagnostics.State.READY) {
                                "Prérequis appels et SMS prêts · tests physiques requis"
                            } else {
                                "Configuration Android incomplète"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (firstRunSetup) {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Configuration initiale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Sentinel va vous guider dans les autorisations et rôles Android nécessaires. Chaque demande reste affichée et confirmée par Android ; vous pouvez refuser ou reporter un accès.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Assistant séquentiel : une seule demande Android à la fois. Après chaque retour, Sentinel relit l’état réellement accordé et reprend à la première étape manquante.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    val (setupPosition, setupTotal) = PhoneCoreSetupWizardStore.stepProgress(setupStep)
                                    Text(
                                        "Étape $setupPosition sur $setupTotal",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    setupAtomicProgress?.let { (granted, total) ->
                                        Text(
                                            "Autorisations de cette étape : $granted/$total accordée(s)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    setupAtomicPermission?.let { permission ->
                                        Text(
                                            "Prochaine demande Android : ${PhoneCoreSetupWizardStore.permissionLabel(permission)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        PhoneCoreSetupWizardStore.stepLabel(setupStep),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        PhoneCoreSetupWizardStore.stepRationale(setupStep),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        PhoneCoreSetupWizardStore.stepPrivacyNote(setupStep),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (
                                        setupStep != PhoneCoreSetupWizardStore.Step.COMPLETE &&
                                        PhoneCoreSetupWizardStore.shouldOfferManualContinue(
                                            targetKey = setupTargetKey,
                                            lastAttemptedTargetKey = attemptedSetupTargetKey,
                                            actionable = PhoneCoreSetupWizardStore.isStepActionable(setupStep, setupFacts)
                                        )
                                    ) {
                                        Button(
                                            onClick = { launchSetupStep(setupStep) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Continuer l’activation")
                                        }
                                    }
                                    if (setupStep != PhoneCoreSetupWizardStore.Step.COMPLETE && attemptedSetupTargetKey == setupTargetKey) {
                                        Text(
                                            when (setupStep) {
                                                PhoneCoreSetupWizardStore.Step.DIALER_ROLE ->
                                                    if (!setupFacts.dialerRoleAvailable)
                                                        "Le rôle Téléphone est indisponible sur cet appareil ou dans cette configuration. Sentinel reste bloqué sur ce prérequis et ne peut pas ouvrir une demande de rôle Android."
                                                    else
                                                        "Le rôle Téléphone est disponible mais non accordé à Sentinel. Android n’indique pas ici si le rôle a été refusé ou reporté."
                                                PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE ->
                                                    if (!setupFacts.callScreeningRoleAvailable)
                                                        "Le rôle Filtrage d’appels est indisponible sur cet appareil ou dans cette configuration. Sentinel reste bloqué sur ce prérequis."
                                                    else
                                                        "Le rôle Filtrage d’appels est disponible mais non accordé à Sentinel."
                                                PhoneCoreSetupWizardStore.Step.SMS_ROLE ->
                                                    if (!setupFacts.smsRoleAvailable)
                                                        "Le rôle SMS est indisponible sur cet appareil ou dans cette configuration. Sentinel reste bloqué sur ce prérequis et ne peut pas ouvrir une demande de rôle Android."
                                                    else
                                                        "Le rôle SMS est disponible mais non accordé à Sentinel."
                                                PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS -> {
                                                    val missing = buildList {
                                                        if (!state.callPermission) add("autorisation pour passer des appels")
                                                        if (!state.phoneStatePermission) add("accès à l’état du téléphone")
                                                        if (!state.contactsPermission) add("accès aux contacts")
                                                        if (notificationPermissionRequired && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                                                            add("autorisation des notifications")
                                                        }
                                                    }
                                                    if (missing.isEmpty()) {
                                                        "Les autorisations de base sont accordées. Sentinel relit l’état Android avant de poursuivre."
                                                    } else {
                                                        "Autorisations encore manquantes : " + missing.joinToString(" · ") + ". Android n’indique pas ici la cause d’un refus ; réessayez ou vérifiez les paramètres de l’application."
                                                    }
                                                }
                                                else ->
                                                    "Cette étape n’est pas encore accordée. Vérifiez les paramètres Android puis réessayez."
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (PhoneCoreSetupWizardStore.isStepActionable(setupStep, setupFacts)) {
                                            Button(
                                                onClick = { setupWizard.clearAttempted(); epoch++ },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Réessayer cette étape") }
                                        }
                                        OutlinedButton(
                                            onClick = { settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Ouvrir les paramètres Android de Sentinel") }
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, PhoneCoreDiagnosticActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Diagnostic activation Android")
                        }

                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("TÉLÉPHONIE SENTINEL", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Préparer le téléphone pour un test réel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text("Chaque état est calculé depuis les rôles, permissions et capacités réellement observés sur cet appareil.", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusChip(if (state.callsReady) "PRÉREQUIS APPELS PRÊTS" else "APPELS À ACTIVER", state.callsReady)
                                    StatusChip("SMS ${PhoneCoreFrenchLabels.smsState(smsModel.state)}", smsModel.state == SmsActivationDiagnostics.State.READY)
                                    StatusChip(
                                        if (readiness.softwarePrerequisitesReady) "PRÉREQUIS LOGICIELS PRÊTS" else "LOGICIEL À FINALISER",
                                        readiness.softwarePrerequisitesReady
                                    )
                                    StatusChip(
                                        when {
                                            readiness.fullyValidated ->
                                                "APPAREIL LOCAL ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}"
                                            physicalEvidence.fullyValidated ->
                                                "PREUVES ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount} · LOGICIEL À RÉACTIVER"
                                            else ->
                                                "VALIDATION PHONE CORE ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}"
                                        },
                                        readiness.fullyValidated
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
                                                readiness.fullyValidated -> "LOCAL VALIDÉ"
                                                physicalEvidence.fullyValidated -> "VALIDATION SUSPENDUE"
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
                                            "100 % des prérequis logiciels observés. Validation Phone Core ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}."
                                        else ->
                                            "Prérequis logiciels incomplets : aucun statut 100 % fonctionnel n’est annoncé."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Preuves locales : ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { validationDetailsExpanded = !validationDetailsExpanded }) {
                                        Text(if (validationDetailsExpanded) "Masquer" else "Voir les détails")
                                    }
                                }
                                if (validationDetailsExpanded) {
                                    readiness.capabilities.filter { it.id != "PHYSICAL_DEVICE" && it.id != "WIFI_SCAN" }.forEach {
                                        Text("• ${PhoneCoreFrenchLabels.capability(it.id)} : ${PhoneCoreFrenchLabels.diagnosticState(it.state)}", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(
                                        "• Preuves sur cet appareil : " + if (physicalEvidence.fullyValidated) "VALIDÉES" else "${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "Les preuves de notification signifient qu’Android a accepté leur publication. L’affichage réel à l’écran doit encore être confirmé pendant les tests physiques.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Text("  ${if (physicalEvidence.incomingCallNotificationPosted) "✓" else "○"} Notification d’appel acceptée par Android", style = MaterialTheme.typography.bodySmall)
                                    Text("  ${if (physicalEvidence.incomingSmsNotificationPosted) "✓" else "○"} Notification SMS acceptée par Android", style = MaterialTheme.typography.bodySmall)
                                    Text("  ${if (physicalEvidence.callerIdUiShown) "✓" else "○"} Fiche d’identification d’appel réellement affichée", style = MaterialTheme.typography.bodySmall)
                                    Text("  ${if (physicalEvidence.inCallUiShown) "✓" else "○"} Interface d’appel Sentinel réellement affichée", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Réseau Wi‑Fi : " +
                                            if (physicalEvidence.wifiScanFresh) {
                                                "scan frais observé · hors certification Phone Core"
                                            } else {
                                                "non mesuré · hors certification Phone Core"
                                            },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
    
                                }
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
                                    "1. Activer les prérequis → 2. Installer l’APK candidate → 3. Observer ${physicalEvidence.requiredCount}/${physicalEvidence.requiredCount} preuves locales → 4. Confirmer visuellement les notifications et l’interface d’appel → 5. Valider multi-version Android + double-SIM + réversibilité → seulement ensuite 100 % fonctionnel",
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
                                !state.dialerRole && !isRoleAvailable(RoleManager.ROLE_DIALER) -> "Rôle Téléphone indisponible sur cet appareil"
                                !state.dialerRole -> "Rôle Téléphone disponible mais non accordé"
                                !state.callPermission -> "Permission d’appel requise"
                                !state.phoneStatePermission -> "Permission de détection des lignes requise"
                                state.callLineState == CallLineState.LOOKUP_FAILED -> "Android n’a pas pu vérifier les lignes d’appel"
                                !state.callLineAvailable -> "Aucune ligne d’appel active détectée"
                                else -> "Prêt pour test appareil"
                            },
                            when {
                                !state.dialerRole && isRoleAvailable(RoleManager.ROLE_DIALER) -> "Choisir Sentinel comme téléphone"
                                !state.dialerRole -> null
                                !state.callPermission -> "Autoriser les appels"
                                !state.phoneStatePermission -> "Autoriser la détection des lignes"
                                state.callLineState == CallLineState.LOOKUP_FAILED -> "Réessayer la détection des lignes"
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
                            "Active le service système de filtrage pour appliquer les règles locales avant l’affichage de l’appel.",
                            state.callScreeningRole,
                            when {
                                state.callScreeningRole -> "Rôle de filtrage actif · test réel requis"
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Disponible à partir d’Android 10"
                                !isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) -> "Rôle de filtrage indisponible sur cet appareil"
                                else -> "Rôle de filtrage disponible mais non accordé"
                            },
                            if (!state.callScreeningRole && isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) "Activer le filtrage" else null
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

                        if (deniedPermissions.isNotEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Autorisation non accordée", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                Text("Android indique qu’au moins une autorisation demandée n’est pas accordée. Sentinel ne suppose pas la cause du refus. Vous pouvez réessayer ou vérifier les autorisations dans les paramètres Android.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les paramètres de Sentinel") }
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Validation locale de l’APK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${physicalEvidence.completedCount}/${physicalEvidence.requiredCount} critères confirmés sur cet APK",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (physicalEvidence.missingCriteria.isNotEmpty()) {
                                    Text(
                                        "Reste à confirmer : " + physicalEvidence.missingCriteria.joinToString(" · ") {
                                            when (it) {
                                                "incoming_call_connected" -> "appel entrant connecté"
                                                "outgoing_call_connected" -> "appel sortant connecté"
                                                "call_screening_observed" -> "filtrage d’appel observé"
                                                "contacts_provider_ready" -> "contacts accessibles"
                                                "call_history_provider_ready" -> "historique d’appels accessible"
                                                "incoming_sms_received" -> "SMS entrant reçu"
                                                "outgoing_sms_submitted" -> "SMS sortant envoyé"
                                                "outgoing_sms_delivered" -> "SMS sortant livré"
                                                "incoming_mms_safe_preview" -> "MMS entrant sécurisé"
                                                "incoming_call_notification" -> "notification d’appel"
                                                "incoming_sms_notification" -> "notification SMS"
                                                "caller_id_ui_shown" -> "Caller ID affiché"
                                                "in_call_ui_shown" -> "interface d’appel affichée"
                                                else -> it
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text("Les ${physicalEvidence.requiredCount} critères locaux requis sont confirmés pour cet APK.", style = MaterialTheme.typography.bodySmall)
                                }
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

    companion object {
        const val EXTRA_FIRST_RUN_SETUP = "com.sentinel.quantum.extra.FIRST_RUN_PHONE_CORE_SETUP"
    }

    private fun readState(smsDiagnostics: SmsActivationDiagnostics, wifiScanner: WifiScanner): RuntimeState {
        val dialer = holdsRole(RoleManager.ROLE_DIALER)
        val screening = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && holdsRole(RoleManager.ROLE_CALL_SCREENING)
        val phoneStatePermission = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val callLineState = if (!phoneStatePermission) {
            CallLineState.PERMISSION_REQUIRED
        } else {
            try {
                if (getSystemService(TelecomManager::class.java)
                        .callCapablePhoneAccounts
                        .orEmpty()
                        .isNotEmpty()
                ) CallLineState.AVAILABLE else CallLineState.NONE
            } catch (_: SecurityException) {
                CallLineState.LOOKUP_FAILED
            } catch (_: RuntimeException) {
                CallLineState.LOOKUP_FAILED
            }
        }
        return RuntimeState(
            dialerRole = dialer,
            callScreeningRole = screening,
            callPermission = hasPermission(Manifest.permission.CALL_PHONE),
            phoneStatePermission = phoneStatePermission,
            callLineState = callLineState,
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

    private enum class CallLineState { AVAILABLE, NONE, PERMISSION_REQUIRED, LOOKUP_FAILED }

    private data class RuntimeState(
        val dialerRole: Boolean,
        val callScreeningRole: Boolean,
        val callPermission: Boolean,
        val phoneStatePermission: Boolean,
        val callLineState: CallLineState,
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
        val callLineAvailable: Boolean
            get() = callLineState == CallLineState.AVAILABLE
        val callsReady: Boolean
            get() = dialerRole && callPermission && phoneStatePermission && callLineAvailable
    }
}

@Composable private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

@Composable private fun CapabilityCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, ready: Boolean, status: String, actionLabel: String?, onAction: () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().semantics { stateDescription = status }
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
        modifier = Modifier.semantics { stateDescription = label },
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
