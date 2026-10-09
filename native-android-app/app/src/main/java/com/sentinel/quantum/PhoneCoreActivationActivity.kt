package com.sentinel.quantum

import android.Manifest
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import com.sentinel.quantum.security.PhoneCoreDiagnostics
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SentinelMissedCallReceiver
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
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/** User-driven activation center for Phone Core roles, permissions and Android settings. */
@OptIn(ExperimentalMaterial3Api::class)
class PhoneCoreActivationActivity : ComponentActivity() {
    private fun hasPermission(permission: String): Boolean =
        PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

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
                    RoleManager.ROLE_CALL_SCREENING ->
                        if (CallScreeningActivationPolicy.read(this) == CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD) {
                            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                        } else {
                            null
                        }
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
        SentinelMissedCallReceiver.ensureChannel(applicationContext)
        SmsNotificationHelper.ensureChannel(applicationContext)
        setContent {
            SentinelQuantumTheme {
                var epoch by remember { mutableStateOf(0) }
                var deniedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
                val smsDiagnostics = remember { SmsActivationDiagnostics(applicationContext) }
                val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val fullScreenIntentReady =
                    SentinelCallNotificationHelper.isFullScreenIntentAllowed(this@PhoneCoreActivationActivity)
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
                var setupPersistenceError by remember {
                    mutableStateOf(intent?.getBooleanExtra(EXTRA_SETUP_PERSISTENCE_ERROR, false) == true)
                }
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
                LaunchedEffect(lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        PhoneCoreLiveRefresh.snapshots(applicationContext).collect { _ ->
                            // Only an explicit permission result grants auto-advance.
                            // Preserve that result if it arrives during the initial snapshot.
                            epoch++
                        }
                    }
                }
                val state = remember(epoch) { readState(smsDiagnostics) }
                val callScreeningState = remember(epoch) {
                    CallScreeningActivationPolicy.read(applicationContext)
                }
                LaunchedEffect(epoch) {
                    deniedPermissions = deniedPermissions.filterNot(::hasPermission).toSet()
                }
                val smsModel = remember(state.smsSnapshot) { SmsActivationUiModel.from(state.smsSnapshot) }
                val smsRoleHeld = state.smsSnapshot.smsRoleState == SmsActivationDiagnostics.SmsRoleState.HELD
                val mmsSafePreviewValidated = remember { MmsSafePreviewReadiness.softwareValidated }
                // Certification remains part of readiness truth, but its raw x/14 detail belongs to
                // PhoneCoreDiagnosticActivity rather than the customer activation journey.
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
                val readiness = remember(state, physicalEvidence, fullScreenIntentReady) {
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
                            physicalDeviceValidated = physicalEvidence.physicalDeviceValidated
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
                    PhoneCoreSetupWizardStore.nextConfigurableStep(setupFacts)
                }
                val setupAtomicPermission = when (setupStep) {
                    PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS ->
                        PhoneCoreSetupWizardStore.firstMissingPermission(
                            listOf(
                                Manifest.permission.CALL_PHONE to state.callPermission,
                                Manifest.permission.READ_PHONE_STATE to state.phoneStatePermission
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
                    PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS ->
                        if (notificationPermissionRequired && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                            Manifest.permission.POST_NOTIFICATIONS
                        } else {
                            null
                        }
                    else -> null
                }
                val setupAtomicProgress: Pair<Int, Int>? = when (setupStep) {
                    PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS -> {
                        val checks = listOf(state.callPermission, state.phoneStatePermission)
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
                    PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW ->
                        (if (mmsSafePreviewValidated) 1 else 0) to 1
                    PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS ->
                        if (notificationPermissionRequired) {
                            (if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) 1 else 0) to 1
                        } else {
                            null
                        }
                    else -> null
                }?.takeIf { it.second > 0 }
                val setupTargetKey = PhoneCoreSetupWizardStore.targetKey(setupStep, setupAtomicPermission)
                val attemptedSetupTargetKey = remember(epoch) { setupWizard.attemptedTargetKey() }

                fun launchSetupStep(step: PhoneCoreSetupWizardStore.Step) {
                    setupPersistenceError = false
                    if (!setupWizard.markAttemptedTarget(setupTargetKey)) {
                        // Do not hand control to Android until the target is durably recorded. A
                        // failed commit must be visible and retryable instead of looking like a
                        // permission/role request that Android silently ignored.
                        setupPersistenceError = true
                        return
                    }
                    when (step) {
                        PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS -> {
                            val permission = PhoneCoreSetupWizardStore.firstMissingPermission(
                                listOf(
                                    Manifest.permission.CALL_PHONE to state.callPermission,
                                    Manifest.permission.READ_PHONE_STATE to state.phoneStatePermission
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
                        PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW -> epoch++
                        PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS -> {
                            if (notificationPermissionRequired && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                                setupPermissionInFlight = Manifest.permission.POST_NOTIFICATIONS
                                setupPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                settingsLauncher.launch(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fullScreenIntentReady) {
                                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                                    } else {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    }
                                )
                            }
                        }
                        PhoneCoreSetupWizardStore.Step.COMPLETE -> {
                            if (!setupWizard.markCompleted()) setupPersistenceError = true
                        }
                    }
                }

                LaunchedEffect(firstRunSetup, setupTargetKey, attemptedSetupTargetKey) {
                    if (!firstRunSetup) return@LaunchedEffect
                    if (setupStep == PhoneCoreSetupWizardStore.Step.COMPLETE) {
                        if (!setupWizard.markCompleted()) setupPersistenceError = true
                    } else if (
                        !setupPersistenceError &&
                        PhoneCoreSetupWizardStore.shouldAutoLaunch(
                            setupTargetKey,
                            attemptedSetupTargetKey,
                            allowTargetAdvance = allowWizardAutoAdvance
                        ) && PhoneCoreSetupWizardStore.isStepActionable(setupStep, setupFacts)
                    ) {
                        allowWizardAutoAdvance = false
                        launchSetupStep(setupStep)
                    }
                }

                Scaffold(topBar = {
                    SentinelTopBar(
                        title = "Téléphonie",
                        subtitle = "Centre d’activation",
                        onBack = { finish() }
                    )
                }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhoneCoreBrand(
                            context = "Activation",
                            status = when {
                                !mmsSafePreviewValidated ->
                                    "Phone Core bloqué · aperçu MMS sécurisé indisponible"
                                state.callsReady && smsModel.state == SmsActivationDiagnostics.State.READY ->
                                    "Prérequis appels et SMS prêts"
                                else ->
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
                                    if (setupPersistenceError) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Column(
                                                Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    "Impossible d’enregistrer la progression de l’assistant. Aucune demande Android n’a été lancée et Phone Core reste bloqué.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                OutlinedButton(
                                                    onClick = { launchSetupStep(setupStep) },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Réessayer l’enregistrement")
                                                }
                                            }
                                        }
                                    }
                                    if (setupStep == PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW) {
                                        Text(
                                            "BLOQUÉ : le contrôle local de l’aperçu MMS sécurisé a échoué ou reste indisponible. Aucun contenu MMS réel ne sera ouvert ; le module Téléphonie restera verrouillé jusqu’à correction du décodeur.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (
                                        setupStep != PhoneCoreSetupWizardStore.Step.COMPLETE &&
                                        setupStep != PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW &&
                                        !setupPersistenceError &&
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
                                    if (
                                        setupStep != PhoneCoreSetupWizardStore.Step.COMPLETE &&
                                        (attemptedSetupTargetKey == setupTargetKey ||
                                            PhoneCoreSetupWizardStore.isBlockedByUnavailableRole(setupStep, setupFacts))
                                    ) {
                                        Text(
                                            when (setupStep) {
                                                PhoneCoreSetupWizardStore.Step.DIALER_ROLE ->
                                                    if (!setupFacts.dialerRoleAvailable)
                                                        "Le rôle Téléphone est indisponible sur cet appareil ou dans cette configuration. Sentinel reste bloqué sur ce prérequis et ne peut pas ouvrir une demande de rôle Android."
                                                    else
                                                        "Le rôle Téléphone est disponible mais non accordé à Sentinel. Android n’indique pas ici si le rôle a été refusé ou reporté."
                                                PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE ->
                                                    if (!setupFacts.callScreeningRoleAvailable)
                                                        "Le filtrage d’appels est indisponible sur cet appareil ou dans cette configuration. Sentinel reste bloqué sur ce prérequis."
                                                    else
                                                        "Le filtrage d’appels est disponible mais non activé pour Sentinel."
                                                PhoneCoreSetupWizardStore.Step.SMS_ROLE ->
                                                    if (!setupFacts.smsRoleAvailable)
                                                        "Le rôle SMS est indisponible sur cet appareil ou dans cette configuration. Sentinel reste bloqué sur ce prérequis et ne peut pas ouvrir une demande de rôle Android."
                                                    else
                                                        "Le rôle SMS est disponible mais non accordé à Sentinel."
                                                PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS -> {
                                                    val missing = buildList {
                                                        if (!state.callPermission) add("autorisation pour passer des appels")
                                                        if (!state.phoneStatePermission) add("accès à l’état du téléphone")
                                                    }
                                                    if (missing.isEmpty()) {
                                                        "Les autorisations téléphonie essentielles sont accordées. Sentinel relit l’état Android avant de poursuivre."
                                                    } else {
                                                        "Autorisations encore manquantes : " + missing.joinToString(" · ") + ". Android n’indique pas ici la cause d’un refus ; réessayez ou vérifiez les paramètres de l’application."
                                                    }
                                                }
                                                PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS ->
                                                    if (notificationPermissionRequired && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                                                        "L’autorisation Android des notifications n’est pas encore accordée. Les canaux et le plein écran seront vérifiés seulement après cette permission."
                                                    } else {
                                                        "Les notifications restent incomplètes : vérifiez les notifications globales, les canaux Appels entrants/Appels manqués/SMS et, si Android le demande, le plein écran d’appel."
                                                    }
                                                PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW ->
                                                    "Le contrôle local de l’aperçu MMS sécurisé est obligatoire avant de déclarer les prérequis logiciels prêts. Ouvrez le diagnostic technique pour examiner ce blocage ; aucun accès opérateur n’est requis pour ce contrôle."
                                                else ->
                                                    "Cette étape n’est pas encore accordée. Vérifiez les paramètres Android puis réessayez."
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (
                                            setupStep != PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW &&
                                            PhoneCoreSetupWizardStore.isStepActionable(setupStep, setupFacts)
                                        ) {
                                            Button(
                                                onClick = { launchSetupStep(setupStep) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Réessayer cette étape") }
                                        }
                                        if (setupStep != PhoneCoreSetupWizardStore.Step.MMS_SAFE_PREVIEW) {
                                            OutlinedButton(
                                                onClick = { settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Ouvrir les paramètres Android de Sentinel") }
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, PhoneCoreDiagnosticActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Diagnostic technique")
                        }

                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "TÉLÉPHONIE SENTINEL",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Finaliser la configuration du téléphone",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Sentinel vérifie directement ce qu’Android autorise réellement sur cet appareil.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatusChip(
                                        if (state.callsReady) "APPELS PRÊTS" else "APPELS À ACTIVER",
                                        state.callsReady
                                    )
                                    StatusChip(
                                        if (readiness.softwarePrerequisitesReady) "CONFIGURATION PRÊTE" else "CONFIGURATION À TERMINER",
                                        readiness.softwarePrerequisitesReady
                                    )
                                }
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "État de configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (readiness.softwarePrerequisitesReady) {
                                        "Les rôles, autorisations et prérequis logiciels observables sont prêts pour les fonctions configurées."
                                    } else {
                                        "Terminez les rôles et autorisations indiqués ci-dessous. Sentinel relit l’état réellement accordé par Android."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "La certification technique de l’APK est séparée de l’usage quotidien. Son détail reste disponible dans Diagnostic technique.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!state.notificationPermissionReady || !state.notificationChannelsReady) {
                                    if (notificationPermissionRequired && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                                        OutlinedButton(
                                            onClick = { permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Autoriser les notifications de téléphonie") }
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
                                                    "Réactiver les canaux de téléphonie"
                                                else
                                                    "Ouvrir les réglages de notifications"
                                            )
                                        }
                                    }
                                    if (!state.notificationChannelsReady) {
                                        Text(
                                            "Au moins un canal système de téléphonie (appels entrants, appels manqués ou SMS) est désactivé. Le statut logiciel reste bloqué.",
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
                                else -> "Configuration prête"
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
                            when (callScreeningState) {
                                CallScreeningActivationPolicy.State.HELD -> "Filtrage système actif"
                                CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD -> "Filtrage disponible mais non activé"
                                CallScreeningActivationPolicy.State.UNAVAILABLE -> "Filtrage indisponible sur cet appareil ou dans cette configuration"
                            },
                            if (callScreeningState == CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD) {
                                "Activer le filtrage"
                            } else null
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

                        CapabilityCard(
                            Icons.Default.Contacts, "Contacts & historique",
                            "Utilisés localement pour afficher les contacts et les appels récents dans le composeur Sentinel.",
                            state.contactsPermission && state.callLogPermission,
                            when {
                                state.contactsPermission && state.callLogPermission -> "Accès local prêt"
                                !state.dialerRole -> "Contacts séparés · rôle Téléphone requis pour l’historique"
                                else -> "Autorisations de téléphonie manquantes"
                            },
                            if (!state.contactsPermission) "Autoriser les contacts"
                            else if (state.dialerRole && !state.callLogPermission) "Autoriser l’historique"
                            else null
                        ) {
                            val optional = buildList {
                                if (!state.contactsPermission) add(Manifest.permission.READ_CONTACTS)
                                if (state.dialerRole && !state.callLogPermission) add(Manifest.permission.READ_CALL_LOG)
                            }.toTypedArray()
                            if (optional.isNotEmpty()) permissionsLauncher.launch(optional)
                        }

                        if (deniedPermissions.isNotEmpty()) Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Autorisation non accordée", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                Text(
                                    "Android indique qu’au moins une autorisation demandée n’est pas accordée. Sentinel ne suppose pas la cause du refus. Vous pouvez réessayer ou vérifier les autorisations dans les paramètres Android.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = { settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Ouvrir les paramètres de Sentinel") }
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Ouvrir les fonctions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, SentinelDialerActivity::class.java)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PhoneInTalk, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Appels & contacts")
                                }
                                OutlinedButton(
                                    onClick = { startActivity(Intent(this@PhoneCoreActivationActivity, SmsComposeActivity::class.java)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Message, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Messages")
                                }
                            }
                        }
                        Text(
                            "L’état « SMS prêt » exige le rôle SMS, les autorisations système requises et au moins une SIM active vérifiée. Sur appareil double-SIM, chaque ligne reste un contexte distinct. La certification technique n’est jamais présentée comme une étape nécessaire à l’usage quotidien.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FIRST_RUN_SETUP = "com.sentinel.quantum.extra.FIRST_RUN_PHONE_CORE_SETUP"
        const val EXTRA_SETUP_PERSISTENCE_ERROR = "com.sentinel.quantum.extra.PHONE_CORE_SETUP_PERSISTENCE_ERROR"
    }

    private fun readState(smsDiagnostics: SmsActivationDiagnostics): RuntimeState {
        val dialer = holdsRole(RoleManager.ROLE_DIALER)
        val screening = CallScreeningActivationPolicy.read(this) == CallScreeningActivationPolicy.State.HELD
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
            notificationPermissionReady = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)) &&
                NotificationManagerCompat.from(this).areNotificationsEnabled(),
            notificationChannelsReady =
                SentinelCallNotificationHelper.isChannelEnabled(this) &&
                    SentinelMissedCallReceiver.isChannelEnabled(this) &&
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
    ElevatedCard(
        Modifier.fillMaxWidth().semantics { stateDescription = status }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        status,
                        color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (ready) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
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
