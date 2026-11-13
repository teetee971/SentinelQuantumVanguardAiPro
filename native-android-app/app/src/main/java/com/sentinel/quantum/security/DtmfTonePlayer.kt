package com.sentinel.quantum.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** UI-thread owned: each digit ends the previous tone; leaving the UI cancels the tone. */
internal class DtmfTonePlayer(private val scope: CoroutineScope, private val durationMs: Long = 180L) {
    private var current: Job? = null

    fun play(start: () -> Boolean, stop: () -> Unit) {
        val previous = current
        current = scope.launch {
            previous?.cancelAndJoin()
            if (start()) {
                try {
                    delay(durationMs)
                } finally {
                    stop()
                }
            }
        }
    }
}
