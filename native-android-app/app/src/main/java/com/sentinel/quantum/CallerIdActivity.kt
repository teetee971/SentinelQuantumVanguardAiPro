package com.sentinel.quantum

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CallerIdProvenance
import com.sentinel.quantum.security.ProtectionProvenance
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.PhoneEvidence
import com.sentinel.quantum.security.PhonePrivateTimeline
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.PhoneRiskCard
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.SentinelConfidence
import com.sentinel.quantum.security.SentinelNumberCard
import com.sentinel.quantum.security.WhatsAppClickToChatPolicy
import com.sentinel.quantum.security.CommunityReportClient
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Read-only caller-ID surface. It never delays or changes the screening decision. */
class CallerIdActivity : ComponentActivity() {
    private var callerUiEvidenceEligible = false
    private var callerUiEvidenceRecorded = false
    private var callerUiEvidenceWriteInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val verificationCode = intent.getStringExtra(EXTRA_VERIFICATION_CODE).orEmpty()
        val action = intent.getStringExtra(EXTRA_ACTION).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        callerUiEvidenceEligible = action in setOf("ALLOW", "BLOCK", "SILENCE") && reason.isNotBlank()
        val settingsStore = SettingsStore(applicationContext)
        val enrichmentEnabled = settingsStore.callerReputationEnrichmentEnabled &&
            ProtectionModePolicy.permitsCallerNumberEnrichment(settingsStore.protectionMode)
        val communityReportingEnabled =
            ProtectionModePolicy.permitsExplicitCommunityReport(settingsStore.protectionMode)
        setContent {
            SentinelQuantumTheme {
                var remoteResult by remember { mutableStateOf<CallerReputationClient.Result?>(null) }
                var remoteStatus by remember { mutableStateOf<String?>(null) }
                var officialAllocation by remember { mutableStateOf<ArcepDirectoryClient.Allocation?>(null) }
                var officialStatus by remember { mutableStateOf<String?>(null) }
                var reportStatus by remember { mutableStateOf<String?>(null) }
                var reportRunning by remember { mutableStateOf(false) }
                var pendingReportCategory by remember { mutableStateOf<CommunityReportClient.Category?>(null) }
                var localName by remember(number) { mutableStateOf(intent.getStringExtra(EXTRA_NAME)) }
                var localOrganisation by remember(number) { mutableStateOf(intent.getStringExtra(EXTRA_ORGANISATION)) }
                var localSource by remember(number) { mutableStateOf(intent.getStringExtra(EXTRA_SOURCE).orEmpty()) }
                var localIdentityVerified by remember(number) {
                    mutableStateOf(intent.getBooleanExtra(EXTRA_IDENTITY_VERIFIED, false))
                }
                val reportClient = remember {
                    CommunityReportClient(egressGate = {
                        ProtectionModePolicy.permitsExplicitCommunityReport(settingsStore.protectionMode)
                    })
                }
                val officialDirectory = remember { ArcepDirectoryClient() }
                val reportScope = rememberCoroutineScope()

                LaunchedEffect(number) {
                    if (number.isNotBlank() && number.any { it.isDigit() }) {
                        val localIdentity = withContext(Dispatchers.IO) {
                            LocalContactLookup(applicationContext).find(number)
                        }
                        if (localIdentity != null) {
                            localName = localIdentity.displayName
                            localOrganisation = localIdentity.organisation
                            localSource = "Répertoire local de l’utilisateur"
                            localIdentityVerified = false
                        }
                    }
                }

                LaunchedEffect(number) {
                    if (number.isBlank() || ArcepDirectoryClient.toFrenchNational(number) == null) {
                        officialAllocation = null
                        officialStatus = "Attribution ARCEP non applicable à ce numéro."
                    } else {
                        officialStatus = "Recherche de l’attribution officielle…"
                        val result = withContext(Dispatchers.IO) {
                            runCatching { officialDirectory.lookup(number) }
                        }
                        result.onSuccess { allocation ->
                            officialAllocation = allocation
                            officialStatus = if (allocation == null) {
                                "Aucune tranche ARCEP correspondante trouvée."
                            } else {
                                "Attribution officielle ARCEP trouvée."
                            }
                        }.onFailure {
                            officialAllocation = null
                            officialStatus = "Index ARCEP temporairement indisponible."
                        }
                    }
                }

                LaunchedEffect(number, enrichmentEnabled) {
                    if (enrichmentEnabled && number.isNotBlank()) {
                        remoteStatus = "Enrichissement en cours…"
                        remoteResult = withContext(Dispatchers.IO) {
                            runCatching {
                                CallerReputationClient(
                                    egressGate = {
                                        settingsStore.callerReputationEnrichmentEnabled &&
                                            ProtectionModePolicy.permitsCallerNumberEnrichment(settingsStore.protectionMode)
                                    }
                                ).evaluate(
                                    callerNumber = number,
                                    recipientCountry = Locale.getDefault().country.ifBlank { "FR" },
                                    verificationStatus = verificationCode.ifBlank { "UNKNOWN" },
                                    privacyMode = PhonePrivacyFirewall.Mode.ENHANCED,
                                    explicitConsent = settingsStore.callerReputationEnrichmentEnabled
                                )
                            }.getOrNull()
                        }
                        remoteStatus = if (remoteResult == null) {
                            "Enrichissement distant indisponible"
                        } else {
                            "Enrichissement distant reçu"
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CallerCard(
                        number = number,
                        country = intent.getStringExtra(EXTRA_COUNTRY).orEmpty(),
                        flag = intent.getStringExtra(EXTRA_FLAG).orEmpty(),
                        type = intent.getStringExtra(EXTRA_TYPE).orEmpty(),
                        verification = intent.getStringExtra(EXTRA_VERIFICATION).orEmpty(),
                        verificationCode = verificationCode,
                        action = action,
                        reason = reason,
                        name = localName,
                        organisation = localOrganisation,
                        source = localSource,
                        verified = localIdentityVerified,
                        remoteResult = remoteResult,
                        remoteStatus = remoteStatus,
                        remoteEnabled = enrichmentEnabled,
                        officialAllocation = officialAllocation,
                        officialStatus = officialStatus,
                        reportStatus = reportStatus,
                        reportRunning = reportRunning,
                        pendingReportCategory = pendingReportCategory,
                        onPrepareReport = { category ->
                            if (!communityReportingEnabled) {
                                reportStatus = "Mode local uniquement : aucun numéro n’est transmis pour signalement."
                            } else if (!reportRunning) {
                                pendingReportCategory = category
                            }
                        },
                        onCancelReport = { pendingReportCategory = null },
                        onReport = { category ->
                            pendingReportCategory = null
                            if (!communityReportingEnabled) {
                                reportStatus = "Mode local uniquement : signalement distant désactivé."
                            } else if (!reportRunning && number.isNotBlank()) {
                                reportRunning = true
                                reportStatus = "Envoi du signalement…"
                                reportScope.launch {
                                    val submitted = withContext(Dispatchers.IO) {
                                        runCatching {
                                            reportClient.submit(
                                                callerNumber = number,
                                                recipientCountry = Locale.getDefault().country.ifBlank { "FR" },
                                                category = category,
                                                protectionMode = settingsStore.protectionMode,
                                                explicitConsent = true
                                            )
                                        }
                                    }
                                    reportRunning = false
                                    submitted.onSuccess { result ->
                                        reportStatus = when (result.status) {
                                            "pending" -> "Signalement reçu et placé en modération."
                                            "duplicate" -> "Signalement déjà reçu récemment."
                                            else -> "Signalement reçu : " + result.status
                                        }
                                    }.onFailure {
                                        reportStatus = "Signalement indisponible pour le moment."
                                    }
                                }
                            }
                        },
                        onDismiss = ::finish
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (
            callerUiEvidenceEligible &&
            !callerUiEvidenceRecorded &&
            !callerUiEvidenceWriteInFlight
        ) {
            callerUiEvidenceWriteInFlight = true
            lifecycleScope.launch {
                val stored = withContext(Dispatchers.IO) {
                    PhonePrivateTimelineStore(applicationContext).append(
                        PhonePrivateTimeline.Event(
                            kind = PhonePrivateTimeline.Kind.CALL,
                            timestampMs = System.currentTimeMillis(),
                            direction = "INCOMING",
                            signal = PhoneCorePhysicalValidation.SIGNAL_CALLER_ID_UI_SHOWN
                        )
                    )
                }
                callerUiEvidenceRecorded = stored
                callerUiEvidenceWriteInFlight = false
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "caller.number"
        const val EXTRA_COUNTRY = "caller.country"
        const val EXTRA_FLAG = "caller.flag"
        const val EXTRA_TYPE = "caller.type"
        const val EXTRA_VERIFICATION = "caller.verification"
        const val EXTRA_VERIFICATION_CODE = "caller.verification_code"
        const val EXTRA_ACTION = "caller.action"
        const val EXTRA_REASON = "caller.reason"
        const val EXTRA_NAME = "caller.name"
        const val EXTRA_ORGANISATION = "caller.organisation"
        const val EXTRA_SOURCE = "caller.source"
        const val EXTRA_IDENTITY_VERIFIED = "caller.identity_verified"
    }
}

@Composable
private fun CallerCard(
    number: String,
    country: String,
    flag: String,
    type: String,
    verification: String,
    verificationCode: String,
    action: String,
    reason: String,
    name: String?,
    organisation: String?,
    source: String,
    verified: Boolean,
    remoteResult: CallerReputationClient.Result?,
    remoteStatus: String?,
    remoteEnabled: Boolean,
    officialAllocation: ArcepDirectoryClient.Allocation?,
    officialStatus: String?,
    reportStatus: String?,
    reportRunning: Boolean,
    pendingReportCategory: CommunityReportClient.Category?,
    onPrepareReport: (CommunityReportClient.Category) -> Unit,
    onCancelReport: () -> Unit,
    onReport: (CommunityReportClient.Category) -> Unit,
    onDismiss: () -> Unit
) {
    val decisionLabel = when (action) {
        "BLOCK" -> "Appel bloqué"
        "SILENCE" -> "Appel mis en sourdine"
        "ALLOW" -> "Appel autorisé"
        else -> "Décision : " + PhoneCoreFrenchLabels.action(action)
    }
    val riskColor = when (action) {
        "BLOCK" -> MaterialTheme.colorScheme.error
        "SILENCE" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val localEvidence = CallerIdProvenance.localIdentity(name, organisation)
    val decisionEvidence = CallerIdProvenance.sentinelDecision(PhoneCoreFrenchLabels.reason(reason))
    val context = androidx.compose.ui.platform.LocalContext.current
    val timelineSummary by produceState(
        initialValue = PhonePrivateTimeline.Summary(emptyList(), coordinatedCallSms = false),
        key1 = context
    ) {
        value = withContext(Dispatchers.IO) {
            PhonePrivateTimelineStore(context).read()
        }
    }
    val numberCard = SentinelNumberCard.build(
        identity = SentinelNumberCard.Identity(name, organisation, country, null, verified),
        evidence = buildList {
            if (name != null || organisation != null) {
                add(PhoneEvidence("LOCAL_CONTACT_MATCH", SentinelConfidence.INDICATIVE))
            }
            when (verificationCode) {
                "VERIFIED" -> add(PhoneEvidence("OPERATOR_VERIFICATION_PASSED", SentinelConfidence.VERIFIED))
                "FAILED" -> add(PhoneEvidence("OPERATOR_VERIFICATION_FAILED", SentinelConfidence.UNKNOWN))
            }
            if (reason.isNotBlank() && reason != "NO_MATCHING_RULE") {
                add(PhoneEvidence(reason, SentinelConfidence.INDICATIVE))
            }
            remoteResult?.let { result ->
                if (result.flags.isNotEmpty() || result.signals > 0) {
                    add(PhoneEvidence("SIGNED_REPUTATION_WARNING", SentinelConfidence.INDICATIVE, localOnly = false))
                }
            }
        },
        events = timelineSummary.events,
        reputation = remoteResult?.let { SentinelNumberCard.Reputation(it.riskScore, it.signals, it.flags) },
        nowMs = System.currentTimeMillis()
    )
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PhoneCoreBrand(
            context = "Identification d’appel",
            status = decisionLabel,
            modifier = Modifier.fillMaxWidth()
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(flag, fontSize = 48.sp, modifier = Modifier.clearAndSetSemantics { })
            Text(
                decisionLabel,
                color = riskColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier.semantics { contentDescription = "Décision Sentinel : $decisionLabel" }
            )
        }
        Text(numberCard.identity.displayName ?: "Identité non disponible", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        numberCard.identity.organisation?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }
        Text(
            "Confiance des données : " + when (numberCard.confidence) {
                SentinelConfidence.VERIFIED -> "vérifiée"
                SentinelConfidence.CORROBORATED -> "corroborée"
                SentinelConfidence.INDICATIVE -> "indicative"
                SentinelConfidence.UNKNOWN -> "non mesurée"
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (numberCard.reasons.isNotEmpty()) {
            Text(
                "Raisons : " + numberCard.reasons.joinToString(" · ") { PhoneCoreFrenchLabels.evidence(it) },
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (numberCard.timeline.coordinatedCallSms) {
            Text("Signal temporel : activité appel + SMS rapprochée détectée.", style = MaterialTheme.typography.bodySmall)
        }
        if (numberCard.shouldConfirmBeforeCallback) {
            Text("Rappel : confirmation renforcée recommandée.", style = MaterialTheme.typography.bodySmall)
        }
        Text(number, fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Numéro appelant : ${number.ifBlank { "non disponible" }}" })
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("Pays", "$flag $country")
                Fact("Type d’appel", type)
                Fact("Vérification opérateur", verification)
                Fact("Identité", if (verified) "Vérifiée par la source locale fournie" else "Non vérifiée")
                localEvidence?.let { EvidenceFact(it) }
                if (source.isNotBlank()) Fact("Source déclarée", source)
                EvidenceFact(decisionEvidence)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Origine et attribution officielles", fontWeight = FontWeight.Bold)
                officialStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                officialAllocation?.let { allocation ->
                    Fact("Opérateur attributaire de la tranche", allocation.attributedOperator ?: allocation.operatorCode)
                    Fact("Territoire déclaré", allocation.territory.orEmpty())
                    Fact("Tranche publiée", allocation.start + " – " + allocation.end)
                    Fact("Date d’attribution", allocation.allocationDate.orEmpty())
                    allocation.businessIdentifier?.let { Fact("Identifiant entreprise", it) }
                    allocation.rcs?.let { Fact("Registre du commerce", it) }
                    allocation.address?.let { Fact("Adresse déclarée", it) }
                    Text(
                        "Source : index de numérotation ARCEP. Une attribution de tranche indique l’origine administrative du bloc de numéros ; elle ne signifie ni que le numéro est fiable, ni qu’il doit être bloqué. L’attributaire peut différer de l’opérateur actuel après portabilité.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (remoteEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Réputation distante activée par l’utilisateur", fontWeight = FontWeight.Bold)
                    remoteStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    remoteResult?.let { result ->
                        val riskCard = PhoneRiskCard.build(
                            PhoneRiskCard.Input(
                                riskScore = result.riskScore,
                                communitySignals = result.signals,
                                categoryCodes = result.categories,
                                sourceLabel = "Sentinel Reputation",
                                observedAtMs = result.reputationObservedAtMs,
                                ttlMs = result.reputationTtlMs
                            ),
                            nowMs = System.currentTimeMillis()
                        )
                        Fact("Niveau de risque", PhoneRiskCard.riskBandLabelFr(riskCard.riskBand))
                        Fact("Score indicatif", riskCard.riskScore?.let { it.toString() + "/100" } ?: "Non mesuré")
                        Fact("Fraîcheur", PhoneRiskCard.freshnessLabelFr(riskCard.freshness))
                        Fact("Action moteur", PhoneCoreFrenchLabels.action(result.action))
                        Fact("Signalements communautaires", result.signals.toString())
                        if (riskCard.categories.isNotEmpty()) {
                            Fact(
                                "Catégories",
                                riskCard.categories.joinToString(" · ") { it.frenchLabel }
                            )
                        }
                        EvidenceFact(
                            CallerIdProvenance.communitySignal(
                                PhoneCoreFrenchLabels.communityIntelligence(result.communityIntelligence)
                            )
                        )
                        if (result.flags.isNotEmpty()) {
                            Fact(
                                "Signaux bruts",
                                result.flags.joinToString(" · ") { PhoneCoreFrenchLabels.reputationFlag(it) }
                            )
                        }
                        if (result.warning.isNotBlank()) {
                            Text(result.warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Source : moteur Sentinel de détection Wangiri et d’usurpation de numéro. Ce score n’est pas une preuve de fraude et ne modifie pas la décision locale déjà rendue.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Text(
                "Enrichissement distant désactivé. Activez-le explicitement dans Blocage d’appels pour autoriser l’envoi du numéro entrant au moteur Sentinel après la décision locale.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Signaler à la communauté", fontWeight = FontWeight.Bold)
                Text(
                    "Le signalement est envoyé dans une file de modération. Il ne modifie pas immédiatement le score de réputation.",
                    style = MaterialTheme.typography.bodySmall
                )
                val reportCategories = listOf(
                    CommunityReportClient.Category.WANGIRI,
                    CommunityReportClient.Category.SPOOFING,
                    CommunityReportClient.Category.PREMIUM_RATE,
                    CommunityReportClient.Category.ROBOCALL,
                    CommunityReportClient.Category.TELEMARKETING,
                    CommunityReportClient.Category.BANK_IMPERSONATION,
                    CommunityReportClient.Category.DELIVERY_SCAM,
                    CommunityReportClient.Category.TECH_SUPPORT_SCAM,
                    CommunityReportClient.Category.GOVERNMENT_IMPERSONATION,
                    CommunityReportClient.Category.HARASSMENT,
                    CommunityReportClient.Category.OTHER
                )
                reportCategories.forEach { category ->
                    OutlinedButton(
                        onClick = { onPrepareReport(category) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !reportRunning
                    ) { Text(category.frenchLabel) }
                }
                pendingReportCategory?.let { category ->
                    Text("Confirmer le signalement ?", fontWeight = FontWeight.Bold)
                    Text(
                        "Le numéro de l’appelant et la catégorie « ${category.frenchLabel} » seront transmis à la modération communautaire Sentinel.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onCancelReport,
                            enabled = !reportRunning,
                            modifier = Modifier.weight(1f)
                        ) { Text("Annuler") }
                        Button(
                            onClick = { onReport(category) },
                            enabled = !reportRunning,
                            modifier = Modifier.weight(1f)
                        ) { Text("Confirmer") }
                    }
                }
                reportStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }

        Text(
            "Le pays est déduit de l’indicatif et peut être trompé. L’opérateur d’une tranche n’est pas forcément l’opérateur actuel après portabilité.",
            style = MaterialTheme.typography.bodySmall
        )
        WhatsAppClickToChatPolicy.urlFor(number)?.let { whatsappUrl ->
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl)))
                    } catch (_: ActivityNotFoundException) {
                        // No HTTPS handler is available on this device.
                    } catch (_: SecurityException) {
                        // Device policy blocked the external handoff.
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ouvrir dans WhatsApp")
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer la fiche") }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "Non disponible" }, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EvidenceFact(evidence: ProtectionProvenance.Evidence) {
    val prefix = ProtectionProvenance.displayPrefix(evidence.source)
    val confidence = when (evidence.confidence) {
        ProtectionProvenance.Confidence.VERIFIED -> "vérifié dans cette source"
        ProtectionProvenance.Confidence.CORROBORATED -> "corroboré"
        ProtectionProvenance.Confidence.INDICATIVE -> "indicatif"
        ProtectionProvenance.Confidence.UNKNOWN -> "non déterminé"
    }
    Fact(prefix, evidence.label + " · " + confidence)
}
