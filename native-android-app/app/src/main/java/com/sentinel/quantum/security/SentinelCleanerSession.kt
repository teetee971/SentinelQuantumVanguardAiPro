package com.sentinel.quantum.security

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Session owned by the activity ViewModel, not the screen's composition. */
class SentinelCleanerSession(
    private val scope: CoroutineScope,
    private val discover: () -> SentinelOwnedCleanupCollector.Discovery,
    private val execute: (SentinelCleanupPolicy.Candidate) -> SentinelCleanupPolicy.Result,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    data class State(
        val discovery: SentinelOwnedCleanupCollector.Discovery? = null,
        val selected: Set<String> = emptySet(),
        val results: List<SentinelCleanupPolicy.Result>? = null,
        val busy: Boolean = false,
        val status: String = "Aucun scan effectué."
    )
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    fun scan(): Boolean {
        if (state.value.busy) return false
        mutableState.value = State(busy = true, status = "Scan en cours…")
        scope.launch {
            try {
                val found = withContext(io) { discover() }
                mutableState.update { it.copy(discovery = found, status = when {
                    !found.coverageComplete -> "Scan partiel : limite atteinte ou emplacement inaccessible."
                    found.candidates.isEmpty() -> "Aucune copie temporaire ancienne à nettoyer dans ce périmètre."
                    else -> "Scan terminé : ${found.candidates.size} copie(s) proposée(s)."
                }) }
            } catch (cancelled: CancellationException) { mutableState.update { it.copy(status = "Scan interrompu.") }; throw cancelled }
              catch (_: Exception) { mutableState.update { it.copy(status = "Scan impossible. Aucun nettoyage effectué.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
        return true
    }

    fun select(ids: Set<String>) {
        if (!state.value.busy) mutableState.update { current ->
            current.copy(selected = current.discovery?.candidates.orEmpty()
                .filter { it.stableId in ids && SentinelCleanupPolicy.canExecuteDirectly(it) }.map { it.stableId }.toSet())
        }
    }

    fun clean(snapshot: List<SentinelCleanupPolicy.Candidate>): Boolean {
        val current = state.value
        if (current.busy || snapshot.isEmpty() || snapshot.distinctBy { it.stableId }.size != snapshot.size ||
            snapshot.any { it !in current.discovery?.candidates.orEmpty() || it.stableId !in current.selected || !SentinelCleanupPolicy.canExecuteDirectly(it) }) return false
        mutableState.update { it.copy(busy = true, selected = emptySet(), results = emptyList(), status = "Nettoyage en cours…") }
        scope.launch {
            try {
                withContext(io) {
                    for (candidate in snapshot) {
                        ensureActive()
                        val result = try { execute(candidate) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { SentinelCleanupPolicy.Result(candidate,
                                candidate.copy(state = SentinelCleanupPolicy.ActionState.FAILED)) }
                        // Publish before attempting the next deletion or observing cancellation.
                        mutableState.update { currentState -> currentState.copy(
                            results = currentState.results.orEmpty() + result,
                            discovery = currentState.discovery?.copy(candidates = currentState.discovery.candidates.map {
                                if (it.stableId == candidate.stableId) result.after else it
                            })) }
                    }
                }
                mutableState.update { it.copy(status = "Traitement terminé. Consultez le rapport.") }
            } catch (cancelled: CancellationException) { mutableState.update { it.copy(status = "Nettoyage interrompu ; rapport partiel.") }; throw cancelled }
              catch (_: Exception) { mutableState.update { it.copy(status = "Nettoyage interrompu ; rapport partiel.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
        return true
    }
}
