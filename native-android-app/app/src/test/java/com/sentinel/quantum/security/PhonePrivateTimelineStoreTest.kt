package com.sentinel.quantum.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhonePrivateTimelineStoreTest {
    private lateinit var context: Context
    private lateinit var store: PhonePrivateTimelineStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PhonePrivateTimelineStore(context)
        store.clear()
    }

    @Test
    fun rejectsFutureEvent() {
        val now = 1_000L
        val accepted = store.append(
            PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.SMS,
                timestampMs = now + 1,
                direction = "INCOMING",
                signal = "RISK_HIGH"
            ),
            now
        )
        assertFalse(accepted)
        assertTrue(store.read(now).events.isEmpty())
    }

    @Test
    fun storesOnlySanitizedBoundedMetadata() {
        val now = 10_000L
        assertTrue(
            store.append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.SMS,
                    timestampMs = now,
                    direction = " INCOMING ",
                    signal = "RISK_HIGH:<secret>"
                ),
                now
            )
        )
        val event = store.read(now).events.single()
        assertEquals("INCOMING", event.direction)
        assertEquals("RISK_HIGH:secret", event.signal)
        assertFalse(event.toString().contains("<secret>"))
    }

    @Test
    fun boundsHistoryToOneHundredEvents() {
        val now = 1_000_000L
        repeat(120) { index ->
            assertTrue(
                store.append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.CALL,
                        timestampMs = now - index,
                        direction = "INCOMING",
                        signal = "ALLOW"
                    ),
                    now
                )
            )
        }
        assertEquals(100, store.read(now).events.size)
    }

    @Test
    fun dropsEventsOlderThanThirtyDays() {
        val now = 40L * 24L * 60L * 60L * 1000L
        val old = now - (31L * 24L * 60L * 60L * 1000L)
        assertTrue(
            store.append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.CALL,
                    timestampMs = old,
                    direction = "INCOMING",
                    signal = "ALLOW"
                ),
                now
            )
        )
        assertTrue(store.read(now).events.isEmpty())
    }
}
