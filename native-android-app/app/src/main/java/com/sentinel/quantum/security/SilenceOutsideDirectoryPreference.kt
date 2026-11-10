package com.sentinel.quantum.security

/**
 * Opt-in for silencing callers who are not in the contact directory.
 *
 * A missing preference is not a choice. Installs that never stored the key stay fail-open,
 * including upgrades from the previous implicit default of true. Only a stored true enables it.
 */
internal object SilenceOutsideDirectoryPreference {
    fun resolved(stored: Boolean?): Boolean = stored == true
}
