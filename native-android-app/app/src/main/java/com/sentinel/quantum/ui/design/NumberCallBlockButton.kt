package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleEngine
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import com.sentinel.quantum.security.ExactCallBlockState
import com.sentinel.quantum.security.ConfirmedCallBlockMutation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Same app-private call rules as the management screen; no SMS filtering or automatic action. */
@Composable
internal fun NumberCallBlockButton(ownerKey: String, number: String, validateCurrent: () -> Boolean) {
    if (CallRuleEngine.normalizeNumber(number) == null) return
    key(ownerKey, number) {
        val context = LocalContext.current.applicationContext
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val store = remember(context) { CallBlocklistStore(context) }
        val scope = rememberCoroutineScope()
        val validate by rememberUpdatedState(validateCurrent)
        var listed by remember { mutableStateOf<Boolean?>(null) }
        var role by remember { mutableStateOf<CallScreeningActivationPolicy.State?>(null) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        fun readListed(): Boolean = ExactCallBlockState.contains(number,
            store.snapshot().blockedNumberHashes, store::fingerprintsForNumber)

        LaunchedEffect(lifecycle, revision) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    val state = withContext(Dispatchers.IO) { readListed() to CallScreeningActivationPolicy.read(context) }
                    listed = state.first
                    role = state.second
                    awaitCancellation()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: RuntimeException) { status = "État de la liste indisponible." }
                finally { listed = null; role = null }
            }
        }
        NumberCallBlockAction(ownerKey, number, listed, busy, role, status) {
            if (!busy && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                busy = true
                // Cancel work/confirmation when leaving RESUMED, not just when composition is removed.
                scope.launch {
                    try {
                        coroutineScope {
                            val operation = currentCoroutineContext().job
                            val pauseWatcher = launch {
                                lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) }
                                operation.cancel()
                            }
                            try {
                                val valid = withContext(Dispatchers.IO) { validate() }
                                currentCoroutineContext().ensureActive()
                                if (!valid) status = "Numéro ou autorisation modifié. Rechargez cet écran."
                                else {
                                    val result = withContext(Dispatchers.IO) {
                                        ConfirmedCallBlockMutation.execute(
                                            validate = { validate() },
                                            isCurrent = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
                                            alreadyListed = { readListed() },
                                            add = { store.addBlockedNumber(number) }
                                        )
                                    }
                                    status = when (result) {
                                        true -> "Règle d’appel enregistrée. Aucun SMS supprimé ou bloqué."
                                        false -> "Ajout impossible : format, limite ou stockage."
                                        null -> "Numéro ou autorisation modifié. Aucune règle ajoutée."
                                    }
                                }
                                revision++
                            } finally { pauseWatcher.cancel() }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: RuntimeException) { status = "Impossible d’enregistrer la règle d’appel." }
                    finally { busy = false }
                }
            }
        }
    }
}


/** Real confirmation UI, injected operation for tests; pending state never saved across recreation. */
@Composable
internal fun NumberCallBlockAction(ownerKey: String, number: String, listed: Boolean?, busy: Boolean,
    role: CallScreeningActivationPolicy.State?, status: String?, onConfirmed: () -> Unit) {
    var pending by remember(ownerKey, number) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) pending = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column {
        OutlinedButton(onClick = { pending = true }, enabled = !busy && listed == false,
            modifier = Modifier.heightIn(min = 48.dp)) {
            Text(when (listed) { true -> "Présent dans votre liste de blocage"; false -> "Bloquer les appels de ce numéro"; null -> "Lecture de la liste…" })
        }
        Text(when (role) {
            CallScreeningActivationPolicy.State.HELD -> "Sentinel choisi pour filtrer les appels."
            CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD -> "Filtrage Android non attribué à Sentinel."
            CallScreeningActivationPolicy.State.UNAVAILABLE -> "Filtrage Android indisponible."
            null -> "État du filtrage non vérifié."
        }, style = MaterialTheme.typography.bodySmall)
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall)
    }
    if (pending) AlertDialog(onDismissRequest = { pending = false },
        title = { Text("Bloquer les appels de ce numéro ?") },
        text = { Column {
            Text(number)
            Text("Une règle exacte sera ajoutée à votre liste locale. Cela ne bloque pas les SMS, ne supprime aucun message et ne modifie pas le contact.")
            Text("Le rôle Android ne garantit pas un rejet effectif. Le premier appel à froid peut encore passer. Les numéros masqués ou différents ne sont pas couverts par cette règle.")
        } },
        confirmButton = { TextButton(onClick = { pending = false; onConfirmed() }, enabled = !busy && listed == false) { Text("Confirmer le blocage d’appels") } },
        dismissButton = { TextButton(onClick = { pending = false }) { Text("Annuler") } })
}
