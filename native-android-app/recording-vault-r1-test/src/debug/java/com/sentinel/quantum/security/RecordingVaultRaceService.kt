package com.sentinel.quantum.security

import android.app.Service
import android.content.Intent
import android.os.*
import java.io.File

/** Debug APK only; two non-exported processes exercise the real Store and KeyStore. */
open class RecordingVaultRaceService : Service() {
    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            val id = message.data.getString("id")!!
            val writer = message.data.getString("writer")!!
            val reply = message.replyTo
            require(Regex("r3race_[a-f0-9]{32}").matches(id))
            require(writer == "a" || writer == "b")
            Thread {
                val result = Bundle()
                result.putInt("pid", Process.myPid())
                result.putString("writer", writer)
                try {
                    File(noBackupFilesDir, "$id-ready-$writer").writeText("ready")
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    while (!File(noBackupFilesDir, "$id-go").exists()) {
                        check(SystemClock.elapsedRealtime() < deadline) { "gate timeout" }
                        Thread.sleep(10)
                    }
                    val vault = RecordingVaultStore(this)
                    try {
                        vault.save(id, "writer-$writer".toByteArray())
                        result.putString("status", "saved")
                    } catch (e: IllegalStateException) {
                        // Characterize rejection only; do not infer that every IllegalStateException
                        // is a duplicate. Preserve its cause in the result for device review.
                        val value = vault.read(id).toString(Charsets.UTF_8)
                        check(value == "writer-a" || value == "writer-b")
                        result.putString("status", "rejected")
                        result.putString("rejection", "${e.javaClass.name}:${e.message}")
                    }
                } catch (e: Exception) {
                    result.putString("status", "error:${e.javaClass.name}:${e.message}")
                }
                reply.send(Message.obtain().apply { data = result })
            }.start()
            true
        })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
}
class RecordingVaultRaceServiceA : RecordingVaultRaceService()
class RecordingVaultRaceServiceB : RecordingVaultRaceService()
