package com.sentinel.quantum.security

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DtmfTonePlayerTest {
    @Test fun leavingTheKeypadStopsTheActiveTone() = runBlocking {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(coroutineContext + Job())
        val player = DtmfTonePlayer(scope, 60_000)
        player.play({ events.add("start"); true }, { events.add("stop") })
        yield()
        scope.cancel()
        yield()
        assertEquals(listOf("start", "stop"), events)
    }

    @Test fun nextDigitStopsPreviousToneBeforeStarting() = runBlocking {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(coroutineContext + Job())
        val player = DtmfTonePlayer(scope, 60_000)
        player.play({ events.add("1"); true }, { events.add("stop1") })
        yield()
        player.play({ events.add("2"); true }, { events.add("stop2") })
        repeat(4) { yield() }
        scope.cancel()
        yield()
        assertEquals(listOf("1", "stop1", "2", "stop2"), events)
    }

    @Test fun refusedToneDoesNotStopAnUnrelatedTone() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        var stopped = false
        DtmfTonePlayer(scope, 1).play({ false }, { stopped = true })
        yield()
        scope.cancel()
        assertFalse(stopped)
    }
}
