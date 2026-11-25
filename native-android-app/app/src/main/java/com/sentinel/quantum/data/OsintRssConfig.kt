package com.sentinel.quantum.data

/** Which public RSS feeds are read. JSON sources are not part of this switch. */
internal object OsintRssConfig {
    fun rssSources(): List<OsintSource> =
        OsintSource.entries.filter { it.kind == OsintSourceKind.RSS }

    /** A missing preference means every RSS feed. An empty set means the user turned them off. */
    fun selection(storedNames: Set<String>?): Set<OsintSource> {
        val allowed = rssSources().associateBy { it.name }
        if (storedNames == null) return allowed.values.toSet()
        return storedNames.mapNotNull { allowed[it] }.toSet()
    }

    fun fetchSources(storedNames: Set<String>?): List<OsintSource> {
        val selected = selection(storedNames)
        return OsintSource.entries.filter { source ->
            source.kind != OsintSourceKind.RSS || source in selected
        }
    }
}
