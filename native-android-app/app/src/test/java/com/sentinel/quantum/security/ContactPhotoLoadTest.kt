package com.sentinel.quantum.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPhotoLoadTest {
    @Test fun canceledReadCannotPublishAfterTheCurrentContactHasLoaded() = runBlocking {
        val previous = ContactPhotoIdentity(12, "content://com.android.contacts/photo/12")
        val current = previous.copy(contactId = 13, uri = "content://com.android.contacts/photo/13")
        val finishPrevious = CompletableDeferred<Unit>()
        var displayed: ContactPhotoSnapshot<String>? = null
        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) {
            displayed = loadContactPhoto(previous) {
                withContext(NonCancellable) {
                    finishPrevious.await()
                    "previous contact pixels"
                }
            }
        }
        try {
            oldJob.cancel()
            displayed = loadContactPhoto(current) { "current contact pixels" }
            finishPrevious.complete(Unit)
            oldJob.join()
            assertEquals("current contact pixels", displayed?.forIdentity(current))
        } finally {
            finishPrevious.complete(Unit)
            oldJob.cancel()
        }
    }
}
