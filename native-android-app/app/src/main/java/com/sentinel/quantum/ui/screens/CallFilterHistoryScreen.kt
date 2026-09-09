package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.security.CallFilterDecisionEntity
import com.sentinel.quantum.security.CallFilterLogStore
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFilterHistoryScreen(navController: NavController) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { CallFilterLogStore.get(context) }
    var history by remember { mutableStateOf<List<CallFilterDecisionEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val reload: suspend () -> Unit = { history = store.history() }
    LaunchedEffect(store) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.call_history_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { store.clear(); reload() } }) {
                        Text(stringResource(R.string.call_history_clear))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.call_history_privacy),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.call_history_empty),
                    Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history, key = { it.id }) { entry ->
                        CallFilterHistoryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallFilterHistoryCard(entry: CallFilterDecisionEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.action, fontWeight = FontWeight.Bold)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(Date(entry.occurredAtMs)),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                stringResource(R.string.call_history_reason, entry.source, entry.reason),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (entry.numberFingerprint == null) {
                    stringResource(R.string.call_history_unknown_number)
                } else {
                    stringResource(R.string.call_history_private_identifier)
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
