package com.sentinel.quantum.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SentinelBottomNavigationItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
fun SentinelBottomNavigation(
    items: List<SentinelBottomNavigationItem>,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = SentinelD1.Panel,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                modifier = Modifier.semantics {
                    contentDescription = item.label
                    role = Role.Tab
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SentinelD1.Cyan,
                    selectedTextColor = SentinelD1.Cyan,
                    indicatorColor = SentinelD1.Card,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Shared visual chrome for Android surfaces.
 *
 * Presentation only: callers remain responsible for supplying truthful runtime state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.ExtraBold
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = SentinelVisualTokens.TextSecondary
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SentinelD1.Panel,
            titleContentColor = SentinelVisualTokens.TextPrimary,
            navigationIconContentColor = SentinelVisualTokens.TextPrimary,
            actionIconContentColor = SentinelVisualTokens.TextPrimary
        )
    )
}

@Composable
fun SentinelPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SentinelD1.Border, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SentinelD1.Card)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SentinelHero(
    eyebrow: String,
    title: String,
    body: String,
    badges: List<Pair<String, Color>>,
    modifier: Modifier = Modifier
) {
    // Keep the historical API used by secondary destinations, but route it through the same
    // native command-centre visual as the newer screens. Badge colors remain semantic: no
    // synthetic green state is introduced when an older caller only supplied a neutral badge.
    val modules = badges.mapIndexed { index, (label, color) ->
        SentinelCoreModule(
            id = "${eyebrow.lowercase()}-$index",
            label = label.take(22),
            status = statusFromBadgeColor(color),
            enabled = false
        )
    }
    val overallStatus = when {
        modules.any { it.status == SentinelStatus.BAD } -> SentinelStatus.BAD
        modules.any { it.status == SentinelStatus.WARN } -> SentinelStatus.WARN
        modules.any { it.status == SentinelStatus.TEST || it.status == SentinelStatus.PURP } -> SentinelStatus.TEST
        else -> SentinelStatus.UNKNOWN
    }
    SentinelHeroVisual(
        status = overallStatus,
        title = title,
        subtitle = "$eyebrow · $body",
        modifier = modifier,
        modules = modules
    )
}

private fun statusFromBadgeColor(color: Color): SentinelStatus = when {
    color == SentinelD1.Success -> SentinelStatus.OK
    color == SentinelD1.Warning -> SentinelStatus.WARN
    color == SentinelD1.Danger -> SentinelStatus.BAD
    color == SentinelD1.Violet -> SentinelStatus.TEST
    color == SentinelD1.Unknown -> SentinelStatus.UNKNOWN
    else -> SentinelStatus.UNKNOWN
}

