package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.SentinelSmsSender
import com.sentinel.quantum.security.SmsDeliveryStatusBus
import com.sentinel.quantum.security.SmsCallbackProgress
import com.sentinel.quantum.security.SmsCallbackFeedback
import com.sentinel.quantum.security.SmsTimestampOrder
import com.sentinel.quantum.security.SmsConversationStore
import com.sentinel.quantum.security.SmsProviderMessageState
import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.security.SmsOtpPrivacy
import com.sentinel.quantum.security.WhatsAppClickToChat
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.MmsLocalInbox
import com.sentinel.quantum.security.SmsActivationActions
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsActivationUiModel
import com.sentinel.quantum.security.SmsActivationRefreshPolicy
import com.sentinel.quantum.security.SmsSubscriptionState
import com.sentinel.quantum.security.SmsSubmitReadiness
import com.sentinel.quantum.security.SmsThreadOrganizer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.PhoneCoreDisclosure
import com.sentinel.quantum.ui.design.PhoneCoreConversationRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sentinel.quantum.ui.design.SentinelTopBar
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
        val openConversationsOnLaunch =
            intent?.getBooleanExtra(EXTRA_OPEN_CONVERSATIONS, false) == true ||
                (
                    intent?.action == Intent.ACTION_MAIN &&
                        initialDestination.isBlank() &&
                        initialBody.isBlank()
                )

        setContent {
            SentinelQuantumTheme {
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                        .isAppearanceLightStatusBars = false
                }
                val scrollState = rememberScrollState()
                var destination by rememberSaveable { mutableStateOf(initialDestination) }
                var body by rememberSaveable { mutableStateOf(initialBody) }
                var status by remember {
                    mutableStateOf<String?>(
                        if (initialMmsIntent) "Envoi MMS sortant non activé : la réception et l’aperçu sécurisé sont disponibles côté logiciel, mais le transport MMS sortant n’est pas encore validé." else null
                    )
                }
                var activeSendToken by remember { mutableStateOf<Int?>(null) }
                var activeProviderMessageId by remember { mutableStateOf<Long?>(null) }
                var callbackProgress by remember { mutableStateOf<SmsCallbackProgress.State?>(null) }
                var providerPersistenceFailed by remember { mutableStateOf(false) }
                var exportConfirmationPending by remember { mutableStateOf(false) }
                var selectedSubscriptionId by remember { mutableStateOf<Int?>(null) }
                var activationEpoch by remember { mutableStateOf(0) }
                var mmsSectionExpanded by remember { mutableStateOf(false) }
                var conversationsSectionExpanded by rememberSaveable { mutableStateOf(initialDestination.isBlank() && initialBody.isBlank() && !initialMmsIntent) }
                var showComposer by rememberSaveable {
                    mutableStateOf(initialDestination.isNotBlank() || initialBody.isNotBlank() || initialMmsIntent)
                }
                var threadCategoryFilter by remember { mutableStateOf(SmsThreadOrganizer.Category.ALL) }
                val settingsStore = remember { SettingsStore(applicationContext) }
                var notificationPreviewEnabled by remember {
                    mutableStateOf(settingsStore.smsNotificationPreviewEnabled)
                }
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
                val subscriptionState = remember { SmsSubscriptionState(applicationContext) }
                val subscriptionResult = remember(activationEpoch) { subscriptionState.load() }
                val activeSubscriptions = when (subscriptionResult) {
                    is SmsSubscriptionState.Result.Available -> subscriptionResult.subscriptions
                    SmsSubscriptionState.Result.PermissionRequired,
                    SmsSubscriptionState.Result.LookupFailed -> emptyList()
                }
                LaunchedEffect(subscriptionResult) {
                    selectedSubscriptionId = when (val result = subscriptionResult) {
                        is SmsSubscriptionState.Result.Available -> SmsSubscriptionState.reconcileSelection(
                            selectedSubscriptionId,
                            result.subscriptions.map { it.subscriptionId }
                        )
                        SmsSubscriptionState.Result.PermissionRequired,
                        SmsSubscriptionState.Result.LookupFailed -> null
                    }
                }
                val sender = remember { SentinelSmsSender(applicationContext) }
                val conversations = remember { SmsConversationStore(applicationContext) }
                val smsAnalyzer = remember { SmsLinkAnalyzer(LocalLogger(applicationContext)) }
                val ioScope = rememberCoroutineScope()
                val mmsDirectory = remember { File(applicationContext.filesDir, "mms-inbox") }
                var mmsItems by remember { mutableStateOf(emptyList<MmsLocalInbox.Item>()) }
                LaunchedEffect(mmsDirectory) {
                    mmsItems = withContext(Dispatchers.IO) { MmsLocalInbox.list(mmsDirectory) }
                }
                var replyDrafts by rememberSaveable(stateSaver = mapSaver(
                    save = { drafts: Map<Long, String> -> drafts.mapKeys { it.key.toString() } },
                    restore = { saved -> saved.entries.mapNotNull { (key, value) ->
                        val id = key.toLongOrNull()
                        val text = value as? String
                        if (id != null && id > 0L && text != null) id to text.take(SentinelSmsSender.MAX_BODY_CHARS) else null
                    }.take(5).toMap() }
                )) { mutableStateOf(emptyMap<Long, String>()) }
                var providerEpoch by remember { mutableStateOf(0) }
                var threads by remember {
                    mutableStateOf(emptyList<SmsConversationStore.ThreadSummary>())
                }
                var selectedThreadId by rememberSaveable { mutableStateOf<Long?>(null) }
                var pendingDeleteThread by remember { mutableStateOf<SmsConversationStore.ThreadSummary?>(null) }
                var pendingDeleteMessage by remember { mutableStateOf<SmsConversationStore.Message?>(null) }
                var threadMessages by remember { mutableStateOf(emptyList<SmsConversationStore.Message>()) }
                val visibleThreads = remember(threads, threadCategoryFilter) {
                    threads.filter { SmsThreadOrganizer.matches(threadCategoryFilter, it.latestBody) }
                }
                fun submitSms(recipient: String, message: String, onAccepted: () -> Unit) {
                    ioScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            sender.send(recipient, message, selectedSubscriptionId)
                        }
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
                            "EMERGENCY_NUMBER_CHECK_FAILED" -> "Vérification du numéro d’urgence impossible. Envoi bloqué par sécurité."
                            "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN" -> "Android a interrompu la demande d’envoi ; le résultat de soumission n’est pas confirmé. Vérifiez le statut du message avant de réessayer."
                            "INVALID_DESTINATION" -> "Numéro destinataire invalide."
                            "INVALID_MESSAGE" -> "Message invalide."
                            else -> "Échec d’envoi."
                        }
                        if (result.accepted) {
                            callbackProgress = null
                            providerPersistenceFailed = false
                            activeSendToken = result.sendToken
                            activeProviderMessageId = result.providerMessageId
                            onAccepted()
                            providerEpoch++
                        }
                    }
                }
                DisposableEffect(activationEpoch) {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) { providerEpoch++ }
                    }
                    val registered = conversations.canRead() && runCatching {
                        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
                    }.isSuccess
                    onDispose { if (registered) runCatching { contentResolver.unregisterContentObserver(observer) } }
                }
                LaunchedEffect(providerEpoch, activationEpoch, selectedThreadId) {
                    val threadId = selectedThreadId
                    val refreshed = withContext(Dispatchers.IO) {
                        conversations.recentThreads(50) to
                            (threadId?.let { conversations.messagesForThread(it, 100) } ?: emptyList())
                    }
                    threads = refreshed.first
                    threadMessages = refreshed.second
                }
                LaunchedEffect(selectedThreadId, threadMessages.lastOrNull()?.id) {
                    if (selectedThreadId != null && threadMessages.isNotEmpty()) {
                        withFrameNanos { }
                        withFrameNanos { }
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
                LaunchedEffect(activeSendToken, activeProviderMessageId) {
                    if (activeSendToken == null || activeProviderMessageId == null) return@LaunchedEffect
                    SmsDeliveryStatusBus.events.collectLatest { event ->
                        if (
                            event.sendToken != activeSendToken ||
                            event.providerMessageId != activeProviderMessageId
                        ) return@collectLatest
                        providerPersistenceFailed = providerPersistenceFailed || !event.providerWriteSucceeded
                        val progress = SmsCallbackProgress.record(
                            callbackProgress, event.partIndex, event.partCount, event.stage, event.successful
                        )
                        if (progress != null) callbackProgress = progress.state
                        callbackProgress?.let {
                            status = SmsCallbackFeedback.message(it, providerPersistenceFailed)
                        }
                        providerEpoch++
                        selectedThreadId?.let { threadId ->
                            threadMessages = withContext(Dispatchers.IO) {
                                conversations.messagesForThread(threadId, 100)
                            }
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        SentinelTopBar(
                            title = "Messages Sentinel",
                            subtitle = "SMS Android · confidentialité locale",
                            onBack = { finish() }
                        )
                    },
                    bottomBar = {
                        val threadId = selectedThreadId
                        val replyAddress = threads.firstOrNull { it.threadId == threadId }?.address
                            ?: threadMessages.lastOrNull()?.address.orEmpty()
                        val replyVisible = !showComposer && threadId != null
                        if (status != null || replyVisible) {
                            Surface(tonalElevation = 4.dp) {
                                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    status?.let { message ->
                                        Text(message, modifier = Modifier.semantics {
                                            liveRegion = LiveRegionMode.Polite
                                        }, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (replyVisible && threadId != null) {
                                        val draft = replyDrafts[threadId].orEmpty()
                                        if (activeSubscriptions.size > 1) {
                                            PhoneCoreDisclosure(title = "Ligne d’envoi") {
                                                activeSubscriptions.forEach { info ->
                                                    OutlinedButton(onClick = { selectedSubscriptionId = info.subscriptionId }) {
                                                        Text((if (selectedSubscriptionId == info.subscriptionId) "✓ " else "") +
                                                            (info.displayName?.toString() ?: "SIM"))
                                                    }
                                                }
                                            }
                                        } else {
                                            Text("Ligne d’envoi : " + (activeSubscriptions.firstOrNull()?.displayName ?: "indisponible"),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = draft,
                                                onValueChange = { text ->
                                                    if (text.isBlank()) replyDrafts = replyDrafts - threadId
                                                    else if (threadId in replyDrafts || replyDrafts.size < 5)
                                                        replyDrafts = replyDrafts + (threadId to text.take(SentinelSmsSender.MAX_BODY_CHARS))
                                                    else status = "Cinq brouillons sont conservés. Terminez-en un avant d’en créer un autre."
                                                },
                                                label = { Text("Répondre") },
                                                enabled = sanitizeSmsDestination(replyAddress) != null,
                                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                                maxLines = 3
                                            )
                                            Button(
                                                onClick = { submitSms(replyAddress, draft) { replyDrafts = replyDrafts - threadId } },
                                                enabled = SmsSubmitReadiness.canSubmit(
                                                    activationCanSend = activationSnapshot.canSend,
                                                    activeSubscriptionIds = activeSubscriptions.map { it.subscriptionId },
                                                    selectedSubscriptionId = selectedSubscriptionId,
                                                    destinationPresent = sanitizeSmsDestination(replyAddress) != null,
                                                    bodyPresent = draft.isNotBlank(),
                                                    isMmsIntent = initialMmsIntent
                                                )
                                            ) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp)); Text("Envoyer") }
                                        }
                                        if (sanitizeSmsDestination(replyAddress) == null)
                                            Text("Cet expéditeur ne permet pas une réponse SMS.", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                ) { scaffoldPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhoneCoreBrand(
                            context = "Messages",
                            status = activationModel.title,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TabRow(selectedTabIndex = if (showComposer) 1 else 0) {
                            Tab(selected = !showComposer, onClick = {
                                showComposer = false
                                selectedThreadId = null
                                conversationsSectionExpanded = true
                            }, text = { Text("Conversations") })
                            Tab(selected = showComposer, onClick = { showComposer = true }, text = { Text("Écrire") })
                        }
                        if (showComposer) {
                            Text("Nouveau SMS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Analyse locale · SMS opérateur sans chiffrement de bout en bout", style = MaterialTheme.typography.bodySmall)
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
                                        "Envoi MMS non disponible",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Sentinel sait recevoir, télécharger et filtrer les MMS entrants dans un chemin local borné. L’envoi MMS sortant reste volontairement verrouillé tant que son transport opérateur n’est pas implémenté et validé. Aucun SMS de substitution ne sera envoyé.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (
                            activationSnapshot.state != SmsActivationDiagnostics.State.READY
                        ) {
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
                                    if (activationSnapshot.needsSendRuntimePermissions) {
                                        OutlinedButton(
                                            onClick = {
                                                val permissions = activationActions.sendPermissionsFor(activationSnapshot)
                                                if (permissions.isNotEmpty()) permissionLauncher.launch(permissions)
                                                else activationEpoch++
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Autoriser les permissions nécessaires à l’envoi") }
                                    }
                                    val inboxPermissions = activationActions.inboxPermissionsFor(activationSnapshot)
                                    if (inboxPermissions.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { permissionLauncher.launch(inboxPermissions) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Autoriser l’accès aux conversations SMS") }
                                    }
                                    if (SmsActivationUiModel.Action.RETRY_SIM_LOOKUP in activationModel.actions) {
                                        OutlinedButton(
                                            onClick = { activationEpoch++ },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Réessayer la détection SIM") }
                                    }
                                }
                            }
    
                            }
                        if (showComposer) {
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
                                minLines = 2,
                                maxLines = 6
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
                                    when (subscriptionResult) {
                                        SmsSubscriptionState.Result.PermissionRequired ->
                                            "Ligne d’envoi indisponible : autorisez l’accès à l’état téléphonique pour vérifier les SIM actives."
                                        SmsSubscriptionState.Result.LookupFailed ->
                                            "Ligne d’envoi indisponible : Android n’a pas pu lire les SIM actives. Réessayez la détection."
                                        is SmsSubscriptionState.Result.Available ->
                                            "Ligne d’envoi indisponible : aucune SIM active n’est détectée."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
    
                            Button(
                                onClick = {
                                    submitSms(destination, body) { body = "" }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = SmsSubmitReadiness.canSubmit(
                                    activationCanSend = activationSnapshot.canSend,
                                    activeSubscriptionIds = activeSubscriptions.map { it.subscriptionId },
                                    selectedSubscriptionId = selectedSubscriptionId,
                                    destinationPresent = destination.isNotBlank(),
                                    bodyPresent = body.isNotBlank(),
                                    isMmsIntent = initialMmsIntent
                                )
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
                            }

                        if (showComposer) {
                            PhoneCoreDisclosure(title = "Confidentialité des notifications") {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (notificationPreviewEnabled) "Expéditeur et aperçu visibles" else "Expéditeur et contenu masqués",
                                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                                    )
                                    Switch(checked = notificationPreviewEnabled, onCheckedChange = { enabled ->
                                        notificationPreviewEnabled = enabled
                                        settingsStore.smsNotificationPreviewEnabled = enabled
                                    })
                                }
                                Text(
                                    if (notificationPreviewEnabled) "Option activée explicitement : nom/numéro et extrait peuvent apparaître dans la notification."
                                    else "La notification indique seulement l’arrivée d’un message. Le contenu reste dans Sentinel.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (!showComposer) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MMS reçus · ${mmsItems.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { mmsSectionExpanded = !mmsSectionExpanded }) {
                                Text(if (mmsSectionExpanded) "Masquer" else "Afficher")
                            }
                        }
                        if (mmsSectionExpanded) {
                            Text("MMS reçus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                onClick = {
                                    ioScope.launch {
                                        mmsItems = withContext(Dispatchers.IO) {
                                            MmsLocalInbox.list(mmsDirectory)
                                        }
                                        status = "Index MMS actualisé"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Actualiser les MMS")
                            }
                            if (mmsItems.isEmpty()) {
                                Text(
                                    "Aucun MMS local reçu pour le moment. Le décodeur et le téléchargement sécurisé sont disponibles côté logiciel ; la validation finale de réception reste à effectuer sur appareil physique et réseau opérateur.",
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
                                            Text("${item.sizeBytes} octets · conservé localement · contenu non sûr maintenu en quarantaine", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
    
    
                        }

                        }
                        if (!showComposer && conversations.canRead()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (selectedThreadId == null) "Conversations · ${threads.size}" else "Conversation ouverte",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { conversationsSectionExpanded = !conversationsSectionExpanded }) {
                                    Text(if (conversationsSectionExpanded) "Masquer" else "Afficher")
                                }
                            }
                        }
                        if (!showComposer && conversationsSectionExpanded) {
                            if (conversations.canRead()) {
                                PhoneCoreDisclosure(title = "Actions et export") {
                                    TextButton(onClick = { providerEpoch++; status = "Conversations actualisées" }) {
                                        Text("Actualiser")
                                    }
                                    TextButton(onClick = { exportConfirmationPending = true }) {
                                        Text("Exporter jusqu’à 100 messages")
                                    }
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
                                                        ioScope.launch {
                                                            val exported = withContext(Dispatchers.IO) {
                                                                conversations.exportRecentMessages(100)
                                                            }
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
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("Continuer") }
                                            }
                                        }
                                    }
                                }
    
                                if (selectedThreadId == null) {
                                    Text(
                                        "Organisation locale indicative · classement fondé uniquement sur l’aperçu du dernier SMS.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SmsThreadOrganizer.Category.entries.forEach { category ->
                                            FilterChip(
                                                selected = threadCategoryFilter == category,
                                                onClick = { threadCategoryFilter = category },
                                                label = { Text(category.labelFr) }
                                            )
                                        }
                                    }
                                    if (threads.isEmpty()) {
                                        Text(
                                            "Aucune conversation SMS disponible. Les nouveaux messages apparaîtront ici après réception ou envoi.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (visibleThreads.isEmpty()) {
                                        Text(
                                            "Aucune conversation dans cette catégorie.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    visibleThreads.forEach { thread ->
                                        key(thread.threadId) {
                                            val previewRisk = remember(thread.threadId, thread.latestBody) { smsAnalyzer.analyze(thread.latestBody) }
                                            PhoneCoreConversationRow(
                                                address = thread.address,
                                                preview = thread.latestBody.take(240),
                                                date = (if (SmsTimestampOrder.isAnomalous(thread.latestTimestampMs, System.currentTimeMillis())) "Date anormale (Android) : " else "") +
                                                    DateFormat.getDateTimeInstance().format(Date(thread.latestTimestampMs)),
                                                count = thread.messageCount,
                                                risk = if (previewRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.LOW && previewRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.UNKNOWN)
                                                    "Analyse locale : ${PhoneCoreFrenchLabels.riskLevel(previewRisk.riskLevel.name)} · ${previewRisk.findings.size} signal(aux)"
                                                else null,
                                                onOpen = {
                                                    selectedThreadId = thread.threadId
                                                    threadMessages = emptyList()
                                                },
                                                onWhatsApp = {
                                                    val uri = WhatsAppClickToChat.uriFor(thread.address)
                                                    if (uri == null) status = "WhatsApp nécessite un numéro au format international (+code pays)."
                                                    else runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                                        .onFailure { status = "Impossible d’ouvrir WhatsApp sur cet appareil." }
                                                },
                                                onDelete = {
                                                    pendingDeleteThread = thread
                                                    status = "Suppression préparée · confirmez ou annulez"
                                                }
                                            )
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
                                                            pendingDeleteThread = null
                                                            ioScope.launch {
                                                                val result = withContext(Dispatchers.IO) {
                                                                    val deleted = conversations.deleteThread(pending.threadId)
                                                                    deleted to if (deleted > 0) conversations.recentThreads(50) else emptyList()
                                                                }
                                                                val (deleted, refreshedThreads) = result
                                                                if (deleted > 0) {
                                                                    threads = refreshedThreads
                                                                    if (selectedThreadId == pending.threadId) {
                                                                        selectedThreadId = null
                                                                        threadMessages = emptyList()
                                                                    }
                                                                    status = "Conversation supprimée · $deleted message(s)"
                                                                } else {
                                                                    status = "Suppression refusée ou impossible"
                                                                }
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
                                    val replyAddress = threads.firstOrNull { it.threadId == selectedThreadId }?.address
                                        ?: threadMessages.lastOrNull()?.address.orEmpty()
                                    Text(replyAddress.ifBlank { "Conversation" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (threadMessages.isEmpty()) {
                                        Text(
                                            "Aucun message disponible dans cette conversation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    threadMessages.forEach { message ->
                                        key(message.id) {
                                            val swipeState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { target ->
                                                    if (target == SwipeToDismissBoxValue.EndToStart) {
                                                        pendingDeleteMessage = message
                                                        status = "Suppression du message préparée · confirmez ou annulez"
                                                    }
                                                    false
                                                }
                                            )
                                            SwipeToDismissBox(
                                                state = swipeState,
                                                enableDismissFromStartToEnd = false,
                                                enableDismissFromEndToStart = true,
                                                backgroundContent = {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(0.92f)
                                                            .align(
                                                                if (message.type == Telephony.Sms.MESSAGE_TYPE_INBOX)
                                                                    Alignment.Start
                                                                else
                                                                    Alignment.End
                                                            )
                                                            .padding(horizontal = 18.dp),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "Supprimer ce message",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            ) {
                                                Card(
                                                    Modifier.fillMaxWidth(0.92f).align(
                                                        if (message.type == Telephony.Sms.MESSAGE_TYPE_INBOX) Alignment.Start else Alignment.End
                                                    ),
                                                    shape = RoundedCornerShape(22.dp),
                                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                                        containerColor = if (message.type == Telephony.Sms.MESSAGE_TYPE_INBOX) MaterialTheme.colorScheme.surfaceContainer
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                    )
                                                ) {
                                                    Column(
                                                        Modifier.padding(14.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            when (SmsProviderMessageState.classify(message.type, message.status)) {
                                                                SmsProviderMessageState.State.RECEIVED -> "Reçu"
                                                                SmsProviderMessageState.State.SENT -> "Envoyé"
                                                                SmsProviderMessageState.State.DELIVERED -> "Envoyé · livré"
                                                                SmsProviderMessageState.State.DELIVERY_PENDING -> "Envoyé · livraison en attente"
                                                                SmsProviderMessageState.State.DELIVERY_FAILED -> "Envoyé · échec de livraison"
                                                                SmsProviderMessageState.State.SENDING -> "Envoi en cours"
                                                                SmsProviderMessageState.State.SEND_FAILED -> "Échec d’envoi"
                                                                SmsProviderMessageState.State.DRAFT -> "Brouillon"
                                                                SmsProviderMessageState.State.OTHER -> "Message"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            (if (SmsTimestampOrder.isAnomalous(message.timestampMs, System.currentTimeMillis())) "Date anormale (Android) : " else "") +
                                                                DateFormat.getDateTimeInstance().format(Date(message.timestampMs)),
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                        Text(message.body.take(1000))
                                                        val messageRisk = remember(message.id, message.body) {
                                                            smsAnalyzer.analyze(message.body)
                                                        }
                                                        val otpPrivacy = remember(message.id, message.body) {
                                                            SmsOtpPrivacy.inspect(message.body)
                                                        }
                                                        if (otpPrivacy.containsOtp) {
                                                            Text(
                                                                "Code à usage unique détecté localement · " +
                                                                    (otpPrivacy.codeLength ?: 0) +
                                                                    " chiffres · contenu non destiné à l’enrichissement distant",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        if (
                                                            messageRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.LOW &&
                                                            messageRisk.riskLevel != SmsLinkAnalyzer.RiskLevel.UNKNOWN
                                                        ) {
                                                            Text(
                                                                "Risque local ${PhoneCoreFrenchLabels.riskLevel(messageRisk.riskLevel.name)} · " +
                                                                    "score ${messageRisk.score}/100 · " +
                                                                    "${messageRisk.findings.joinToString { PhoneCoreFrenchLabels.smsFinding(it.code) }}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                        TextButton(
                                                            onClick = {
                                                                pendingDeleteMessage = message
                                                                status = "Suppression du message préparée · confirmez ou annulez"
                                                            },
                                                            modifier = Modifier.align(Alignment.End)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = null
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Supprimer")
                                                        }
                                                    }
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
                                                    pendingDeleteMessage = null
                                                    ioScope.launch {
                                                        val threadId = selectedThreadId
                                                        val result = withContext(Dispatchers.IO) {
                                                            val deleted = conversations.deleteMessage(pending.id)
                                                            val refreshedMessages = if (deleted && threadId != null) {
                                                                conversations.messagesForThread(threadId, 100)
                                                            } else emptyList()
                                                            val refreshedThreads = if (deleted) {
                                                                conversations.recentThreads(50)
                                                            } else emptyList()
                                                            Triple(deleted, refreshedMessages, refreshedThreads)
                                                        }
                                                        val (deleted, refreshedMessages, refreshedThreads) = result
                                                        if (deleted) {
                                                            if (threadId != null && selectedThreadId == threadId) {
                                                                threadMessages = refreshedMessages
                                                            }
                                                            threads = refreshedThreads
                                                            status = "Message supprimé"
                                                        } else {
                                                            status = "Suppression refusée ou impossible"
                                                        }
                                                    }
                                                }, modifier = Modifier.weight(1f)) { Text("Supprimer") }
                                            }
                                        }
                                    }
                                }
                            }
    
    
                        }




                    }
                }
            }
        }
    }
    companion object {
        const val EXTRA_OPEN_CONVERSATIONS = "com.sentinel.quantum.extra.OPEN_SMS_CONVERSATIONS"
    }

}

