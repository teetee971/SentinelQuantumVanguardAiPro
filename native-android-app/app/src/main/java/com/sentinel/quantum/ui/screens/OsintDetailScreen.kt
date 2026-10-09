package com.sentinel.quantum.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.sentinel.quantum.data.OsintLinkPolicy
import com.sentinel.quantum.data.OsintPublicationTime
import com.sentinel.quantum.data.OsintTransientItemStore
import com.sentinel.quantum.ui.design.SentinelTopBar
import java.text.DateFormat

/**
 * Read-only detail of an OSINT alert already visible to the user. Complete snapshots are resolved
 * from [OsintFeedCache]; a currently displayed partial snapshot can be resolved from the bounded
 * process-local [OsintTransientItemStore]. No network access is performed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsintDetailScreen(navController: NavController, itemId: String) {
    val context = LocalContext.current
    val cache = remember(context) { OsintFeedCache(context.applicationContext) }
    val item: OsintFeedItem? = remember(itemId) {
        OsintTransientItemStore.find(itemId)
            ?: cache.load()?.items?.firstOrNull { it.id == itemId }
    }

    LaunchedEffect(itemId) {
        if (item != null) {
            cache.markRead(item.id)
        }
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.osint_detail_title),
                subtitle = "Source mise en cache · ouverture externe explicite",
                onBack = { navController.popBackStack() }
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

        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val publicationLabel = OsintPublicationTime.format(
            item.pubDate,
            dateFormat,
            stringResource(R.string.osint_date_unknown)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.osint_detail_source_value, item.source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.osint_detail_date_value,
                    publicationLabel
                ),
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
                text = item.title.ifBlank { stringResource(R.string.osint_untitled) },
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

            val openable = OsintLinkPolicy.isSafeHttpsUrl(item.link)
            var openFailed by remember { mutableStateOf(false) }

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
                    } catch (_: SecurityException) {
                        true
                    } catch (_: IllegalArgumentException) {
                        true
                    } catch (_: RuntimeException) {
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
