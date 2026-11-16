package com.sentinel.quantum.security

import android.content.Context

/** Local memory of announcement fingerprints only. Names and addresses are not stored. */
object JoinedLanSighting {
    fun acceptable(fingerprint: String): Boolean =
        fingerprint.length == 64 && fingerprint.all { it in '0'..'9' || it in 'a'..'f' }

    fun label(alreadyKnown: Boolean): String =
        if (alreadyKnown) "déjà annoncé sur cet appareil" else "première annonce enregistrée ici"
}

class JoinedLanSightingMemory(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun known(fingerprints: Collection<String>): Set<String> {
        val stored = preferences.getStringSet(KEY, emptySet()).orEmpty()
        return fingerprints.filter { JoinedLanSighting.acceptable(it) && it in stored }.toSet()
    }

    fun remember(fingerprints: Collection<String>) {
        val accepted = fingerprints.filter { JoinedLanSighting.acceptable(it) }
        if (accepted.isEmpty()) return
        val next = preferences.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        next.addAll(accepted)
        while (next.size > MAX_STORED) {
            next.remove(next.first())
        }
        preferences.edit().putStringSet(KEY, next.toSet()).apply()
    }

    private companion object {
        const val PREFERENCES = "joined_lan_sightings"
        const val KEY = "fingerprints"
        const val MAX_STORED = 200
    }
}
