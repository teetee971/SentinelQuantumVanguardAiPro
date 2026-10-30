package com.sentinel.quantum.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-owner session state. Payloads stay in bounded memory, never SavedState/Bundle or disk. */
internal class MmsAttachmentDraft(
    private val scope: CoroutineScope,
    private val load: suspend (List<String>) -> MmsAttachmentLoader.Result
) {
    data class State(
        val attachments: List<MmsAttachmentLoader.LoadedAttachment> = emptyList(),
        val loading: Boolean = false,
        val errorCode: String? = null
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var loadingJob: Job? = null

    fun attachmentsForSubmission(): List<MmsAttachmentLoader.LoadedAttachment>? =
        state.value.takeUnless { it.loading }?.attachments

    data class Submission internal constructor(
        internal val generation: Long,
        val attachments: List<MmsAttachmentLoader.LoadedAttachment>
    )

    fun submissionSnapshot(): Submission? =
        attachmentsForSubmission()?.let { Submission(generation, it.toList()) }

    fun clearIfUnchanged(submitted: Submission): Boolean {
        if (submitted.generation != generation || state.value.loading) return false
        clear()
        return true
    }

    fun select(sources: List<String>): Job? {
        if (sources.isEmpty()) return null // Picker cancellation preserves the previous selection.
        clear()
        if (sources.size > MmsSendEligibilityPolicy.MAX_ATTACHMENTS) {
            mutableState.value = State(errorCode = "TOO_MANY_ATTACHMENTS")
            return null
        }
        if (sources.any { it.length !in 1..4096 }) {
            mutableState.value = State(errorCode = "ATTACHMENT_URI_REJECTED")
            return null
        }
        val request = generation
        val requestedSources = sources.toList()
        mutableState.value = State(loading = true)
        return scope.launch {
            val result = try {
                load(requestedSources)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MmsAttachmentLoader.Result.Rejected("ATTACHMENT_READ_FAILED")
            }
            currentCoroutineContext().ensureActive()
            if (request != generation) return@launch
            mutableState.value = when (result) {
                is MmsAttachmentLoader.Result.Loaded -> State(attachments = result.attachments.toList())
                is MmsAttachmentLoader.Result.Rejected -> State(errorCode = result.reason)
            }
        }.also { loadingJob = it }
    }

    fun remove(index: Int) {
        val attachments = state.value.attachments
        if (index !in attachments.indices) return
        clear()
        mutableState.value = State(attachments = attachments.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun clear() {
        generation++
        loadingJob?.cancel()
        loadingJob = null
        mutableState.value = State()
    }
}
