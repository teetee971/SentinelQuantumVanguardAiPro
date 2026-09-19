package com.sentinel.quantum

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CallerIdProvenance
import com.sentinel.quantum.security.ProtectionProvenance
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
        val enrichmentEnabled = SettingsStore(applicationContext).callerReputationEnrichmentEnabled
        setContent {
            SentinelQuantumTheme {
                var remoteResult by remember { mutableStateOf<CallerReputationClient.Result?>(null) }
                var remoteStatus by remember { mutableStateOf<String?>(null) }
                var reportStatus by remember { mutableStateOf<String?>(null) }
                var reportRunning by remember { mutableStateOf(false) }
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
                                    verificationStatus = verificationCode.ifBlank { "UNKNOWN" }
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
                        onReport = { category ->
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
    onReport: (CommunityReportClient.Category) -> Unit,
    onDismiss: () -> Unit
) {
    val riskColor = when (action) {
        "BLOCK" -> Color(0xFFE15555)
        "SILENCE" -> Color(0xFFF4B740)
        else -> Color(0xFF32D6A0)
    }
    val localEvidence = CallerIdProvenance.localIdentity(name, organisation)
    val decisionEvidence = CallerIdProvenance.sentinelDecision(reason)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("SENTINEL CALL ID", color = Color(0xFF66C7FF), fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(flag, fontSize = 48.sp)
            Text(action, color = riskColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Text(name ?: "Identité non disponible", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        organisation?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }
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
                    onClick = { onReport(CommunityReportClient.Category.WANGIRI) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Wangiri / appel très court") }
                OutlinedButton(
                    onClick = { onReport(CommunityReportClient.Category.SPOOFING) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Usurpation / spoofing") }
                OutlinedButton(
                    onClick = { onReport(CommunityReportClient.Category.PREMIUM_RATE) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Numéro surtaxé") }
                OutlinedButton(
                    onClick = { onReport(CommunityReportClient.Category.ROBOCALL) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !reportRunning
                ) { Text("Robocall / appel automatisé") }
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
    Column(Modifier.fillMaxWidth().background(Color.Transparent)) {
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
