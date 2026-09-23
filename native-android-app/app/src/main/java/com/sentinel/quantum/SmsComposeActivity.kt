package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.SentinelSmsSender
import com.sentinel.quantum.security.SmsDeliveryStatusBus
import com.sentinel.quantum.security.SmsConversationStore
import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.security.SmsOtpPrivacy
import com.sentinel.quantum.security.WhatsAppClickToChat
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.MmsLocalInbox
import com.sentinel.quantum.security.SmsActivationActions
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsActivationUiModel
import com.sentinel.quantum.security.SmsActivationRefreshPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest

/**
 * SENDTO composer and staged conversation surface for the future default-SMS role.
 *
 * Sending, reading, exporting and deleting remain fail-closed unless Android confirms that
 * Sentinel is the default SMS handler and the corresponding runtime permission is granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SmsComposeActivity : ComponentActivity() {
    private fun sanitizeSmsDestination(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 32) return null
        if (value.count { it == '+' } > 1 || ('+' in value && !value.startsWith("+"))) return null
        if (!value.all { it.isDigit() || it in "+*#" }) return null
        return value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialScheme = intent?.data?.scheme.orEmpty()
        val initialMmsIntent = initialScheme.equals("mms", ignoreCase = true) ||
            initialScheme.equals("mmsto", ignoreCase = true)
        val initialDestination = sanitizeSmsDestination(
            intent?.data?.schemeSpecificPart.orEmpty().substringBefore('?')
        ).orEmpty()
        val initialBody = intent?.getStringExtra("sms_body")
            .orEmpty()
            .take(SentinelSmsSender.MAX_BODY_CHARS)

        setContent {
            SentinelQuantumTheme {
                var destination by remember { mutableStateOf(initialDestination) }
                var body by remember { mutableStateOf(initialBody) }
                var status by remember {
                    mutableStateOf<String?>(
                        if (initialMmsIntent) "Envoi MMS non activé : le décodeur et le transport MMS restent en validation sécurisée." else null
                    )
                }
                var activeSendToken by remember { mutableStateOf<Int?>(null) }
                var exportConfirmationPending by remember { mutableStateOf(false) }
                var selectedSubscriptionId by remember { mutableStateOf<Int?>(null) }
                var activationEpoch by remember { mutableStateOf(0) }
                val activationDiagnostics = remember { SmsActivationDiagnostics(applicationContext) }
                val activationActions = remember { SmsActivationActions(applicationContext) }
                val activationSnapshot = remember(activationEpoch) { activationDiagnostics.snapshot() }
                val activationModel = remember(activationSnapshot) { SmsActivationUiModel.from(activationSnapshot) }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (
                            event == Lifecycle.Event.ON_RESUME &&
                            SmsActivationRefreshPolicy.shouldRefresh(
                                SmsActivationRefreshPolicy.Event.ACTIVITY_RESUMED
                            )
                        ) {
                            activationEpoch++
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    activationEpoch++
                }
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    activationEpoch++
                }
                val activeSubscriptions = remember(activationEpoch) {
                    if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        runCatching {
                            applicationContext.getSystemService(SubscriptionManager::class.java)
                                .activeSubscriptionInfoList.orEmpty()
                                .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                        }.getOrDefault(emptyList())
                    } else emptyList()
                }
                LaunchedEffect(activeSubscriptions) {
                    selectedSubscriptionId = when {
                        activeSubscriptions.size == 1 -> activeSubscriptions.first().subscriptionId
                        selectedSubscriptionId != null &&
                            activeSubscriptions.any { it.subscriptionId == selectedSubscriptionId } -> selectedSubscriptionId
                        else -> null
                    }
                }
                val sender = remember { SentinelSmsSender(applicationContext) }
                val conversations = remember { SmsConversationStore(applicationContext) }
                val smsAnalyzer = remember { SmsLinkAnalyzer(LocalLogger(applicationContext)) }
                LaunchedEffect(activeSendToken) {
                    if (activeSendToken == null) return@LaunchedEffect
                    SmsDeliveryStatusBus.events.collectLatest { event ->
                        if (event.sendToken == activeSendToken) {
                            val part = if (event.partCount > 1) " · partie ${event.partIndex + 1}/${event.partCount}" else ""
                            status = when (event.stage) {
                                SmsDeliveryStatusBus.Stage.SENT ->
                                    if (event.successful) "Android signale l’envoi SMS réussi$part." else "Échec d’envoi signalé par Android$part."
                                SmsDeliveryStatusBus.Stage.DELIVERED ->
                                    if (event.successful) "Accusé de livraison reçu$part." else "Échec de livraison signalé$part."
                            }
                        }
                    }
                }
                val mmsDirectory = remember { File(applicationContext.filesDir, "mms-inbox") }
                var mmsItems by remember { mutableStateOf(MmsLocalInbox.list(mmsDirectory)) }
                var threads by remember {
                    mutableStateOf(
                        if (conversations.canRead()) conversations.recentThreads(50)
                        else emptyList()
                    )
                }
                var selectedThreadId by remember { mutableStateOf<Long?>(null) }
                var pendingDeleteThread by remember { mutableStateOf<SmsConversationStore.ThreadSummary?>(null) }
                var pendingDeleteMessage by remember { mutableStateOf<SmsConversationStore.Message?>(null) }
                var threadMessages by remember { mutableStateOf(emptyList<SmsConversationStore.Message>()) }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column {
                                    Text("Messages Sentinel", fontWeight = FontWeight.Bold)
                                    Text("SMS protégé", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                                }
                            }
                        )
                    }
                ) { scaffoldPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SMS SÉCURISÉ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Nouveau message", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Analyse locale et protection Sentinel. Aucun message n’est envoyé sans votre action.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (initialMmsIntent) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "MMS en validation",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Sentinel a reçu une demande MMS, mais l’envoi MMS reste verrouillé tant que le transport et le décodage sécurisés ne sont pas validés. Aucun SMS de substitution ne sera envoyé.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(activationModel.title, fontWeight = FontWeight.Bold)
                                Text(activationModel.detail, style = MaterialTheme.typography.bodySmall)
                                if (SmsActivationUiModel.Action.REQUEST_SMS_ROLE in activationModel.actions) {
                                    Button(
                                        onClick = {
                                            val request = activationActions.roleRequestIntent()
                                                ?: activationActions.legacyDefaultAppsIntent()
                                            if (request != null) roleLauncher.launch(request)
                                            else status = "Le sélecteur SMS Android n’est pas disponible sur cet appareil."
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Activer Sentinel pour les SMS") }
                                }
                                if (SmsActivationUiModel.Action.REQUEST_RUNTIME_PERMISSIONS in activationModel.actions) {
                                    OutlinedButton(
                                        onClick = {
                                            val permissions = activationActions.permissionsFor(activationSnapshot)
                                            if (permissions.isNotEmpty()) permissionLauncher.launch(permissions)
                                            else activationEpoch++
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Autoriser les permissions SMS nécessaires") }
                                }
                                if (SmsActivationUiModel.Action.RETRY_SIM_LOOKUP in activationModel.actions) {
                                    OutlinedButton(
                                        onClick = { activationEpoch++ },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Réessayer la détection SIM") }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it.take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Destinataire") },
                            supportingText = { Text("Numéro de téléphone, 32 caractères maximum") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it.take(SentinelSmsSender.MAX_BODY_CHARS) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Message") },
                            supportingText = { Text("${body.length} / ${SentinelSmsSender.MAX_BODY_CHARS}") },
                            minLines = 4,
                            maxLines = 8
                        )
                        if (activeSubscriptions.size > 1) {
                            Text("Ligne d’envoi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            activeSubscriptions.forEachIndexed { index, info ->
                                val id = info.subscriptionId
                                OutlinedButton(
                                    onClick = { selectedSubscriptionId = id },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val label = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                                        ?: "SIM ${index + 1}"
                                    Text(if (selectedSubscriptionId == id) "✓ $label" else label)
                                }
                            }
                            Text(
                                "Choisissez explicitement la SIM à utiliser. Sentinel ne sélectionne pas arbitrairement une ligne.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (activeSubscriptions.size == 1) {
                            Text(
                                "Ligne d’envoi : ${activeSubscriptions.first().displayName ?: "SIM 1"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "Ligne d’envoi indisponible tant que l’accès à l’état téléphonique n’est pas accordé ou qu’aucune SIM active n’est détectée.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                val result = sender.send(destination, body, selectedSubscriptionId)
                                status = when (result.reason) {
                                    "SUBMITTED_TO_ANDROID_TELEPHONY" -> "Demande d’envoi confiée à Android ; en attente du statut réseau."
                                    "SMS_SUBSCRIPTION_REQUIRED", "USER_SELECTION_REQUIRED" -> "Choisissez la SIM à utiliser."
                                    "REQUESTED_SUBSCRIPTION_NOT_ACTIVE" -> "La SIM sélectionnée n’est plus active. Actualisez puis choisissez une autre ligne."
                                    "NO_ACTIVE_SMS_SUBSCRIPTION" -> "Aucune SIM SMS active détectée."
                                    "READ_PHONE_STATE_PERMISSION_NOT_GRANTED" -> "Permission d’accès à l’état téléphonique non accordée."
                                    "SMS_SUBSCRIPTION_LOOKUP_FAILED" -> "Impossible de vérifier les SIM actives."
                                    "EMERGENCY_NUMBER_USE_DIALER" -> "Numéro d’urgence détecté : utilisez le composeur téléphonique."
                                    "SMS_ROLE_NOT_HELD" -> "Sentinel n’est pas l’application SMS par défaut."
                                    "SEND_SMS_PERMISSION_NOT_GRANTED" -> "Permission d’envoi SMS non accordée."
                                    "OUTGOING_PROVIDER_PERSIST_FAILED" -> "Impossible d’enregistrer le SMS dans la conversation. Envoi annulé."
                                    "TELEPHONY_SEND_FAILED" -> "Android n’a pas pu soumettre le SMS au système radio."
                                    "INVALID_DESTINATION" -> "Numéro destinataire invalide."
                                    "INVALID_MESSAGE" -> "Message invalide."
                                    else -> "Échec d’envoi."
                                }
                                if (result.accepted) {
                                    activeSendToken = result.sendToken
                                    body = ""
                                    threads = conversations.recentThreads(50)
                                    selectedThreadId?.let {
                                        threadMessages = conversations.messagesForThread(it, 100)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !initialMmsIntent && activationSnapshot.canSend && destination.isNotBlank() && body.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (body.isBlank()) "Écrire un message" else "Envoyer")
                        }

                        if (!sender.holdsSmsRole()) {
                            Text(
                                "Envoi, lecture et export restent verrouillés tant que Sentinel n’est pas l’application SMS par défaut choisie par l’utilisateur.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text("MMS reçus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = {
                                mmsItems = MmsLocalInbox.list(mmsDirectory)
                                status = "Index MMS actualisé"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Actualiser les MMS")
                        }
                        if (mmsItems.isEmpty()) {
                            Text(
                                "Aucun MMS local indexé. Les pièces jointes restent verrouillées tant que leur décodage sécurisé n’est pas validé.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            mmsItems.forEach { item ->
                                Card(
                                    Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("MMS local", fontWeight = FontWeight.Bold)
                                        Text(DateFormat.getDateTimeInstance().format(Date(item.receivedAtMs)), style = MaterialTheme.typography.bodySmall)
                                        Text("${item.sizeBytes} octets · contenu non ouvert", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        if (conversations.canRead()) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    "Conversations récentes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    threads = conversations.recentThreads(50)
                                    selectedThreadId?.let { threadMessages = conversations.messagesForThread(it, 100) }
                                    status = "Conversations actualisées"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Actualiser")
                            }

                            OutlinedButton(
                                onClick = { exportConfirmationPending = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Exporter jusqu’à 100 messages")
                            }
                            if (exportConfirmationPending) {
                                Card(
                                    Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Exporter les messages ?", fontWeight = FontWeight.Bold)
                                        Text(
                                            "L’export peut contenir les numéros de téléphone, le texte des SMS et leurs dates. Le fichier ne sera partagé qu’avec l’application que vous choisirez ensuite.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    exportConfirmationPending = false
                                                    status = "Export annulé"
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Annuler") }
                                            Button(
                                                onClick = {
                                                    exportConfirmationPending = false
                                                    val exported = conversations.exportRecentMessages(100)
                                                    if (exported == null) {
                                                        status = "Aucun message exportable"
                                                    } else {
                                                        val share = Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/json"
                                                            putExtra(Intent.EXTRA_STREAM, exported.uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        startActivity(Intent.createChooser(share, "Exporter les messages"))
                                                        status = "Export préparé : ${exported.messageCount} messages"
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Continuer") }
                                        }
                                    }
                                }
                            }

                            if (selectedThreadId == null) {
                                if (threads.isEmpty()) {
                                    Text(
                                        "Aucune conversation SMS disponible. Les nouveaux messages apparaîtront ici après réception ou envoi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                threads.forEach { thread ->
                                    var swipeDistance by remember(thread.threadId) { mutableStateOf(0f) }
                                    Card(
                                        Modifier
                                            .fillMaxWidth()
                                            .pointerInput(thread.threadId) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        if (abs(swipeDistance) >= 180f) {
                                                            pendingDeleteThread = thread
                                                            status = "Suppression préparée · confirmez ou annulez"
                                                        }
                                                        swipeDistance = 0f
                                                    },
                                                    onDragCancel = { swipeDistance = 0f },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        swipeDistance += dragAmount
                                                    }
                                                )
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (abs(swipeDistance) >= 90f) {
                                                Text(
                                                    "Relâchez pour supprimer",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                                                                        Text(thread.address.ifBlank { "Inconnu" }, fontWeight = FontWeight.Bold)
                                            Text(
                                                DateFormat.getDateTimeInstance().format(Date(thread.latestTimestampMs)),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(thread.latestBody.take(240))
                                            val previewRisk = remember(thread.threadId, thread.latestBody) { smsAnalyzer.analyze(thread.latestBody) }
                                            if (previewRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.LOW && previewRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.UNKNOWN) {
                                                Text(
                                                    "Analyse locale : ${previewRisk.riskLevel.name} · ${previewRisk.findings.size} signal(aux)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Text(
                                                "${thread.messageCount} message(s)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    val uri = WhatsAppClickToChat.uriFor(thread.address)
                                                    if (uri == null) {
                                                        status = "WhatsApp nécessite un numéro au format international (+code pays)."
                                                    } else {
                                                        runCatching {
                                                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                                                        }.onFailure {
                                                            status = "Impossible d’ouvrir WhatsApp sur cet appareil."
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Ouvrir dans WhatsApp")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    selectedThreadId = thread.threadId
                                                    threadMessages = conversations.messagesForThread(thread.threadId, 100)
                                                    destination = thread.address.take(32)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Ouvrir la conversation")
                                            }
                                        }
                                    }
                                }
                                pendingDeleteThread?.let { pending ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Supprimer cette conversation ?", fontWeight = FontWeight.Bold)
                                            Text(pending.address.ifBlank { "Inconnu" }, style = MaterialTheme.typography.bodySmall)
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        pendingDeleteThread = null
                                                        status = "Suppression annulée"
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("Annuler") }
                                                Button(
                                                    onClick = {
                                                        val deleted = conversations.deleteThread(pending.threadId)
                                                        pendingDeleteThread = null
                                                        if (deleted > 0) {
                                                            threads = conversations.recentThreads(50)
                                                            status = "Conversation supprimée · $deleted message(s)"
                                                        } else {
                                                            status = "Suppression refusée ou impossible"
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("Supprimer") }
                                            }
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedThreadId = null
                                        threadMessages = emptyList()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retour aux conversations")
                                }
                                if (threadMessages.isEmpty()) {
                                    Text(
                                        "Aucun message disponible dans cette conversation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                threadMessages.forEach { message ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(message.address.ifBlank { "Inconnu" })
                                            Text(
                                                when (message.type) {
                                                    android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX -> "Reçu"
                                                    android.provider.Telephony.Sms.MESSAGE_TYPE_SENT -> "Envoyé"
                                                    android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                                                    android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Envoi en cours"
                                                    android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED -> "Échec d’envoi"
                                                    android.provider.Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Brouillon"
                                                    else -> "Message"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                DateFormat.getDateTimeInstance().format(Date(message.timestampMs)),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(message.body.take(1000))
                                            val messageRisk = remember(message.id, message.body) { smsAnalyzer.analyze(message.body) }
                                            val otpPrivacy = remember(message.id, message.body) { SmsOtpPrivacy.inspect(message.body) }
                                            if (otpPrivacy.containsOtp) {
                                                Text(
                                                    "Code à usage unique détecté localement · " + (otpPrivacy.codeLength ?: 0) + " chiffres · contenu non destiné à l’enrichissement distant",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (messageRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.LOW && messageRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.UNKNOWN) {
                                                Text(
                                                    "Risque local ${messageRisk.riskLevel.name} · score ${messageRisk.score}/100 · ${messageRisk.findings.joinToString { it.code }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    pendingDeleteMessage = message
                                                    status = "Suppression du message préparée · confirmez ou annulez"
                                                }
                                            ) {
                                                Text("Supprimer ce message")
                                            }

                                        }
                                    }
                                }
                            }
                            pendingDeleteMessage?.let { pending ->
                                Card(
                                    Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Supprimer ce message ?", fontWeight = FontWeight.Bold)
                                        Text("Cette action supprime le message de la base SMS Android.", style = MaterialTheme.typography.bodySmall)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                pendingDeleteMessage = null
                                                status = "Suppression annulée"
                                            }, modifier = Modifier.weight(1f)) { Text("Annuler") }
                                            Button(onClick = {
                                                val deleted = conversations.deleteMessage(pending.id)
                                                pendingDeleteMessage = null
                                                status = if (deleted) {
                                                    selectedThreadId?.let { threadMessages = conversations.messagesForThread(it, 100) }
                                                    threads = conversations.recentThreads(50)
                                                    "Message supprimé"
                                                } else "Suppression refusée ou impossible"
                                            }, modifier = Modifier.weight(1f)) { Text("Supprimer") }
                                        }
                                    }
                                }
                            }
                        }

                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                        Text(
                            "Les exports sont créés dans un cache privé temporaire et ne sont partagés qu’après votre action explicite.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
