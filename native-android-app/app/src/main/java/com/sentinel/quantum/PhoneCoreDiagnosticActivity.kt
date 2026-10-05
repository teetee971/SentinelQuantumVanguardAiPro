package com.sentinel.quantum

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.PhoneCoreCertificationScopeProvider
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.security.SystemCallLogReader
import com.sentinel.quantum.security.readTelecomInCall
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Local-only technical view of raw Android and Phone Core certification facts. */
@OptIn(ExperimentalMaterial3Api::class)
class PhoneCoreDiagnosticActivity : ComponentActivity() {
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
        setContent {
            SentinelQuantumTheme {
                val callSession by SentinelInCallService.sessions.collectAsState()
                var telecomInCall by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            telecomInCall = readTelecomInCall()
                            delay(1_000)
                        }
                    }
                }
                var epoch by remember { mutableIntStateOf(0) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) epoch++
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val snapshot = remember(epoch) {
                    PhoneCoreLocalDiagnostics.read(applicationContext)
                }
                val runtimeFacts = remember(epoch) {
                    PhoneCoreRuntimeFacts.read(applicationContext)
                }
                val softwarePrerequisitesReady = remember(runtimeFacts) {
                    PhoneCoreSetupWizardStore.softwarePrerequisitesReady(runtimeFacts)
                }
                val setupChecks = remember(runtimeFacts) {
                    listOf(
                        PhoneCoreSetupWizardStore.Step.DIALER_ROLE to runtimeFacts.dialerRoleHeld,
                        PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE to runtimeFacts.callScreeningRoleHeld,
                        PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS to runtimeFacts.corePermissionsReady,
                        PhoneCoreSetupWizardStore.Step.CALL_LOG_PERMISSION to runtimeFacts.callLogPermissionGranted,
                        PhoneCoreSetupWizardStore.Step.SMS_ROLE to runtimeFacts.smsRoleHeld,
                        PhoneCoreSetupWizardStore.Step.SMS_PERMISSIONS to runtimeFacts.smsRuntimePermissionsReady,
                        PhoneCoreSetupWizardStore.Step.MMS_PERMISSIONS to runtimeFacts.mmsPermissionsReady,
                        PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS to runtimeFacts.notificationChannelsReady
                    )
                }
                val readyStepCount = remember(setupChecks) { setupChecks.count { it.second } }
                val nextSetupStep = remember(runtimeFacts) {
                    PhoneCoreSetupWizardStore.nextStep(runtimeFacts)
                }
                val remainingStepLabels = remember(setupChecks) {
                    setupChecks.filterNot { it.second }.map { PhoneCoreSetupWizardStore.stepLabel(it.first) }
                }
                val installTimestampMs = remember { currentInstallTimestamp() }
                val certificationScopeBound = remember(epoch) {
                    PhoneCoreCertificationScopeProvider.current(applicationContext) != null
                }
                val certificationEvidence by produceState(
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
                val certificationCriteria = remember(certificationEvidence) {
                    listOf(
                        "incoming_call_connected" to certificationEvidence.incomingCallConnected,
                        "outgoing_call_connected" to certificationEvidence.outgoingCallConnected,
                        "call_screening_observed" to certificationEvidence.callScreeningObserved,
                        "contacts_provider_ready" to certificationEvidence.contactsProviderReady,
                        "call_history_provider_ready" to certificationEvidence.callHistoryProviderReady,
                        "incoming_sms_received" to certificationEvidence.incomingSmsReceived,
                        "outgoing_sms_submitted" to certificationEvidence.outgoingSmsSubmitted,
                        "outgoing_sms_delivered" to certificationEvidence.outgoingSmsDeliveredSuccessfully,
                        "incoming_mms_safe_preview" to certificationEvidence.incomingMmsSafePreview,
                        "outgoing_mms_sent" to certificationEvidence.outgoingMmsSentSuccessfully,
                        "incoming_call_notification" to certificationEvidence.incomingCallNotificationPosted,
                        "incoming_sms_notification" to certificationEvidence.incomingSmsNotificationPosted,
                        "caller_id_ui_shown" to certificationEvidence.callerIdUiShown,
                        "in_call_ui_shown" to certificationEvidence.inCallUiShown
                    )
                }

                Scaffold(
                    topBar = {
                        SentinelTopBar(
                            title = "Diagnostic Phone Core",
                            subtitle = "Local · lecture seule · sans PII",
                            onBack = { finish() }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DiagnosticCard("État Phone Core") {
                            Fact(
                                "Prérequis logiciels",
                                if (softwarePrerequisitesReady) "PRÊTS" else "INCOMPLETS"
                            )
                            Fact("Étapes Android", "$readyStepCount/8 prêtes")
                            if (!softwarePrerequisitesReady) {
                                Text(
                                    "Prochaine étape : ${PhoneCoreSetupWizardStore.stepLabel(nextSetupStep)}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (remainingStepLabels.size > 1) {
                                    Text(
                                        "Restent aussi à configurer : " +
                                            remainingStepLabels.drop(1).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                Text(
                                    "Tous les prérequis logiciels sont observés. Les tests physiques restent distincts.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        DiagnosticCard("Certification Phone Core · technique") {
                            Fact(
                                "Portée de certification",
                                if (certificationScopeBound) "LIÉE À CET APK" else "ABSENTE"
                            )
                            Fact(
                                "Preuves observées",
                                "${certificationEvidence.completedCount}/${certificationEvidence.requiredCount}"
                            )
                            Fact(
                                "Certification locale",
                                if (certificationEvidence.fullyValidated) "COMPLÈTE" else "INCOMPLÈTE"
                            )
                            Text(
                                "Ces observations locales servent à la qualification technique de Sentinel, y compris sur émulateur. Elles ne sont pas des étapes que le client doit exécuter pour utiliser les fonctions déjà configurées. Elles ne valident ni un appareil physique ni un réseau opérateur ; cette validation reste indépendante.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            certificationCriteria.forEach { (id, passed) ->
                                Fact(
                                    PhoneCorePhysicalValidation.criterionLabel(id),
                                    if (passed) "OUI" else "NON"
                                )
                            }
                        }
                        DiagnosticCard("Liaison de l’appel en direct") {
                            Fact("Appel détecté par Android", when (telecomInCall) {
                                true -> "OUI"; false -> "NON"; null -> "NON VÉRIFIABLE"
                            })
                            Fact("Service Telecom Sentinel", if (callSession.serviceConnected) "LIÉ" else "NON LIÉ")
                            Fact("Sessions publiées actuelles", callSession.calls.size.toString())
                            Fact("Session affichable", yesNo(callSession.primary != null))
                            if (telecomInCall == true && callSession.primary == null) {
                                Text(
                                    "Défaut de liaison : un appel est détecté sans session Sentinel affichable.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        DiagnosticCard("Système") {
                            Fact("Android", "${snapshot.androidRelease} · API ${snapshot.sdkInt}")
                            Fact("Sentinel", "${snapshot.appVersionName} · code ${snapshot.appVersionCode}")
                            Fact("Téléphonie vocale", yesNo(snapshot.voiceCapable))
                        }
                        DiagnosticCard("Rôles Android") {
                            RoleFact("Téléphone", snapshot.dialerRoleAvailable, snapshot.dialerRoleHeld)
                            RoleFact("Filtrage d’appels", snapshot.callScreeningRoleAvailable, snapshot.callScreeningRoleHeld)
                            RoleFact("SMS", snapshot.smsRoleAvailable, snapshot.smsRoleHeld)
                        }
                        DiagnosticCard("Autorisations") {
                            Fact("Passer des appels", yesNo(snapshot.callPermission))
                            Fact("État du téléphone", yesNo(snapshot.phoneStatePermission))
                            Fact("Contacts", yesNo(snapshot.contactsPermission))
                            Fact("Historique des appels", yesNo(snapshot.callLogPermission))
                            Fact("Envoyer SMS", yesNo(snapshot.sendSmsPermission))
                            Fact("Lire SMS", yesNo(snapshot.readSmsPermission))
                            Fact("Recevoir SMS", yesNo(snapshot.receiveSmsPermission))
                            Fact("Recevoir MMS", yesNo(snapshot.receiveMmsPermission))
                            Fact("WAP Push MMS", yesNo(snapshot.receiveWapPushPermission))
                            Fact("Notifications", yesNo(snapshot.postNotificationsPermission))
                        }
                        DiagnosticCard("Notifications") {
                            Fact("Notifications globales", yesNo(snapshot.notificationsGloballyEnabled))
                            Fact("Canal appels", yesNo(snapshot.callNotificationChannelEnabled))
                            Fact("Canal SMS", yesNo(snapshot.smsNotificationChannelEnabled))
                            Fact(
                                "Plein écran appels",
                                if (snapshot.fullScreenIntentAllowed) "OUI" else "NON · À ACTIVER"
                            )
                            if (!snapshot.fullScreenIntentAllowed) {
                                Text(
                                    "Android 14+ gère cet accès séparément des permissions classiques. Il devient la prochaine étape seulement lorsque les étapes précédentes sont prêtes.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            "Le détail x/14 reste volontairement limité à ce diagnostic technique. Les écrans client doivent présenter des états qualitatifs de configuration, sans transformer la certification interne en parcours utilisateur.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DiagnosticCard(title: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@androidx.compose.runtime.Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@androidx.compose.runtime.Composable
private fun RoleFact(label: String, available: Boolean, held: Boolean) {
    Fact(label, when {
        !available -> "INDISPONIBLE"
        held -> "ACCORDÉ"
        else -> "DISPONIBLE · NON ACCORDÉ"
    })
}

private fun yesNo(value: Boolean): String = if (value) "OUI" else "NON"
