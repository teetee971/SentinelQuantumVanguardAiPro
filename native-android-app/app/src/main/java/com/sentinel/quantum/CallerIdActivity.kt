package com.sentinel.quantum

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CallerIdProvenance
import com.sentinel.quantum.security.ProtectionProvenance
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.security.PhoneEvidence
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SentinelConfidence
import com.sentinel.quantum.security.SentinelNumberCard
import com.sentinel.quantum.security.CommunityReportClient
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Read-only caller-ID surface. It never delays or changes the screening decision. */
class CallerIdActivity : ComponentActivity() {
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
        val settingsStore = SettingsStore(applicationContext)
        val enrichmentEnabled = settingsStore.callerReputationEnrichmentEnabled &&
            ProtectionModePolicy.permitsCallerNumberEnrichment(settingsStore.protectionMode)
        setContent {
            SentinelQuantumTheme {
                var remoteResult by remember { mutableStateOf<CallerReputationClient.Result?>(null) }
                var remoteStatus by remember { mutableStateOf<String?>(null) }
                var reportStatus by remember { mutableStateOf<String?>(null) }
                var reportRunning by remember { mutableStateOf(false) }
                var pendingReportCategory by remember { mutableStateOf<CommunityReportClient.Category?>(null) }
                val reportClient = remember { CommunityReportClient() }
                val reportScope = rememberCoroutineScope()
                LaunchedEffect(number, enrichmentEnabled) {
                    if (enrichmentEnabled && number.isNotBlank()) {
                        remoteStatus = "Enrichissement en cours…"
                        remoteResult = withContext(Dispatchers.IO) {
                            runCatching {
                                CallerReputationClient().evaluate(
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
                        action = intent.getStringExtra(EXTRA_ACTION).orEmpty(),
                        reason = intent.getStringExtra(EXTRA_REASON).orEmpty(),
                        name = intent.getStringExtra(EXTRA_NAME),
                        organisation = intent.getStringExtra(EXTRA_ORGANISATION),
                        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
                        verified = intent.getBooleanExtra(EXTRA_IDENTITY_VERIFIED, false),
                        remoteResult = remoteResult,
                        remoteStatus = remoteStatus,
                        remoteEnabled = enrichmentEnabled,
                        reportStatus = reportStatus,
                        reportRunning = reportRunning,
                        pendingReportCategory = pendingReportCategory,
                        onPrepareReport = { category ->
                            if (!reportRunning) pendingReportCategory = category
                        },
                        onCancelReport = { pendingReportCategory = null },
                        onReport = { category ->
                            pendingReportCategory = null
                            if (!reportRunning && number.isNotBlank()) {
                                reportRunning = true
                                reportStatus = "Envoi du signalement…"
                                reportScope.launch {
                                    val submitted = withContext(Dispatchers.IO) {
                                        runCatching {
                                            reportClient.submit(
                                                callerNumber = number,
                                                recipientCountry = Locale.getDefault().country.ifBlank { "FR" },
                                                category = category
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
    action: String,
    reason: String,
    name: String?,
    organisation: String?,
    source: String,
    verified: Boolean,
    remoteResult: CallerReputationClient.Result?,
    remoteStatus: String?,
    remoteEnabled: Boolean,
    reportStatus: String?,
    reportRunning: Boolean,
    pendingReportCategory: CommunityReportClient.Category?,
    onPrepareReport: (CommunityReportClient.Category) -> Unit,
    onCancelReport: () -> Unit,
    onReport: (CommunityReportClient.Category) -> Unit,
    onDismiss: () -> Unit
) {
    val riskColor = when (action) {
        "BLOCK" -> MaterialTheme.colorScheme.error
        "SILENCE" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val localEvidence = CallerIdProvenance.localIdentity(name, organisation)
    val decisionEvidence = CallerIdProvenance.sentinelDecision(reason)
    val context = androidx.compose.ui.platform.LocalContext.current
    val timelineSummary = remember(context) { PhonePrivateTimelineStore(context).read() }
    val numberCard = SentinelNumberCard.build(
        identity = SentinelNumberCard.Identity(name, organisation, country, null, verified),
        evidence = buildList {
            if (verified) add(PhoneEvidence("LOCAL_IDENTITY", SentinelConfidence.VERIFIED))
            if (verification.isNotBlank()) add(PhoneEvidence("OPERATOR_VERIFICATION", SentinelConfidence.INDICATIVE))
            if (reason.isNotBlank()) add(PhoneEvidence(reason, SentinelConfidence.INDICATIVE))
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("SENTINEL CALL ID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(flag, fontSize = 48.sp)
            Text(
                when (action) {
                    "BLOCK" -> "Appel bloqué"
                    "SILENCE" -> "Appel silencieux"
                    "ALLOW" -> "Appel autorisé"
                    else -> "Décision : " + action.ifBlank { "non disponible" }
                },
                color = riskColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
        }
        Text(numberCard.identity.displayName ?: "Identité non disponible", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        numberCard.identity.organisation?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }
        Text(
            "Confiance Sentinel : " + when (numberCard.confidence) {
                SentinelConfidence.VERIFIED -> "vérifiée"
                SentinelConfidence.INDICATIVE -> "indicative"
                else -> numberCard.confidence.name.lowercase()
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (numberCard.reasons.isNotEmpty()) {
            Text("Raisons : " + numberCard.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (numberCard.timeline.coordinatedCallSms) {
            Text("Signal temporel : activité appel + SMS rapprochée détectée.", style = MaterialTheme.typography.bodySmall)
        }
        if (numberCard.shouldConfirmBeforeCallback) {
            Text("Rappel : confirmation renforcée recommandée.", style = MaterialTheme.typography.bodySmall)
        }
        Text(number, fontSize = 24.sp)
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
        if (remoteEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Réputation distante opt-in", fontWeight = FontWeight.Bold)
                    remoteStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    remoteResult?.let { result ->
                        Fact("Score indicatif", "${result.riskScore}/100")
                        Fact("Action moteur", result.action)
                        Fact("Signalements communautaires", result.signals.toString())
                        EvidenceFact(CallerIdProvenance.communitySignal(result.communityIntelligence))
                        if (result.flags.isNotEmpty()) {
                            Fact("Signaux", result.flags.joinToString(" · "))
                        }
                        if (result.warning.isNotBlank()) {
                            Text(result.warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Source : moteur Sentinel Wangiri/Spoofing. Ce score n’est pas une preuve de fraude et ne modifie pas la décision locale déjà rendue.",
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
                OutlinedButton(
                    onClick = { onPrepareReport(CommunityReportClient.Category.WANGIRI) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Wangiri / appel très court") }
                OutlinedButton(
                    onClick = { onPrepareReport(CommunityReportClient.Category.SPOOFING) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Usurpation / spoofing") }
                OutlinedButton(
                    onClick = { onPrepareReport(CommunityReportClient.Category.PREMIUM_RATE) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Numéro surtaxé") }
                OutlinedButton(
                    onClick = { onPrepareReport(CommunityReportClient.Category.ROBOCALL) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Robocall / appel automatisé") }
                pendingReportCategory?.let { category ->
                    val label = when (category) {
                        CommunityReportClient.Category.WANGIRI -> "Wangiri / appel très court"
                        CommunityReportClient.Category.SPOOFING -> "Usurpation / spoofing"
                        CommunityReportClient.Category.PREMIUM_RATE -> "Numéro surtaxé"
                        CommunityReportClient.Category.ROBOCALL -> "Robocall / appel automatisé"
                        CommunityReportClient.Category.OTHER -> "Autre signalement"
                    }
                    Text("Confirmer le signalement ?", fontWeight = FontWeight.Bold)
                    Text(
                        "Le numéro de l’appelant et la catégorie « $label » seront transmis à la modération communautaire Sentinel.",
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
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(
            "Le pays est déduit de l’indicatif et peut être trompé. L’opérateur d’une tranche n’est pas forcément l’opérateur actuel après portabilité.",
            style = MaterialTheme.typography.bodySmall
        )
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
