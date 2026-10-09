package com.sentinel.quantum.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sentinel.quantum.PhoneCoreRuntimeFacts
import com.sentinel.quantum.PhoneCoreSetupWizardStore
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationsHubScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var runtimeEpoch by remember { mutableStateOf(0) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) runtimeEpoch++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val phoneFacts = remember(runtimeEpoch) {
        PhoneCoreRuntimeFacts.read(context.applicationContext)
    }
    val smsSnapshot = remember(runtimeEpoch) {
        SmsActivationDiagnostics(context.applicationContext).snapshot()
    }
    val phoneSteps = listOf(
        phoneFacts.corePermissionsReady,
        phoneFacts.dialerRoleHeld,
        phoneFacts.callScreeningRoleHeld,
        phoneFacts.callLogPermissionGranted,
        phoneFacts.smsRoleHeld,
        phoneFacts.smsRuntimePermissionsReady,
        phoneFacts.mmsPermissionsReady,
        phoneFacts.notificationChannelsReady
    )
    val readySteps = phoneSteps.count { it }
    val smsStatus = when (smsSnapshot.smsRoleState) {
        SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE ->
            "SMS indisponible dans cette configuration Android."
        SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD ->
            "SMS bloqué · choisissez Sentinel comme application SMS par défaut."
        SmsActivationDiagnostics.SmsRoleState.HELD -> when {
            !phoneFacts.smsRuntimePermissionsReady ->
                "Rôle SMS actif · autorisations SMS encore à accorder."
            !phoneFacts.mmsPermissionsReady ->
                "SMS prêts · autorisations MMS encore à accorder."
            else ->
                "Prérequis SMS/MMS prêts · validation physique MMS encore requise."
        }
    }
    val callsStatus = if (phoneFacts.dialerRoleHeld) {
        "Composeur Sentinel activé · tests d’appel réels encore distincts."
    } else {
        "À activer · définir Sentinel comme application Téléphone."
    }
    var showExternalChannels by remember { mutableStateOf(false) }
    var actionStatus by rememberSaveable { mutableStateOf<String?>(null) }

    fun launchPhoneSurface(request: Intent, surface: String) {
        actionStatus = null
        try {
            context.startActivity(request)
        } catch (_: ActivityNotFoundException) {
            actionStatus = "Android n’a pas pu ouvrir $surface. Vérifiez l’installation de Sentinel, puis réessayez."
        } catch (_: RuntimeException) {
            actionStatus = "Android a refusé l’ouverture de $surface. Vérifiez l’installation de Sentinel, puis réessayez."
        }
    }

    val externalChannels = listOf(
        "WhatsApp" to "Ouverture ponctuelle depuis une fiche contact/numéro · pas de synchronisation",
        "Telegram" to "Non raccordé",
        "Instagram" to "Non raccordé",
        "Messenger" to "Non raccordé",
        "Signal" to "Non raccordé",
        "Discord" to "Non raccordé",
        "Teams" to "Non raccordé · compte requis",
        "Slack" to "Non raccordé · compte requis"
    )

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Communications",
                subtitle = "Téléphone, SMS/MMS & canaux externes",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelHero(
                eyebrow = "Communications",
                title = "Canaux Sentinel",
                body = "Téléphonie et messagerie locales, avec connexions externes séparées et état explicite. Un canal non raccordé reste affiché comme non raccordé.",
                badges = listOf(
                    "Local" to SentinelD1.Success,
                    "État explicite" to SentinelD1.Cyan
                )
            )
            actionStatus?.let { status ->
                Text(
                    status,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SentinelSectionHeader(
                title = "Actions essentielles",
                subtitle = "Téléphoner, écrire et terminer l’activation sans chercher dans les réglages."
            )
            ChannelStatus(
                "Phone Core",
                if (PhoneCoreSetupWizardStore.softwarePrerequisitesReady(phoneFacts)) {
                    "8/8 étapes Android prêtes · passer aux tests physiques."
                } else {
                    "$readySteps/8 étapes Android prêtes · reprendre la configuration."
                },
                actionLabel = if (PhoneCoreSetupWizardStore.softwarePrerequisitesReady(phoneFacts)) "Tester" else "Continuer"
            ) {
                launchPhoneSurface(
                    Intent(context, PhoneCoreActivationActivity::class.java),
                    "la configuration Phone Core"
                )
            }
            ChannelStatus("Appels", callsStatus, actionLabel = if (phoneFacts.dialerRoleHeld) "Ouvrir" else "Activer") {
                launchPhoneSurface(
                    Intent(context, SentinelDialerActivity::class.java),
                    "le composeur Téléphone"
                )
            }
            ChannelStatus(
                "SMS / MMS",
                smsStatus,
                actionLabel = if (smsSnapshot.smsRoleState == SmsActivationDiagnostics.SmsRoleState.HELD) "Ouvrir" else "Activer"
            ) {
                launchPhoneSurface(
                    Intent(context, SmsComposeActivity::class.java),
                    "la messagerie SMS/MMS"
                )
            }
            SentinelSectionHeader(
                title = "Canaux externes",
                subtitle = "Pas de synchronisation silencieuse : les intégrations non configurées restent séparées."
            )
            OutlinedButton(
                onClick = { showExternalChannels = !showExternalChannels },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (showExternalChannels) {
                        "Masquer les canaux externes"
                    } else {
                        "Voir les canaux externes (${externalChannels.size})"
                    }
                )
            }
            if (showExternalChannels) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        externalChannels.forEach { (name, status) ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "Aucun message, contact ou contenu tiers n’est importé ou transmis par cet écran.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChannelStatus(
    name: String,
    status: String,
    actionLabel: String = "Ouvrir",
    onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onClick != null) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
