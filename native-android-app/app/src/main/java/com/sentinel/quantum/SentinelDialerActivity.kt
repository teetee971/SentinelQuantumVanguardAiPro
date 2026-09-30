package com.sentinel.quantum

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.provider.CallLog
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CallLineSelectionPolicy
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.PhoneCoreCertificationScopeProvider
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.ui.design.PhoneCoreUiState
import com.sentinel.quantum.ui.design.SentinelStateChip
import com.sentinel.quantum.ui.design.SentinelState
import com.sentinel.quantum.ui.design.SentinelEvidenceProgress
import com.sentinel.quantum.security.EmergencyCallGuard
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.security.SystemCallLogReader
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sentinel-owned dial-pad surface. Direct PSTN placement is fail-closed behind explicit
 * ROLE_DIALER ownership and CALL_PHONE permission; otherwise Sentinel does not place the call.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SentinelDialerActivity : ComponentActivity() {
    private var pendingNumber: String? = null
    private var callActionStatus by mutableStateOf<String?>(null)
    private var contactsPermissionGranted by mutableStateOf(false)
    private var openContactsAfterPermissionGrant by mutableStateOf(false)
    private var phoneStatePermissionGranted by mutableStateOf(false)
    private var callLineRefreshEpoch by mutableStateOf(0)
    private var selectedCallAccount by mutableStateOf<PhoneAccountHandle?>(null)

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
        openContactsAfterPermissionGrant = granted
    }

    private var callLogPermissionGranted by mutableStateOf(false)

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        callLogPermissionGranted = granted
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingNumber
        pendingNumber = null
        if (granted) pending?.let(::placeCallIfReady)
        else callActionStatus = "Autorisation d’appel refusée. Aucun appel n’a été lancé."
    }

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        phoneStatePermissionGranted = granted
        callLineRefreshEpoch++
        val pending = pendingNumber
        pendingNumber = null
        if (granted) pending?.let(::placeCallIfReady)
        else callActionStatus = "Accès à l’état téléphonique refusé : Sentinel ne choisira pas une SIM à votre place."
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingNumber
        pendingNumber = null
        if (holdsDialerRole()) pending?.let(::placeCallIfReady)
        else callActionStatus = "Sentinel n’est pas l’application Téléphone par défaut. Aucun appel n’a été lancé."
    }

    private fun holdsDialerRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            roles.isRoleAvailable(RoleManager.ROLE_DIALER) && roles.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
        }
    }

    private fun requestDialerRole(number: String) {
        pendingNumber = number
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                callActionStatus = "Sélectionnez Sentinel comme application Téléphone pour continuer."
                dialerRoleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } else {
                pendingNumber = null
                callActionStatus = "Le rôle Téléphone n’est pas disponible sur cet appareil."
            }
        } else {
            dialerRoleLauncher.launch(
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName
                )
            )
        }
    }

    private data class CallLineOption(
        val key: String,
        val handle: PhoneAccountHandle,
        val label: String
    )

    private fun callAccountKey(handle: PhoneAccountHandle): String =
        handle.componentName.flattenToShortString() + "#" + handle.id

    private sealed interface CallLineLoadResult {
        data class Available(val lines: List<CallLineOption>) : CallLineLoadResult
        data object PermissionRequired : CallLineLoadResult
        data object LookupFailed : CallLineLoadResult
    }

    private fun loadCallLines(): CallLineLoadResult {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return CallLineLoadResult.PermissionRequired

        val telecom = getSystemService(TelecomManager::class.java)
        val handles = try {
            telecom.callCapablePhoneAccounts.orEmpty()
        } catch (_: SecurityException) {
            return CallLineLoadResult.LookupFailed
        } catch (_: RuntimeException) {
            return CallLineLoadResult.LookupFailed
        }
        if (handles.isEmpty()) return CallLineLoadResult.Available(emptyList())

        val subscriptionLabels: Map<Int, String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val telephony = getSystemService(TelephonyManager::class.java)
            // Telecom owns the call-capable account truth. SubscriptionManager is used only
            // to enrich labels, so an OEM/telephony metadata failure must not discard otherwise
            // valid PhoneAccountHandles. Generic labels remain deterministic and safe.
            val subscriptions = try {
                getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                    .associateBy { it.subscriptionId }
            } catch (_: SecurityException) {
                emptyMap()
            } catch (_: RuntimeException) {
                emptyMap()
            }
            handles.mapNotNull { handle ->
                val subId = runCatching { telephony.getSubscriptionId(handle) }
                    .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                val info = subscriptions[subId] ?: return@mapNotNull null
                val display = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "Ligne"
                val slot = info.simSlotIndex
                subId to if (slot >= 0) "SIM " + (slot + 1) + " · " + display else display
            }.toMap()
        } else emptyMap()

        val telephony = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(TelephonyManager::class.java)
        } else null

        val lines = handles.mapIndexed { index, handle ->
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { telephony?.getSubscriptionId(handle) }
                    .getOrNull()
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            } else SubscriptionManager.INVALID_SUBSCRIPTION_ID
            CallLineOption(
                key = callAccountKey(handle),
                handle = handle,
                label = subscriptionLabels[subId] ?: "Ligne " + (index + 1)
            )
        }
        return CallLineLoadResult.Available(lines)
    }

    private fun placeCallIfReady(number: String) {
        val safeNumber = sanitizeDialNumber(number)
        if (safeNumber == null) {
            callActionStatus = "Numéro invalide. Aucun appel n’a été lancé."
            return
        }
        if (!holdsDialerRole()) {
            callActionStatus = "Rôle Téléphone requis. Aucun appel n’a été lancé."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = safeNumber
            callActionStatus = "Autorisation Android d’appel requise."
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        val platformConfirmsEmergency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                getSystemService(TelephonyManager::class.java).isEmergencyNumber(safeNumber)
            }.getOrDefault(false)
        } else false
        if (!EmergencyCallGuard.requiresExplicitPhoneAccountSelection(platformConfirmsEmergency)) {
            val failure = runCatching {
                telecom.placeCall(Uri.parse("tel:" + Uri.encode(safeNumber)), Bundle())
            }.exceptionOrNull()
            callActionStatus = if (failure == null) {
                "Appel d’urgence transmis directement à Android pour routage système."
            } else {
                "Android n’a pas pu transmettre l’appel d’urgence."
            }
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = safeNumber
            callActionStatus = "Autorisez la détection des lignes afin que Sentinel ne choisisse jamais une SIM arbitrairement."
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            return
        }

        val lineResult = loadCallLines()
        val lines = when (lineResult) {
            CallLineLoadResult.PermissionRequired -> {
                selectedCallAccount = null
                callActionStatus = "Autorisation d’état téléphonique requise. Aucun appel n’a été lancé."
                return
            }
            CallLineLoadResult.LookupFailed -> {
                selectedCallAccount = null
                callActionStatus = "Android n’a pas pu vérifier les lignes d’appel actives. Aucun appel n’a été lancé."
                return
            }
            is CallLineLoadResult.Available -> lineResult.lines
        }
        val selection = CallLineSelectionPolicy.reconcile(
            activeIds = lines.map { it.key },
            selectedId = selectedCallAccount?.let(::callAccountKey)
        )
        if (!selection.hasUsableLine) {
            selectedCallAccount = null
            callActionStatus = "Aucune ligne d’appel active détectée. Aucun appel n’a été lancé."
            return
        }
        if (selection.explicitChoiceRequired) {
            selectedCallAccount = null
            callLineRefreshEpoch++
            callActionStatus = "Plusieurs lignes sont actives : choisissez explicitement la SIM à utiliser."
            return
        }
        val selectedLine = lines.firstOrNull { it.key == selection.selectedId }
        if (selectedLine == null) {
            selectedCallAccount = null
            callActionStatus = "La ligne sélectionnée n’est plus disponible. Choisissez une ligne active."
            return
        }
        selectedCallAccount = selectedLine.handle

        // isOutgoingCallPermitted() is only advisory here. Some OEM Telecom
        // implementations can report false for a SIM account even though the app
        // currently holds ROLE_DIALER and TelecomManager.placeCall() is allowed.
        // The actual placeCall() result is therefore the source of truth.
        val outgoingPermissionHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { telecom.isOutgoingCallPermitted(selectedLine.handle) }.getOrNull()
        } else null
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selectedLine.handle)
        }
        val failure = runCatching {
            telecom.placeCall(Uri.parse("tel:" + Uri.encode(safeNumber)), extras)
        }.exceptionOrNull()
        callActionStatus = if (failure == null) {
            "Demande d’appel transmise à Android via " + selectedLine.label + "."
        } else {
            val hint = if (outgoingPermissionHint == false) " La ligne était signalée indisponible par Android." else ""
            "Android n’a pas pu démarrer l’appel sur " + selectedLine.label + "." + hint
        }
    }

    private fun sanitizeDialNumber(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 32) return null
        if (value.count { it == '+' } > 1 || ('+' in value && !value.startsWith("+"))) return null
        if (!value.all { it.isDigit() || it in "+*#" }) return null
        return value
    }

    private fun initialDialNumber(): String {
        if (intent?.action != Intent.ACTION_DIAL) return ""
        val uri = intent?.data ?: return ""
        if (!uri.scheme.equals("tel", ignoreCase = true)) return ""
        return sanitizeDialNumber(uri.schemeSpecificPart.orEmpty()) ?: ""
    }

    private fun currentInstallTimestamp(): Long = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }
    }.getOrDefault(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactsPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        callLogPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        phoneStatePermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        setContent {
            SentinelQuantumTheme {
                var number by remember { mutableStateOf(initialDialNumber()) }
                var directoryStatus by remember { mutableStateOf("Saisissez un numéro pour l’identifier.") }
                var lookupRunning by remember { mutableStateOf(false) }
                var contactStatus by remember { mutableStateOf<String?>(null) }
                var reputationStatus by remember { mutableStateOf<String?>(null) }
                var showContacts by remember { mutableStateOf(false) }
                var showRecents by remember { mutableStateOf(false) }
                var recentItems by remember { mutableStateOf(emptyList<SystemCallLogReader.Entry>()) }
                var contactQuery by remember { mutableStateOf("") }
                var contactItems by remember { mutableStateOf(emptyList<LocalContactLookup.Contact>()) }
                var pendingBlockNumber by remember { mutableStateOf<String?>(null) }
                val context = this@SentinelDialerActivity
                val blocklist = remember { CallBlocklistStore(context) }
                val installTimestampMs = remember { currentInstallTimestamp() }
                var resumeEpoch by remember { mutableStateOf(0) }
                DisposableEffect(context) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            contactsPermissionGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                            callLogPermissionGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.READ_CALL_LOG
                            ) == PackageManager.PERMISSION_GRANTED
                            phoneStatePermissionGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.READ_PHONE_STATE
                            ) == PackageManager.PERMISSION_GRANTED
                            callLineRefreshEpoch++
                            resumeEpoch++
                        }
                    }
                    context.lifecycle.addObserver(observer)
                    onDispose { context.lifecycle.removeObserver(observer) }
                }
                val arcep = remember { ArcepDirectoryClient() }
                val rtr = remember { RtrDirectoryClient() }
                val contacts = remember { LocalContactLookup(context) }
                val settings = remember { SettingsStore(context) }
                val reputation = remember {
                    CallerReputationClient(egressGate = {
                        settings.callerReputationEnrichmentEnabled &&
                            ProtectionModePolicy.permitsCallerNumberEnrichment(settings.protectionMode)
                    })
                }
                val callLog = remember { SystemCallLogReader(context) }
                val scope = rememberCoroutineScope()
                val callLineResult = remember(callLineRefreshEpoch, phoneStatePermissionGranted) {
                    if (phoneStatePermissionGranted) loadCallLines()
                    else CallLineLoadResult.PermissionRequired
                }
                val callLines = (callLineResult as? CallLineLoadResult.Available)?.lines.orEmpty()
                LaunchedEffect(callLineResult) {
                    val selection = CallLineSelectionPolicy.reconcile(
                        activeIds = callLines.map { it.key },
                        selectedId = selectedCallAccount?.let(::callAccountKey)
                    )
                    selectedCallAccount = selection.selectedId
                        ?.let { selected -> callLines.firstOrNull { it.key == selected }?.handle }
                }

                fun lookup() {
                    if (number.isBlank() || lookupRunning) return
                    lookupRunning = true
                    directoryStatus = "Recherche officielle…"
                    contactStatus = contacts.find(number)?.let { identity ->
                        "Contact : " + identity.displayName + (identity.organisation?.let { " · $it" } ?: "")
                    }
                    val remoteReputationAllowed = settings.callerReputationEnrichmentEnabled &&
                        ProtectionModePolicy.permitsCallerNumberEnrichment(settings.protectionMode)
                    reputationStatus = if (remoteReputationAllowed) "Réputation Sentinel : analyse…" else null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val at = RtrDirectoryClient.normalize(number)
                                if (at != null) {
                                    val r = rtr.lookup(number)
                                    when {
                                        r == null -> "Format autrichien non reconnu"
                                        r.status == "ambiguous" -> "RTR : attribution ambiguë — aucune identité déduite"
                                        r.matches.isNotEmpty() -> {
                                            val m = r.matches.first()
                                            "RTR : " + (m.allocationHolder ?: m.status) + (m.area?.let { " · $it" } ?: "")
                                        }
                                        else -> "RTR : " + r.status
                                    }
                                } else {
                                    val a = arcep.lookup(number)
                                    if (a == null) "ARCEP : aucune attribution correspondante"
                                    else "ARCEP : " + (a.attributedOperator ?: a.operatorCode) + (a.territory?.let { " · $it" } ?: "")
                                }
                            }.getOrElse { "Répertoire officiel temporairement indisponible" }
                        }
                        directoryStatus = result
                        if (remoteReputationAllowed) {
                            reputationStatus = withContext(Dispatchers.IO) {
                                runCatching {
                                    val r = reputation.evaluate(
                                        callerNumber = number,
                                        recipientCountry = "FR",
                                        verificationStatus = "outgoing_user_lookup",
                                        privacyMode = PhonePrivacyFirewall.Mode.ENHANCED,
                                        explicitConsent = settings.callerReputationEnrichmentEnabled
                                    )
                                    "Réputation Sentinel : risque ${r.riskScore}/100 · ${r.action}" +
                                        if (r.flags.isNotEmpty()) " · " + r.flags.take(3).joinToString(", ") else ""
                                }.getOrElse { "Réputation Sentinel temporairement indisponible" }
                            }
                        }
                        lookupRunning = false
                    }
                }

                val clipboard = LocalClipboardManager.current
                @Suppress("UNUSED_VARIABLE") val roleRefresh = resumeEpoch
                val runtimeSetupFacts = remember(resumeEpoch) {
                    PhoneCoreRuntimeFacts.read(applicationContext)
                }
                val nextSetupStep = PhoneCoreSetupWizardStore.nextStep(runtimeSetupFacts)
                val protectionReady = PhoneCoreSetupWizardStore.softwarePrerequisitesReady(runtimeSetupFacts)
                val nextSetupLabel = PhoneCoreSetupWizardStore.stepLabel(nextSetupStep)
                val physicalEvidence = remember(resumeEpoch) {
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
                val protectionState = PhoneCoreUiState.derive(
                    softwarePrerequisitesReady = protectionReady,
                    physicalCompleted = physicalEvidence.completedCount,
                    physicalRequired = physicalEvidence.requiredCount
                )

                pendingBlockNumber?.let { candidate ->
                    AlertDialog(
                        onDismissRequest = { pendingBlockNumber = null },
                        icon = { Icon(Icons.Default.Block, contentDescription = null) },
                        title = { Text("Bloquer ce numéro ?") },
                        text = {
                            Text(
                                "Le numéro sera ajouté aux règles locales de filtrage Sentinel. " +
                                    "L’action est explicite et réversible depuis la gestion du blocage."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val blocked = blocklist.addBlockedNumber(candidate)
                                    callActionStatus = if (blocked) {
                                        "Numéro ajouté à la liste de blocage locale."
                                    } else {
                                        "Le numéro n’a pas pu être ajouté à la liste de blocage."
                                    }
                                    pendingBlockNumber = null
                                }
                            ) { Text("Confirmer le blocage") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingBlockNumber = null }) { Text("Annuler") }
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        SentinelTopBar(
                            title = "Protection mobile",
                            subtitle = if (protectionReady) {
                                "État local · prérequis logiciels prêts"
                            } else {
                                "État local · finalisez les prérequis Android"
                            },
                            onBack = { finish() },
                            actions = {
                                SentinelStateChip(state = protectionState)
                                Spacer(Modifier.width(8.dp))
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhoneCoreBrand(
                            context = "Téléphone",
                            status = if (protectionReady) "Prérequis logiciels prêts" else "Configuration Android requise",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(modifier = Modifier.size(76.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Shield, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        PhoneCoreUiState.phoneCoreHeadline(protectionState),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        if (physicalEvidence.fullyValidated && protectionReady)
                                            "Validation Phone Core : ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount} preuves observées sur cette installation."
                                        else if (protectionReady && physicalEvidence.completedCount == 0)
                                            "Prérequis téléphoniques visibles prêts · validation Phone Core 0/${physicalEvidence.requiredCount}. Aucun critère de validation n’est encore confirmé sur cette installation."
                                        else if (protectionReady)
                                            "Validation Phone Core en cours : ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount} preuves observées. Le statut reste « À tester » jusqu’à ${physicalEvidence.requiredCount}/${physicalEvidence.requiredCount}."
                                        else
                                            "Sentinel n’affiche jamais « protégé » tant que les rôles et autorisations nécessaires ne sont pas réellement accordés. Validation Phone Core ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SentinelEvidenceProgress(
                                        label = "Validation Phone Core",
                                        completed = physicalEvidence.completedCount,
                                        required = physicalEvidence.requiredCount
                                    )
                                    if (protectionReady && !physicalEvidence.fullyValidated) {
                                        Spacer(Modifier.height(8.dp))
                                        val automaticMissing = physicalEvidence.missingCriteria.filter {
                                            PhoneCorePhysicalValidation.criterionKind(it) ==
                                                PhoneCorePhysicalValidation.CriterionKind.AUTOMATIC_CHECK
                                        }
                                        val operationalMissing = physicalEvidence.missingCriteria.filter {
                                            PhoneCorePhysicalValidation.criterionKind(it) ==
                                                PhoneCorePhysicalValidation.CriterionKind.OPERATIONAL_TEST
                                        }
                                        val unknownMissing = physicalEvidence.missingCriteria.filter {
                                            PhoneCorePhysicalValidation.criterionKind(it) ==
                                                PhoneCorePhysicalValidation.CriterionKind.UNKNOWN
                                        }
                                        Text(
                                            buildString {
                                                append(operationalMissing.size)
                                                append(
                                                    if (operationalMissing.size == 1)
                                                        " test opérationnel restant"
                                                    else
                                                        " tests opérationnels restants"
                                                )
                                                if (automaticMissing.isNotEmpty()) {
                                                    append(" · ")
                                                    append(automaticMissing.size)
                                                    append(
                                                        if (automaticMissing.size == 1)
                                                            " vérification automatique en attente"
                                                        else
                                                            " vérifications automatiques en attente"
                                                    )
                                                }
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (automaticMissing.isNotEmpty()) {
                                            Text(
                                                "Vérification automatique en attente : " +
                                                    automaticMissing.joinToString(" · ") {
                                                        PhoneCorePhysicalValidation.criterionLabel(it)
                                                    },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (unknownMissing.isNotEmpty()) {
                                            Text(
                                                "Schéma de validation incohérent : ${unknownMissing.size} critère non classé. Aucune interprétation automatique n’est appliquée.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        operationalMissing.firstOrNull()?.let { criterion ->
                                            Text(
                                                "Prochain test : ${PhoneCorePhysicalValidation.criterionLabel(criterion)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (!protectionReady) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Prochaine étape : $nextSetupLabel",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                startActivity(
                                                    Intent(
                                                        this@SentinelDialerActivity,
                                                        PhoneCoreActivationActivity::class.java
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Configurer Phone Core")
                                        }
                                    }
                                }
                            }
                        }

                        Text("Vérification d’un numéro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = number,
                                onValueChange = {
                                    number = it.take(32)
                                    directoryStatus = "Saisissez un numéro puis lancez la vérification."
                                    contactStatus = null
                                    reputationStatus = null
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Numéro") },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            Button(onClick = { lookup() }, enabled = number.isNotBlank() && !lookupRunning, modifier = Modifier.height(56.dp)) {
                                Text(if (lookupRunning) "…" else "Vérifier")
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Résultat Sentinel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                contactStatus?.let { Text(it, fontWeight = FontWeight.Bold) }
                                Text(directoryStatus, style = MaterialTheme.typography.bodySmall)
                                reputationStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (!settings.callerReputationEnrichmentEnabled ||
                                    !ProtectionModePolicy.permitsCallerNumberEnrichment(settings.protectionMode)) {
                                    Text("Réputation distante désactivée · analyse locale et attribution officielle uniquement.",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Un résultat heuristique ou une absence de signalement ne prouve jamais qu’un numéro est sûr.",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (holdsDialerRole()) placeCallIfReady(number) else requestDialerRole(number) },
                                enabled = sanitizeDialNumber(number) != null, modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(4.dp)); Text("Appeler") }
                            Button(
                                onClick = {
                                    val safe = sanitizeDialNumber(number) ?: return@Button
                                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(safe))).setClass(context, SmsComposeActivity::class.java))
                                },
                                enabled = sanitizeDialNumber(number) != null, modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.Message, null); Spacer(Modifier.width(4.dp)); Text("SMS") }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(number)) }, enabled = number.isNotBlank(), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copier")
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Blocage rapide", fontWeight = FontWeight.Bold)
                                    Text("Accès au module de blocage local Sentinel.", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        pendingBlockNumber = sanitizeDialNumber(number)
                                        if (pendingBlockNumber == null) {
                                            callActionStatus = "Saisissez un numéro valide avant de demander son blocage."
                                        }
                                    }
                                ) { Text("Bloquer") }
                            }
                        }

                        Text("Fonctionnalités de protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        data class ProtectionItem(val title: String, val detail: String, val state: SentinelState)
                        val protectionItems = listOf(
                            ProtectionItem(
                                "Filtrage d’appels",
                                "Rôle Filtrage d’appels observé sur cet appareil",
                                if (runtimeSetupFacts.callScreeningRoleHeld) SentinelState.READY else SentinelState.TO_CONFIGURE
                            ),
                            ProtectionItem(
                                "Identification d’appel",
                                "Accès Contacts observé sur cet appareil",
                                if (contactsPermissionGranted) SentinelState.READY else SentinelState.TO_CONFIGURE
                            ),
                            ProtectionItem(
                                "Protection SMS/MMS",
                                "État complet disponible dans le centre Phone Core",
                                SentinelState.TO_CONFIGURE
                            ),
                            ProtectionItem(
                                "Enrichissement distant",
                                "Préférence utilisateur ; disponibilité réseau non déduite",
                                if (settings.callerReputationEnrichmentEnabled) SentinelState.READY else SentinelState.TO_CONFIGURE
                            ),
                            ProtectionItem("Scanner réseau local", "État non mesuré depuis cet écran", SentinelState.UNAVAILABLE),
                            ProtectionItem("Analyse des applications", "État non mesuré depuis cet écran", SentinelState.UNAVAILABLE),
                            ProtectionItem("Analyse de liens/URLs", "État non mesuré depuis cet écran", SentinelState.UNAVAILABLE),
                            ProtectionItem("Exposition numérique", "Non certifiée dans Phone Core", SentinelState.UNAVAILABLE),
                            ProtectionItem("Veille OSINT", "Flux séparé ; état non mesuré ici", SentinelState.UNAVAILABLE)
                        )
                        protectionItems.chunked(2).forEach { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { item ->
                                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(item.detail, style = MaterialTheme.typography.labelSmall)
                                            SentinelStateChip(state = item.state)
                                        }
                                    }
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!holdsDialerRole()) requestDialerRole(number)
                                    else if (!callLogPermissionGranted) callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                                    else { recentItems = callLog.recent(100); showRecents = true; showContacts = false }
                                }, modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.History, null); Spacer(Modifier.width(4.dp)); Text("Récents") }
                            OutlinedButton(
                                onClick = {
                                    if (contactsPermissionGranted) {
                                        val result = contacts.listWithState(500)
                                        contactItems = result.contacts
                                        showContacts = result.state == LocalContactLookup.ContactAccessState.READY
                                        showRecents = false
                                    } else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }, modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.Contacts, null); Spacer(Modifier.width(4.dp)); Text("Contacts") }
                        }

                        if (showRecents && callLogPermissionGranted) {
                            recentItems.take(25).forEach { entry ->
                                OutlinedButton(
                                    onClick = { entry.number?.let(::sanitizeDialNumber)?.let { number = it; showRecents = false } },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(entry.number ?: "Numéro masqué", fontWeight = FontWeight.Bold)
                                        Text("Durée : ${entry.durationSeconds} s", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        LaunchedEffect(openContactsAfterPermissionGrant) {
                            if (openContactsAfterPermissionGrant && contactsPermissionGranted) {
                                openContactsAfterPermissionGrant = false
                                val result = contacts.listWithState(500)
                                contactItems = result.contacts
                                showContacts = result.state == LocalContactLookup.ContactAccessState.READY
                            }
                        }

                        if (showContacts && contactsPermissionGranted) {
                            OutlinedTextField(value = contactQuery, onValueChange = { contactQuery = it.take(80) },
                                modifier = Modifier.fillMaxWidth(), label = { Text("Rechercher un contact") }, singleLine = true)
                            val q = contactQuery.trim()
                            contactItems.asSequence().filter {
                                q.isBlank() || it.displayName.contains(q, true) || it.phoneNumber.contains(q)
                            }.take(30).forEach { contact ->
                                OutlinedButton(
                                    onClick = {
                                        sanitizeDialNumber(contact.phoneNumber)?.let {
                                            number = it; contactStatus = "Contact : " + contact.displayName; showContacts = false
                                        }
                                    }, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(contact.displayName, fontWeight = FontWeight.Bold)
                                        Text(contact.phoneNumber.take(64), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Ligne d’appel", fontWeight = FontWeight.Bold)
                                when {
                                    !phoneStatePermissionGranted -> {
                                        Text("Autorisez la détection des lignes pour éviter tout choix arbitraire de SIM.", style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("Autoriser") }
                                    }
                                    callLines.isEmpty() -> Text("Aucune ligne active détectée.", color = MaterialTheme.colorScheme.error)
                                    callLines.size == 1 -> Text(callLines.first().label + " · ligne unique active", style = MaterialTheme.typography.bodySmall)
                                    else -> callLines.forEach { line ->
                                        TextButton(onClick = { selectedCallAccount = line.handle }) {
                                            Text(if (selectedCallAccount?.let(::callAccountKey) == line.key) "✓ " + line.label else line.label)
                                        }
                                    }
                                }
                            }
                        }

                        callActionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("État affiché à partir des capacités réellement observables. La certification locale exige ${physicalEvidence.requiredCount} critères Phone Core sur cet APK.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
