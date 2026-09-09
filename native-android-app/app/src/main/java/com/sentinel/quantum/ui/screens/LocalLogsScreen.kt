package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.security.LocalLogger
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLogsScreen(navController: NavController) {
    val context = LocalContext.current
    val logger = remember { LocalLogger(context) }
    var logs by remember { mutableStateOf(logger.getLogs()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LocalLogger.LogLevel?>(null) }
    var shareStatus by remember { mutableStateOf<String?>(null) }
    val shareNoneText = stringResource(R.string.local_logs_share_none)
    val shareChooserText = stringResource(R.string.local_logs_share_chooser)
    val shareFailedText = stringResource(R.string.local_logs_share_failed)

    val filteredLogs = remember(logs, searchQuery, selectedLevel) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        logs.filter { log ->
            (selectedLevel == null || log.level == selectedLevel) &&
                (query.isEmpty() ||
                    log.tag.lowercase(Locale.ROOT).contains(query) ||
                    log.message.lowercase(Locale.ROOT).contains(query))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.local_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        logger.clearLogs()
                        logs = emptyList()
                        shareStatus = null
                    }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.local_logs_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.local_logs_count, logs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (logs.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(200) },
                    label = { Text(stringResource(R.string.local_logs_search_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text(stringResource(R.string.local_logs_filter_all)) }
                    )
                    LocalLogger.LogLevel.values().forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = if (selectedLevel == level) null else level },
                            label = { Text(level.name) }
                        )
                    }
                }
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.local_logs_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.local_logs_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs) { log ->
                        LogEntryCard(log)
                    }
                }
            }

            shareStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { logs = logger.getLogs() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_refresh))
                }
                Button(
                    onClick = {
                        val exportFile = logger.exportSanitizedCopy()
                        if (exportFile == null) {
                            shareStatus = shareNoneText
                            return@Button
                        }
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                exportFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, shareChooserText))
                            shareStatus = null
                        } catch (_: Exception) {
                            shareStatus = shareFailedText
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = logs.isNotEmpty()
                ) {
                    Text(stringResource(R.string.action_share))
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(log: LocalLogger.LogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.level) {
                LocalLogger.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                LocalLogger.LogLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                LocalLogger.LogLevel.SECURITY -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.level.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "[${log.tag}]",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
