package com.sentinel.quantum.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.OsintFeedItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only detail of a locally cached OSINT alert. No network access is performed here:
 * the item is resolved from [OsintFeedCache]. The original link can be opened in the user's
 * browser, which is an explicit user action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsintDetailScreen(navController: NavController, itemId: String) {
    val context = LocalContext.current
    val cache = remember(context) { OsintFeedCache(context.applicationContext) }
    val item: OsintFeedItem? = remember(itemId) {
        cache.load()?.items?.firstOrNull { it.id == itemId }
    }

    LaunchedEffect(itemId) {
        if (item != null) {
            cache.markRead(item.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.osint_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.osint_detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${stringResource(R.string.osint_source)} ${item.source}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${stringResource(R.string.osint_date)} ${dateFormat.format(Date(item.pubDate.time))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.category.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = item.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = item.description.ifBlank {
                    stringResource(R.string.osint_detail_no_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item.link.isNotBlank()) {
                Text(
                    text = item.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val openable = item.link.startsWith("https://")
            var openFailed by remember(itemId) { mutableStateOf(false) }
            Button(
                onClick = {
                    openFailed = try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        false
                    } catch (_: ActivityNotFoundException) {
                        true
                    }
                },
                enabled = openable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.osint_detail_open_browser))
            }

            if (!openable) {
                Text(
                    text = stringResource(R.string.osint_detail_link_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (openFailed) {
                Text(
                    text = stringResource(R.string.osint_detail_open_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
