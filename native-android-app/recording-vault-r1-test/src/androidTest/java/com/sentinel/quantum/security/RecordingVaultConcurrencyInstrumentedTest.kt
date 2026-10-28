package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultConcurrencyInstrumentedTest {

    @Test
    fun concurrentInstancesCannotOverwriteRecording() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val id = "race_" +
            UUID.randomUUID().toString().replace("-", "")

        val workers = 8
        val pool = Executors.newFixedThreadPool(workers)
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val success = AtomicInteger(0)

        val vault = RecordingVaultStore(context)

        try {
            val tasks = (0 until workers).map { index ->
                pool.submit {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))

                    try {
                        RecordingVaultStore(context).save(
                            id,
                            "writer-$index".toByteArray()
                        )
                        success.incrementAndGet()
                    } catch (_: IllegalStateException) {
                        // Le doublon doit être rejeté.
                    }
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            tasks.forEach {
                it.get(20, TimeUnit.SECONDS)
            }

            assertEquals(1, success.get())
            assertTrue(vault.exists(id))
            assertTrue(
                vault.read(id).toString(Charsets.UTF_8)
                    .startsWith("writer-")
            )
        } finally {
            start.countDown()
            pool.shutdownNow()
            vault.delete(id)
        }
    }
}
