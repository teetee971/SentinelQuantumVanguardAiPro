package com.sentinel.quantum.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class MmsAttachmentDraftTest {
    private fun image(value: Int) = MmsAttachmentLoader.LoadedAttachment("image/png", byteArrayOf(value.toByte()))

    @Test fun submissionSnapshotIsUnavailableUntilTheSelectedImagesFinishLoading() = runBlocking {
        val finish = CompletableDeferred<MmsAttachmentLoader.Result>()
        val draft = MmsAttachmentDraft(this) { finish.await() }
        val job = draft.select(listOf("content://test/one"))!!
        try {
            assertNull(draft.attachmentsForSubmission())
            finish.complete(MmsAttachmentLoader.Result.Loaded(listOf(image(1))))
            job.join()
            assertArrayEquals(byteArrayOf(1), draft.attachmentsForSubmission()!!.single().payload)
        } finally { finish.complete(MmsAttachmentLoader.Result.Rejected("CANCELLED")); draft.clear() }
    }

    @Test fun cancelingThePickerKeepsTheExistingSelection() = runBlocking {
        val draft = MmsAttachmentDraft(this) { MmsAttachmentLoader.Result.Loaded(listOf(image(1))) }
        draft.select(listOf("content://test/one"))!!.join()
        assertNull(draft.select(emptyList()))
        assertArrayEquals(byteArrayOf(1), draft.state.value.attachments.single().payload)
    }

    @Test fun removingOneImageKeepsTheOtherImage() = runBlocking {
        val draft = MmsAttachmentDraft(this) { MmsAttachmentLoader.Result.Loaded(listOf(image(1), image(2))) }
        draft.select(listOf("content://test/one", "content://test/two"))!!.join()
        draft.remove(0)
        assertArrayEquals(byteArrayOf(2), draft.state.value.attachments.single().payload)
    }

    @Test fun clearPreventsALateReadFromRestoringRemovedImages() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val draft = MmsAttachmentDraft(this) {
            withContext(NonCancellable) { finish.await(); MmsAttachmentLoader.Result.Loaded(listOf(image(1))) }
        }
        val job = draft.select(listOf("content://test/one"))!!
        try {
            kotlinx.coroutines.yield()
            assertTrue(draft.state.value.loading)
            draft.clear()
            finish.complete(Unit)
            job.join()
            assertTrue(draft.state.value.attachments.isEmpty())
            assertFalse(draft.state.value.loading)
        } finally { finish.complete(Unit); draft.clear() }
    }

    @Test fun newestSelectionSurvivesThePreviousReadFinishingLate() = runBlocking {
        val finishPrevious = CompletableDeferred<Unit>()
        val draft = MmsAttachmentDraft(this) { sources ->
            if (sources.single().endsWith("one")) withContext(NonCancellable) {
                finishPrevious.await(); MmsAttachmentLoader.Result.Loaded(listOf(image(1)))
            } else MmsAttachmentLoader.Result.Loaded(listOf(image(2)))
        }
        val previous = draft.select(listOf("content://test/one"))!!
        try {
            kotlinx.coroutines.yield()
            draft.select(listOf("content://test/two"))!!.join()
            finishPrevious.complete(Unit)
            previous.join()
            assertArrayEquals(byteArrayOf(2), draft.state.value.attachments.single().payload)
        } finally { finishPrevious.complete(Unit); draft.clear() }
    }

    @Test fun rejectedReplacementCannotKeepAnOldAttachmentReadyForSending() = runBlocking {
        val draft = MmsAttachmentDraft(this) { sources ->
            if (sources.single().endsWith("one")) MmsAttachmentLoader.Result.Loaded(listOf(image(1)))
            else MmsAttachmentLoader.Result.Rejected("ATTACHMENT_READ_FAILED")
        }
        draft.select(listOf("content://test/one"))!!.join()
        draft.select(listOf("content://test/unreadable"))!!.join()
        assertTrue(draft.state.value.attachments.isEmpty())
        assertEquals("ATTACHMENT_READ_FAILED", draft.state.value.errorCode)
        assertFalse(draft.state.value.loading)
    }

    @Test fun tooManyImagesAreRejectedBeforeReadingThem() = runBlocking {
        val draft = MmsAttachmentDraft(this) { error("must not read an oversized selection") }
        assertNull(draft.select(List(9) { "content://test/$it" }))
        assertEquals("TOO_MANY_ATTACHMENTS", draft.state.value.errorCode)
        assertTrue(draft.state.value.attachments.isEmpty())
    }

    @Test fun unexpectedReadFailureIsReportedWithoutPublishingAnAttachment() = runBlocking {
        val draft = MmsAttachmentDraft(this) { throw IllegalStateException("provider failure") }
        draft.select(listOf("content://test/one"))!!.join()
        assertEquals("ATTACHMENT_READ_FAILED", draft.state.value.errorCode)
        assertTrue(draft.state.value.attachments.isEmpty())
        assertFalse(draft.state.value.loading)
    }
}
