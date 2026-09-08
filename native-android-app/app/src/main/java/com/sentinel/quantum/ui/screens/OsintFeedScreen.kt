package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.sentinel.quantum.data.OsintRepository
import com.sentinel.quantum.data.OsintSource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsintFeedScreen(navController: NavController) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { OsintFeedCache(context) }
    val repository = remember(context) { OsintRepository(cache) }
    var feedItems by remember { mutableStateOf<List<OsintFeedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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
                val fresh = repository.fetchAllFeeds()
                if (fresh.isNotEmpty()) {
                    feedItems = fresh
                    cachedAtMs = null
                } else if (feedItems.isEmpty()) {
                    errorMessage = "Aucune donnée disponible"
                }
            } catch (e: Exception) {
                if (feedItems.isEmpty()) {
                    errorMessage = "Erreur de chargement"
                }
            } finally {
                isLoading = false
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        repository.loadCached()?.let { cached ->
            feedItems = cached.items
            cachedAtMs = cached.fetchedAtMs
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
            TopAppBar(
                title = { Text(stringResource(R.string.osint_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = loadFeeds) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.osint_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
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
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    OsintSource.values().forEach { source ->
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
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    errorMessage != null && feedItems.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: "",
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
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    filteredItems.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.osint_no_match),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
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
                    text = dateFormat.format(item.pubDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Text(
                text = item.title,
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
