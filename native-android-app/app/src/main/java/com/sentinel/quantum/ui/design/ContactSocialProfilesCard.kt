package com.sentinel.quantum.ui.design

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sentinel.quantum.security.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** No network client: explicit browser search plus owner-confirmed app-private links. */
@Composable
internal fun ContactSocialProfilesCard(contact: LocalContactLookup.Contact) {
    val owner = ContactSocialProfiles.ownerKey(contact.lookupKey)
    if (owner == null) {
        SentinelGlassCard(Modifier.fillMaxWidth()) {
            Text("Profils liés", style = MaterialTheme.typography.titleMedium)
            Text("La liaison de profils n’est pas disponible pour ce contact. Fermez et rechargez le carnet pour réessayer.")
        }
        return
    }
    key(owner) { BoundContactSocialProfiles(contact, owner) }
}

private fun hasContactPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/** Check the live selected provider identity; never bind persisted links by a reused numeric ID. */
private fun ownsCurrentContact(context: Context, contactId: Long, owner: String): Boolean {
    if (!hasContactPermission(context)) return false
    return runCatching {
        val matches = context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null
        )?.use { cursor ->
            cursor.moveToFirst() && ContactSocialProfiles.ownerKey(cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))) == owner
        } ?: false
        matches && hasContactPermission(context)
    }.getOrDefault(false)
}

@Composable
private fun BoundContactSocialProfiles(contact: LocalContactLookup.Contact, owner: String) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ContactSocialProfileStore(context) }
    val scope = rememberCoroutineScope()
    var links by remember { mutableStateOf<ContactSocialLinksResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(owner, contact.contactId) {
        links = null
        val loaded = withContext(Dispatchers.IO) {
            if (ownsCurrentContact(context, contact.contactId, owner)) store.read(owner) else ContactSocialLinksResult(false)
        }
        currentCoroutineContext().ensureActive()
        links = if (hasContactPermission(context)) loaded else ContactSocialLinksResult(false)
    }
    fun perform(action: suspend () -> Unit) {
        if (busy || links?.available != true) return
        busy = true
        scope.launch {
            try {
                val allowed = withContext(Dispatchers.IO) { ownsCurrentContact(context, contact.contactId, owner) }
                currentCoroutineContext().ensureActive()
                if (!allowed || !hasContactPermission(context)) {
                    links = ContactSocialLinksResult(false)
                    status = "Accès au contact indisponible. Fermez puis rouvrez la fiche."
                } else action()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: RuntimeException) { status = "Action indisponible. Aucun résultat distant n’a été importé." }
            finally { busy = false }
        }
    }
    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    ContactSocialProfilesContent(owner, contact.displayName, links, busy, status,
        onSearch = { network, name, company ->
            val url = ContactSocialProfiles.searchUrl(network, name, company)
            if (url == null) status = "Vérifiez le nom et l’entreprise (120 caractères maximum par champ)."
            else perform { open(url) }
        },
        onAssociate = { network, url, summary -> perform {
            val saved = withContext(Dispatchers.IO) {
                if (!ownsCurrentContact(context, contact.contactId, owner)) false
                else {
                    currentCoroutineContext().ensureActive()
                    store.associate(owner, network, url, summary, System.currentTimeMillis(), confirmed = true)
                }
            }
            currentCoroutineContext().ensureActive()
            if (saved && hasContactPermission(context)) {
                val loaded = withContext(Dispatchers.IO) { store.read(owner) }
                if (hasContactPermission(context)) links = loaded
                status = "Profil associé par vous. Aucune identité vérifiée automatiquement."
            } else status = "Liaison non enregistrée : accès, limite de stockage ou erreur locale."
        } },
        onOpen = { link -> if (link.owner == owner) perform { open(link.url) } },
        onRemove = { link -> if (link.owner == owner) perform {
            val removed = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                store.remove(owner, link.network)
            }
            currentCoroutineContext().ensureActive()
            if (removed && hasContactPermission(context)) {
                val loaded = withContext(Dispatchers.IO) { store.read(owner) }
                if (hasContactPermission(context)) links = loaded
                status = "Liaison retirée de Sentinel."
            } else status = "Liaison non retirée."
        } }
    )
}

private data class PendingContactProfile(val network: SocialNetwork, val url: String, val summary: String)

