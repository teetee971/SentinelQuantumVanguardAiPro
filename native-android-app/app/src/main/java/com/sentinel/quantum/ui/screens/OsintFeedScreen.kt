package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.OsintFeedItem
import com.sentinel.quantum.data.OsintPublicationTime
import com.sentinel.quantum.data.OsintRepository
import com.sentinel.quantum.data.OsintSource
import com.sentinel.quantum.data.OsintTransientItemStore
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private enum class OsintDisplayedSnapshot {
    NONE,
    COMPLETE,
    PARTIAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsintFeedScreen(navController: NavController) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { OsintFeedCache(context) }
    val repository = remember(context) { OsintRepository(cache) }
    var feedItems by remember { mutableStateOf<List<OsintFeedItem>>(emptyList()) }
    var displayedSnapshot by remember { mutableStateOf(OsintDisplayedSnapshot.NONE) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var coverageMessage by remember { mutableStateOf<String?>(null) }
    var cachedAtMs by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var readIds by remember { mutableStateOf(cache.readIds()) }
    val scope = rememberCoroutineScope()

    val loadFeeds: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = repository.fetchAllFeedsResult()
                when {
                    result.isComplete && result.items.isNotEmpty() -> {
                        cache.save(result.items)
                        OsintTransientItemStore.clear()
                        feedItems = result.items
                        displayedSnapshot = OsintDisplayedSnapshot.COMPLETE
                        cachedAtMs = null
                        coverageMessage = null
                    }

                    result.isComplete -> {
                        OsintTransientItemStore.clear()
                        if (
                            displayedSnapshot == OsintDisplayedSnapshot.COMPLETE &&
                            feedItems.isNotEmpty()
                        ) {
                            coverageMessage = context.getString(R.string.osint_complete_empty_cached)
                        } else {
                            feedItems = emptyList()
                            displayedSnapshot = OsintDisplayedSnapshot.NONE
                            cachedAtMs = null
                            coverageMessage = null
                            errorMessage = context.getString(R.string.osint_no_data)
                        }
                    }

                    else -> {
                        coverageMessage = context.getString(
                            R.string.osint_partial_coverage,
                            result.successfulSourceCount,
                            result.totalSourceCount
                        )

                        if (displayedSnapshot == OsintDisplayedSnapshot.COMPLETE) {
                            // Keep the last complete snapshot visible. A partial refresh must not
                            // replace complete cached/fresh truth with an incomplete network view.
                            OsintTransientItemStore.clear()
                        } else if (result.items.isNotEmpty()) {
                            // No complete snapshot exists. Partial data is useful only while its
                            // exact coverage remains visible, and every later partial refresh must
                            // replace it so the displayed items match the displayed coverage.
                            feedItems = result.items
                            displayedSnapshot = OsintDisplayedSnapshot.PARTIAL
                            cachedAtMs = null
                            OsintTransientItemStore.replace(result.items)
                        } else {
                            feedItems = emptyList()
                            displayedSnapshot = OsintDisplayedSnapshot.NONE
                            cachedAtMs = null
                            OsintTransientItemStore.clear()
                            errorMessage = context.getString(R.string.osint_partial_no_complete)
                        }
                    }
                }
            } catch (_: Exception) {
                if (feedItems.isEmpty()) {
                    errorMessage = context.getString(R.string.osint_error)
                }
                // Do not clear an existing partial-coverage warning here: keeping the warning is
                // more truthful than leaving an old partial snapshot looking complete after a
                // failed refresh.
            } finally {
                isLoading = false
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) { repository.loadCached() }
        if (cached != null) {
            feedItems = cached.items
            cachedAtMs = cached.fetchedAtMs
            displayedSnapshot = OsintDisplayedSnapshot.COMPLETE
            OsintTransientItemStore.clear()
        }
        loadFeeds()
    }

    val filteredItems = remember(feedItems, searchQuery, selectedSource) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        feedItems.filter { item ->
            (selectedSource == null || item.source == selectedSource) &&
                (query.isEmpty() ||
                    item.title.lowercase(Locale.ROOT).contains(query) ||
                    item.description.lowercase(Locale.ROOT).contains(query))
        }
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.osint_title),
                subtitle = "Flux, sources & cache local",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = loadFeeds) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.osint_refresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            cachedAtMs?.let { fetchedAtMs ->
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                Text(
                    text = stringResource(R.string.osint_cached_at, dateFormat.format(Date(fetchedAtMs))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SentinelD1.Card)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            coverageMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (feedItems.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(200) },
                    label = { Text(stringResource(R.string.osint_search_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSource == null,
                        onClick = { selectedSource = null },
                        label = { Text(stringResource(R.string.osint_filter_all)) }
                    )
                    OsintSource.entries.forEach { source ->
                        FilterChip(
                            selected = selectedSource == source.displayName,
                            onClick = {
                                selectedSource = if (selectedSource == source.displayName) null else source.displayName
                            },
                            label = { Text(source.displayName) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && feedItems.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    errorMessage != null && feedItems.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = loadFeeds) {
                                Text(stringResource(R.string.osint_refresh))
                            }
                        }
                    }

                    feedItems.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.osint_no_data),
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    filteredItems.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.osint_no_match),
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                OsintFeedCard(
                                    item = item,
                                    isRead = readIds.contains(item.id),
                                    onOpen = {
                                        cache.markRead(item.id)
                                        readIds = cache.readIds()
                                        navController.navigate(Screen.OsintDetail.createRoute(item.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OsintFeedCard(item: OsintFeedItem, isRead: Boolean = false, onOpen: () -> Unit = {}) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    val publicationLabel = OsintPublicationTime.format(
        item.pubDate,
        dateFormat,
        stringResource(R.string.osint_date_unknown)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.osint_source)} ${item.source}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = publicationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Text(
                text = item.title.ifBlank { stringResource(R.string.osint_untitled) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description.take(300) + if (item.description.length > 300) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
        }
    }
}
