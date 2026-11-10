package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-memory view of contact phone numbers. CallScreeningService may only read [current].
 * Contacts queries stay on the maintenance and settings paths.
 */
internal object CallDirectoryCache {
    const val MAX_NUMBERS = 10_000

    data class View(val ready: Boolean, val numbers: Set<String>)

    private sealed interface Load {
        data class Ready(val numbers: Set<String>) : Load
        data object Truncated : Load
        data object Unavailable : Load
    }

    @Volatile
    private var view: View = View(ready = false, numbers = emptySet())

    private val observing = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-call-directory").apply { isDaemon = true }
    }

    fun current(): View = view

    /**
     * @return false only when a readable directory could not be queried and a later retry may succeed.
     * Truncation and a missing permission fail open: screening must not silence every caller.
     */
    fun refresh(context: Context, silenceOutsideDirectory: Boolean): Boolean {
        val app = context.applicationContext
        if (!silenceOutsideDirectory) {
            view = View(ready = false, numbers = emptySet())
            return true
        }
        if (
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            view = View(ready = false, numbers = emptySet())
            return true
        }
        when (val loaded = runCatching { loadNumbers(app) }.getOrElse { return false }) {
            Load.Unavailable -> {
                view = View(ready = false, numbers = emptySet())
                return false
            }
            Load.Truncated -> {
                view = View(ready = false, numbers = emptySet())
                return true
            }
            is Load.Ready -> {
                view = View(ready = true, numbers = loaded.numbers)
                ensureObserving(app)
                return true
            }
        }
    }

    private fun loadNumbers(context: Context): Load {
        val numbers = LinkedHashSet<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        ) ?: return Load.Unavailable
        cursor.use { rows ->
            val index = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (index < 0) return Load.Ready(emptySet())
            while (rows.moveToNext()) {
                if (numbers.size >= MAX_NUMBERS) return Load.Truncated
                val canonical = CallRuleEngine.normalizeNumber(rows.getString(index)) ?: continue
                numbers += canonical
            }
        }
        return Load.Ready(numbers)
    }

    private fun ensureObserving(context: Context) {
        if (!observing.compareAndSet(false, true)) return
        val app = context.applicationContext
        app.contentResolver.registerContentObserver(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    executor.execute {
                        val enabled = runCatching {
                            CallBlocklistStore(app).silenceOutsideDirectory()
                        }.getOrDefault(false)
                        refresh(app, enabled)
                    }
                }
            }
        )
    }
}
