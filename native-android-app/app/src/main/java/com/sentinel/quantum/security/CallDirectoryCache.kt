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
 * Process-memory view of contact phone numbers.
 * [currentForScreening] may check READ_CONTACTS and then reads memory only.
 * Contacts queries stay on the maintenance, startup and settings paths.
 * Turning silence off drops this view without querying the provider.
 */
internal object CallDirectoryCache {
    const val MAX_NUMBERS = DirectoryPublication.MAX_NUMBERS

    typealias View = DirectoryPublication.View

    private sealed interface Load {
        data class Ready(val numbers: Set<String>) : Load
        data object Truncated : Load
        data object Unavailable : Load
    }

    private val publication = DirectoryPublication()
    private val observing = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-call-directory").apply { isDaemon = true }
    }

    fun current(): View = publication.current()

    /**
     * Screening path. A revoked permission is detected here and the directory is dropped
     * before the decision, so a stale ready view cannot keep silencing callers.
     * The publication generation also stops an in-flight refresh from publishing that view again.
     * This does not query [ContactsContract].
     */
    fun currentForScreening(context: Context): View {
        if (!contactsPermissionGranted(context)) {
            return publication.closeBecausePermissionMissing()
        }
        return publication.current()
    }

    /**
     * Preference path. Disabling publishes an empty view on the caller thread and does not
     * read contacts. Enabling reloads on the directory thread; until that publish, screening
     * stays fail-open even though the snapshot flag is already true.
     */
    fun onSilencePreferenceChanged(context: Context, enabled: Boolean) {
        if (!enabled) {
            refresh(context, false)
            return
        }
        executor.execute { refresh(context.applicationContext, true) }
    }

    /**
     * @return false only when a readable directory could not be queried and a later retry may succeed.
     * Truncation, a missing permission and a query failure fail open: the published view is not ready,
     * never a partial set. A newer refresh or a detected revocation supersedes an older query.
     */
    fun refresh(context: Context, silenceOutsideDirectory: Boolean): Boolean {
        val app = context.applicationContext
        val ticket = publication.openRefresh()
        fun publish(next: View) = publication.publish(ticket, next, contactsPermissionGranted(app))
        if (!silenceOutsideDirectory) {
            publish(View(ready = false, numbers = emptySet()))
            return true
        }
        if (!contactsPermissionGranted(app)) {
            publish(View(ready = false, numbers = emptySet()))
            return true
        }
        val loaded = runCatching { loadNumbers(app) }.getOrElse {
            if (publication.isLatest(ticket)) publish(View(ready = false, numbers = emptySet()))
            return !publication.isLatest(ticket)
        }
        if (!publication.isLatest(ticket)) return true
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
                if (publication.isLatest(ticket)) ensureObserving(app)
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
                if (!DirectoryPublication.hasRoom(numbers.size)) return Load.Truncated
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
