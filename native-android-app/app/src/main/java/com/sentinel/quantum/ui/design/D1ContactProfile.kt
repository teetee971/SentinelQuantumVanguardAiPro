package com.sentinel.quantum.ui.design

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sentinel.quantum.R
import com.sentinel.quantum.security.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sentinel.quantum.data.BoundedInputReader
import com.sentinel.quantum.security.ContactPhotoPolicy
import com.sentinel.quantum.security.ContactPhotoIdentity
import com.sentinel.quantum.security.LocalContactLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local provider photo only, bounded and loaded off the UI thread. Never an invented portrait. */
@Composable
fun D1ContactAvatar(contact: LocalContactLookup.Contact, modifier: Modifier = Modifier, refreshGeneration: Int = 0) {
    val context = LocalContext.current.applicationContext
    val safeUri = ContactPhotoPolicy.localUri(contact.photoUri)
    val identity = ContactPhotoIdentity(contact.contactId, safeUri, contact.photoRevision, refreshGeneration)
    val photo = rememberContactPhoto(identity) {
        withContext(Dispatchers.IO) {
            if (safeUri == null || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) null
            else runCatching {
                val bytes = context.contentResolver.openInputStream(Uri.parse(safeUri))?.use {
                    BoundedInputReader.read(it, 2 * 1024 * 1024)
                } ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..8000 || bounds.outHeight !in 1..8000) return@runCatching null
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Surface(modifier.clip(CircleShape), shape = CircleShape, color = SentinelD1.Cyan.copy(alpha = 0.16f), contentColor = SentinelD1.Cyan) {
        val actualPhoto = photo
        if (actualPhoto != null) Image(actualPhoto, contentDescription = null, contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) {
            Text(contact.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** State and provider boundary: profile details exist only for the current resumed selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun D1ContactProfileSheet(
    contact: LocalContactLookup.Contact,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    photoRefreshGeneration: Int = 0
) {
    val context = LocalContext.current.applicationContext
    val reader = remember(context) { LocalContactProfileReader(context) }
    val owner = LocalLifecycleOwner.current
    var activation by remember { mutableIntStateOf(0) }
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE) {
                activation++
                resumed = event == Lifecycle.Event.ON_RESUME
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val request = ContactProfileRequest(contact.contactId, contact.photoRevision, photoRefreshGeneration, activation)
    val details by produceState<ContactProfileSnapshot?>(null, request, resumed) {
        value = null
        if (resumed) value = loadContactProfile(request, reader::canRead) {
            withContext(Dispatchers.IO) { reader.read(request.contactId) }
        }
    }
    // ProduceState may retain the old value until the new producer starts; never render it.
    val current = details?.forRequest(request).takeIf { resumed && reader.canRead() }
    D1ContactProfileFrame(contact, current, resumed && reader.canRead(), onDismiss, onCall, onMessage,
        onEdit, onShare, photoRefreshGeneration)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun D1ContactProfileFrame(
    contact: LocalContactLookup.Contact,
    details: ContactProfileSnapshot?,
    accessActive: Boolean,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    photoRefreshGeneration: Int = 0
) {
    val contentState = rememberSaveableStateHolder()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = SentinelD1.Background, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.96f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sentinel · Contacts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fermer") }
            }
            if (!accessActive) {
                Text("Accès aux contacts suspendu. Revenez à la fiche après autorisation.", Modifier.padding(16.dp))
            } else {
                contentState.SaveableStateProvider(contact.lookupKey ?: "contact-session:${contact.contactId}") {
                    D1ContactProfileContent(contact, details, onCall, onMessage, onEdit, onShare,
                        photoRefreshGeneration, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Real screen content, also exercised by instrumented tests without reading personal contacts. */
@Composable
internal fun D1ContactProfileContent(
    contact: LocalContactLookup.Contact,
    details: ContactProfileSnapshot?,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    photoRefreshGeneration: Int = 0,
    modifier: Modifier = Modifier
) {
    val numbers = remember(details) { ContactProfileDetails.phoneNumbers(details) }
    val buttonColors = ButtonDefaults.filledTonalButtonColors(containerColor = SentinelD1.Cyan.copy(alpha = 0.16f), contentColor = SentinelD1.Cyan)
    // At the content boundary, Android's elastic overscroll can consume reverse drags
    // while releasing its stretch. Keep this profile's scroll direct, including in the modal sheet.
    // This is a scoped mitigation candidate; S24+ end-of-page validation is still required.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState(), overscrollEffect = null).testTag("contact-profile-scroll")
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.d1_orbital_globe), contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
            D1ContactAvatar(contact, Modifier.size(112.dp).border(2.dp, SentinelD1.Cyan, CircleShape), photoRefreshGeneration)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(contact.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            Text("Contact du carnet Android", color = SentinelD1.Cyan, style = MaterialTheme.typography.labelLarge)
        }
        // Edit/share precede long details so they remain easy to reach with large text.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onEdit, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) { Text("Modifier") }
            OutlinedButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text("Partager") }
        }
        SentinelGlassCard(Modifier.fillMaxWidth()) {
            Text("Téléphone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (numbers.isEmpty()) Text(when (details?.status) {
                ContactProfileStatus.READY -> "Aucun numéro renseigné"
                ContactProfileStatus.PERMISSION_REQUIRED -> "Autorisation Contacts requise"
                ContactProfileStatus.UNAVAILABLE -> "Numéros indisponibles"
                null -> "Chargement des numéros…"
            })
            numbers.forEach { number ->
                val numberKey = ContactDialNumberPolicy.fromProvider(number)
                val phoneLabel = details?.fields?.firstOrNull {
                    it.kind == ContactProfileKind.PHONE && ContactDialNumberPolicy.fromProvider(it.value) == numberKey
                }?.label
                if (!phoneLabel.isNullOrBlank()) Text(phoneLabel, color = SentinelD1.Cyan, style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(number, style = MaterialTheme.typography.titleMedium) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onCall(number) }, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) { Text("Appeler") }
                    OutlinedButton(onClick = { onMessage(number) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Message") }
                }
            }
        }
        ContactSocialProfilesCard(contact)
        when (details?.status) {
            null -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Chargement des informations du carnet…") }
            ContactProfileStatus.PERMISSION_REQUIRED -> Text("Autorisation Contacts requise. Aucune information complémentaire affichée.")
            ContactProfileStatus.UNAVAILABLE -> Text("Informations complémentaires indisponibles. Fermez puis rouvrez la fiche pour réessayer.")
            ContactProfileStatus.READY -> {
                val labels = linkedMapOf(
                    ContactProfileKind.NAME to "Nom détaillé",
                    ContactProfileKind.EMAIL to "E-mails",
                    ContactProfileKind.ADDRESS to "Adresses",
                    ContactProfileKind.ORGANISATION to "Entreprise et fonction",
                    ContactProfileKind.EVENT to "Anniversaire et dates",
                    ContactProfileKind.NOTE to "Notes",
                    ContactProfileKind.WEBSITE to "Sites et profils importés",
                    ContactProfileKind.NICKNAME to "Autres noms",
                    ContactProfileKind.RELATION to "Relations",
                    ContactProfileKind.IM to "Messagerie instantanée",
                    ContactProfileKind.SIP to "Adresses SIP"
                )
                labels.forEach { (kind, label) ->
                    val fields = details.fields.filter { it.kind == kind }
                    // Common fields stay visible even when absent; optional kinds appear only if imported.
                    if (fields.isNotEmpty() || kind in setOf(ContactProfileKind.EMAIL, ContactProfileKind.ADDRESS,
                            ContactProfileKind.ORGANISATION, ContactProfileKind.EVENT, ContactProfileKind.NOTE)) {
                        SentinelGlassCard(Modifier.fillMaxWidth()) {
                            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            if (fields.isEmpty()) Text("Non renseigné dans Contacts Android", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            fields.forEach { field ->
                                if (field.label.isNotBlank()) Text(field.label, color = SentinelD1.Cyan, style = MaterialTheme.typography.labelLarge)
                                SelectionContainer { Text(field.value, style = MaterialTheme.typography.bodyLarge) }
                            }
                        }
                    }
                }
            }
        }
        Text("Source : carnet Android du profil courant. Aucune vérification d’identité ni de profil social. " +
            "Les historiques d’appels et de messages ne sont pas intégrés à cette fiche. " +
            "Lecture limitée à 256 rubriques, 16 384 caractères par note et 4 096 par autre valeur ; " +
            "consultez Contacts Android pour les données au-delà de ces limites.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
}
