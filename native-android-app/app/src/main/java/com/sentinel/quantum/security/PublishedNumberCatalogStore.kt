package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Disk copy of a published numbering catalog. Screening never reads this store.
 * A rejected document does not replace the previous accepted copy.
 */
internal object PublishedNumberCatalogStore {
    data class Stamp(val kind: PublishedCatalogPolicy.Kind, val fetchedAtMs: Long, val generatedAt: String?)

    private val lock = Any()
    @Volatile private var directory: File? = null

    fun bind(context: Context) {
        val dir = File(context.applicationContext.filesDir, "published-number-catalogs")
        if (!dir.isDirectory) dir.mkdirs()
        directory = dir
    }

    fun readFresh(kind: PublishedCatalogPolicy.Kind, now: Long = System.currentTimeMillis()): String? {
        val stamp = stamp(kind) ?: return null
        if (!PublishedCatalogPolicy.fresh(stamp.fetchedAtMs, now)) return null
        return readBody(kind)
    }

    fun isFresh(kind: PublishedCatalogPolicy.Kind, now: Long = System.currentTimeMillis()): Boolean =
        readFresh(kind, now) != null

    fun stamps(): List<Stamp> = PublishedCatalogPolicy.Kind.entries.mapNotNull(::stamp)

    fun commit(
        kind: PublishedCatalogPolicy.Kind,
        document: String,
        now: Long = System.currentTimeMillis()
    ): PublishedCatalogPolicy.Decision {
        val decision = PublishedCatalogPolicy.evaluate(kind, document)
        if (!decision.accepted) return decision
        val dir = directory ?: return PublishedCatalogPolicy.Decision(false, "CATALOG_UNBOUND")
        synchronized(lock) {
            val body = File(dir, fileName(kind))
            val meta = File(dir, metaName(kind))
            val tmpBody = File(dir, fileName(kind) + ".tmp")
            val tmpMeta = File(dir, metaName(kind) + ".tmp")
            tmpBody.writeText(document, Charsets.UTF_8)
            tmpMeta.writeText(
                listOf(now.toString(), decision.generatedAt.orEmpty()).joinToString("\n"),
                Charsets.UTF_8
            )
            if (!tmpBody.renameTo(body) || !tmpMeta.renameTo(meta)) {
                tmpBody.delete()
                tmpMeta.delete()
                return PublishedCatalogPolicy.Decision(false, "CATALOG_STORE_FAILED")
            }
        }
        return decision
    }

    private fun stamp(kind: PublishedCatalogPolicy.Kind): Stamp? {
        val dir = directory ?: return null
        val meta = File(dir, metaName(kind))
        if (!meta.isFile || !File(dir, fileName(kind)).isFile) return null
        val lines = runCatching { meta.readText(StandardCharsets.UTF_8).split('\n') }.getOrNull() ?: return null
        val fetched = lines.getOrNull(0)?.toLongOrNull() ?: return null
        val generated = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
        return Stamp(kind, fetched, generated)
    }

    private fun readBody(kind: PublishedCatalogPolicy.Kind): String? {
        val dir = directory ?: return null
        return runCatching { File(dir, fileName(kind)).readText(StandardCharsets.UTF_8) }.getOrNull()
    }

    private fun fileName(kind: PublishedCatalogPolicy.Kind): String = kind.name.lowercase() + ".json"

    private fun metaName(kind: PublishedCatalogPolicy.Kind): String = kind.name.lowercase() + ".meta"
}