/** Actual presentation used in the sheet; callbacks remain gesture-only and testable with fixtures. */
@Composable
internal fun ContactSocialProfilesContent(
    owner: String,
    displayName: String,
    links: ContactSocialLinksResult?,
    busy: Boolean,
    status: String?,
    onSearch: (SocialNetwork, String, String) -> Unit,
    onAssociate: (SocialNetwork, String, String) -> Unit,
    onOpen: (ContactSocialLink) -> Unit,
    onRemove: (ContactSocialLink) -> Unit
) {
    var expanded by rememberSaveable(owner) { mutableStateOf(false) }
    var name by rememberSaveable(owner) { mutableStateOf(displayName.take(120)) }
    var company by rememberSaveable(owner) { mutableStateOf("") }
    var network by rememberSaveable(owner) { mutableStateOf(SocialNetwork.LINKEDIN) }
    var url by rememberSaveable(owner) { mutableStateOf("") }
    var summary by rememberSaveable(owner) { mutableStateOf("") }
    var pending by remember(owner) { mutableStateOf<PendingContactProfile?>(null) }
    var formError by remember(owner) { mutableStateOf<String?>(null) }
    val enabled = !busy && links?.available == true
    SentinelGlassCard(Modifier.fillMaxWidth()) {
        Text("Profils liés", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text("Recherche assistée dans le navigateur · aucune collecte API", color = SentinelD1.Cyan, style = MaterialTheme.typography.labelLarge)
        when {
            links == null -> Text("Chargement des liaisons locales…")
            !links.available -> Text("Liaisons indisponibles. Fermez puis rouvrez la fiche ; aucune donnée existante ne sera remplacée.")
            links.links.isEmpty() -> Text("Aucun profil associé. Choisissez et confirmez vous-même le bon compte.")
        }
        links?.links?.forEach { link ->
            Text(link.network.label, style = MaterialTheme.typography.titleSmall, color = SentinelD1.Cyan)
            Text(link.url, style = MaterialTheme.typography.bodyMedium)
            Text("Associé par vous le " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(link.associatedAtMs)), style = MaterialTheme.typography.bodySmall)
            if (link.summary.isNotBlank()) {
                Text("Informations renseignées par vous", style = MaterialTheme.typography.labelLarge)
                Text(link.summary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen(link) }, enabled = enabled) { Text("Ouvrir le profil") }
                TextButton(onClick = { onRemove(link) }, enabled = enabled) { Text("Retirer la liaison") }
            }
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = !expanded }, enabled = enabled) { Text(if (expanded) "Masquer la recherche" else "Rechercher ou associer un profil") }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialNetwork.entries.forEach { item ->
                    FilterChip(selected = network == item, onClick = { network = item; formError = null }, label = { Text(item.label) }, enabled = !busy)
                }
            }
            OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nom à rechercher") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(company, { company = it.take(120) }, label = { Text("Entreprise (facultative)") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("En ouvrant la recherche, vous transmettez uniquement ces deux champs à DuckDuckGo. Vérifiez-les avant de continuer. Un homonyme n’est pas une identité confirmée.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onSearch(network, name, company) }, enabled = enabled && name.isNotBlank()) { Text("Rechercher dans le navigateur") }
            OutlinedTextField(url, { url = it.take(2048); formError = null }, label = { Text("URL du profil choisi") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(summary, { summary = it.take(2000) }, label = { Text("Informations publiques relevées (facultatif)") }, supportingText = { Text("Saisie manuelle, sans vérification ni collecte automatique") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (formError != null) Text(formError!!, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = {
                val canonical = ContactSocialProfiles.canonical(network, url)
                if (canonical == null) formError = "Entrez une URL HTTPS de profil ${network.label}, pas une publication ou une recherche."
                else pending = PendingContactProfile(network, canonical, summary)
            }, enabled = enabled && url.isNotBlank()) { Text("Examiner la liaison") }
        }
    }
    pending?.let { selected ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("Associer ce profil ?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contact : $displayName")
                Text(selected.network.label + " : " + selected.url)
                Text("Vous confirmez cette correspondance. Sentinel ne vérifie pas l’identité et ne modifie pas Contacts Android.")
            } },
            confirmButton = { TextButton(onClick = {
                pending = null
                onAssociate(selected.network, selected.url, selected.summary)
            }, enabled = enabled) { Text("Confirmer la liaison") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } }
        )
    }
}
