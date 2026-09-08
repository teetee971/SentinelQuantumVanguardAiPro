package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.security.ExplainableAI
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneMonitor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<PhoneMonitor.SpamCheckResult?>(null) }
    var explanation by remember { mutableStateOf<ExplainableAI.Explanation?>(null) }
    var monitorStats by remember { mutableStateOf<PhoneMonitor.MonitorStats?>(null) }

    val logger = remember { LocalLogger(context) }
    val phoneMonitor = remember { PhoneMonitor(logger) }
    val explainableAI = remember { ExplainableAI(logger) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_security_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.phone_security_heading), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.phone_security_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.take(64) },
                label = { Text(stringResource(R.string.phone_security_label)) },
                placeholder = { Text(stringResource(R.string.phone_security_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        checkResult = phoneMonitor.checkNumber(phoneNumber)
                        explanation = checkResult?.let(explainableAI::explainSpamCheck)
                        monitorStats = phoneMonitor.getStats()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phoneNumber.isNotBlank()
            ) { Text(stringResource(R.string.phone_security_check)) }

            checkResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.phone_security_result), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phone_security_risk_level, result.riskLevel.name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phone_security_reason, result.reason))
                        Text(
                            stringResource(R.string.phone_security_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            monitorStats?.let { stats ->
                Text(
                    stringResource(R.string.phone_security_stats, stats.totalChecks, stats.elevatedRiskChecks),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            explanation?.let { exp ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.phone_security_explanation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(exp.summary, style = MaterialTheme.typography.bodyMedium)
                        if (exp.recommendations.isNotEmpty()) {
                            Text(stringResource(R.string.phone_security_recommendations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            exp.recommendations.forEach { recommendation -> Text("• $recommendation") }
                        }
                    }
                }
            }
        }
    }
}
