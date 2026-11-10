package com.sentinel.quantum.security

/**
 * Opt-in for downloading official ARCEP and RTR allocation copies.
 * A missing preference is not consent, including installs that previously treated the absent key as on.
 */
internal object PublishedCatalogRefreshPreference {
    fun resolved(stored: Boolean?): Boolean = stored == true
}
