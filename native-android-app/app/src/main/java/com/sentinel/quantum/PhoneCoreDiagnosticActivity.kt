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
import androidx.compose.material3.TopAppBar
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
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Diagnostic activation Android", fontWeight = FontWeight.Bold)
                                    Text("Local · lecture seule · sans PII", style = MaterialTheme.typography.labelSmall)
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DiagnosticCard("État Phone Core") {
                            Fact(
                                "Prérequis logiciels",
                                if (softwarePrerequisitesReady) "PRÊTS" else "INCOMPLETS"
                            )
                            if (!snapshot.fullScreenIntentAllowed) {
                                Text(
                                    "Blocage actuel : autoriser l’affichage plein écran des appels. Sur Android 14+ cet accès spécial est distinct des permissions classiques.",
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
                            Fact("POST_NOTIFICATIONS", yesNo(snapshot.postNotificationsPermission))
                        }
                        DiagnosticCard("Notifications") {
                            Fact("Notifications globales", yesNo(snapshot.notificationsGloballyEnabled))
                            Fact("Canal appels", yesNo(snapshot.callNotificationChannelEnabled))
                            Fact("Canal SMS", yesNo(snapshot.smsNotificationChannelEnabled))
                            Fact(
                                "Plein écran appels",
                                if (snapshot.fullScreenIntentAllowed) "OUI" else "NON · BLOQUANT"
                            )
                        }
                        Text(
                            "Cet écran n’est pas une certification 14/14. Il affiche uniquement des faits Android relus au retour au premier plan. Aucun numéro, SIM, compte, contact, identifiant matériel ou résultat du wizard n’est affiché.",
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
