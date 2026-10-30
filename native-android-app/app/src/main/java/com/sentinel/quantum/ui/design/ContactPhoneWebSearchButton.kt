package com.sentinel.quantum.ui.design

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sentinel.quantum.security.ContactPhoneWebSearch
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.LocalContactPhoneWebGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ContactPhoneWebSearchButton(contact: LocalContactLookup.Contact, rawNumber: String) {
    val number = ContactPhoneWebSearch.normalized(rawNumber) ?: return
    val lookupKey = contact.lookupKey?.takeIf { it.isNotBlank() } ?: return
    key(contact.contactId, lookupKey, number) {
        val context = LocalContext.current.applicationContext
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val guard = remember(context) { LocalContactPhoneWebGuard(context) }
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        ContactPhoneWebSearchAction(lookupKey, number, busy, status) {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val current = withContext(Dispatchers.IO) { guard.isCurrent(contact.contactId, lookupKey, number) }
                        currentCoroutineContext().ensureActive()
                        if (current && guard.hasPermission() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            val url = ContactPhoneWebSearch.url(number) ?: return@launch
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else status = "Contact ou autorisation modifié. Aucune recherche ouverte ; rechargez la fiche."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: RuntimeException) { status = "Impossible d’ouvrir la recherche dans le navigateur." }
                    finally { busy = false }
                }
            }
        }
    }
}

/** Actual confirmation UI; no automatic launches and no saved pending permission after recreation. */
@Composable
internal fun ContactPhoneWebSearchAction(owner: String, number: String, busy: Boolean, status: String?, onConfirmed: () -> Unit) {
    var pending by remember(owner, number) { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { pending = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Rechercher ce numéro sur le web")
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall)
    }
    if (pending) AlertDialog(onDismissRequest = { pending = false },
        title = { Text("Rechercher ce numéro sur internet ?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(number, style = MaterialTheme.typography.titleMedium)
            Text("Ce numéro sera transmis à DuckDuckGo et la recherche s’ouvrira dans votre navigateur. Aucun autre champ du contact ne sera envoyé par Sentinel.")
            Text("Un résultat peut être ancien ou concerner une autre personne. Aucune identité ni liaison de profil ne sera confirmée automatiquement.")
        } },
        confirmButton = { TextButton(onClick = { pending = false; onConfirmed() }, enabled = !busy) { Text("Ouvrir la recherche") } },
        dismissButton = { TextButton(onClick = { pending = false }) { Text("Annuler") } })
}
