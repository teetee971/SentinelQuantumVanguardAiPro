package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** App-private noBackupFilesDir, atomically replaced. No Room, export, contact write or remote I/O. */
class ContactSocialProfileStore internal constructor(private val readRaw: () -> String?, private val writeRaw: (String) -> Boolean) {
    constructor(context: Context) : this(
        { ContactSocialProfileFile(context.applicationContext.noBackupFilesDir).read() },
        { ContactSocialProfileFile(context.applicationContext.noBackupFilesDir).write(it) }
    )

    fun read(owner: String): ContactSocialLinksResult = synchronized(lock) {
        if (!validOwner(owner)) return@synchronized ContactSocialLinksResult(false)
        val rows = decode() ?: return@synchronized ContactSocialLinksResult(false)
        ContactSocialLinksResult(true, rows.filter { it.owner == owner })
    }

    fun associate(owner: String, network: SocialNetwork, url: String, summary: String, atMs: Long, confirmed: Boolean): Boolean = synchronized(lock) {
        if (!confirmed || !validOwner(owner) || atMs <= 0 || summary.length > 2000 || summary.any { it.isISOControl() && it !in "\n\t" }) return@synchronized false
        val canonical = ContactSocialProfiles.canonical(network, url) ?: return@synchronized false
        val rows = decode() ?: return@synchronized false
        val updated = rows.filterNot { it.owner == owner && it.network == network } + ContactSocialLink(owner, network, canonical, summary.trim(), atMs)
        persist(updated)
    }

    fun remove(owner: String, network: SocialNetwork): Boolean = synchronized(lock) {
        if (!validOwner(owner)) return@synchronized false
        val rows = decode() ?: return@synchronized false
        persist(rows.filterNot { it.owner == owner && it.network == network })
    }

    private fun decode(): List<ContactSocialLink>? = runCatching {
        val raw = readRaw() ?: return emptyList()
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        val obj = JSONObject(raw)
        if (obj.opt("version") != 1) return null
        val array = obj.getJSONArray("links")
        if (array.length() > MAX_LINKS) return null
        val seen = mutableSetOf<Pair<String, SocialNetwork>>()
        (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            val owner = row.getString("owner")
            val network = SocialNetwork.valueOf(row.getString("network"))
            val url = row.getString("url")
            val summary = row.getString("summary")
            val rawTime = row.opt("associatedAtMs")
            if (rawTime !is Long && rawTime !is Int) return null
            val time = (rawTime as Number).toLong()
            if (!validOwner(owner) || row.opt("source") != "USER_CONFIRMED_LINK" || time <= 0 ||
                summary.length > 2000 || summary.any { it.isISOControl() && it !in "\n\t" } || ContactSocialProfiles.canonical(network, url) != url || !seen.add(owner to network)) return null
            ContactSocialLink(owner, network, url, summary, time)
        }
    }.getOrNull()

    private fun persist(rows: List<ContactSocialLink>): Boolean {
        if (rows.size > MAX_LINKS) return false
        val array = JSONArray()
        rows.forEach { row -> array.put(JSONObject().put("owner", row.owner).put("network", row.network.name)
            .put("url", row.url).put("summary", row.summary).put("associatedAtMs", row.associatedAtMs).put("source", "USER_CONFIRMED_LINK")) }
        val raw = JSONObject().put("version", 1).put("links", array).toString()
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return false
        return runCatching { writeRaw(raw) }.getOrDefault(false)
    }

    companion object {
        private val lock = Any()
        private const val MAX_LINKS = 200
        private const val MAX_BYTES = 1024 * 1024
        private fun validOwner(owner: String) = owner.matches(Regex("[0-9a-f]{64}"))
    }
}
