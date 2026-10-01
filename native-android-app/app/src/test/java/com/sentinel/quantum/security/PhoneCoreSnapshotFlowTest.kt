package com.sentinel.quantum.security

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhoneCoreSnapshotFlowTest {
    @Test fun emitsChangesAndStopsReadingAfterCancellation() = runBlocking {
        val facts = listOf(false, false, true, true, false)
        var reads = 0
        val observed = phoneCoreSnapshots(
            read = { facts[reads++] },
            pause = {}
        ).take(3).toList()
        assertEquals(listOf(false, true, false), observed)
        assertEquals(5, reads)
    }

    @Test fun readerFailureIsNotConvertedToReady() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                phoneCoreSnapshots<Boolean>(
                    read = { throw IllegalStateException("unavailable") },
                    pause = {}
                ).take(1).toList()
            }
        }
    }
}

