package com.sentinel.quantum.security

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultProcessConcurrencyInstrumentedTest {
    @Test fun twoAndroidProcessesCannotOverwriteRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "r3race_" + UUID.randomUUID().toString().replace("-", "")
        val results = CopyOnWriteArrayList<Bundle>()
        val finished = CountDownLatch(2)
        val replies = Messenger(Handler(Looper.getMainLooper()) {
            results.add(it.data)
            finished.countDown()
            true
        })
        val connections = mutableListOf<ServiceConnection>()
        val vault = RecordingVaultStore(context)
        try {
            for (writer in listOf("a", "b")) {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        Messenger(binder).send(Message.obtain().apply {
                            replyTo = replies
                            data = Bundle().apply { putString("id", id); putString("writer", writer) }
                        })
                    }
                    override fun onServiceDisconnected(name: ComponentName) {}
                }
                val intent = Intent().setClassName(context.packageName,
                    "com.sentinel.quantum.security.RecordingVaultRaceService${writer.uppercase()}")
                assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
                connections.add(connection)
            }
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!listOf("a", "b").all { File(context.noBackupFilesDir, "$id-ready-$it").exists() }) {
                assertTrue("Processes did not reach the gate", SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(10)
            }
            File(context.noBackupFilesDir, "$id-go").writeText("go")
            assertTrue(finished.await(30, TimeUnit.SECONDS))
            android.util.Log.i("SentinelR3", "RACE id=$id results=" + results.joinToString {
                "pid=${it.getInt("pid")},writer=${it.getString("writer")},status=${it.getString("status")},rejection=${it.getString("rejection")}"
            })
            assertEquals(2, results.map { it.getInt("pid") }.distinct().size)
            assertTrue(results.none { it.getInt("pid") == Process.myPid() })
            assertEquals(results.toString(), 1, results.count { it.getString("status") == "saved" })
            assertEquals(results.toString(), 1, results.count { it.getString("status") == "rejected" })
            val winner = results.single { it.getString("status") == "saved" }.getString("writer")
            assertEquals("writer-$winner", vault.read(id).toString(Charsets.UTF_8))
        } finally {
            connections.forEach { context.unbindService(it) }
            vault.delete(id)
            listOf("ready-a", "ready-b", "go").forEach { File(context.noBackupFilesDir, "$id-$it").delete() }
        }
    }
}
