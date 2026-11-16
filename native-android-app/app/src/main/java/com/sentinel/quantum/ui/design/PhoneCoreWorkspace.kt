@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Secondary controls remain reachable without occupying the primary communication flow. */
@Composable
fun PhoneCoreDisclosure(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(if (expanded) "Masquer" else "Afficher", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (expanded) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

/** No call is submitted by entering digits. The caller owns the explicit call action. */
@Composable
fun PhoneCoreNumberPad(number: String, onChange: (String) -> Unit) {
    val letters = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", "")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789", "*0#").forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { column, digit ->
                    FilledTonalButton(
                        onClick = { if (number.length < 32) onChange(number + digit) },
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                            Text(letters[rowIndex * 3 + column], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (number.isEmpty()) onChange("+") }, enabled = number.isEmpty()) { Text("+ international") }
            TextButton(onClick = { onChange(number.dropLast(1)) }, enabled = number.isNotEmpty()) { Text("Effacer un chiffre") }
        }
    }
}

@Composable
fun PhoneCoreConversationRow(
    address: String,
    preview: String,
    date: String,
    count: Int,
    risk: String?,
    onOpen: () -> Unit,
    onWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // The swipe never deletes data. It only requests the existing explicit
                // confirmation surface in SmsComposeActivity; returning false keeps the
                // row in place until the user confirms there.
                onDelete()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(22.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text("Supprimer…", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), contentColor = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(address.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(address.ifBlank { "Expéditeur inconnu" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    risk?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    Text("$count message(s) chargé(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Actions de la conversation") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ouvrir dans WhatsApp") }, onClick = { menu = false; onWhatsApp() })
                        DropdownMenuItem(text = { Text("Supprimer la conversation…") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
