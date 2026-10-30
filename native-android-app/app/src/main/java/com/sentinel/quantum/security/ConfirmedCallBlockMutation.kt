package com.sentinel.quantum.security

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared execution boundary for explicit additions from number actions, outside Telecom. */
internal object ConfirmedCallBlockMutation {
    private val additions = Mutex()

    suspend fun execute(validate: suspend () -> Boolean, isCurrent: () -> Boolean,
        alreadyListed: suspend () -> Boolean, add: suspend () -> Boolean): Boolean? = additions.withLock {
        currentCoroutineContext().ensureActive()
        if (!validate()) return@withLock null
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) return@withLock null
        val exists = alreadyListed()
        // Provider/Keystore work can complete even after cancellation; check after those reads.
        currentCoroutineContext().ensureActive()
        if (!validate()) return@withLock null
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) return@withLock null
        if (exists) true else add()
    }
}
