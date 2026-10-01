package com.sentinel.quantum

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.SentinelTopBar

/** Local-only view of raw Android activation facts. It performs no requests and no network I/O. */
@OptIn(ExperimentalMaterial3Api::class)
class PhoneCoreDiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
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
                        PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS to runtimeFacts.corePermissionsReady,
                        PhoneCoreSetupWizardStore.Step.DIALER_ROLE to runtimeFacts.dialerRoleHeld,
                        PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE to runtimeFacts.callScreeningRoleHeld,
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
                Scaffold(
                    topBar = {
                        SentinelTopBar(
                            title = "Diagnostic activation Android",
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
                            "Cet écran n’est pas une certification Phone Core 13/13. Il affiche uniquement des faits Android relus au retour au premier plan. Aucun numéro, SIM, compte, contact, identifiant matériel ou résultat du wizard n’est affiché.",
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
