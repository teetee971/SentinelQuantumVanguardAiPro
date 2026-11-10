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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-memory view of contact phone numbers.
 * [currentForScreening] may check READ_CONTACTS and then reads memory only.
 * Contacts queries stay on the maintenance, startup and settings paths.
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

    private val generation = AtomicInteger()
    private val observing = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-call-directory").apply { isDaemon = true }
    }

    fun current(): View = view

    /**
     * Screening path. A revoked permission is detected here and the directory is dropped
     * before the decision, so a stale ready view cannot keep silencing callers.
     * This does not query [ContactsContract].
     */
    fun currentForScreening(context: Context): View {
        if (!contactsPermissionGranted(context)) {
            val closed = View(ready = false, numbers = emptySet())
            view = closed
            return closed
        }
        return view
    }

    /**
     * @return false only when a readable directory could not be queried and a later retry may succeed.
     * Truncation and a missing permission fail open: the published view is not ready, never a partial set.
     * A newer refresh supersedes an older in-flight query.
     */
    fun refresh(context: Context, silenceOutsideDirectory: Boolean): Boolean {
        val app = context.applicationContext
        val ticket = generation.incrementAndGet()
        fun stillLatest() = generation.get() == ticket
        fun publish(next: View) {
            if (stillLatest()) view = next
        }
        if (!silenceOutsideDirectory) {
            publish(View(ready = false, numbers = emptySet()))
            return true
        }
        if (!contactsPermissionGranted(app)) {
            publish(View(ready = false, numbers = emptySet()))
            return true
        }
        val loaded = runCatching { loadNumbers(app) }.getOrElse {
            return stillLatest().not()
        }
        if (!stillLatest()) return true
        if (!contactsPermissionGranted(app)) {
            publish(View(ready = false, numbers = emptySet()))
            return true
        }
        return when (loaded) {
            Load.Unavailable -> {
                publish(View(ready = false, numbers = emptySet()))
                false
            }
            Load.Truncated -> {
                publish(View(ready = false, numbers = emptySet()))
                true
            }
            is Load.Ready -> {
                publish(View(ready = true, numbers = loaded.numbers))
                if (stillLatest()) ensureObserving(app)
                true
            }
        }
    }

    private fun contactsPermissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

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
