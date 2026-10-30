package com.sentinel.quantum.security

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ContactProfileDetailsTest {
    private val request = ContactProfileRequest(7, 10, 1)

    @Test fun duplicateRowsAreCollapsedButDifferentLabelsAndValuesRemain() {
        val fields = ContactProfileDetails.normalize(listOf(
            ContactProfileField(ContactProfileKind.EMAIL, "Personnel", " alice@example.invalid "),
            ContactProfileField(ContactProfileKind.EMAIL, "Personnel", "alice@example.invalid"),
            ContactProfileField(ContactProfileKind.EMAIL, "Travail", "work@example.invalid"),
            ContactProfileField(ContactProfileKind.NOTE, "", ""),
            ContactProfileField(ContactProfileKind.NOTE, "", "line one\nline two")
        ))
        assertEquals(listOf("alice@example.invalid", "work@example.invalid", "line one\nline two"), fields.map { it.value })
        assertEquals("Travail", fields[1].label)
    }

    @Test fun readyNumbersComeOnlyFromFreshProviderRows() {
        val result = ContactProfileSnapshot(request, ContactProfileStatus.READY, listOf(
            ContactProfileField(ContactProfileKind.PHONE, "Mobile", "01 23 45 67 89"),
            ContactProfileField(ContactProfileKind.PHONE, "Travail", "0123456789"),
            ContactProfileField(ContactProfileKind.EMAIL, "", "not-a-phone@example.invalid")
        ))
        assertEquals(listOf("01 23 45 67 89"), ContactProfileDetails.phoneNumbers(result))
        assertTrue(ContactProfileDetails.phoneNumbers(result.copy(fields = emptyList())).isEmpty())
    }

    @Test fun unavailableOrDeniedSnapshotsCannotSupplyActionNumbers() {
        val rows = listOf(ContactProfileField(ContactProfileKind.PHONE, "", "0123456789"))
        assertTrue(ContactProfileDetails.phoneNumbers(ContactProfileSnapshot(request, ContactProfileStatus.PERMISSION_REQUIRED, rows)).isEmpty())
        assertTrue(ContactProfileDetails.phoneNumbers(ContactProfileSnapshot(request, ContactProfileStatus.UNAVAILABLE, rows)).isEmpty())
        assertTrue(ContactProfileDetails.phoneNumbers(null).isEmpty())
    }

    @Test fun boundsProviderContentWithoutChangingSourceRows() {
        val source = ContactProfileField(ContactProfileKind.NOTE, "n".repeat(200), "x".repeat(20000))
        val result = ContactProfileDetails.normalize(listOf(source)).single()
        assertEquals(16384, result.value.length)
        assertEquals(80, result.label.length)
        assertEquals(20000, source.value.length)
    }

    @Test fun deniedPermissionNeverReadsProvider() = runBlocking {
        var reads = 0
        val result = loadContactProfile(request, { false }) { reads++; emptyList() }
        assertEquals(ContactProfileStatus.PERMISSION_REQUIRED, result.status)
        assertEquals(0, reads)
        assertTrue(result.fields.isEmpty())
    }

    @Test fun revocationDuringReadDiscardsPrivateFields() = runBlocking {
        var allowed = true
        val result = loadContactProfile(request, { allowed }) {
            allowed = false
            listOf(ContactProfileField(ContactProfileKind.NOTE, "", "private fixture"))
        }
        assertEquals(ContactProfileStatus.PERMISSION_REQUIRED, result.status)
        assertTrue(result.fields.isEmpty())
    }

    @Test fun unavailableProviderIsDistinctFromEmptyContact() = runBlocking {
        assertEquals(ContactProfileStatus.UNAVAILABLE, loadContactProfile(request, { true }) { null }.status)
        assertEquals(ContactProfileStatus.READY, loadContactProfile(request, { true }) { emptyList() }.status)
    }

    @Test fun snapshotCannotBeUsedForAnotherContactRevisionOrResume() = runBlocking {
        val result = loadContactProfile(request, { true }) { emptyList() }
        assertNotNull(result.forRequest(request))
        assertNull(result.forRequest(request.copy(contactId = 8)))
        assertNull(result.forRequest(request.copy(revision = 11)))
        assertNull(result.forRequest(request.copy(generation = 2)))
        assertNull(result.forRequest(request.copy(activation = 1)))
    }

    @Test fun cancelledPermissionFailureCannotPublishOverCurrentContact() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        var displayed: ContactProfileSnapshot? = null
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            displayed = loadContactProfile(request, { true }) {
                withContext(NonCancellable) { finish.await(); throw SecurityException("fixture revoked") }
            }
        }
        old.cancel()
        val current = request.copy(contactId = 8)
        displayed = loadContactProfile(current, { true }) { emptyList() }
        finish.complete(Unit)
        old.join()
        assertNotNull(displayed?.forRequest(current))
    }

    @Test fun cancelledOldProviderReadCannotPublishOverCurrentContact() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        var displayed: ContactProfileSnapshot? = null
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            displayed = loadContactProfile(request, { true }) {
                withContext(NonCancellable) { finish.await(); emptyList() }
            }
        }
        old.cancel()
        val current = request.copy(contactId = 8)
        displayed = loadContactProfile(current, { true }) { emptyList() }
        finish.complete(Unit)
        old.join()
        assertNotNull(displayed?.forRequest(current))
    }
}
