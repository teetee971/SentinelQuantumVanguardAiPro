package com.sentinel.quantum.security

import android.content.Context
import java.security.MessageDigest

data class TrackerRepeatState(val sightings: Int, val places: Int)

/** Same Find My announcement seen again. A repeat is not proof of following. */
object TrackerRepeat {
    fun identity(payload: ByteArray): String {
        val stable = payload.copyOf()
        if (stable.size > 2) stable[2] = 0
        return sha256(stable)
    }

    fun place(bssid: String): String? {
        val normalized = bssid.trim().lowercase()
        if (normalized.isBlank() || normalized == "02:00:00:00:00:00") return null
        return sha256(normalized.toByteArray())
    }

    fun label(sightings: Int, places: Int): String = when {
        sightings >= 3 && places >= 2 ->
            "Annonce revue au moins 3 fois sur des réseaux différents. Indice de déplacement, pas une preuve de suivi."
        sightings >= 2 ->
            "Annonce revue $sightings fois sans changement de réseau. Cela peut être un objet qui reste ici."
        else -> "Annonce vue une fois. Pas un suivi."
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class TrackerRepeatMemory(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(identity: String): TrackerRepeatState = synchronized(LOCK) {
        parse(preferences.getString(KEY, null)).entries[identity] ?: TrackerRepeatState(0, 0)
    }

    fun record(identity: String, place: String?): TrackerRepeatState = synchronized(LOCK) {
        if (!hex(identity) || (place != null && !hex(place))) return TrackerRepeatState(0, 0)
        val entries = parse(preferences.getString(KEY, null)).entries.toMutableMap()
        val previous = entries[identity]
        val places = previous?.placeKeys.orEmpty().toMutableSet()
        if (place != null && places.size < MAX_PLACES) places += place
        val next = Stored(sightings = (previous?.sightings ?: 0) + 1, placeKeys = places)
        entries[identity] = next
        while (entries.size > MAX_IDENTITIES) {
            entries.remove(entries.keys.first())
        }
        preferences.edit().putString(KEY, write(entries)).apply()
        TrackerRepeatState(next.sightings, next.placeKeys.size)
    }

    private fun hex(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun parse(raw: String?): Parsed {
        if (raw.isNullOrBlank()) return Parsed(linkedMapOf())
        val entries = linkedMapOf<String, Stored>()
        raw.lineSequence().forEach { line ->
            val parts = line.split('|')
            if (parts.size != 3 || !hex(parts[0])) return@forEach
            val count = parts[1].toIntOrNull() ?: return@forEach
            if (count !in 1..10_000) return@forEach
            val places = parts[2].split(',').filter { it.isNotEmpty() && hex(it) }.take(MAX_PLACES).toSet()
            entries[parts[0]] = Stored(count, places)
        }
        return Parsed(entries)
    }

    private fun write(entries: Map<String, Stored>): String =
        entries.entries.joinToString("\n") { (identity, stored) ->
            "$identity|${stored.sightings}|${stored.placeKeys.joinToString(",")}"
        }

    private data class Stored(val sightings: Int, val placeKeys: Set<String>)
    private data class Parsed(val entries: Map<String, Stored>)

    private companion object {
        const val PREFERENCES = "tracker_repeat"
        const val KEY = "entries"
        const val MAX_IDENTITIES = 40
        const val MAX_PLACES = 8
        val LOCK = Any()
    }
}
