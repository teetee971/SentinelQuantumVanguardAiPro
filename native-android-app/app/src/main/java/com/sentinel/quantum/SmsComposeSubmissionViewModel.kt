package com.sentinel.quantum

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.quantum.security.SmsSubmissionGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns a single composer submission across activity recreation. Saved state contains only correlation metadata. */
internal class SmsComposeSubmissionViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    data class Target(val revision: String, val threadId: Long?, val mms: Boolean)
    data class Receipt(
        val accepted: Boolean,
        val reason: String,
        val providerMessageId: Long? = null,
        val sendToken: Int? = null
    ) {
        val preventsRetry: Boolean get() = accepted || reason in uncertainReasons
    }
    data class Submission(val target: Target, val receipt: Receipt?)

    private val blockedDrafts = savedState.get<Bundle>(BLOCKED_DRAFTS) ?: Bundle()
    private val mutableSubmission = MutableStateFlow(restore(savedState[KEY]))
    val submission = mutableSubmission.asStateFlow()
    private var displayed: Submission? = null

    fun receiptDisplayed(submission: Submission) {
        if (mutableSubmission.value === submission && submission.receipt != null) displayed = submission
    }

    fun submit(target: Target, transport: () -> Receipt): Boolean {
        val current = mutableSubmission.value
        if ((current != null && current !== displayed) || blocksDraft(target.revision, target.threadId)) {
            return false
        }
        // Admission and its try/finally are installed on the caller before any suspension.
        var admitted = false
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val gate = SmsSubmissionGate()
            if (!isActive || !gate.tryAcquire()) return@launch
            admitted = true
            try {
                if (current?.receipt?.accepted == true) {
                    // The prior receipt already cleared its corresponding draft on the live screen.
                    draftChanged(current.target.revision, current.target.threadId)
                }
                displayed = null
                publish(Submission(target, null))
                val receipt = withContext(Dispatchers.IO) {
                    try {
                        transport()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Receipt(false, INTERRUPTED)
                    }
                }
                publish(Submission(target, receipt))
            } finally {
                gate.release()
            }
        }
        return admitted
    }

    fun blocksDraft(revision: String?, threadId: Long?): Boolean =
        revision != null && blockedDrafts.getString(draftKey(threadId)) == revision

    fun draftChanged(revision: String?, threadId: Long?) {
        if (!blocksDraft(revision, threadId)) return
        blockedDrafts.remove(draftKey(threadId))
        savedState[BLOCKED_DRAFTS] = Bundle(blockedDrafts)
    }

    private fun draftKey(threadId: Long?): String = threadId?.toString() ?: "composer"

    private fun publish(submission: Submission) {
        val target = submission.target
        val receipt = submission.receipt
        val key = draftKey(target.threadId)
        if (receipt == null) {
            // A snapshot taken before the result must also prevent replay on process restore.
            blockedDrafts.putString(key, target.revision)
        } else if (!receipt.preventsRetry && blocksDraft(target.revision, target.threadId)) {
            blockedDrafts.remove(key)
        }
        savedState[BLOCKED_DRAFTS] = Bundle(blockedDrafts)
        savedState[KEY] = Bundle().apply {
            putString("revision", target.revision)
            target.threadId?.let { putLong("thread", it) }
            putBoolean("mms", target.mms)
            submission.receipt?.let { receipt ->
                putBoolean("accepted", receipt.accepted)
                putString("reason", receipt.reason)
                receipt.providerMessageId?.let { putLong("provider", it) }
                receipt.sendToken?.let { putInt("token", it) }
            }
        }
        mutableSubmission.value = submission
    }

    private companion object {
        const val KEY = "sms_composer_submission"
        const val BLOCKED_DRAFTS = "sms_composer_blocked_drafts"
        const val INTERRUPTED = "COMPOSER_SUBMISSION_INTERRUPTED"
        val uncertainReasons = setOf(
            INTERRUPTED,
            "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN",
            "MMS_SUBMISSION_OUTCOME_UNKNOWN"
        )

        fun restore(bundle: Bundle?): Submission? {
            val revision = bundle?.getString("revision") ?: return null
            val threadId = bundle.getLong("thread").takeIf { it > 0L }
            val target = Target(revision, threadId, bundle.getBoolean("mms"))
            // After process death a saved in-flight action may have reached Android already.
            val receipt = Receipt(
                accepted = bundle.getBoolean("accepted"),
                reason = bundle.getString("reason") ?: INTERRUPTED,
                providerMessageId = bundle.getLong("provider").takeIf { it > 0L },
                sendToken = bundle.getInt("token").takeIf { it > 0 }
            )
            return Submission(target, receipt)
        }
    }
}
