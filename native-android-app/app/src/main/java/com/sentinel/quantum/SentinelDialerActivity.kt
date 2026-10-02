package com.sentinel.quantum

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CallLineSelectionPolicy
import com.sentinel.quantum.security.CallRuleEngine
import com.sentinel.quantum.security.CallHistoryInsights
import com.sentinel.quantum.security.CallHistoryPresentationPolicy
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.PhoneCoreCertificationScopeProvider
import com.sentinel.quantum.security.PhoneFavoriteStore
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.ui.design.PhoneCoreUiState
import com.sentinel.quantum.ui.design.SentinelStateChip
import com.sentinel.quantum.ui.design.SentinelState
import com.sentinel.quantum.ui.design.SentinelEvidenceProgress
import com.sentinel.quantum.security.EmergencyCallGuard
import com.sentinel.quantum.security.FamilySafetyPolicy
import com.sentinel.quantum.security.PhoneNumberRiskRules
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.ContactDialNumberPolicy
import com.sentinel.quantum.security.ContactSearchPolicy
import com.sentinel.quantum.security.ContactPresentationPolicy
import com.sentinel.quantum.security.WhatsAppClickToChatPolicy
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.security.SystemCallLogReader
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.PhoneCoreNumberPad
import com.sentinel.quantum.ui.design.PhoneCoreDisclosure
import androidx.compose.runtime.saveable.rememberSaveable
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val ASSISTED_CONFIRMATION_TTL_MS = 2L * 60L * 1000L
private const val CONTACTS_PAGE_SIZE = 50
private const val CALL_HISTORY_PAGE_SIZE = 25
private const val CALL_HISTORY_LOAD_LIMIT = 500

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
    private var assistedConfirmationNumber by mutableStateOf<String?>(null)
    private var assistedConfirmationBypassNumber: String? = null
    private var assistedConfirmationBypassExpiresAtMs: Long = 0L

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
        openContactsAfterPermissionGrant = granted
    }

    private var callLogPermissionGranted by mutableStateOf(false)
    private var openRecentsAfterDialerRoleGrant by mutableStateOf(false)
    private var openRecentsAfterCallLogPermissionGrant by mutableStateOf(false)

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        callLogPermissionGranted = granted
        openRecentsAfterCallLogPermissionGrant = granted
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

    private val recentsDialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = holdsDialerRole()
        openRecentsAfterDialerRoleGrant = granted
        if (!granted) {
            callActionStatus = "Sentinel doit être l’application Téléphone par défaut pour lire l’historique Android. Aucun appel n’a été lancé."
        }
    }

    private fun holdsDialerRole(): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = getSystemService(RoleManager::class.java)
                roles.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                    roles.isRoleHeld(RoleManager.ROLE_DIALER)
            } else {
                getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
            }
        }

    private fun requestDialerRole(number: String) {
        pendingNumber = number
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val request = AndroidRoleReadPolicy.readOrNull {
                val roles = getSystemService(RoleManager::class.java)
                if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    null
                } else {
                    roles.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                }
            }
            if (request != null) {
                callActionStatus = "Sélectionnez Sentinel comme application Téléphone pour continuer."
                dialerRoleLauncher.launch(request)
            } else {
                pendingNumber = null
                callActionStatus = "Le rôle Téléphone n’est pas disponible sur cet appareil."
            }
        } else {
            val request = AndroidRoleReadPolicy.readOrNull {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName
                )
            }
            if (request != null) {
                dialerRoleLauncher.launch(request)
            } else {
                pendingNumber = null
                callActionStatus = "Android n’a pas pu ouvrir le sélecteur d’application Téléphone."
            }
        }
    }

    private fun requestDialerRoleForRecents() {
        openRecentsAfterDialerRoleGrant = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val request = AndroidRoleReadPolicy.readOrNull {
                val roles = getSystemService(RoleManager::class.java)
                if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    null
                } else {
                    roles.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                }
            }
            if (request != null) {
                callActionStatus = "Sélectionnez Sentinel comme application Téléphone pour afficher l’historique."
                recentsDialerRoleLauncher.launch(request)
            } else {
                callActionStatus = "Le rôle Téléphone n’est pas disponible sur cet appareil."
            }
        } else {
            val request = AndroidRoleReadPolicy.readOrNull {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName
                )
            }
            if (request != null) {
                recentsDialerRoleLauncher.launch(request)
            } else {
                callActionStatus = "Android n’a pas pu ouvrir le sélecteur d’application Téléphone."
            }
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
        if (assistedConfirmationNumber != null && assistedConfirmationNumber != safeNumber) {
            assistedConfirmationNumber = null
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
            assistedConfirmationNumber = null
            assistedConfirmationBypassNumber = null
            assistedConfirmationBypassExpiresAtMs = 0L
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

        if (assistedConfirmationBypassNumber != null && assistedConfirmationBypassNumber != safeNumber) {
            assistedConfirmationBypassNumber = null
            assistedConfirmationBypassExpiresAtMs = 0L
        }
        val assistedProfile = SettingsStore(applicationContext).familySafetyProfile
        val assistedRisk = PhoneNumberRiskRules.assistedRisk(safeNumber)
        val assistedAction = FamilySafetyPolicy.decide(
            FamilySafetyPolicy.Context(
                profile = assistedProfile,
                risk = assistedRisk,
                platformEmergency = false
            )
        )
        val assistedBypassValid =
            assistedConfirmationBypassNumber == safeNumber &&
                System.currentTimeMillis() <= assistedConfirmationBypassExpiresAtMs
        if (
            assistedAction == FamilySafetyPolicy.Action.REQUIRE_CONFIRMATION &&
            !assistedBypassValid
        ) {
            assistedConfirmationBypassNumber = null
            assistedConfirmationBypassExpiresAtMs = 0L
            assistedConfirmationNumber = safeNumber
            callActionStatus =
                "Protection assistée : ce numéro correspond à une plage locale à tarification potentiellement élevée. Confirmez explicitement avant l’appel."
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
        assistedConfirmationBypassNumber = null
        assistedConfirmationBypassExpiresAtMs = 0L
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
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                        .isAppearanceLightStatusBars = false
                }
                var number by rememberSaveable { mutableStateOf(initialDialNumber()) }
                var directoryStatus by remember { mutableStateOf("Saisissez un numéro pour l’identifier.") }
                var lookupRunning by remember { mutableStateOf(false) }
                var contactStatus by remember { mutableStateOf<String?>(null) }
                var reputationStatus by remember { mutableStateOf<String?>(null) }
                var phoneTab by rememberSaveable { mutableStateOf(
                    if (intent?.getBooleanExtra(EXTRA_OPEN_CONTACTS, false) == true) 2 else 0
                ) }
                var showContacts by remember { mutableStateOf(intent?.getBooleanExtra(EXTRA_OPEN_CONTACTS, false) == true) }
                var showRecents by remember { mutableStateOf(false) }
                var recentItems by remember { mutableStateOf(emptyList<SystemCallLogReader.Entry>()) }
                var recentVisibleLimit by remember { mutableStateOf(CALL_HISTORY_PAGE_SIZE) }
                var recentLoading by remember { mutableStateOf(false) }
                val recentSummary = remember(recentItems) {
                    CallHistoryInsights.summarize(recentItems)
                }
                var contactQuery by remember { mutableStateOf("") }
                var contactFilter by rememberSaveable { mutableStateOf(0) }
                var favoriteRefreshEpoch by remember { mutableStateOf(0) }
                var contactItems by remember { mutableStateOf(emptyList<LocalContactLookup.Contact>()) }
                var contactVisibleLimit by remember { mutableStateOf(CONTACTS_PAGE_SIZE) }
                var contactsLoading by remember { mutableStateOf(false) }
                var contactListStatus by remember { mutableStateOf<String?>(null) }
                var pendingBlockNumber by remember { mutableStateOf<String?>(null) }
                val context = this@SentinelDialerActivity
                val blocklist = remember { CallBlocklistStore(context) }
                val favorites = remember { PhoneFavoriteStore(context) }
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
                LaunchedEffect(lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        PhoneCoreLiveRefresh.snapshots(applicationContext).collect { snapshot ->
                            contactsPermissionGranted = snapshot.diagnostics.contactsPermission
                            callLogPermissionGranted = snapshot.diagnostics.callLogPermission
                            phoneStatePermissionGranted = snapshot.diagnostics.phoneStatePermission
                            callLineRefreshEpoch++
                            resumeEpoch++
                        }
                    }
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

                fun refreshRecents() {
                    if (recentLoading || !holdsDialerRole() || !callLogPermissionGranted) return
                    recentLoading = true
                    scope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            callLog.recent(CALL_HISTORY_LOAD_LIMIT)
                        }
                        recentItems = loaded
                        recentVisibleLimit = CALL_HISTORY_PAGE_SIZE
                        showRecents = true
                        showContacts = false
                        recentLoading = false
                    }
                }

                fun refreshContacts() {
                    if (contactsLoading || !contactsPermissionGranted) return
                    contactsLoading = true
                    contactListStatus = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            contacts.listWithState()
                        }
                        contactsLoading = false
                        when (result.state) {
                            LocalContactLookup.ContactAccessState.READY -> {
                                contactItems = result.contacts
                                contactVisibleLimit = CONTACTS_PAGE_SIZE
                                showContacts = true
                                showRecents = false
                                contactFilter = 0
                                contactListStatus = when {
                                    result.contacts.isEmpty() ->
                                        "Aucun contact accessible dans le profil Android courant."
                                    result.providerPhoneMismatchCount > 0 ->
                                        "${result.providerPhoneMismatchCount} contact(s) sont signalé(s) avec un numéro par Android sans valeur lisible."
                                    else -> null
                                }
                            }
                            LocalContactLookup.ContactAccessState.PERMISSION_REQUIRED -> {
                                showContacts = false
                                contactListStatus = "Autorisation Contacts requise."
                            }
                            LocalContactLookup.ContactAccessState.PROVIDER_UNAVAILABLE -> {
                                showContacts = false
                                contactListStatus = "Répertoire Android temporairement indisponible."
                            }
                        }
                    }
                }

                LaunchedEffect(openRecentsAfterDialerRoleGrant) {
                    if (openRecentsAfterDialerRoleGrant && holdsDialerRole()) {
                        openRecentsAfterDialerRoleGrant = false
                        if (callLogPermissionGranted) {
                            refreshRecents()
                        } else {
                            openRecentsAfterCallLogPermissionGrant = false
                            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    }
                }

                LaunchedEffect(openRecentsAfterCallLogPermissionGrant) {
                    if (
                        openRecentsAfterCallLogPermissionGrant &&
                        callLogPermissionGranted &&
                        holdsDialerRole()
                    ) {
                        openRecentsAfterCallLogPermissionGrant = false
                        refreshRecents()
                    }
                }

                LaunchedEffect(Unit) {
                    if (intent?.getBooleanExtra(EXTRA_OPEN_CONTACTS, false) == true) {
                        if (contactsPermissionGranted) {
                            refreshContacts()
                        } else {
                            // Keep the resume flag false until Android returns the permission result.
                            // The launcher callback flips it to true only after a real grant, which
                            // guarantees the follow-up effect runs exactly once.
                            openContactsAfterPermissionGrant = false
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                }

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
                    val lookupNumber = number
                    lookupRunning = true
                    directoryStatus = "Recherche officielle…"
                    contactStatus = null
                    val remoteReputationAllowed = settings.callerReputationEnrichmentEnabled &&
                        ProtectionModePolicy.permitsCallerNumberEnrichment(settings.protectionMode)
                    reputationStatus = if (remoteReputationAllowed) "Réputation Sentinel : analyse…" else null
                    scope.launch {
                        val localIdentity = withContext(Dispatchers.IO) {
                            contacts.find(lookupNumber)
                        }
                        contactStatus = localIdentity?.let { identity ->
                            "Contact : " + identity.displayName + (identity.organisation?.let { " · $it" } ?: "")
                        }
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val at = RtrDirectoryClient.normalize(lookupNumber)
                                if (at != null) {
                                    val r = rtr.lookup(lookupNumber)
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
                                    val a = arcep.lookup(lookupNumber)
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
                                        callerNumber = lookupNumber,
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
                val smsMmsPrerequisitesReady =
                    runtimeSetupFacts.smsRoleHeld &&
                        runtimeSetupFacts.smsRuntimePermissionsReady &&
                        runtimeSetupFacts.mmsPermissionsReady
                val nextSetupLabel = PhoneCoreSetupWizardStore.stepLabel(nextSetupStep)
                val physicalEvidence by produceState(
                    initialValue = PhoneCorePhysicalValidation.evaluate(emptyList()),
                    key1 = resumeEpoch
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
                val protectionState = PhoneCoreUiState.derive(
                    softwarePrerequisitesReady = protectionReady,
                    physicalCompleted = physicalEvidence.completedCount,
                    physicalRequired = physicalEvidence.requiredCount,
                    operationalEnvironmentReady = remember(resumeEpoch) {
                        PhoneCoreRuntimeFacts.hasOperationalCarrierEnvironment(applicationContext)
                    }
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

                assistedConfirmationNumber?.let { candidate ->
                    AlertDialog(
                        onDismissRequest = {
                            assistedConfirmationNumber = null
                            assistedConfirmationBypassNumber = null
                            assistedConfirmationBypassExpiresAtMs = 0L
                            callActionStatus = "Appel annulé par l’utilisateur."
                        },
                        title = { Text("Confirmation renforcée") },
                        text = {
                            Text(
                                "Le numéro $candidate correspond à une plage locale à tarification " +
                                    "potentiellement élevée. Ce signal n’est pas une preuve de fraude. " +
                                    "Confirmez uniquement si vous souhaitez réellement lancer cet appel."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    assistedConfirmationBypassNumber = candidate
                                    assistedConfirmationBypassExpiresAtMs =
                                        System.currentTimeMillis() + ASSISTED_CONFIRMATION_TTL_MS
                                    assistedConfirmationNumber = null
                                    placeCallIfReady(candidate)
                                }
                            ) { Text("Appeler quand même") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    assistedConfirmationNumber = null
                                    assistedConfirmationBypassNumber = null
                                    assistedConfirmationBypassExpiresAtMs = 0L
                                    callActionStatus = "Appel annulé par l’utilisateur."
                                }
                            ) { Text("Annuler") }
                        }
                    )
                }

                LaunchedEffect(phoneTab, contactsPermissionGranted, callLogPermissionGranted, resumeEpoch) {
                    if (phoneTab == 1 && holdsDialerRole() && callLogPermissionGranted) refreshRecents()
                    if (phoneTab == 2 && contactsPermissionGranted) refreshContacts()
                }
                Scaffold(
                    topBar = {
                        SentinelTopBar(
                            title = "Téléphone Sentinel",
                            subtitle = if (protectionReady) {
                                pluralStringResource(
                                    R.plurals.phone_core_ready_validation_count,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.requiredCount
                                )
                            } else {
                                pluralStringResource(
                                    R.plurals.phone_core_configuration_validation_count,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.requiredCount
                                )
                            },
                            onBack = { finish() },
                            actions = {
                                SentinelStateChip(
                                    state = protectionState,
                                    onClick = {
                                        context.startActivity(
                                            Intent(context, PhoneCoreActivationActivity::class.java)
                                        )
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        )
                    },
                    bottomBar = {
                        if (phoneTab == 0) {
                            Surface(tonalElevation = 4.dp) {
                                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { if (holdsDialerRole()) placeCallIfReady(number) else requestDialerRole(number) },
                                        enabled = sanitizeDialNumber(number) != null,
                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                    ) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text("Appeler") }
                                    FilledTonalIconButton(onClick = {
                                        val safe = sanitizeDialNumber(number) ?: return@FilledTonalIconButton
                                        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(safe))).setClass(context, SmsComposeActivity::class.java))
                                    }, enabled = sanitizeDialNumber(number) != null, modifier = Modifier.size(56.dp)) {
                                        Icon(Icons.Default.Message, "Écrire un SMS au numéro saisi")
                                    }
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(number)) },
                                        enabled = number.isNotBlank(), modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.ContentCopy, "Copier le numéro")
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (phoneTab == 3) PhoneCoreBrand(
                            context = "Téléphone",
                            status = if (protectionReady) {
                                pluralStringResource(
                                    R.plurals.phone_core_ready_validation_count,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.requiredCount
                                )
                            } else {
                                pluralStringResource(
                                    R.plurals.phone_core_configuration_validation_count,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.completedCount,
                                    physicalEvidence.requiredCount
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TabRow(selectedTabIndex = phoneTab) {
                            listOf("Clavier", "Récents", "Répertoire", "Réglages").forEachIndexed { index, label ->
                                Tab(selected = phoneTab == index, onClick = {
                                    phoneTab = index
                                    if (index == 1) {
                                        if (!holdsDialerRole()) requestDialerRoleForRecents()
                                        else if (!callLogPermissionGranted) callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                                        else refreshRecents()
                                    }
                                    if (index == 2) {
                                        if (contactsPermissionGranted) refreshContacts()
                                        else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }) { Text(label, modifier = Modifier.padding(vertical = 14.dp), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        if (phoneTab == 3) {
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

                        }
                        if (phoneTab == 0) {
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

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (callLines.size != 1) Text("Ligne d’appel", fontWeight = FontWeight.Bold)
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

                        PhoneCoreNumberPad(number = number, onChange = {
                            number = it
                            directoryStatus = "Saisissez un numéro puis lancez la vérification."
                            contactStatus = null
                            reputationStatus = null
                        })
                        if (contactStatus != null || reputationStatus != null || lookupRunning) {
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

                        }
                        if (phoneTab == 3) {
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
                                if (smsMmsPrerequisitesReady)
                                    "Rôle SMS et autorisations SMS/MMS observés"
                                else
                                    "Rôle ou autorisations SMS/MMS à finaliser",
                                if (smsMmsPrerequisitesReady) SentinelState.READY else SentinelState.TO_CONFIGURE
                            ),
                            ProtectionItem(
                                "Enrichissement distant",
                                if (settings.callerReputationEnrichmentEnabled)
                                    "Activé par l’utilisateur ; disponibilité réseau non mesurée ici"
                                else
                                    "Désactivé par l’utilisateur",
                                if (settings.callerReputationEnrichmentEnabled) SentinelState.UNKNOWN else SentinelState.TO_CONFIGURE
                            ),
                            ProtectionItem("Scanner réseau local", "État non mesuré depuis cet écran", SentinelState.UNKNOWN),
                            ProtectionItem("Analyse des applications", "État non mesuré depuis cet écran", SentinelState.UNKNOWN),
                            ProtectionItem("Analyse de liens/URLs", "État non mesuré depuis cet écran", SentinelState.UNKNOWN),
                            ProtectionItem("Exposition numérique", "Module séparé ; état non mesuré dans Phone Core", SentinelState.UNKNOWN),
                            ProtectionItem("Veille OSINT", "Flux séparé ; état non mesuré ici", SentinelState.UNKNOWN)
                        )
                        protectionItems.forEach { item ->
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            item.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            item.detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SentinelStateChip(state = item.state)
                                }
                            }
                        }

                        }
                        if (phoneTab == 1 && !callLogPermissionGranted) {
                            Text("Autorisez l’historique des appels pour afficher les récents.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (phoneTab == 2 && !contactsPermissionGranted) {
                            Text("Autorisez les contacts pour ouvrir votre carnet local.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (phoneTab == 1 && recentLoading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                "Lecture de l’historique Android…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (phoneTab == 1 && callLogPermissionGranted) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Résumé du journal Android", fontWeight = FontWeight.Bold)
                                    Text(
                                        "${recentSummary.total} appel(s) lu(s) · " +
                                            "${recentSummary.incoming} entrant(s) · " +
                                            "${recentSummary.outgoing} sortant(s) · " +
                                            "${recentSummary.missed} manqué(s)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "${recentSummary.rejected} rejeté(s) · " +
                                            "${recentSummary.blocked} bloqué(s) · " +
                                            "durée cumulée ${CallHistoryInsights.durationLabelFr(recentSummary.totalDurationSeconds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Lecture bornée aux $CALL_HISTORY_LOAD_LIMIT appels les plus récents accessibles dans le journal Android ; ces chiffres ne mesurent pas automatiquement le spam évité.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (recentItems.isEmpty()) {
                                Text(
                                    "Aucune entrée d’appel disponible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            recentItems.take(recentVisibleLimit).forEach { entry ->
                                OutlinedButton(
                                    onClick = {
                                        entry.number?.let(ContactDialNumberPolicy::fromProvider)?.let {
                                            number = it
                                            phoneTab = 0
                                            showRecents = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        val recentLabels = CallHistoryPresentationPolicy.labels(
                                            entry.number,
                                            entry.cachedName
                                        )
                                        Text(
                                            recentLabels.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        recentLabels.secondary?.let { secondary ->
                                            Text(
                                                secondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            CallHistoryInsights.typeLabelFr(entry.type) +
                                                " · " +
                                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                                    .format(Date(entry.dateMillis)),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Durée : " + CallHistoryInsights.durationLabelFr(entry.durationSeconds),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (recentItems.size > recentVisibleLimit) {
                                val recentRemaining = recentItems.size - recentVisibleLimit
                                OutlinedButton(
                                    onClick = {
                                        recentVisibleLimit =
                                            (recentVisibleLimit + CALL_HISTORY_PAGE_SIZE)
                                                .coerceAtMost(recentItems.size)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Afficher ${minOf(CALL_HISTORY_PAGE_SIZE, recentRemaining)} de plus · " +
                                            "${recentRemaining} restant(s)"
                                    )
                                }
                            }
                        }

                        LaunchedEffect(openContactsAfterPermissionGrant) {
                            if (openContactsAfterPermissionGrant && contactsPermissionGranted) {
                                openContactsAfterPermissionGrant = false
                                refreshContacts()
                            }
                        }

                        if (contactsLoading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                "Lecture du répertoire Android…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        contactListStatus?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (phoneTab == 2 && contactsPermissionGranted) {
                            val callableCount = remember(contactItems) {
                                contactItems.count { it.phoneNumbers.isNotEmpty() }
                            }
                            val phoneCount = remember(contactItems) {
                                contactItems.sumOf { ContactPresentationPolicy.displayNumbers(it.phoneNumbers).size }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(64.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Contacts,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Votre répertoire",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            "$callableCount contact(s) appelable(s) · $phoneCount numéro(s)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Vos contacts restent sur cet appareil.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = contactQuery,
                                onValueChange = {
                                    contactQuery = it.take(80)
                                    contactVisibleLimit = CONTACTS_PAGE_SIZE
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Rechercher dans le répertoire") },
                                placeholder = { Text("Nom ou numéro") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = contactFilter == 0,
                                    onClick = {
                                        contactFilter = 0
                                        contactVisibleLimit = CONTACTS_PAGE_SIZE
                                    },
                                    label = { Text("Appelables", maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = contactFilter == 1,
                                    onClick = {
                                        contactFilter = 1
                                        contactVisibleLimit = CONTACTS_PAGE_SIZE
                                    },
                                    label = { Text("Tous", maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = contactFilter == 2,
                                    onClick = {
                                        contactFilter = 2
                                        contactVisibleLimit = CONTACTS_PAGE_SIZE
                                    },
                                    label = { Text("Sans numéro", maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = contactFilter == 3,
                                    onClick = {
                                        contactFilter = 3
                                        contactVisibleLimit = CONTACTS_PAGE_SIZE
                                    },
                                    label = { Text("Favoris", maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            val q = contactQuery.trim()
                            val selectedFilter = when (contactFilter) {
                                1 -> ContactPresentationPolicy.Filter.ALL
                                2 -> ContactPresentationPolicy.Filter.WITHOUT_NUMBER
                                else -> ContactPresentationPolicy.Filter.CALLABLE
                            }
                            val favoriteNumbers = remember(favoriteRefreshEpoch) { favorites.all() }
                            val filteredContacts = remember(contactItems, q, contactFilter, favoriteNumbers) {
                                contactItems.filter { contact ->
                                    val presentationMatch = ContactPresentationPolicy.include(
                                        hasReadableNumber = contact.phoneNumbers.isNotEmpty(),
                                        filter = selectedFilter
                                    )
                                    val favoriteMatch = contactFilter != 3 ||
                                        contact.phoneNumbers.any { rawNumber ->
                                            CallRuleEngine.normalizeNumber(rawNumber) in favoriteNumbers
                                        }
                                    presentationMatch && favoriteMatch && ContactSearchPolicy.matches(
                                        displayName = contact.displayName,
                                        phoneNumbers = contact.phoneNumbers,
                                        rawQuery = q
                                    )
                                }
                            }

                            Text(
                                when {
                                    filteredContacts.isEmpty() && q.isNotBlank() ->
                                        "Aucun résultat pour « $q »."
                                    filteredContacts.isEmpty() ->
                                        "Aucun contact dans cette catégorie."
                                    else ->
                                        "${filteredContacts.size} contact(s) · ${minOf(contactVisibleLimit, filteredContacts.size)} affiché(s)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val sectionedContacts = remember(filteredContacts) {
                                filteredContacts.withIndex()
                                    .sortedWith(
                                        compareBy<IndexedValue<LocalContactLookup.Contact>> {
                                            ContactPresentationPolicy.sectionOrderKey(it.value.displayName)
                                        }.thenBy { it.index }
                                    )
                                    .map { it.value }
                            }
                            val visibleContacts = sectionedContacts.take(contactVisibleLimit)
                            visibleContacts.forEachIndexed { index, contact ->
                                val sectionLabel =
                                    ContactPresentationPolicy.sectionLabel(contact.displayName)
                                val previousSection = visibleContacts
                                    .getOrNull(index - 1)
                                    ?.let { ContactPresentationPolicy.sectionLabel(it.displayName) }
                                if (sectionLabel != previousSection) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            sectionLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        HorizontalDivider(modifier = Modifier.weight(1f))
                                    }
                                }

                                val displayNumbers =
                                    ContactPresentationPolicy.displayNumbers(contact.phoneNumbers)
                                val initial = contact.displayName
                                    .trim()
                                    .firstOrNull()
                                    ?.uppercaseChar()
                                    ?.toString()
                                    ?: "?"

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(50.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        initial,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    contact.displayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    if (displayNumbers.isEmpty())
                                                        "Aucun numéro téléphonique"
                                                    else if (displayNumbers.size == 1)
                                                        "1 numéro"
                                                    else
                                                        "${displayNumbers.size} numéros",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (displayNumbers.isEmpty()) {
                                            Text(
                                                if (contact.providerHasPhoneNumber) {
                                                    "Android signale un numéro, mais aucune valeur lisible n’est exposée à Sentinel."
                                                } else {
                                                    "Ajoutez un numéro dans votre application Contacts pour pouvoir appeler ou écrire depuis Sentinel."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            displayNumbers.forEach { phoneNumber ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(
                                                            start = 14.dp,
                                                            end = 6.dp,
                                                            top = 6.dp,
                                                            bottom = 6.dp
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            phoneNumber.take(64),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        val dialable =
                                                            ContactDialNumberPolicy.fromProvider(phoneNumber)
                                                        val isFavorite = dialable != null && dialable in favoriteNumbers
                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                if (dialable != null && favorites.setFavorite(dialable, !isFavorite)) {
                                                                    favoriteRefreshEpoch++
                                                                    contactListStatus = if (isFavorite) "Retiré des favoris." else "Ajouté aux favoris."
                                                                }
                                                            },
                                                            enabled = dialable != null
                                                        ) {
                                                            Icon(
                                                                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                                contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris"
                                                            )
                                                        }
                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                if (dialable != null) {
                                                                    number = dialable
                                                                    contactStatus = "Contact : " + contact.displayName
                                                                    if (holdsDialerRole()) {
                                                                        placeCallIfReady(dialable)
                                                                    } else {
                                                                        requestDialerRole(dialable)
                                                                    }
                                                                }
                                                            },
                                                            enabled = dialable != null
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Phone,
                                                                contentDescription = "Appeler ${contact.displayName}"
                                                            )
                                                        }
                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                if (dialable != null) {
                                                                    startActivity(
                                                                        Intent(
                                                                            Intent.ACTION_SENDTO,
                                                                            Uri.parse("smsto:" + Uri.encode(dialable))
                                                                        ).setClass(
                                                                            context,
                                                                            SmsComposeActivity::class.java
                                                                        )
                                                                    )
                                                                }
                                                            },
                                                            enabled = dialable != null
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Message,
                                                                contentDescription = "Écrire à ${contact.displayName}"
                                                            )
                                                        }
                                                    }
                                                }

                                                WhatsAppClickToChatPolicy.urlFor(phoneNumber)?.let { whatsappUrl ->
                                                    TextButton(
                                                        onClick = {
                                                            try {
                                                                startActivity(
                                                                    Intent(
                                                                        Intent.ACTION_VIEW,
                                                                        Uri.parse(whatsappUrl)
                                                                    )
                                                                )
                                                            } catch (_: ActivityNotFoundException) {
                                                                contactListStatus =
                                                                    "Aucune application ne peut ouvrir WhatsApp sur cet appareil."
                                                            } catch (_: SecurityException) {
                                                                contactListStatus =
                                                                    "Ouverture WhatsApp bloquée par la sécurité Android."
                                                            }
                                                        },
                                                        modifier = Modifier.align(Alignment.End)
                                                    ) {
                                                        Text("WhatsApp")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredContacts.size > contactVisibleLimit) {
                                val remaining = filteredContacts.size - contactVisibleLimit
                                OutlinedButton(
                                    onClick = {
                                        contactVisibleLimit =
                                            (contactVisibleLimit + CONTACTS_PAGE_SIZE)
                                                .coerceAtMost(filteredContacts.size)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Afficher ${minOf(CONTACTS_PAGE_SIZE, remaining)} de plus · " +
                                            "$remaining restant(s)"
                                    )
                                }
                            }
                        }

                        callActionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (phoneTab == 3) {
                            Text("État affiché à partir des capacités réellement observables. La certification locale exige ${physicalEvidence.requiredCount} critères Phone Core sur cet APK.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_CONTACTS = "sentinel.extra.OPEN_CONTACTS"
    }
}

